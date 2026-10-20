package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * 一条长期记忆。
 *
 * [ownerId] 是助手 id；[GLOBAL_OWNER] 表示所有助手共用的全局记忆。
 */
data class MemoryEntry(
    val id: String,
    val ownerId: String,
    val text: String,
    val createdAt: Long,
)

/**
 * 本机长期记忆（SharedPreferences + JSON）。
 *
 * 「记忆」开关只决定要不要把对应记忆注入系统提示词；关闭开关不会删数据。
 */
internal object MemoryStore {

    const val GLOBAL_OWNER = "__global__"
    private const val PREFS = "hub_memory"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRY_CHARS = 500
    private const val MAX_PROMPT_CHARS = 5000

    var entries: List<MemoryEntry> by mutableStateOf(emptyList())
        private set

    fun initialize(context: Context) {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return
        entries = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val owner = item.optString("ownerId")
                    val text = item.optString("text").trim()
                    if (id.isBlank() || owner.isBlank() || text.isBlank()) continue
                    add(MemoryEntry(id, owner, text, item.optLong("createdAt")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun forAssistant(id: String): List<MemoryEntry> =
        entries.filter { it.ownerId == id }.sortedByDescending { it.createdAt }

    fun global(): List<MemoryEntry> = forAssistant(GLOBAL_OWNER)

    fun add(context: Context, ownerId: String, text: String): MemoryEntry? {
        val clean = text.trim().take(MAX_ENTRY_CHARS)
        if (clean.isEmpty()) return null
        val entry = MemoryEntry(
            id = "m-" + UUID.randomUUID(),
            ownerId = ownerId,
            text = clean,
            createdAt = System.currentTimeMillis(),
        )
        entries = entries + entry
        persist(context)
        return entry
    }

    fun update(context: Context, id: String, text: String) {
        val clean = text.trim().take(MAX_ENTRY_CHARS)
        if (clean.isEmpty()) return
        entries = entries.map { if (it.id == id) it.copy(text = clean) else it }
        persist(context)
    }

    fun delete(context: Context, id: String) {
        entries = entries.filterNot { it.id == id }
        persist(context)
    }

    fun deleteAssistant(context: Context, assistantId: String) {
        entries = entries.filterNot { it.ownerId == assistantId }
        persist(context)
    }

    /** 按助手的两个开关拼出本轮相关的系统提示词；没命中时返回空串。 */
    fun promptFor(assistant: HubAssistant, userText: String): String {
        return formatPrompt(relevantFor(assistant, userText))
    }

    /** 这一轮真正会注入的长期记忆（和 promptFor 使用同一份列表）。 */
    fun activeFor(assistant: HubAssistant): List<MemoryEntry> =
        buildList {
            if (assistant.memory) addAll(forAssistant(assistant.id))
            if (assistant.globalMemory) addAll(global())
        }.take(30)

    /**
     * 只挑和当前问题相关的记忆。
     *
     * 明确提到「记得 / 之前 / 偏好」时视为用户在问记忆，取最近的几条；
     * 普通问题则要求问题与记忆至少有一个中文二元词或英文词重合，避免「求根公式」
     * 这种无关消息每次都把全部记忆塞进提示词。
     */
    fun relevantFor(assistant: HubAssistant, userText: String): List<MemoryEntry> {
        val candidates = activeFor(assistant)
        val query = userText.trim()
        if (candidates.isEmpty() || query.isEmpty()) return emptyList()
        val explicit = MEMORY_INTENT_TERMS.any { query.contains(it, ignoreCase = true) }
        val queryTokens = memoryTokens(query)
        return candidates
            .map { entry ->
                val overlap = if (queryTokens.isEmpty()) {
                    0
                } else {
                    queryTokens.intersect(memoryTokens(entry.text)).size
                }
                Triple(entry, overlap, entry.createdAt)
            }
            .filter { explicit || it.second > 0 }
            .sortedWith(
                compareByDescending<Triple<MemoryEntry, Int, Long>> { it.second }
                    .thenByDescending { it.third },
            )
            .take(6)
            .map { it.first }
    }

    /** 纯函数，便于单元测试：普通问题是否和一条记忆相关。 */
    internal fun isMemoryQueryRelevant(userText: String, memoryText: String): Boolean {
        val query = userText.trim()
        if (query.isEmpty()) return false
        if (MEMORY_INTENT_TERMS.any { query.contains(it, ignoreCase = true) }) return true
        val queryTokens = memoryTokens(query)
        if (queryTokens.isEmpty()) return false
        return queryTokens.any { it in memoryTokens(memoryText) }
    }

    private fun memoryTokens(text: String): Set<String> {
        val lower = text.lowercase(Locale.ROOT)
        val out = mutableSetOf<String>()
        WORD_TOKEN.findAll(lower).forEach { match ->
            if (match.value.length >= 2 && match.value !in STOP_TOKENS) {
                out += match.value
            }
        }
        CJK_RUN.findAll(lower).forEach { match ->
            val value = match.value
            if (value.length == 1) {
                if (value !in STOP_TOKENS) out += value
            } else {
                for (index in 0 until value.length - 1) {
                    val bigram = value.substring(index, index + 2)
                    if (bigram !in STOP_TOKENS) out += bigram
                }
            }
        }
        return out
    }

    /** 纯格式化，便于单元测试。 */
    internal fun formatPrompt(list: List<MemoryEntry>): String {
        if (list.isEmpty()) return ""
        val body = buildString {
            append("【长期记忆】")
            append('\n')
            list.take(30).forEach { entry ->
                append("- ").append(entry.text.trim()).append('\n')
            }
        }.trim().take(MAX_PROMPT_CHARS)
        return body
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("ownerId", entry.ownerId)
                put("text", entry.text)
                put("createdAt", entry.createdAt)
            })
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val WORD_TOKEN = Regex("[a-z0-9]{2,}")
    private val CJK_RUN = Regex("[\\u3400-\\u9fff]+")
    private val MEMORY_INTENT_TERMS = listOf(
        "记得", "记忆", "记住", "忘记", "之前", "上次", "以前",
        "我说过", "提过", "偏好", "习惯", "我是谁", "关于我", "我的资料",
    )
    private val STOP_TOKENS = setOf(
        "什么", "怎么", "如何", "可以", "告诉", "一下", "我的", "你的",
        "是否", "为什么", "这个", "那个", "现在", "今天", "请问", "帮我",
        "一个", "需要", "想要", "以及", "还是", "就是", "我们", "你们",
    )
}
