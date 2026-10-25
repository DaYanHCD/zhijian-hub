package com.fingertipfuture.hub.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个模型供应商：接口地址 + Key + **它下面有哪些模型**。
 *
 * 和「指尖」一样是"用户自带 Key"的模式：不联任何自有服务器，所以也不依赖账号体系。
 */
data class HubProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    /** 这家**能用**的模型（「自动检测」拉回来的；只是备选池）。 */
    val availableModels: List<String> = emptyList(),
    /** 用户**选中**要用**的模型（聊天时挑的就是这些）。 */
    val models: List<String> = emptyList(),
    /**
     * 是否启用。开关在**提供商列表**那张卡片上。
     * 禁用的供应商不参与"当前对话"，也不出现在模型选择里。
     */
    val enabled: Boolean = false,
    /** 接口路径（拼在 baseUrl 后面）。预设都填好了，默认 `/chat/completions`。 */
    val apiPath: String = DEFAULT_API_PATH,
    /** 厂商头像：`ic_brand_<logo>` 的后半截（见 `ui/provider/BrandIcons.kt`）。自定义的留空 → 用名字首字。 */
    val logo: String = "",
) {
    companion object {
        const val DEFAULT_API_PATH = "/chat/completions"
    }
}

/**
 * 内置厂商：名字 / 接口地址 / 接口路径 / 真实厂商 Logo，**全部预设好**。
 *
 * 用户要做的只有两件事：把那家的卡片开关打开、把 Key 填进去（模型可以点"自动检测"）。
 * Logo 来自 LobeHub icons（MIT），已转成 `res/drawable/ic_brand_*.xml`。
 *
 * 2026-10-16 用户要求：**预设只留 11 家**（顺序照用户给的：DeepSeek、OpenAI、谷歌、Kimi、智谱、
 * 千问、硅基流动、MiniMax、混元、火山引擎、xAI）。以前铺过的其它 14 家进了 [HUB_RETIRED_PRESETS]，
 * 见那边关于"老数据怎么处理"的说明。
 */
val HUB_PRESETS: List<HubProvider> = listOf(
    HubProvider(
        "deepseek", "DeepSeek", "https://api.deepseek.com/v1",
        apiPath = "/chat/completions", logo = "deepseek",
    ),
    HubProvider(
        "openai", "OpenAI", "https://api.openai.com/v1",
        apiPath = "/chat/completions", logo = "openai",
    ),
    HubProvider(
        "gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
        apiPath = "/chat/completions", logo = "gemini",
    ),
    HubProvider(
        "moonshot", "Kimi（月之暗面）", "https://api.moonshot.cn/v1",
        apiPath = "/chat/completions", logo = "moonshot",
    ),
    HubProvider(
        "zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
        apiPath = "/chat/completions", logo = "zhipu",
    ),
    HubProvider(
        "qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
        apiPath = "/chat/completions", logo = "qwen",
    ),
    HubProvider(
        "siliconcloud", "硅基流动", "https://api.siliconflow.cn/v1",
        apiPath = "/chat/completions", logo = "siliconcloud",
    ),
    HubProvider(
        "minimax", "MiniMax", "https://api.minimax.chat/v1",
        apiPath = "/chat/completions", logo = "minimax",
    ),
    HubProvider(
        "hunyuan", "腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1",
        apiPath = "/chat/completions", logo = "hunyuan",
    ),
    HubProvider(
        "volcengine", "火山引擎方舟（豆包）", "https://ark.cn-beijing.volces.com/api/v3",
        apiPath = "/chat/completions", logo = "volcengine",
    ),
    HubProvider(
        "xai", "xAI", "https://api.x.ai/v1",
        apiPath = "/chat/completions", logo = "xai",
    ),
)

/**
 * 2026-10-16 之前铺过、之后从预设里去掉的 14 家（只留名字和地址，用来认老数据）。
 *
 * 处理方式（见 `ProviderStore.initialize`）：**只清"没动过"的那些** ——
 * 既没填 Key、开关也没打开的，直接从列表里去掉；
 * 只要用户填过 Key 或打开过开关，就说明他在用，一律留着，绝不因为改预设把人家配好的东西删了。
 */
private val HUB_RETIRED_PRESETS: List<Pair<String, String>> = listOf(
    "阶跃星辰" to "https://api.stepfun.com/v1",
    "讯飞星火" to "https://spark-api-open.xf-yun.com/v1",
    "百度千帆" to "https://qianfan.baidubce.com/v2",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "Mistral AI" to "https://api.mistral.ai/v1",
    "Groq" to "https://api.groq.com/openai/v1",
    "Ollama（本机）" to "http://localhost:11434/v1",
    "Together AI" to "https://api.together.xyz/v1",
    "Fireworks AI" to "https://api.fireworks.ai/inference/v1",
    "Hugging Face" to "https://router.huggingface.co/v1",
    "Perplexity" to "https://api.perplexity.ai",
    "AiHubMix" to "https://aihubmix.com/v1",
    "Cerebras" to "https://api.cerebras.ai/v1",
    "NVIDIA NIM" to "https://integrate.api.nvidia.com/v1",
)

