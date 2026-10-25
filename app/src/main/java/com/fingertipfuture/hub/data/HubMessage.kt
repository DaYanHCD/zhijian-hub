package com.fingertipfuture.hub.data

import org.json.JSONArray
import org.json.JSONObject

/** 这一轮实际注入的技能（用于思考框里的 Skill 行）。 */
internal data class HubSkillTrace(
    val id: String,
    val name: String,
    val description: String = "",
)

/** 搜索结果的一项；标题可点，URL 用来跳浏览器。 */
internal data class HubSearchResult(
    val title: String,
    val url: String,
)

/** 这一轮真实发起的一次联网搜索。 */
internal data class HubSearchTrace(
    val query: String,
    val resultCount: Int = 0,
    val providerKinds: List<String> = emptyList(),
    /** 本轮实际发起请求的供应商；即使结果没进最终 top N，也保留在时间线里。 */
    val queriedProviderKinds: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val results: List<HubSearchResult> = emptyList(),
    val pending: Boolean = false,
    val error: String = "",
    /** true = 命中 5 分钟内存缓存，本次没有再次请求搜索供应商。 */
    val cached: Boolean = false,
)

/**
 * 思考时间线的一步。
 *
 * [kind]：reasoning / thinking / location / search / skill / memory。
 * 理解问题、整理搜索词、组织回答这些本地规划步骤也记成 thinking，
 * 但 [detail] 会写清这一小步实际做了什么，不伪造模型的隐藏思维。
 */
internal data class HubThinkingStep(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String = "",
    val status: String = STATUS_DONE,
    val items: List<HubSearchResult> = emptyList(),
    /** 这一行搜索是否复用了 5 分钟内存缓存。 */
    val cached: Boolean = false,
    val providerKinds: List<String> = emptyList(),
    /** reasoning 步骤自己的耗时；与其它步骤无关，0 表示没有测到。 */
    val durationMs: Long = 0,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
    }
}

/**
 * 一条消息。
 *
 * 2026-10-14 从 `ui/chat/HubMessage.kt` 挪到 `data/`：它是对话的**数据模型**，
 * 存储层（[ChatDatabase]）和存档格式（[ChatArchive]）都要用它，界面层只是渲染它。
 *
 * [streaming] 为 true 时气泡右下角显示一个跳动的光标（正文还空着就先显示 `…`）。
 * [at] 是这条消息的时间戳，用来画聊天时间分隔。
 */
internal data class HubMessage(
    val role: String,
    val text: String,
    val at: Long,
    /** 生成这条回复的模型名（用户消息 / 老消息为空）。 */
    val model: String = "",
    /**
     * 这一条助手的**思考过程**（reasoning_content）。
     *
     * 存下来是为了「回传历史思考过程」那个开关：有些中转要求把上一轮的思考一起带回去。
     * 界面里放在「思考内容」折叠段中，默认只显示两行预览。
     */
    val reasoning: String = "",
    /**
     * 「思考了 X 秒」里的那个 X（毫秒）。
     *
     * 定义：从**发出请求**到**收到第一段正文**之间的时间 —— 对推理模型来说就是它在想的那段，
     * 普通模型也能量出"首字延迟"。0 表示没量到（老消息 / 没联网成功），这时界面不画思考框。
     */
    val thoughtMs: Long = 0,
    /** 这一轮真正拼进系统提示词的技能。 */
    val skillsUsed: List<HubSkillTrace> = emptyList(),
    /** 这一轮真正调用过的搜索（含查询词、结果数和来源）。 */
    val searchTraces: List<HubSearchTrace> = emptyList(),
    /** 思考框里的真实执行顺序：思考 → 位置 → 思考 → 搜索 → 思考。 */
    val thinkingSteps: List<HubThinkingStep> = emptyList(),
    /** 这一条带的附件（图片 / 文件 / 位置）。 */
    val attachments: List<HubAttachment> = emptyList(),
    /** 只在本机显示的提示（比如"已导出/已导入"）：不发给模型，也**不落库**。 */
    val localOnly: Boolean = false,
    val streaming: Boolean = false,
    /**
     * 本机稳定 id（来自 message_node.id）。只用于 Compose 列表 key /
     * 定位消息；不发给模型，也不写进导出存档。
     */
    val id: String = "",
    /** 仅在本次发送后触发一次单表情气泡动画；不写入数据库。 */
    val playEmojiOnce: Boolean = false,
) {
    companion object {
        /**
         * 本地助手提醒的持久化标记。
         *
         * 这类消息需要留在聊天记录里，但不能作为真正的模型回复发送给后续接口。
         */
        const val LOCAL_NOTICE_MODEL = "__hub_local_notice__"
    }

    /** DeepSeek 官方 Search 每成功请求 5 元 / 1000 次。 */
    val searchCostYuan: Double
        get() = searchTraces.count {
            SearchProvider.KIND_DEEPSEEK in it.providerKinds &&
                !it.pending &&
                !it.cached &&
                it.error.isBlank() &&
                it.resultCount > 0
        } * 0.005
}

