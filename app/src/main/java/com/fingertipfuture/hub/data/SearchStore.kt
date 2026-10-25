package com.fingertipfuture.hub.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一个搜索供应商（用户自己加的，带 Key 与模式）。 */
data class SearchProvider(
    val id: String,
    /** deepseek / doubao / tavily / firecrawl —— 决定调哪个接口（旧的 brave / searxng / google 仍能读）。 */
    val kind: String,
    val name: String,
    /** 是否启用（并行搜索只用启用的那些；快捷弹层里逐家开关）。 */
    val enabled: Boolean = true,
    val apiKey: String = "",
    /** SearXNG 是自建地址；Google 这里放的是 cx（搜索引擎 id）。 */
    val baseUrl: String = "",
    /** false = 普通模式，true = 全面模式（更全、更费额度）。 */
    val deep: Boolean = false,
    /** 是否加入并行搜索。 */
    val inParallel: Boolean = false,
) {
    companion object {
        const val KIND_BING = "bing"
        const val KIND_DEEPSEEK = "deepseek"
        const val KIND_DOUBAO = "doubao"
        const val KIND_TAVILY = "tavily"
        const val KIND_FIRECRAWL = "firecrawl"
        const val KIND_BRAVE = "brave"
        const val KIND_SEARXNG = "searxng"
        const val KIND_GOOGLE = "google"
    }
}

/**
 * 搜索设置的数据（2026-10-16 用户要求）。
 *
 * - 用户添加的搜索供应商（[items]）：每家带 API Key、普通 / 全面模式；
 * - [inParallel] 的那些会**同时调用**（多线并行）；
 * - [maxResults] 每次搜索取几条，默认 **10**（免得一直搜把额度烧光）。
 *
 * 搜索配置已经接入 `ChatRequestRunner` / `SearchContext`：需要实时信息时由本地规则和
 * 搜索规划器决定是否发起请求；普通闲聊和翻译不会搜索。
 */
object SearchStore {

    private const val PREFS = "hub_search"
    private const val KEY_ITEMS = "items"
    private const val KEY_MAX = "max_results"
    private const val KEY_BING_DEFAULTS = "bing_defaults_v1"

    private val DEFAULT_BING = SearchProvider(
        id = "sp-builtin-bing",
        kind = SearchProvider.KIND_BING,
        name = "Bing（免费）",
        enabled = true,
        inParallel = true,
    )

    /** 能加的搜索服务（kind → 显示名 + 一句话说明）。 */
    val kinds: List<Triple<String, String, String>> = listOf(
        Triple(
            SearchProvider.KIND_DEEPSEEK,
            "DeepSeek",
            "官方联网搜索，按成功请求计费；全面模式可读取网页正文",
        ),
        Triple(
            SearchProvider.KIND_DOUBAO,
            "豆包搜索",
            "火山引擎联网搜索，需要 API Key；适合中文实时信息",
        ),
        Triple(
            SearchProvider.KIND_TAVILY,
            "Tavily",
            "面向 AI 的搜索服务，需要 API Key；摘要完整、接入简单",
        ),
        Triple(
            SearchProvider.KIND_FIRECRAWL,
            "Firecrawl",
            "需要 API Key；可抓取并整理网页正文，适合深度检索",
        ),
    )

    var items: List<SearchProvider> by mutableStateOf(emptyList())
        private set

    /** 每次搜索取几条（默认 10）。 */
    var maxResults: Int by mutableIntStateOf(10)
        private set

    /** 同 ProviderStore：有 Key 解密失败时，本次进程不再覆盖整份搜索配置。 */
    private var secretLoadFailed = false

    /**
     * 自动联网搜索已固定开启（2026-10-19 用户要求删除总开关）。
     * 是否真的搜仍由 FeatureActivation / 搜索规划器按这条消息决定；闲聊与翻译不搜。
     */
    val chatEnabled: Boolean get() = true

    val enabled: List<SearchProvider> get() = items.filter { it.enabled }

    /** 加了并行搜索的那些（只算启用的）。 */
    val parallel: List<SearchProvider> get() = items.filter { it.enabled && it.inParallel }

