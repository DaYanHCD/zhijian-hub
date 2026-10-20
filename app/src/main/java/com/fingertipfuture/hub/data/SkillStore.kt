package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 技能（skills）仓库：**内置的 + 用户自己加的**。
 *
 * 一个技能就是一段系统提示词（见 [HubSkill]）。内置技能在 [HUB_SKILLS] 里，
 * 改不了也删不掉；用户加的（手动粘贴 / 从文件 / 从 GitHub 拉进来的 skill.md）
 * 存在这里，能改能删。
 *
 * `skill.md` 的格式（和 Anthropic 那套一样，用 `---` 包一段 front matter）：
 *
 * ```markdown
 * ---
 * name: 翻译
 * description: 把内容译成中文
 * ---
 * 你是专业翻译……（这段就是提示词）
 * ```
 *
 * 没有 front matter 也能收：名字取文件名的第一行、提示词就是全文。
 */
object SkillStore {

    private const val PREFS = "hub_skills"
    private const val KEY_ITEMS = "items"

    /** 用户自己加的技能。 */
    var userSkills: List<HubSkill> by mutableStateOf(emptyList())
        private set

    /** 全部技能：内置的在前，用户加的在后。 */
    val all: List<HubSkill> get() = HUB_SKILLS + userSkills

    fun initialize(context: Context) {
        userSkills = prefs(context).getString(KEY_ITEMS, null)?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        runCatching {
                            val json = array.getJSONObject(index)
                            HubSkill(
                                id = json.optString("id"),
                                name = json.optString("name").ifBlank { "未命名技能" },
                                description = json.optString("description"),
                                prompt = json.optString("prompt"),
                            )
                        }.getOrNull()
                            ?.takeIf { it.prompt.isNotBlank() }
                            ?.let { add(it) }
                    }
                }
            }.getOrNull()
        }.orEmpty()
    }

    fun byId(id: String): HubSkill? = all.firstOrNull { it.id == id }

    /** 内置技能不允许删改（内容跟着版本走）。 */
    fun isBuiltIn(id: String): Boolean = HUB_SKILLS.any { it.id == id }

    fun add(context: Context, name: String, description: String, prompt: String): HubSkill {
        val skill = HubSkill(
            id = "s-" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "未命名技能" },
            description = description,
            prompt = prompt,
        )
        userSkills = userSkills + skill
        persist(context)
        return skill
    }

    fun update(context: Context, skill: HubSkill) {
        val index = userSkills.indexOfFirst { it.id == skill.id }
        if (index < 0) return
        userSkills = userSkills.toMutableList().also { it[index] = skill }
        persist(context)
    }

    fun remove(context: Context, id: String) {
        if (isBuiltIn(id)) return
        userSkills = userSkills.filterNot { it.id == id }
        persist(context)
    }

    /**
     * 把一段 skill.md 解析成技能。
     *
     * [fallbackName] 用在「文件里没写 name」时（比如从文件名推）。
     */
    fun parseSkillMd(text: String, fallbackName: String): HubSkill? {
        val raw = text.replace("\r\n", "\n").trim()
        if (raw.isBlank()) return null
        var name = ""
        var description = ""
        var body = raw
        if (raw.startsWith("---")) {
            val end = raw.indexOf("\n---", startIndex = 3)
            if (end > 0) {
                val head = raw.substring(3, end)
                body = raw.substring(end + 4).trim()
                head.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("name:") -> name = trimmed.removePrefix("name:").trim().trim('"', '\'')
                        trimmed.startsWith("description:") ->
                            description = trimmed.removePrefix("description:").trim().trim('"', '\'')
                    }
                }
            }
        }
        if (body.isBlank()) return null
        return HubSkill(
            id = "s-" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { fallbackName.ifBlank { "未命名技能" } },
            description = description,
            prompt = body,
        )
    }

    /** 把技能还原成 skill.md 文本（「查看并修改」那一页用）。 */
    fun toMarkdown(skill: HubSkill): String = buildString {
        append("---\n")
        append("name: ").append(skill.name).append('\n')
        if (skill.description.isNotBlank()) append("description: ").append(skill.description).append('\n')
        append("---\n\n")
        append(skill.prompt)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(context: Context) {
        prefs(context).edit()
            .putString(KEY_ITEMS, JSONArray().apply {
                userSkills.forEach { skill ->
                    put(JSONObject().apply {
                        put("id", skill.id)
                        put("name", skill.name)
                        put("description", skill.description)
                        put("prompt", skill.prompt)
                    })
                }
            }.toString())
            .apply()
    }
}
