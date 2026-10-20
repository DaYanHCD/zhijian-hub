package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 助手的思考强度是「自动」时跟随全局设置；助手自己明确选过档位则以助手为准。
 *
 * 以前这里直接取了 `assistant.thinkingDepth`，导致聊天页和默认模型页的档位
 * 看起来能选、实际永远不生效。抽成纯函数，方便回归测试。
 */
internal fun resolvedThinkingDepth(assistantDepth: Int, globalDepth: Int): Int =
    if (assistantDepth == ThinkingDepthConfig.AUTO) globalDepth else assistantDepth

/** 聊天页「技能插件」选中的技能也算本轮可用的技能，不能让选择只停在设置里。 */
internal fun resolvedSkillIds(
    assistantSkillIds: List<String>,
    selectedSkillId: String,
): List<String> = (assistantSkillIds + selectedSkillId)
    .filter { it.isNotBlank() }
    .distinct()

/**
 * 一个助手（2026-10-16 用户定的结构）。
 *
 * 助手 = 一套人格 + 一套参数 + 一套它能用的东西：
 * 头像 / 名称 / 默认聊天模型 / 温度 / 思考强度 / 最大 token / 系统提示词 /
 * 能用哪些技能 / 记忆相关开关 / 它被允许用哪些能力（时间、位置、日历…）。
 *
 * ⚠️ 这些字段**真的会用**（见 `AssistantStore.effective*` 与 `ChatScreen`）：
 * 聊天时用的是**当前助手**的模型、温度、思考强度、最大 token、系统提示词与技能，
 * 而不是「模型与服务」里那套全局值（全局值仍然是默认助手的默认值）。
 */
data class HubAssistant(
    val id: String,
    val name: String,
    /** 内置但仍在开发：只在助手管理显示，副屏的「选助手」不列。 */
    val underDevelopment: Boolean = false,
    /** 自定义头像图片的路径（空 = 没设，退回模型标志 / 默认头像）。 */
    val avatarPath: String = "",
    /** 默认聊天模型：留空就用「模型与服务」里全局选中的那家。 */
    val providerId: String = "",
    val model: String = "",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /** 思考强度：0 关闭 / 1 自动 / 2 低 / 3 中等 / 4 高 / 5 超高 / 6 最大。 */
    val thinkingDepth: Int = ThinkingDepthConfig.AUTO,
    /** 这个助手的系统提示词。 */
    val systemPrompt: String = "",
    /** 这个助手能用的技能（HubSkills 里的 id）。 */
    val skillIds: List<String> = emptyList(),
    // ---- 记忆设置 ----
    val memory: Boolean = false,
    val globalMemory: Boolean = false,
    /** 参考历史聊天记录：关掉就只把当前这一条发出去（省 token）。 */
    val useHistory: Boolean = true,
    // ---- 权限与隐私（这个助手被允许用什么）----
    val permTime: Boolean = false,
    val permLocation: Boolean = false,
    val permScreenTime: Boolean = false,
    val permCalendar: Boolean = false,
    val permInstalledApps: Boolean = false,
    val permSms: Boolean = false,
    val permMms: Boolean = false,
    val permContacts: Boolean = false,
    val permActivity: Boolean = false,
)

/**
 * 助手列表（本机，SharedPreferences 一个 JSON）。
 *
 * 默认有一个助手「AI」（就是把以前那个内置助手当成第一个），用户可以在
 * 设置 › 通用设置 › 助手管理 里新建 / 复制 / 删除，并选一个当**当前助手** ——
 * 聊天的那个助手就是当前助手。
 */
object AssistantStore {

    private const val PREFS = "hub_assistants"
    private const val KEY_ITEMS = "items"
    private const val KEY_CURRENT = "current_id"
    private const val KEY_DEFAULT_PERMS = "default_permissions"
    private const val KEY_THINKING_V2 = "thinking_depth_v2"
    private const val KEY_TRANSLATION_DEFAULT = "translation_default_v1"

    /** 内置的两个“正在开发”助手。 */
    private val BUILTIN_ASSISTANTS = listOf(
        HubAssistant(
            id = "builtin-virtual-friend",
            name = "虚拟网友",
            underDevelopment = true,
            skillIds = listOf(TRANSLATION_SKILL_ID),
        ),
        HubAssistant(
            id = "builtin-butler",
            name = "管家",
            underDevelopment = true,
            skillIds = listOf(TRANSLATION_SKILL_ID),
        ),
    )