    fun initialize(context: Context) {
        val prefs = prefs(context)
        SecretStore.resetDecryptFailure()
        secretLoadFailed = false
        val rawItems = prefs.getString(KEY_ITEMS, null)
        val loaded = rawItems?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        runCatching {
                            val json = array.getJSONObject(index)
                            SearchProvider(
                                id = json.optString("id"),
                                kind = json.optString("kind"),
                                name = json.optString("name"),
                                enabled = json.optBoolean("enabled", true),
                                apiKey = SecretStore.decrypt(json.optString("apiKey")),
                                baseUrl = json.optString("baseUrl"),
                                deep = json.optBoolean("deep", false),
                                inParallel = json.optBoolean("inParallel", false),
                            )
                        }.getOrNull()?.let { add(it) }
                    }
                }
            }.getOrNull()
        }
        val configCorrupt = rawItems != null && loaded == null
        // 旧测试版曾提供过「官方搜索」（kind=official）：现已下线，启动时自动清掉。
        val validItems = loaded.orEmpty()
            .filterNot { it.kind == "official" }
            .map {
                if (it.kind == SearchProvider.KIND_DEEPSEEK) {
                    it.copy(name = "DeepSeek")
                } else {
                    it
                }
            }
        val bingDefaultsApplied = prefs.getBoolean(KEY_BING_DEFAULTS, false)
        val storedBing = validItems.firstOrNull { it.kind == SearchProvider.KIND_BING }
        val bing = (storedBing ?: DEFAULT_BING).let {
            if (bingDefaultsApplied) {
                it
            } else {
                it.copy(enabled = true, inParallel = true)
            }
        }
        items = listOf(bing) + validItems.filterNot { it.kind == SearchProvider.KIND_BING }
        maxResults = prefs.getInt(KEY_MAX, 10).coerceIn(1, 50)
        secretLoadFailed = SecretStore.lastDecryptFailed
        // 老版本 Key 是明文；启动时重写一次，自动迁成 Keystore 密文。
        // 解密失败则不回写，避免空 Key 覆盖原密文。
        if (!secretLoadFailed && !configCorrupt) {
            runCatching { persist(context) }
            if (!bingDefaultsApplied) {
                prefs.edit().putBoolean(KEY_BING_DEFAULTS, true).apply()
            }
        }
    }

    fun byId(id: String): SearchProvider? = items.firstOrNull { it.id == id }

    fun add(context: Context, kind: String, name: String): SearchProvider {
        if (kind == SearchProvider.KIND_BING) {
            return items.firstOrNull { it.kind == SearchProvider.KIND_BING } ?: DEFAULT_BING.also {
                items = listOf(it) + items
                persist(context)
                SearchContext.clearCache()
            }
        }
        // DeepSeek 聊天供应商已经存了同一把 Key，添加官方搜索时直接沿用，少填一次。
        val sharedKey = if (kind == SearchProvider.KIND_DEEPSEEK) {
            ProviderStore.providers
                .firstOrNull {
                    (it.logo == "deepseek" || it.baseUrl.contains("api.deepseek.com")) &&
                        it.apiKey.isNotBlank()
                }
                ?.apiKey
                .orEmpty()
        } else {
            ""
        }
        val provider = SearchProvider(
            id = "sp-" + UUID.randomUUID().toString().take(8),
            kind = kind,
            name = name,
            apiKey = sharedKey,
        )
        items = items + provider
        persist(context)
        SearchContext.clearCache()
        return provider
    }

    fun upsert(context: Context, provider: SearchProvider) {
        val index = items.indexOfFirst { it.id == provider.id }
        items = if (index >= 0) {
            items.toMutableList().also { it[index] = provider }
        } else {
            items + provider
        }
        persist(context)
        SearchContext.clearCache()
    }

    fun remove(context: Context, id: String) {
        if (items.firstOrNull { it.id == id }?.kind == SearchProvider.KIND_BING) return
        items = items.filterNot { it.id == id }
        persist(context)
        SearchContext.clearCache()
    }

    fun setMaxResults(context: Context, value: Int) {
        maxResults = value.coerceIn(1, 50)
        prefs(context).edit().putInt(KEY_MAX, maxResults).apply()
        SearchContext.clearCache()
    }

    /** 快捷弹层里的「并行搜索」总开关：所有**已启用**的供应商一起改。 */
    fun setAllParallel(context: Context, value: Boolean) {
        items = items.map { if (it.enabled) it.copy(inParallel = value) else it }
        persist(context)
        SearchContext.clearCache()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(context: Context) {
        if (secretLoadFailed) {
            Log.e("HubSearchStore", "有 Key 解密失败，本次不写回搜索配置，避免空 Key 覆盖密文")
            return
        }
        runCatching {
            prefs(context).edit().putString(
                KEY_ITEMS,
                JSONArray().apply {
                    items.forEach { provider ->
                        put(JSONObject().apply {
                            put("id", provider.id)
                            put("kind", provider.kind)
                            put("name", provider.name)
                            put("enabled", provider.enabled)
                            put("apiKey", SecretStore.encrypt(provider.apiKey))
                            put("baseUrl", provider.baseUrl)
                            put("deep", provider.deep)
                            put("inParallel", provider.inParallel)
                        })
                    }
                }.toString(),
            ).apply()
        }.onFailure { Log.e("HubSearchStore", "保存搜索配置失败", it) }
    }
}
