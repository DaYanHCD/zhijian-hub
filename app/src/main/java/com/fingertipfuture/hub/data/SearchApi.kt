package com.fingertipfuture.hub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * 联网搜索（2026-10-16 用户要求：搜索设置里能添加第三方搜索 API、能测、能多家并行）。
 *
 * 纯 `HttpURLConnection`，不引任何库。现在可选的四家：
 *
 * | 类型 | 接口 | 认证 |
 * |---|---|---|
 * | Bing（免费） | `GET https://cn.bing.com/search?q=`（抓 HTML，失败再试 www） | 不用 Key |
 * | DeepSeek 官方搜索 | `POST https://api.deepseek.com/v1/tools/search` | 头 `Authorization: Bearer` |
 * | 豆包搜索 | `POST https://open.feedcoopapi.com/search_api/web_search` | 头 `Authorization: Bearer` |
 * | Tavily | `POST https://api.tavily.com/search` | 请求体里的 `api_key` |
 * | Firecrawl | `POST https://api.firecrawl.dev/v2/search` | 头 `Authorization: Bearer` |
 *
 * 旧的 Brave / SearXNG / Google 解析还留着，老配置不会因为改版就失效。
 *
 * **普通模式** = 只要标题/链接/摘要；**全面模式** = 多要一些（Tavily 的 `advanced` + 正文、
 * Brave 多取一些结果、Google 也带摘要），更全但更费额度。
 */
object SearchApi {

    /** 搜索结果页/JSON 超过这个大小直接判失败，避免异常服务端把内存打爆。 */
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

    /** 一条搜索结果。 */
    data class Hit(
        val title: String,
        val url: String,
        val snippet: String = "",
        val score: Double = 0.0,
        val authority: Int = 0,
        val publishedAt: String = "",
        val siteName: String = "",
    )