/** 对话参数（参考 RikkaHub 的 assistant 思路：助手要有"人格"）。 */
data class HubChatSettings(
    /** 稳定前缀：助手人格、技能、格式约束；同一助手应尽量保持不变，便于供应商命中 prompt cache。 */
    val systemPrompt: String = "",
    /** 每轮变化的内容：当前时间、相关记忆、设备上下文、搜索结果；始终放在稳定前缀之后。 */
    val dynamicContext: String = "",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /**
     * 思考深度：0 关闭 / 1 自动 / 2 低 / 3 中等 / 4 高 / 5 超高 / 6 最大。
     * 自动不额外发参数；其余会带上 `reasoning_effort`（支持它的服务才认）。
     */
    val thinkingDepth: Int = ThinkingDepthConfig.AUTO,
    /** 选中的技能（skills）id，空 = 不用技能。技能就是一段系统提示词，见 HubSkills.kt。 */
    val skillId: String = "",
)

/**
 * 本机设置存储（SharedPreferences + JSON）。
 *
 * 2026-10-13 第二次改造（用户要求）：
 * - `enabled` 在提供商编辑页的配置分区切换，所以**内置厂商默认"禁用"**，用户开哪家就用哪家；
 * - 内置厂商**全部铺进列表**（预设好名字/地址/路径/头像），缺哪个补哪个；
 * - 删掉的字段（Response API / 回传思考 / 余额）不再读写，老 JSON 里多出来的键直接忽略。
 */
object ProviderStore {

    private const val PREFS = "hub_settings"
    private const val KEY_PROVIDERS = "providers"
    private const val KEY_LEGACY_PROVIDER = "provider"
    private const val KEY_CHAT = "chat_settings"
    private const val KEY_CURRENT_PROVIDER = "current_provider_id"
    private const val KEY_CURRENT_MODEL = "current_model"
    /** 用户删掉过的内置厂商（名字），下次启动不要再自动铺回来。 */
    private const val KEY_HIDDEN_PRESETS = "hidden_presets"

    /** 全部供应商（含没启用的内置厂商），按列表顺序。 */
    var providers: List<HubProvider> by mutableStateOf(emptyList())
        private set

    /** 启用的供应商。当前对话、模型选择都只认这些。 */
    val enabledProviders: List<HubProvider>
        get() = providers.filter { it.enabled }

    /** 当前对话用哪个供应商 / 模型。 */
    var currentProviderId: String? by mutableStateOf(null)
        private set

    var currentModel: String by mutableStateOf("")
        private set

    var chatSettings: HubChatSettings by mutableStateOf(HubChatSettings())
        private set

    /**
     * 本轮初始化有没有 Key 解密失败。
     *
     * 失败条目在内存里只能看到空串；如果之后因为改别的设置触发 persist，
     * 这个空串会被重新加密写回，造成不可逆的 Key 丢失。因此失败后本次进程
     * 只允许读、不允许覆盖整份 provider 配置。
     */
    private var secretLoadFailed = false

    val currentProvider: HubProvider?
        get() = providers.firstOrNull { it.id == currentProviderId && it.enabled }
            ?: enabledProviders.firstOrNull()

    val currentModels: List<String>
        get() = currentProvider?.models.orEmpty()

    /** 已经接入、至少有一个模型的供应商（不要求它恰好是当前选中项）。 */
    val usableProviders: List<HubProvider>
        get() = enabledProviders.filter { it.baseUrl.isNotBlank() && it.models.isNotEmpty() }

