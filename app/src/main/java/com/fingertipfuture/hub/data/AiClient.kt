package com.fingertipfuture.hub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一轮对话（发给模型的历史）。[attachments] 是这一轮带的图片 / 文件 / 位置。 */
data class HubTurn(
    val role: String,
    val content: String,
    val attachments: List<HubAttachment> = emptyList(),
)

/**
 * OpenAI 兼容的对话客户端。
 *
 * **纯安卓原生**：HttpURLConnection 逐行读 SSE（data: {...}），不引 WebView、
 * 不引跨平台库、不引 OkHttp —— 这里只有"发一段 JSON、读一串分片"这一件事。
 *
 * 每个供应商可以配自己的 `apiPath`（默认 /chat/completions —— 内置厂商都预设好了）。
 * 另外提供 [listModels]：拉这家现在能用哪些模型（配置页那颗「自动检测模型」）。
 */
object AiClient {

    private const val CONNECT_TIMEOUT_MS = 20_000

    /** 流式读超时：两段增量之间可能等很久（推理模型尤甚），给宽一点。 */
    private const val STREAM_READ_TIMEOUT_MS = 180_000

    /**
     * 当前正在读的那条连接。
     *
     * 为什么要留这个引用：`isCancelled()` 只在"下一行 SSE 到达"时才被检查，
     * 而 `readLine()` 最多阻塞 180 秒。模型卡住时点「停止生成」会像没反应一样，
     * 用户只能杀进程。掐断底层流能让阻塞的读取立刻抛错退出。
     */
    /**
     * 一次请求自己的连接句柄。
     *
     * 以前是全局 activeStream / activeConnection，标题生成和聊天同时跑时，
     * `interrupt()` 会把另一条正常请求一起掐掉。现在每次 stream 都拿一个句柄，
     * 停止/超时只关自己这一条。
     */
    class StreamHandle internal constructor() {
        @Volatile internal var input: java.io.InputStream? = null
        @Volatile internal var connection: HttpURLConnection? = null
        @Volatile internal var cancelled: Boolean = false

        fun cancel() {
            cancelled = true
            runCatching { input?.close() }
            runCatching { connection?.disconnect() }
        }
    }

    fun newStreamHandle(): StreamHandle = StreamHandle()

    /** 只中止指定请求（聊天 / 标题生成各用各的句柄）。 */
    fun interrupt(handle: StreamHandle?) {
        handle?.cancel()
    }