/** 接口返回的 token 用量（没有返回时三项都是 0）。 */
data class HubTokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val cached: Int = 0,
)

internal operator fun HubTokenUsage.plus(other: HubTokenUsage): HubTokenUsage =
    HubTokenUsage(
        input = input + other.input,
        output = output + other.output,
        cached = cached + other.cached,
    )

internal val HubMessage.isFromUser: Boolean get() = role == "user"

internal fun skillTracesToJson(items: List<HubSkillTrace>): JSONArray = JSONArray().apply {
    items.forEach { item ->
        put(
            JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("description", item.description)
            },
        )
    }
}

internal fun skillTracesFromJson(array: JSONArray?): List<HubSkillTrace> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        HubSkillTrace(
            id = item.optString("id"),
            name = item.optString("name").ifBlank { item.optString("id") },
            description = item.optString("description"),
        )
    }
}

internal fun searchTracesToJson(items: List<HubSearchTrace>): JSONArray = JSONArray().apply {
    items.forEach { item ->
        put(
            JSONObject().apply {
                put("query", item.query)
                put("resultCount", item.resultCount)
                put("providerKinds", JSONArray().apply { item.providerKinds.forEach { put(it) } })
                put(
                    "queriedProviderKinds",
                    JSONArray().apply { item.queriedProviderKinds.forEach { put(it) } },
                )
                put("titles", JSONArray().apply { item.titles.forEach { put(it) } })
                put("results", searchResultsToJson(item.results))
                put("pending", item.pending)
                put("error", item.error)
                put("cached", item.cached)
            },
        )
    }
}

internal fun searchTracesFromJson(array: JSONArray?): List<HubSearchTrace> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        HubSearchTrace(
            query = item.optString("query"),
            resultCount = item.optInt("resultCount"),
            providerKinds = item.optJSONArray("providerKinds")?.let { values ->
                (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            queriedProviderKinds = item.optJSONArray("queriedProviderKinds")?.let { values ->
                (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            titles = item.optJSONArray("titles")?.let { values ->
                (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            results = searchResultsFromJson(item.optJSONArray("results")).ifEmpty {
                item.optJSONArray("titles")?.let { values ->
                    (0 until values.length()).mapNotNull { index ->
                        values.optString(index).takeIf { it.isNotBlank() }
                            ?.let { HubSearchResult(it, "") }
                    }
                }.orEmpty()
            },
            pending = item.optBoolean("pending", false),
            error = item.optString("error"),
            cached = item.optBoolean("cached", false),
        )
    }
}

private fun searchResultsToJson(items: List<HubSearchResult>): JSONArray = JSONArray().apply {
    items.forEach { result ->
        put(
            JSONObject().apply {
                put("title", result.title)
                put("url", result.url)
            },
        )
    }
}

private fun searchResultsFromJson(array: JSONArray?): List<HubSearchResult> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val title = item.optString("title")
        val url = item.optString("url")
        if (title.isBlank() && url.isBlank()) null else HubSearchResult(title, url)
    }
}

internal fun thinkingStepsToJson(items: List<HubThinkingStep>): JSONArray = JSONArray().apply {
    items.forEach { step ->
        put(
            JSONObject().apply {
                put("id", step.id)
                put("kind", step.kind)
                put("title", step.title)
                put("detail", step.detail)
                put("status", step.status)
                put("items", searchResultsToJson(step.items))
                put("cached", step.cached)
                put("providerKinds", JSONArray().apply { step.providerKinds.forEach { put(it) } })
                put("durationMs", step.durationMs)
            },
        )
    }
}

internal fun thinkingStepsFromJson(array: JSONArray?): List<HubThinkingStep> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        HubThinkingStep(
            id = item.optString("id").ifBlank { "step-$index" },
            kind = item.optString("kind").ifBlank { "thinking" },
            title = item.optString("title"),
            detail = item.optString("detail"),
            status = item.optString("status").ifBlank { HubThinkingStep.STATUS_DONE },
            items = searchResultsFromJson(item.optJSONArray("items")),
            cached = item.optBoolean("cached", false),
            providerKinds = item.optJSONArray("providerKinds")?.let { values ->
                (0 until values.length()).map { values.optString(it) }
            }.orEmpty(),
            durationMs = item.optLong("durationMs", 0L).coerceAtLeast(0L),
        )
    }
}