    fun initialize(context: Context) {
        val prefs = prefs(context)
        SecretStore.resetDecryptFailure()
        secretLoadFailed = false
        val rawProviders = prefs.getString(KEY_PROVIDERS, null)
        val parsedProviders = readProviders(prefs)
        // 整份 JSON 坏掉时不要 persist：否则启动时会把仅存的原始文本覆盖成只剩内置预设。
        val configCorrupt = rawProviders != null && parsedProviders == null
        val storedRaw = parsedProviders ?: migrateLegacy(prefs) ?: emptyList()
        // 2026-10-16 预设从 25 家收到 11 家：把"退场且没动过"的去掉（填过 Key 或开过开关的留着）
        val stored = storedRaw.filterNot { it.isRetiredAndUntouched() }
        providers = mergePresets(stored, hiddenPresets(prefs))

        currentProviderId = prefs.getString(KEY_CURRENT_PROVIDER, null)
            ?.takeIf { id -> providers.any { it.id == id } }
            ?: currentProvider?.id
        currentModel = prefs.getString(KEY_CURRENT_MODEL, null)
            ?.takeIf { it.isNotBlank() && it in currentModels }
            ?: currentModels.firstOrNull().orEmpty()

        prefs.getString(KEY_CHAT, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                HubChatSettings(
                    systemPrompt = json.optString("systemPrompt"),
                    temperature = json.opt("temperature")?.let { (it as? Number)?.toDouble() },
                    maxTokens = json.opt("maxTokens")?.let { (it as? Number)?.toInt() },
                    thinkingDepth = json.optInt("thinkingDepth", ThinkingDepthConfig.AUTO),
                    skillId = json.optString("skillId"),
                )
            }.getOrNull()?.let { chatSettings = it }
        }
        if (!prefs.getBoolean("thinking_depth_v2", false)) {
            chatSettings = chatSettings.copy(
                thinkingDepth = ThinkingDepthConfig.fromLegacy(chatSettings.thinkingDepth),
            )
            prefs.edit().putBoolean("thinking_depth_v2", true).apply()
        }
        if (chatSettings.skillId in RETIRED_HUB_SKILL_IDS) {
            // 内置技能已删除：清掉旧版本留下的全局选择，避免副屏「插件」还显示旧技能。
            chatSettings = chatSettings.copy(skillId = "")
        }