    suspend fun search(provider: SearchProvider, query: String, maxResults: Int): Result<List<Hit>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = when (provider.kind) {
                    SearchProvider.KIND_BING -> bing(query, maxResults)
                    SearchProvider.KIND_DEEPSEEK -> deepseek(provider, query, maxResults)
                    SearchProvider.KIND_DOUBAO -> doubao(provider, query, maxResults)
                    SearchProvider.KIND_TAVILY -> tavily(provider, query, maxResults)
                    SearchProvider.KIND_FIRECRAWL -> firecrawl(provider, query, maxResults)
                    SearchProvider.KIND_BRAVE -> brave(provider, query, maxResults)
                    SearchProvider.KIND_SEARXNG -> searxng(provider, query)
                    SearchProvider.KIND_GOOGLE -> google(provider, query, maxResults)
                    else -> throw IOException("不认识的搜索服务")
                }
                val hits = if (provider.kind == SearchProvider.KIND_BING) {
                    parseBingHtml(text)
                } else {
                    parseHits(provider.kind, text, includeContent = provider.deep)
                }
                require(
                    hits.isNotEmpty(),
                    if (provider.kind == SearchProvider.KIND_BING) {
                        "没有拿到结果（Bing 可能被风控拦截，换一家或稍后再试）"
                    } else {
                        "没有拿到结果，检查 Key、余额和接口状态"
                    },
                )
                hits.take(maxResults)
            }
        }

    private fun require(condition: Boolean, message: String) {
        if (!condition) throw IOException(message)
    }

    private fun tavily(provider: SearchProvider, query: String, maxResults: Int): String {
        require(provider.apiKey.isNotBlank(), "先填 API Key")
        val body = JSONObject().apply {
            put("api_key", provider.apiKey)
            put("query", query)
            put("max_results", maxResults)
            put("search_depth", if (provider.deep) "advanced" else "basic")
            if (provider.deep) put("include_raw_content", true)
        }
        return postJson("https://api.tavily.com/search", body, null)
    }

    /**
     * DeepSeek 官方 Search。返回 `results[]`，每项含 title / url / snippet / summary / content。
     *
     * `need_content=true` 时官方会把网页正文一起返回，所以全面模式不需要再单独调用 Fetch；
     * 这样只记一次 Search 成功请求的费用。
     */
    private fun deepseek(provider: SearchProvider, query: String, maxResults: Int): String {
        val key = provider.apiKey.trim()
        require(key.isNotBlank(), "先填 DeepSeek API Key")
        return postJson(
            "https://api.deepseek.com/v1/tools/search",
            deepseekSearchBody(query, maxResults, provider.deep),
            key,
        )
    }

    internal fun deepseekSearchBody(query: String, maxResults: Int, includeContent: Boolean): JSONObject =
        JSONObject().apply {
            put("query", query)
            put("count", maxResults.coerceIn(1, 50))
            put("need_content", includeContent)
        }

    /** 豆包（火山引擎）联网搜索。字段名是首字母大写的，照官方协议来。 */
    private fun doubao(provider: SearchProvider, query: String, maxResults: Int): String {
        require(provider.apiKey.isNotBlank(), "先填 API Key")
        val body = JSONObject().apply {
            put("Query", query)
            put("SearchType", "web")
            put("Count", maxResults)
            put("NeedSummary", provider.deep)
            put(
                "Filter",
                JSONObject().apply {
                    put("NeedContent", provider.deep)
                    put("NeedUrl", true)
                },
            )
        }
        return postJson("https://open.feedcoopapi.com/search_api/web_search", body, provider.apiKey)
    }

    /** Firecrawl 搜索（v2；v1 的 `data` 数组也兼容）。 */
    private fun firecrawl(provider: SearchProvider, query: String, maxResults: Int): String {
        require(provider.apiKey.isNotBlank(), "先填 API Key")
        val body = JSONObject().apply {
            put("query", query)
            put("limit", maxResults)
        }
        return postJson("https://api.firecrawl.dev/v2/search", body, provider.apiKey)
    }

    /**
     * Bing（免费，不用 Key）—— 和 RikkaHub 的 `BingLocalOptions` 一个思路：
     * 直接抓搜索结果页的 HTML，从 `li.b_algo` 里取标题 / 链接 / 摘要。
     *
     * - 桌面 Chrome UA + `Accept-Language` + `Referer` + `SRCHHPGUSR=ULSR=1`：
     *   RikkaHub 也是这么伪装浏览器的，少一个都更容易被拦；
     * - 先试 `cn.bing.com`（国内网络下 www 经常只回一个 JS 壳），没有结果再试 `www.bing.com`；
     * - 被风控 / 结构改了就拿不到 `b_algo`，调用方会给出明确提示。
     */
    private fun bing(query: String, maxResults: Int): String {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val urls = listOf(
            "https://cn.bing.com/search?q=" + enc(query) + "&count=" + maxResults,
            "https://www.bing.com/search?q=" + enc(query) + "&count=" + maxResults,
        )
        var last = ""
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val conn = open(url)
                conn.setRequestProperty("User-Agent", ua)
                conn.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                )
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                conn.setRequestProperty("Referer", "https://www.bing.com/")
                conn.setRequestProperty("Cookie", "SRCHHPGUSR=ULSR=1")
                last = read(conn)
                if (last.contains("b_algo")) return last
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (last.isBlank() && lastError != null) throw lastError
        return last
    }

    private fun brave(provider: SearchProvider, query: String, maxResults: Int): String {
        require(provider.apiKey.isNotBlank(), "先填 API Key")
        val url = "https://api.search.brave.com/res/v1/web/search?q=" + enc(query) + "&count=" + maxResults
        val conn = open(url)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("X-Subscription-Token", provider.apiKey)
        return read(conn)
    }

    private fun searxng(provider: SearchProvider, query: String): String {
        val base = provider.baseUrl.ifBlank { "http://localhost:8080" }.trimEnd('/')
        val url = base + "/search?format=json&language=zh-CN&q=" + enc(query)
        return read(open(url))
    }

    private fun google(provider: SearchProvider, query: String, maxResults: Int): String {
        require(provider.apiKey.isNotBlank(), "先填 API Key（Google 的 key）")
        require(provider.baseUrl.isNotBlank(), "先填 cx（搜索引擎 id）")
        val url = "https://www.googleapis.com/customsearch/v1?key=" + enc(provider.apiKey) +
            "&cx=" + enc(provider.baseUrl) + "&num=" + maxResults.coerceAtMost(10) + "&q=" + enc(query)
        return read(open(url))
    }

    private fun open(url: String): HttpURLConnection {
        NetworkGuard.requireAllowed(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 20_000
        }
    }

    private fun read(conn: HttpURLConnection): String = try {
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) throw IOException("搜索响应过大")
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }.orEmpty()
        if (status !in 200..299) throw IOException("HTTP " + status + " · " + text.take(160))
        text
    } finally {
        runCatching { conn.disconnect() }
    }

    private fun postJson(url: String, body: JSONObject, token: String?): String {
        NetworkGuard.requireAllowed(url)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { setRequestProperty("Authorization", "Bearer " + it) }
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return read(conn)
    }

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    /**
     * Bing 的结果页是 HTML：每个结果是一个 `<li class="b_algo">`，
     * 标题在 `h2 > a`、链接是那个 a 的 href、摘要在 `.b_caption p`。
     * 这里用正则切块（不引 Jsoup，保持纯原生）。
     */
    private fun parseBingHtml(html: String): List<Hit> {
        val blocks = Regex("(?=<li class=\"b_algo\")").split(html).drop(1)
        return blocks.mapNotNull { block ->
            val link = Regex("<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"")
                .find(block)?.groupValues?.get(1).orEmpty()
            val title = Regex("<h2[^>]*>[\\s\\S]*?<a[^>]*>([\\s\\S]*?)</a>")
                .find(block)?.groupValues?.get(1).orEmpty()
            val snippet = Regex("<p[^>]*>([\\s\\S]*?)</p>")
                .find(block)?.groupValues?.get(1).orEmpty()
            if (link.isBlank()) return@mapNotNull null
            Hit(
                title = cleanHtml(title).ifBlank { link },
                url = link,
                snippet = cleanHtml(snippet),
            )
        }
    }

    /** 去标签 + 解 HTML 实体 + 合并空白。 */
    private fun cleanHtml(raw: String): String {
        var text = raw.replace(Regex("<[^>]+>"), " ")
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        text = Regex("&#(\\d+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        text = Regex("&#[xX]([0-9a-fA-F]+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** 四家的返回结构不一样，这里统一成 [Hit]。 */
    internal fun parseHits(kind: String, raw: String, includeContent: Boolean = false): List<Hit> {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array: JSONArray = when (kind) {
            SearchProvider.KIND_DEEPSEEK -> json.optJSONArray("results")
            SearchProvider.KIND_DOUBAO -> json.optJSONObject("Result")?.optJSONArray("WebResults")
            SearchProvider.KIND_TAVILY -> json.optJSONArray("results")
            SearchProvider.KIND_FIRECRAWL ->
                json.optJSONObject("data")?.optJSONArray("web") ?: json.optJSONArray("data")
            SearchProvider.KIND_BRAVE -> json.optJSONObject("web")?.optJSONArray("results")
            SearchProvider.KIND_SEARXNG -> json.optJSONArray("results")
            SearchProvider.KIND_GOOGLE -> json.optJSONArray("items")
            else -> null
        } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            // 豆包是首字母大写的字段，其他家是小写
            val title = item.str("title").ifBlank { item.str("Title") }.ifBlank { item.str("name") }
            val url = item.str("url").ifBlank { item.str("Url") }.ifBlank { item.str("link") }
            val snippet = if (kind == SearchProvider.KIND_DEEPSEEK) {
                item.str("summary")
                    .ifBlank { item.str("snippet") }
                    .ifBlank { if (includeContent) item.str("content").take(1800) else "" }
            } else {
                item.str("content")
                    .ifBlank { item.str("snippet") }
                    .ifBlank { item.str("description") }
                    .ifBlank { item.str("Summary") }
                    .ifBlank { item.str("Snippet") }
            }
            if (url.isBlank()) {
                null
            } else {
                Hit(
                    title = title.ifBlank { url },
                    url = url,
                    snippet = snippet,
                    score = item.optDouble("rerank_score", 0.0),
                    authority = item.optInt("authority", 0),
                    publishedAt = item.str("date"),
                    siteName = item.str("site_name"),
                )
            }
        }
    }

    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key)
}