    /** 一个空壳，兜底用（正常路径上 items 至少有一个）。 */
    private val FALLBACK = HubAssistant(
        id = "default",
        name = "AI",
        skillIds = listOf(TRANSLATION_SKILL_ID),
    )

    var items: List<HubAssistant> by mutableStateOf(emptyList())
        private set

    var currentId: String by mutableStateOf("")
        private set

    /**
     * 全局「权限与隐私」那一页里的开关 = **新建助手时的默认权限**（2026-10-16）。
     * 已经建好的助手各用各的（助手管理里那个页面改的是它自己）。
     */
    var defaultPermissions: HubAssistant by mutableStateOf(HubAssistant(id = "_perm_defaults", name = "默认"))
        private set

    /** 当前聊天用的助手。 */
    val current: HubAssistant
        get() = items.firstOrNull { it.id == currentId && !it.underDevelopment }
            ?: items.firstOrNull { !it.underDevelopment }
            ?: FALLBACK

    fun initialize(context: Context) {
        val prefs = prefs(context)
        val rawItems = prefs.getString(KEY_ITEMS, null)
        val loaded = rawItems?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        runCatching { fromJson(array.getJSONObject(index)) }
                            .getOrNull()
                            ?.let { add(it) }
                    }
                }
            }.getOrNull()
        }
        val configCorrupt = rawItems != null && loaded == null
        val merged = (loaded?.takeIf { it.isNotEmpty() } ?: listOf(FALLBACK))
            .map { assistant ->
                // 旧的内置技能已清理（现有全新「翻译」）；把失效的技能 id 一并迁移掉。
                if (assistant.skillIds.any(RETIRED_HUB_SKILL_IDS::contains)) {
                    assistant.copy(
                        skillIds = assistant.skillIds.filterNot(RETIRED_HUB_SKILL_IDS::contains),
                    )
                } else {
                    assistant
                }
            }
            .toMutableList()
        BUILTIN_ASSISTANTS.forEach { builtin ->
            val index = merged.indexOfFirst { it.id == builtin.id }
            if (index < 0) {
                merged += builtin
            } else {
                // 状态由 App 管：老数据 / 复制出来的同 id 项不能把「正在开发」弄丢。
                merged[index] = merged[index].copy(underDevelopment = true)
            }
        }
        // 「当前助手」必须是一个可用的普通助手；开发中的内置项不参与兜底。
        if (merged.none { !it.underDevelopment }) merged.add(0, FALLBACK)
        items = merged
        currentId = prefs.getString(KEY_CURRENT, null)
            ?.takeIf { id -> items.any { it.id == id && !it.underDevelopment } }
            ?: items.firstOrNull { !it.underDevelopment }?.id
            ?: FALLBACK.id
        defaultPermissions = prefs.getString(KEY_DEFAULT_PERMS, null)?.let { permRaw ->
            runCatching { fromJson(JSONObject(permRaw)) }.getOrNull()
        } ?: HubAssistant(id = "_perm_defaults", name = "默认")
        if (!prefs.getBoolean(KEY_THINKING_V2, false)) {
            items = items.map {
                it.copy(thinkingDepth = ThinkingDepthConfig.fromLegacy(it.thinkingDepth))
            }
            prefs.edit().putBoolean(KEY_THINKING_V2, true).apply()
        }
        // 2026-10-17 用户要求所有助手默认开启翻译。只补一次：
        // 之后用户在某个助手里手动关掉，不会被下次启动强行打开。
        if (!prefs.getBoolean(KEY_TRANSLATION_DEFAULT, false)) {
            items = items.map { assistant ->
                if (TRANSLATION_SKILL_ID in assistant.skillIds) {
                    assistant
                } else {
                    assistant.copy(skillIds = listOf(TRANSLATION_SKILL_ID) + assistant.skillIds)
                }
            }
            prefs.edit().putBoolean(KEY_TRANSLATION_DEFAULT, true).apply()
        }
        if (!configCorrupt) persist(context)
    }

    fun byId(id: String): HubAssistant? = items.firstOrNull { it.id == id }

    fun upsert(context: Context, assistant: HubAssistant) {
        val index = items.indexOfFirst { it.id == assistant.id }
        items = if (index >= 0) {
            items.toMutableList().also { it[index] = assistant }
        } else {
            items + assistant
        }
        persist(context)
    }

    /** 全局权限页用：一次把这组权限应用到所有助手，避免切助手后还要再同意一遍。 */
    fun updateAll(context: Context, transform: (HubAssistant) -> HubAssistant) {
        items = items.map(transform)
        persist(context)
    }

    /** 复制一个助手（名字后面加「副本」）。 */
    fun duplicate(context: Context, id: String) {
        val source = byId(id) ?: return
        if (source.underDevelopment) return
        val copy = source.copy(
            id = newId(),
            name = source.name + " 副本",
            avatarPath = "",
        )
        items = items + copy
        persist(context)
    }

    /** 删掉一个助手（最后一个不给删，免得列表空掉）。 */
    fun remove(context: Context, id: String) {
        if (BUILTIN_ASSISTANTS.any { it.id == id }) return
        val selectable = items.filterNot { it.underDevelopment }
        if (selectable.any { it.id == id } && selectable.size <= 1) return
        items = items.filterNot { it.id == id }
        if (currentId == id) currentId = items.firstOrNull { !it.underDevelopment }?.id.orEmpty()
        // 头像文件也删掉
        runCatching { avatarFile(context, id).delete() }
        MemoryStore.deleteAssistant(context, id)
        persist(context)
    }

    /** 改全局默认权限（模型与服务 › 权限与隐私 那一页）。 */
    fun setDefaultPermissions(context: Context, value: HubAssistant) {
        defaultPermissions = value
        prefs(context).edit().putString(KEY_DEFAULT_PERMS, toJson(value).toString()).apply()
    }

    /**
     * 用户在系统设置里授权后，把对应能力同步给所有助手。
     *
     * 这是 2026-10-19 用户要求「在权限与隐私里开启后，默认助手直接获得权限」的落点：
     * 只要系统已经授权，就把这项能力视为可用；关掉系统权限时不会反过来抹掉用户
     * 在 App 里手动关掉的开关，避免每次回前台都覆盖用户选择。
     */
    internal fun syncSystemGrants(
        context: Context,
        grants: SystemPermissionGrants,
        disabled: Set<String> = emptySet(),
    ) {
        fun merge(value: HubAssistant): HubAssistant = value.copy(
            permLocation = if ("location" in disabled) false else value.permLocation || grants.location,
            permCalendar = if ("calendar" in disabled) false else value.permCalendar || grants.calendar,
            permContacts = if ("contacts" in disabled) false else value.permContacts || grants.contacts,
            permSms = if ("sms" in disabled) false else value.permSms || grants.sms,
            permMms = if ("mms" in disabled) false else value.permMms || grants.sms,
            permActivity = if ("activity" in disabled) false else value.permActivity || grants.activity,
            permScreenTime = if ("screen_time" in disabled) false else value.permScreenTime || grants.usageAccess,
        )
        val nextDefault = merge(defaultPermissions)
        if (nextDefault != defaultPermissions) {
            setDefaultPermissions(context, nextDefault)
        }
        if (items.any { merge(it) != it }) {
            updateAll(context, ::merge)
        }
    }

    /** 新建助手：把全局默认权限套上去（其余字段空着，用户自己填）。 */
    fun createAssistant(context: Context, name: String): HubAssistant {
        val assistant = defaultPermissions.copy(
            id = newId(),
            name = name,
            avatarPath = "",
            providerId = "",
            model = "",
            temperature = null,
            maxTokens = null,
            thinkingDepth = ThinkingDepthConfig.AUTO,
            systemPrompt = "",
            skillIds = listOf(TRANSLATION_SKILL_ID),
        )
        upsert(context, assistant)
        return assistant
    }

    fun setCurrent(context: Context, id: String) {
        if (items.none { it.id == id && !it.underDevelopment }) return
        currentId = id
        persist(context)
    }

    fun newId(): String = "a-" + UUID.randomUUID().toString().take(8)

    /** 这个助手的头像文件（存 filesDir/assistant_avatars/<id>.jpg）。 */
    fun avatarFile(context: Context, id: String): File =
        File(context.filesDir, "assistant_avatars").apply { mkdirs() }.let { File(it, id + ".jpg") }

    /**
     * 稳定前缀：同一助手、同一组技能下尽量不变化，放到 system message 最前面，
     * 让 DeepSeek / OpenAI 等供应商的 prompt cache 能复用。
     */
    fun stableSystemPrompt(
        assistant: HubAssistant,
        activeSkillIds: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        if (assistant.systemPrompt.isNotBlank()) parts += assistant.systemPrompt.trim()
        activeSkillIds.distinct().forEach { id ->
            SkillStore.byId(id)?.let { parts += it.prompt }
        }
        return parts.joinToString("\n\n")
    }

    /** 每轮变化的内容，必须放在稳定前缀之后，避免破坏 prompt cache。 */
    fun dynamicSystemPrompt(
        assistant: HubAssistant,
        userText: String,
    ): String {
        val parts = mutableListOf<String>()
        if (assistant.permTime && FeatureActivation.needsTimeContext(userText)) {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA).format(Date())
            parts += "现在是 " + now + "。"
        }
        MemoryStore.promptFor(assistant, userText)
            .takeIf { it.isNotBlank() }
            ?.let { parts += it }
        return parts.joinToString("\n\n")
    }

    /** 实际用的对话参数：助手的温度 / 最大 token / 思考强度（没填就用全局的）。 */
    fun effectiveChatSettings(
        assistant: HubAssistant,
        activeSkillIds: List<String>,
        userText: String,
    ): HubChatSettings {
        val global = ProviderStore.chatSettings
        val allSkillIds = resolvedSkillIds(activeSkillIds, global.skillId)
        return global.copy(
            systemPrompt = stableSystemPrompt(assistant, allSkillIds),
            dynamicContext = dynamicSystemPrompt(assistant, userText),
            skillId = "",
            temperature = assistant.temperature ?: global.temperature,
            maxTokens = assistant.maxTokens ?: global.maxTokens,
            thinkingDepth = resolvedThinkingDepth(assistant.thinkingDepth, global.thinkingDepth),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(context: Context) {
        prefs(context).edit()
            .putString(KEY_ITEMS, JSONArray().apply { items.forEach { put(toJson(it)) } }.toString())
            .putString(KEY_CURRENT, currentId)
            .apply()
    }

    private fun toJson(a: HubAssistant): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("name", a.name)
        put("underDevelopment", a.underDevelopment)
        put("avatarPath", a.avatarPath)
        put("providerId", a.providerId)
        put("model", a.model)
        put("temperature", a.temperature ?: JSONObject.NULL)
        put("maxTokens", a.maxTokens ?: JSONObject.NULL)
        put("thinkingDepth", a.thinkingDepth)
        put("systemPrompt", a.systemPrompt)
        put("skillIds", JSONArray().apply { a.skillIds.forEach { put(it) } })
        put("memory", a.memory)
        put("globalMemory", a.globalMemory)
        put("useHistory", a.useHistory)
        put("permTime", a.permTime)
        put("permLocation", a.permLocation)
        put("permScreenTime", a.permScreenTime)
        put("permCalendar", a.permCalendar)
        put("permInstalledApps", a.permInstalledApps)
        put("permSms", a.permSms)
        put("permMms", a.permMms)
        put("permContacts", a.permContacts)
        put("permActivity", a.permActivity)
    }

    private fun fromJson(json: JSONObject): HubAssistant = HubAssistant(
        id = json.optString("id"),
        name = json.optString("name").ifBlank { "助手" },
        underDevelopment = json.optBoolean("underDevelopment", false),
        avatarPath = json.optString("avatarPath"),
        providerId = json.optString("providerId"),
        model = json.optString("model"),
        temperature = json.opt("temperature")?.let { (it as? Number)?.toDouble() },
        maxTokens = json.opt("maxTokens")?.let { (it as? Number)?.toInt() },
        thinkingDepth = json.optInt("thinkingDepth", ThinkingDepthConfig.AUTO),
        systemPrompt = json.optString("systemPrompt"),
        skillIds = json.optJSONArray("skillIds")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty(),
        memory = json.optBoolean("memory", false),
        globalMemory = json.optBoolean("globalMemory", false),
        useHistory = json.optBoolean("useHistory", true),
        permTime = json.optBoolean("permTime", false),
        permLocation = json.optBoolean("permLocation", false),
        permScreenTime = json.optBoolean("permScreenTime", false),
        permCalendar = json.optBoolean("permCalendar", false),
        permInstalledApps = json.optBoolean("permInstalledApps", false),
        permSms = json.optBoolean("permSms", false),
        permMms = json.optBoolean("permMms", false),
        permContacts = json.optBoolean("permContacts", false),
        permActivity = json.optBoolean("permActivity", false),
    )
}

/** 系统当前真实授予的一组能力，给权限页和助手同步用。 */
internal data class SystemPermissionGrants(
    val location: Boolean,
    val usageAccess: Boolean,
    val calendar: Boolean,
    val contacts: Boolean,
    val sms: Boolean,
    val activity: Boolean,
    val notifications: Boolean,
    val camera: Boolean,
    val microphone: Boolean,
)