        secretLoadFailed = SecretStore.lastDecryptFailed
        // 解密失败时不要回写：否则空 Key 会覆盖原密文，造成不可逆的丢失。
        if (!secretLoadFailed && !configCorrupt) {
            runCatching { persist(context) }
        }
    }

    /**
     * 内置厂商铺进列表：缺哪个补哪个（默认禁用），用户已经建过的**原样保留**。
     * "是不是同一家"按名字或接口地址判断，免得升级后冒出两个 DeepSeek。
     */
    private fun mergePresets(stored: List<HubProvider>, hidden: Set<String>): List<HubProvider> {
        val missing = HUB_PRESETS.filter { preset ->
            preset.name !in hidden && stored.none { existing ->
                existing.name.equals(preset.name, ignoreCase = true) ||
                    (existing.baseUrl.isNotBlank() &&
                        existing.baseUrl.trim().trimEnd('/') == preset.baseUrl.trimEnd('/'))
            }
        }
        return stored + missing
    }

    /** 切换当前对话用的供应商 / 模型（入口在聊天页顶栏「更多」里）。 */
    fun select(context: Context, providerId: String, model: String): Boolean {
        val target = providers.firstOrNull { it.id == providerId } ?: return false
        currentProviderId = target.id
        currentModel = model
        return persistSelection(context)
    }

    /** 新增或更新一个供应商（按 id 匹配）。 */
    fun upsert(context: Context, provider: HubProvider): Boolean {
        val index = providers.indexOfFirst { it.id == provider.id }
        providers = if (index >= 0) {
            providers.toMutableList().also { it[index] = provider }
        } else {
            // 新加的排最前面：用户刚建的自己要看得到（内置厂商沉在下面）
            listOf(provider) + providers
        }
        if (currentProviderId == provider.id && currentModel !in provider.models) {
            currentModel = provider.models.firstOrNull().orEmpty()
        }
        return persist(context)
    }

    /** 提供商编辑页配置分区里的启用开关。 */
    fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        val index = providers.indexOfFirst { it.id == id }
        if (index < 0) return false
        val updated = providers[index].copy(enabled = enabled)
        providers = providers.toMutableList().also { it[index] = updated }
        // 关掉当前这家 → 顺位换到下一家启用的（没有就空着，聊天页会提示去配）
        if (!enabled && currentProviderId == id) {
            currentProvider?.let { fallback ->
                currentProviderId = fallback.id
                currentModel = fallback.models.firstOrNull().orEmpty()
            }
        }
        return persist(context)
    }

    /** 提供商列表长按拖动后的顺序；按 id 保存，缺项会自动补在末尾。 */
    fun reorder(context: Context, orderedIds: List<String>): Boolean {
        if (orderedIds.isEmpty()) return false
        val byId = providers.associateBy { it.id }
        val reordered = orderedIds.mapNotNull(byId::get)
        providers = reordered + providers.filterNot { it.id in orderedIds }
        return persist(context)
    }

    fun remove(context: Context, id: String): Boolean {
        val target = providers.firstOrNull { it.id == id }
        providers = providers.filterNot { it.id == id }
        // 内置厂商删了就记下来，别在下次启动又把它铺回来
        if (target != null && HUB_PRESETS.any { it.name.equals(target.name, ignoreCase = true) }) {
            val hidden = hiddenPresets(prefs(context)) + target.name
            prefs(context).edit()
                .putString(KEY_HIDDEN_PRESETS, JSONArray().apply { hidden.forEach { put(it) } }.toString())
                .apply()
        }
        if (currentProviderId == id) {
            currentProviderId = currentProvider?.id
            currentModel = currentModels.firstOrNull().orEmpty()
        }
        return persist(context)
    }

    fun saveChatSettings(context: Context, value: HubChatSettings) {
        chatSettings = value
        prefs(context).edit().putString(KEY_CHAT, JSONObject().apply {
            put("systemPrompt", value.systemPrompt.trim())
            put("temperature", value.temperature ?: JSONObject.NULL)
            put("maxTokens", value.maxTokens ?: JSONObject.NULL)
            put("thinkingDepth", value.thinkingDepth)
            put("skillId", value.skillId)
        }.toString()).apply()
    }

    /** 是不是"已经退场的预设 + 用户没动过"（没填 Key、开关也没开）。 */
    private fun HubProvider.isRetiredAndUntouched(): Boolean {
        if (apiKey.isNotBlank() || enabled) return false
        val url = baseUrl.trim().trimEnd('/')
        return HUB_RETIRED_PRESETS.any { (_, retiredUrl) -> url.isNotBlank() && url == retiredUrl.trimEnd('/') }
    }

    private fun hiddenPresets(prefs: android.content.SharedPreferences): Set<String> =
        prefs.getString(KEY_HIDDEN_PRESETS, null)?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.optString(it) }.toSet()
            }.getOrNull()
        }.orEmpty()

    private fun readProviders(prefs: android.content.SharedPreferences): List<HubProvider>? =
        prefs.getString(KEY_PROVIDERS, null)?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        // 单条损坏不能把用户其它供应商一起弄丢。
                        runCatching { toProvider(array.getJSONObject(index)) }
                            .getOrNull()
                            ?.let { add(it) }
                    }
                }
            }.getOrNull()
        }

    /** 0.1.x 的老数据：只有一个供应商。 */
    private fun migrateLegacy(prefs: android.content.SharedPreferences): List<HubProvider>? =
        prefs.getString(KEY_LEGACY_PROVIDER, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                val model = json.optString("model")
                listOf(
                    HubProvider(
                        id = json.optString("id").ifBlank { "custom" },
                        name = json.optString("name").ifBlank { "自定义" },
                        baseUrl = json.optString("baseUrl"),
                        apiKey = SecretStore.decrypt(json.optString("apiKey")),
                        models = listOfNotNull(model.takeIf { it.isNotBlank() }),
                        enabled = true,
                    ),
                )
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

    private fun toProvider(json: JSONObject): HubProvider {
        val models = json.optJSONArray("models")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty()
        val available = json.optJSONArray("availableModels")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty()
        val single = json.optString("model").takeIf { it.isNotBlank() }
        return HubProvider(
            id = json.optString("id"),
            name = json.optString("name"),
            baseUrl = json.optString("baseUrl"),
            apiKey = SecretStore.decrypt(json.optString("apiKey")),
            models = if (models.isNotEmpty()) models else listOfNotNull(single),
            availableModels = available,
            // 老数据没有 enabled 键：那时候能出现在列表里的都是"在用"的，沿用旧行为（true）
            enabled = if (json.has("enabled")) json.optBoolean("enabled") else true,
            apiPath = json.optString("apiPath").ifBlank { HubProvider.DEFAULT_API_PATH },
            logo = json.optString("logo"),
        )
    }

    private fun toJson(value: HubProvider): JSONObject = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("baseUrl", value.baseUrl)
        put("apiKey", SecretStore.encrypt(value.apiKey))
        put("models", JSONArray().apply { value.models.forEach { put(it) } })
        put("availableModels", JSONArray().apply { value.availableModels.forEach { put(it) } })
        put("enabled", value.enabled)
        put("apiPath", value.apiPath)
        put("logo", value.logo)
    }

    private fun persistSelection(context: Context): Boolean = runCatching {
        prefs(context).edit()
            .putString(KEY_CURRENT_PROVIDER, currentProviderId)
            .putString(KEY_CURRENT_MODEL, currentModel)
            .commit()
    }.getOrDefault(false)

    private fun persist(context: Context): Boolean {
        if (secretLoadFailed) {
            Log.e("HubProviderStore", "有 Key 解密失败，本次不写回 provider 配置，避免空 Key 覆盖密文")
            return false
        }
        return runCatching {
            prefs(context).edit()
                .putString(
                    KEY_PROVIDERS,
                    JSONArray().apply {
                        providers.forEach { put(toJson(it)) }
                    }.toString(),
                )
                .putString(KEY_CURRENT_PROVIDER, currentProviderId)
                .putString(KEY_CURRENT_MODEL, currentModel)
                .commit()
        }.onFailure { Log.e("HubProviderStore", "保存供应商配置失败", it) }
            .getOrDefault(false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
