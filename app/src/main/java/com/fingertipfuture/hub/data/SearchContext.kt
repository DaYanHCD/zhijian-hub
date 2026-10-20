package com.fingertipfuture.hub.data

import android.util.Log
import com.fingertipfuture.hub.core.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class SearchOutcome(
    val context: String,
    val trace: HubSearchTrace?,
    val evidence: List<SearchEvidence> = emptyList(),
)

internal data class SearchEvidence(
    val query: String,
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * 把当前用户的这句话送到已启用的搜索供应商，整理成一段可注入模型的资料。
 *
 * 只有当 SearchStore.chatEnabled 打开时才调用；每轮最多取 SearchStore.maxResults 条。
 */
internal object SearchContext {

    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val CACHE_MAX_ENTRIES = 20

    private data class CacheEntry(val at: Long, val outcome: SearchOutcome)

    private val cache = object : LinkedHashMap<String, CacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > CACHE_MAX_ENTRIES
    }

    suspend fun build(
        query: String,
        /** 同一用户问题 + 最近上下文的稳定指纹；用于关键词被模型改写时仍能命中短时缓存。 */
        intentHint: String = query,
        allowIntentCache: Boolean = true,
    ): Result<SearchOutcome> = withContext(Dispatchers.IO) {
        if (!SearchStore.chatEnabled) return@withContext Result.success(SearchOutcome("", null))
        if (query.isBlank()) return@withContext Result.failure(IOException("搜索关键词为空"))

        val providers = SearchStore.parallel.ifEmpty { SearchStore.enabled.take(1) }
        // 默认开启后，没配供应商的用户不应该每条消息都收到失败 Toast。
        if (providers.isEmpty()) return@withContext Result.success(SearchOutcome("", null))

        val providerKey = buildString {
            append(query.trim().lowercase()).append('|').append(SearchStore.maxResults)
            providers.forEach { append('|').append(it.id).append(':').append(it.kind) }
        }
        val exactKey = "exact:$providerKey"
        val intentKey = "intent:" + intentHint
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim() +
            "|" + SearchStore.maxResults +
            providers.joinToString("") { "|${it.id}:${it.kind}" }
        fun hit(key: String): SearchOutcome? {
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.at > CACHE_TTL_MS) return null
            return entry.outcome.copy(
                context = entry.outcome.context.replaceFirst(
                    "【联网搜索结果】",
                    "【联网搜索结果（5 分钟内缓存）】",
                ),
                trace = entry.outcome.trace?.copy(cached = true, pending = false),
            )
        }
        synchronized(cache) {
            if (AppConfig.IS_DEBUG) {
                Log.i(
                    "HubSearchCache",
                    "lookup exact=${exactKey.hashCode()} intent=${intentKey.hashCode()} " +
                        "allowIntent=$allowIntentCache",
                )
            }
            val cached = hit(exactKey) ?: if (allowIntentCache) hit(intentKey) else null
            if (cached != null) {
                if (AppConfig.IS_DEBUG) {
                    Log.i("HubSearchCache", "hit q=${query.take(60)} entries=${cache.size}")
                }
                return@withContext Result.success(cached)
            }
            if (AppConfig.IS_DEBUG) {
                Log.i("HubSearchCache", "miss q=${query.take(60)} entries=${cache.size}")
            }
        }

        val results = searchAll(providers, query)
        val ranked = mergeHits(results, SearchStore.maxResults)
        val hits = ranked
        if (hits.isEmpty()) return@withContext Result.failure(IOException("搜索没有返回结果"))

        val body = buildString {
            append("【联网搜索结果】\n")
            append("以下是刚刚搜索到的公开网页资料，回答时请优先参考，并在合适时给出链接：\n")
            hits.forEachIndexed { index, (provider, hit) ->
                append(index + 1).append(". ").append(hit.title).append('\n')
                if (hit.snippet.isNotBlank()) {
                    append("   ").append(hit.snippet.replace('\n', ' ').take(1200)).append('\n')
                }
                append("   ").append(hit.url).append('\n')
                append("   来源：").append(provider.name).append('\n')
                if (hit.publishedAt.isNotBlank()) {
                    append("   发布时间：").append(hit.publishedAt).append('\n')
                }
            }
        }.take(6000)
        val outcome = SearchOutcome(
                context = body,
                trace = HubSearchTrace(
                    query = query.take(200),
                    resultCount = hits.size,
                    providerKinds = hits.map { it.first.kind }.distinct(),
                    queriedProviderKinds = providers.map { it.kind }.distinct(),
                    titles = hits.map { it.second.title }.filter { it.isNotBlank() }.take(8),
                    results = hits.map {
                        HubSearchResult(
                            title = it.second.title.ifBlank { it.second.url },
                            url = it.second.url,
                        )
                    }.take(8),
                ),
                evidence = hits.map { (_, hit) ->
                    SearchEvidence(
                        query = query,
                        title = hit.title,
                        url = hit.url,
                        snippet = hit.snippet,
                    )
                },
            )
        val entry = CacheEntry(System.currentTimeMillis(), outcome)
        synchronized(cache) {
            cache[exactKey] = entry
            if (allowIntentCache) cache[intentKey] = entry
        }
        if (AppConfig.IS_DEBUG) {
            Log.i("HubSearchCache", "store q=${query.take(60)} entries=${synchronized(cache) { cache.size }}")
        }
        Result.success(outcome)
    }

    /**
     * 真正发起多家搜索。每一家各自 async，互不等待；返回顺序仍按 providers 的配置顺序。
     * 单独抽出来是为了能用本地 HTTP 服务验证“同时发出”，而不是只靠读代码猜。
     */
    internal suspend fun searchAll(
        providers: List<SearchProvider>,
        query: String,
        search: suspend (SearchProvider, String) -> Result<List<SearchApi.Hit>> = { provider, text ->
            SearchApi.search(provider, text, SearchStore.maxResults)
        },
    ): List<Pair<SearchProvider, SearchApi.Hit>> = coroutineScope {
        providers.map { provider ->
            async {
                val startedAt = System.currentTimeMillis()
                logParallel("start ${provider.name} @ $startedAt")
                val hits = search(provider, query)
                logParallel(
                    "done ${provider.name} ${System.currentTimeMillis() - startedAt}ms " +
                        "ok=${hits.isSuccess} n=${hits.getOrNull()?.size ?: 0}",
                )
                hits.getOrNull()?.map { provider to it }.orEmpty()
            }
        }.awaitAll().flatten()
    }

    /**
     * 合并多家结果：每家先取一条“种子”，剩下的再按相关度 / 权威度 / 发布时间排。
     * 这样并行搜索才不会因为某家没有打分，最终 top N 被另一家全部吃掉。
     */
    internal fun mergeHits(
        results: List<Pair<SearchProvider, SearchApi.Hit>>,
        maxResults: Int,
    ): List<Pair<SearchProvider, SearchApi.Hit>> {
        if (results.isEmpty()) return emptyList()
        val comparator = compareByDescending<Pair<SearchProvider, SearchApi.Hit>> { it.second.score }
            .thenByDescending { it.second.authority }
            .thenByDescending { it.second.publishedAt }
        val byProvider = results.groupBy { it.first.id }.values.map { group ->
            group.sortedWith(comparator)
                .distinctBy { normalizeUrl(it.second.url) }
                .distinctBy { it.second.title.lowercase().replace(Regex("""\s+"""), "") }
        }
        val seeds = byProvider.mapNotNull { it.firstOrNull() }
        val merged = seeds + byProvider.flatten().sortedWith(comparator)
        val seenUrls = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()
        return merged.filter { (_, hit) ->
            val url = normalizeUrl(hit.url)
            val title = hit.title.lowercase().replace(Regex("""\s+"""), "")
            if (url in seenUrls || title in seenTitles) {
                false
            } else {
                seenUrls += url
                seenTitles += title
                true
            }
        }.take(maxResults)
    }

    /** 单元测试里 android.util.Log 不可用；日志失败不能影响搜索本身。 */
    private fun logParallel(message: String) {
        if (!AppConfig.IS_DEBUG) return
        runCatching { Log.i("HubSearchParallel", message) }
    }

    /** 搜索设置改变或用户主动重搜时可清空；目前按 5 分钟自动失效。 */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun normalizeUrl(url: String): String = url
        .trim()
        .substringBefore('#')
        .removeSuffix("/")
        .lowercase()
}