    /**
     * 流式对话。
     *
     * @param onDelta 每收到一段正文回调一次，传"累加后的全文"，界面直接拿去渲染。
     * @param onReasoning 思考过程（reasoning_content / reasoning delta）的累加全文；没有就一次都不回调。
     * @param isCancelled 返回 true 就立刻停下（等价于点「停止生成」）。
     * @param streaming 传 false 就一次拿完整回复（「设置 › 界面 › 流式输出」关掉时走这条）。
     */
    suspend fun stream(
        provider: HubProvider,
        model: String,
        turns: List<HubTurn>,
        settings: HubChatSettings = HubChatSettings(),
        isCancelled: () -> Boolean = { false },
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit = {},
        onUsage: suspend (HubTokenUsage) -> Unit = {},
        streaming: Boolean = true,
        /** 后台规划 / 标题生成等任务传 false，不注入正文排版和技能提示词。 */
        formatResponse: Boolean = true,
        handle: StreamHandle? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val request = handle ?: newStreamHandle()
        if (request.cancelled) {
            return@withContext Result.success("")
        }
        var accumulated = ""
        var reasoningAccumulated = ""
        var connection: HttpURLConnection? = null
        try {
            require(provider.baseUrl.isNotBlank()) { "请先填写接口地址" }
            require(model.isNotBlank()) { "请先选择模型" }

            val base = provider.baseUrl.trim().trimEnd('/')
            val path = provider.apiPath.trim().let { custom ->
                when {
                    custom.isEmpty() -> "/chat/completions"
                    custom.startsWith("/") -> custom
                    else -> "/$custom"
                }
            }
            val endpoint = base + path
            NetworkGuard.requireAllowed(endpoint)
            val openAiOfficial = base.contains("api.openai.com", ignoreCase = true)
            val openAiReasoning = openAiOfficial &&
                Regex("^(o\\d|gpt-5)", RegexOption.IGNORE_CASE).containsMatchIn(model)

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    // 系统提示词 = 用户自己写的 + 选中技能的提示词
                    buildSystemPrompt(settings, formatResponse).takeIf { it.isNotEmpty() }?.let { prompt ->
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", prompt)
                        })
                    }
                    budgetedTurns(turns.takeLast(30)).forEach { turn ->
                        put(JSONObject().apply {
                            put("role", turn.role)
                            put("content", buildContent(turn))
                        })
                    }
                })
                put("stream", streaming)
                // 老中转不认 stream_options，只对明确支持的几家发，避免直接 400。
                if (streaming && supportsStreamUsage(base)) {
                    put("stream_options", JSONObject().apply { put("include_usage", true) })
                }
                // OpenAI 的 o 系列不接受 temperature / max_tokens。
                if (!openAiReasoning) settings.temperature?.let { put("temperature", it) }
                settings.maxTokens?.let {
                    put(if (openAiReasoning) "max_completion_tokens" else "max_tokens", it)
                }
                // 思考深度七档：自动不额外发；其余按档位发 reasoning_effort。
                when (settings.thinkingDepth) {
                    0 -> if (!openAiOfficial) put("reasoning_effort", "none")
                    2 -> put("reasoning_effort", "low")
                    3 -> put("reasoning_effort", "medium")
                    4 -> put("reasoning_effort", "high")
                    5 -> put("reasoning_effort", if (openAiOfficial) "high" else "xhigh")
                    6 -> put("reasoning_effort", if (openAiOfficial) "high" else "max")
                }
            }
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = STREAM_READ_TIMEOUT_MS
                doOutput = true
                // 本机 / 局域网服务（Ollama、LM Studio…）通常不要 Key：填了才带这个头
                if (provider.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer " + provider.apiKey)
                }
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", if (streaming) "text/event-stream" else "application/json")
            }
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            request.connection = connection
            if (request.cancelled) return@withContext Result.success(accumulated)

            val status = connection.responseCode
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val message = runCatching {
                    val json = JSONObject(raw)
                    json.optJSONObject("error")?.optString("message").orEmpty()
                        .ifBlank { json.optString("message") }
                        .ifBlank { raw }
                }.getOrDefault(raw)
                return@withContext Result.failure(IOException("HTTP " + status + " · " + message.take(400)))
            }

            // 「流式输出」关掉时：一次拿完整回复，再一次性交给界面（答题思路、思考过程都一样）
            if (!streaming) {
                val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = runCatching { JSONObject(raw) }.getOrNull()
                    ?: return@withContext Result.failure(IOException("接口没返回 JSON"))
                val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                usageOf(json)?.let { onUsage(it) }
                val reasoning = message.str("reasoning_content")
                if (reasoning.isNotEmpty()) {
                    reasoningAccumulated = reasoning
                    onReasoning(reasoning)
                }
                val content = message.str("content")
                accumulated = content
                if (content.isNotEmpty()) onDelta(content)
                return@withContext Result.success(content)
            }

            // 逐行读 SSE：空行分隔事件，"data:" 后面是 JSON，[DONE] 结束
            val stream = connection.inputStream
            request.input = stream
            if (request.cancelled) return@withContext Result.success(accumulated)
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (isCancelled() || request.cancelled) break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    // 有些中转发完 [DONE] 不会主动断开连接；继续读会一直卡到
                    // readTimeout（180s），表现成“正文已经完整，停止键还在转”。
                    if (payload == "[DONE]") break
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    usageOf(json)?.let { onUsage(it) }

                    val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                    val reason = delta.str("reasoning_content")
                    if (reason.isNotEmpty()) {
                        reasoningAccumulated += reason
                        onReasoning(reasoningAccumulated)
                    }
                    val content = delta.str("content")
                    if (content.isNotEmpty()) {
                        accumulated += content
                        onDelta(accumulated)
                    }
                }
            }
            Result.success(accumulated)
        } catch (t: Throwable) {
            // 被 interrupt() 掐断的读取会抛 SocketException("Socket closed") ——
            // 这是「用户点了停止」或「界面被销毁」，不是失败
            if (isCancelled()) Result.success(accumulated) else Result.failure(t)
        } finally {
            request.input = null
            request.connection = null
            runCatching { connection?.disconnect() }
        }
    }

    /** 兼容 OpenAI / DeepSeek 两种缓存字段命名。 */
    private fun usageOf(json: JSONObject): HubTokenUsage? {
        val usage = json.optJSONObject("usage") ?: return null
        val input = usage.optInt("prompt_tokens", usage.optInt("input_tokens", 0))
        val output = usage.optInt("completion_tokens", usage.optInt("output_tokens", 0))
        val cached = usage.optJSONObject("prompt_tokens_details")
            ?.optInt("cached_tokens", 0)
            ?: usage.optInt("cache_read_input_tokens", usage.optInt("cached_tokens", 0))
        if (input == 0 && output == 0 && cached == 0) return null
        return HubTokenUsage(input = input, output = output, cached = cached)
    }

    /**
     * 查账户余额（2026-10-16：提供商配置页的「查询余额」）。
     *
     * 只有几家有公开接口，别家会抛「没有公开的余额接口」——不编数字。
     */
    suspend fun fetchBalance(provider: HubProvider): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(provider.apiKey.isNotBlank()) { "先填 API Key" }
            val host = provider.baseUrl.lowercase()
            val url = when {
                host.contains("deepseek") -> "https://api.deepseek.com/user/balance"
                host.contains("siliconflow") -> "https://api.siliconflow.cn/v1/user/info"
                host.contains("moonshot") -> "https://api.moonshot.cn/v1/users/me/balance"
                host.contains("openrouter") -> "https://openrouter.ai/api/v1/auth/key"
                else -> throw IOException("这家没有公开的余额接口（可以点「测试连接」看看通不通）")
            }
            NetworkGuard.requireAllowed(url)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer " + provider.apiKey)
                setRequestProperty("Accept", "application/json")
            }
            val status = conn.responseCode
            val raw = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            runCatching { conn.disconnect() }
            if (status !in 200..299) throw IOException("HTTP " + status + " · " + raw.take(160))
            val json = JSONObject(raw)
            when {
                host.contains("deepseek") -> {
                    val info = json.optJSONArray("balance_infos")?.optJSONObject(0)
                    "余额 " + (info?.optString("total_balance") ?: "?") + " " +
                        (info?.optString("currency") ?: "")
                }
                host.contains("siliconflow") ->
                    "余额 " + (json.optJSONObject("data")?.optString("totalBalance") ?: "?")
                host.contains("moonshot") ->
                    "余额 " + (json.optJSONObject("data")?.optString("available_balance") ?: "?")
                else -> {
                    val data = json.optJSONObject("data")
                    "已用 " + (data?.optString("usage") ?: "?") + " / 额度 " +
                        (data?.optString("limit") ?: "?")
                }
            }
        }
    }

    /** 系统提示词 = 用户自己写的 + 选中技能的提示词。 */
    private fun buildSystemPrompt(settings: HubChatSettings, formatResponse: Boolean): String {
        val base = settings.systemPrompt.trim()
        val skill = SkillStore.byId(settings.skillId)?.prompt.orEmpty()
        val languageRule = LanguageStore.assistantLanguageInstruction()
        return listOf(
            base,
            skill,
            if (formatResponse) RESPONSE_FORMATTING_PROMPT else "",
            languageRule,
            // 动态内容必须最后放，稳定前缀才能命中供应商缓存。
            settings.dynamicContext.trim(),
        )
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    /** 让回复自然使用 Markdown 的结构化排版，但简单问题不强行套模板。 */
    private val RESPONSE_FORMATTING_PROMPT = """
输出排版要求：
1. 回复使用 Markdown；复杂内容按“结论 / 要点 / 步骤 / 补充”组织，使用二级或三级标题分段。
2. 对比、参数、选项、优缺点、时间线等适合列表化展示的信息，优先使用 Markdown 表格。
3. 操作步骤使用有序列表，并列信息使用无序列表；代码、命令、配置和终端输出使用围栏代码块并标注语言。
4. 引用网页资料时保留可点击的 Markdown 链接。
5. 简单寒暄、单句事实或很短的回答保持简短，不要为了排版硬加标题、表格或总结。
6. 如果用户或已启用技能明确要求了输出形式（例如纯译文、只返回代码、JSON），以该要求为准，不要额外添加标题或解释。
""".trimIndent()

    /**
     * 一轮消息的 `content`：
     * - 没有图片 → 纯字符串（原行为），文件和位置拼成文字说明接在后面；
     * - 有图片 → 多模态数组（`text` + `image_url` 的 base64 data URL）。
     *
     * 文件和位置没有标准的结构化表示，主流做法也是拼进文字；**文本类文件的内容会一起带上**
     * （最多 2 万字符），这样模型才真的用得上。
     */
    private suspend fun buildContent(turn: HubTurn): Any {
        val extra = buildString {
            turn.attachments.forEach { attachment ->
                if (attachment.isImage) return@forEach
                append('\n').append(attachment.describe())
                if (attachment.isFile) {
                    val content = AttachmentStore.readTextContent(attachment)
                    if (content.isNotBlank()) {
                        append("\n--- 文件内容开始 ---\n").append(content).append("\n--- 文件内容结束 ---")
                    }
                }
            }
        }
        val text = turn.content + extra
        // 单轮最多带 4 张图：再多 base64 会把请求体撑得非常大，
        // 而且大多数模型单次也只能稳定处理有限张。
        val images = turn.attachments.filter { it.isImage }.take(4)
        if (images.isEmpty()) return text

        return JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            })
            images.forEach { image ->
                val dataUrl = AttachmentStore.imageDataUrl(image) ?: return@forEach
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply { put("url", dataUrl) })
                })
            }
        }
    }

    /**
     * 拉一遍这家供应商现在**能用哪些模型**：GET `{baseUrl}/models`（OpenAI 兼容的都支持）。
     *
     * 不支持的（比如火山方舟要自己填 endpoint id）会失败，界面提示一下就好。
     */
    suspend fun listModels(provider: HubProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        val base = provider.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext Result.failure(IOException("请先填写接口地址"))
        try {
            val url = "$base/models"
            NetworkGuard.requireAllowed(url)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30_000
                if (provider.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer " + provider.apiKey)
                }
                setRequestProperty("Accept", "application/json")
            }
            try {
                val status = conn.responseCode
                if (status !in 200..299) {
                    return@withContext Result.failure(IOException("HTTP $status"))
                }
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(raw)
                val array = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                val ids = (0 until array.length())
                    .mapNotNull { index ->
                        val item = array.optJSONObject(index)
                        item?.optString("id").orEmpty()
                            .ifBlank { item?.optString("name").orEmpty() }
                            .takeIf { it.isNotBlank() }
                    }
                    .distinct()
                    .sorted()
                Result.success(ids)
            } finally {
                runCatching { conn.disconnect() }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * JSON null / 缺键一律当空串。
     *
     * ⚠️ 不能直接 `optString(key)`：org.json 遇到 **JSON null** 会返回字符串 "null"。
     * DeepSeek 这类推理模型在思考阶段发的正是 `"content": null`（正文阶段反过来发
     * `"reasoning_content": null`），2026-10-16 实测正文气泡里会拼出一大串 "null"。
     */
    private fun JSONObject?.str(key: String): String =
        if (this == null || isNull(key)) "" else optString(key)

    /** 只有明确支持 stream_options 的供应商才带 usage 选项，避免老中转直接拒绝请求。 */
    private fun supportsStreamUsage(baseUrl: String): Boolean {
        val host = baseUrl.lowercase()
        return host.contains("api.openai.com") ||
            host.contains("api.deepseek.com") ||
            host.contains("siliconflow") ||
            host.contains("moonshot") ||
            host.contains("api.x.ai")
    }
}

/**
 * 给历史消息一个粗略的字符预算。
 *
 * 只按“最近 30 条”截断不够：一条消息可能带 2 万字符的文件内容，
 * 30 条就远超上下文窗口。这里从最新往前装，保证最后一条用户消息一定在。
 */
internal fun budgetedTurns(
    turns: List<HubTurn>,
    maxChars: Int = 120_000,
): List<HubTurn> {
    if (turns.isEmpty()) return emptyList()
    val selected = ArrayDeque<HubTurn>()
    var used = 0L
    for (turn in turns.asReversed()) {
        val size = turn.content.length.toLong() + turn.attachments.sumOf { attachment ->
            if (attachment.isImage) 4_096L else 512L
        }
        if (selected.isNotEmpty() && used + size > maxChars) break
        selected.addFirst(turn)
        used += size
    }
    return selected.toList()
}
