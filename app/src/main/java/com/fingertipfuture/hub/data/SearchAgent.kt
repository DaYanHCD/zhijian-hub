package com.fingertipfuture.hub.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal sealed interface AgentDecision {
    data class Search(val queries: List<String>, val reason: String) : AgentDecision
    data class Answer(val reason: String) : AgentDecision
}

internal data class AgentPlanningResult(
    val decision: AgentDecision,
    val usage: HubTokenUsage = HubTokenUsage(),
    val reasoning: String = "",
    val durationMs: Long = 0,
)

/**
 * 模型驱动的搜索规划器。
 *
 * 它不直接回答用户，只决定下一步是“搜索哪些关键词”还是“资料够了，开始回答”。
 * 每轮最多搜两条关键词，最多两轮；第二轮能看到第一轮标题/摘要，决定是否补搜。
 *
 * 这一步用当前模型（或设置里的“快速模型”）做一次短 JSON 调用。它取代了原先单靠
 * 本地关键词表判断搜索的做法；本地 `FeatureActivation.planSearch` 只在规划调用失败时兜底。
 */
internal object SearchAgent {

    suspend fun decide(
        provider: HubProvider,
        model: String,
        userText: String,
        recentTurns: List<HubTurn>,
        location: String,
        previousSearchQuery: String,
        evidence: List<SearchEvidence>,
        handle: AiClient.StreamHandle?,
        includeCurrentTime: Boolean = false,
    ): Result<AgentPlanningResult> {
        var usage = HubTokenUsage()
        var reasoning = ""
        val startedAt = System.currentTimeMillis()
        val payload = buildString {
            if (includeCurrentTime) {
                append("当前时间：")
                append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()))
                append('\n')
            }
            if (location.isNotBlank()) append("当前位置：").append(location).append('\n')
            if (previousSearchQuery.isNotBlank()) {
                append("上一轮搜索词：").append(previousSearchQuery.take(200)).append('\n')
            }
            append("\n最近对话：\n")
            recentTurns.takeLast(6).forEach { turn ->
                append(if (turn.role == "user") "用户：" else "助手：")
                append(turn.content.replace('\n', ' ').take(500))
                append('\n')
            }
            if (evidence.isNotEmpty()) {
                append("\n本轮已经拿到的搜索结果：\n")
                evidence.take(12).forEachIndexed { index, item ->
                    append(index + 1).append(". [").append(item.query).append("] ")
                    append(item.title.take(140)).append('\n')
                    if (item.snippet.isNotBlank()) {
                        append("   ").append(item.snippet.replace('\n', ' ').take(420)).append('\n')
                    }
                    append("   ").append(item.url).append('\n')
                }
            }
            append("\n本次用户消息：\n").append(userText.take(3000))
        }
        val settings = HubChatSettings(
            systemPrompt = SYSTEM_PROMPT,
            temperature = 0.1,
            maxTokens = 480,
        )
        return AiClient.stream(
            provider = provider,
            model = model,
            turns = listOf(HubTurn(role = "user", content = payload)),
            settings = settings,
            streaming = false,
            formatResponse = false,
            handle = handle,
            onDelta = {},
            onReasoning = { reasoning = it },
            onUsage = { usage = it },
        ).mapCatching { raw ->
            AgentPlanningResult(
                decision = parseDecision(raw),
                usage = usage,
                reasoning = reasoning,
                durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L),
            )
        }
    }

    private fun parseDecision(raw: String): AgentDecision {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "搜索规划没有返回 JSON" }
        val json = JSONObject(raw.substring(start, end + 1))
        val action = json.optString("action").trim().lowercase()
        val reason = json.optString("reason").trim().take(220)
        return when (action) {
            "search" -> {
                val queries = buildList {
                    json.optJSONArray("queries")?.let { array ->
                        (0 until array.length()).forEach { index ->
                            val value = FeatureActivation.sanitizePlannedQuery(array.optString(index))
                            if (value.isNotBlank()) add(value)
                        }
                    }
                    val single = FeatureActivation.sanitizePlannedQuery(json.optString("query"))
                    if (single.isNotBlank()) add(single)
                }.distinct().take(2)
                require(queries.isNotEmpty()) { "搜索规划没有给出关键词" }
                AgentDecision.Search(queries, reason.ifBlank { "模型判断需要联网核对" })
            }
            "answer", "" -> AgentDecision.Answer(
                reason.ifBlank { "现有资料已经足够回答" },
            )
            else -> error("不认识的搜索规划动作：$action")
        }
    }

    private val SYSTEM_PROMPT = """
你是搜索规划器，不负责回答用户。你必须只输出一个 JSON 对象，不要 Markdown、不要解释。

可选动作：
1. 需要联网或需要补搜时：
{"action":"search","queries":["关键词1","关键词2"],"reason":"为什么搜"}
2. 已有资料足够或不需要联网时：
{"action":"answer","reason":"为什么可以直接回答"}

规则：
- 用户明确要求搜索、查询、联网、核对、最新消息时，必须 search。
- 用户抱怨上次“不是最新、关键词错了、明明没搜、搜得不对”时，这是纠错重搜要求。
  不要搜索脏话或投诉原句，要结合最近对话和“上一轮搜索词”重新生成正确关键词。
- 用户点名了搜索对象时保留对象；用户点名地点时保留地点；天气问题补上“今天天气 天气预报”。
- “这个、它、刚才那个、最新消息”等指代必须结合最近对话展开。
- 搜索词应是搜索引擎关键词，不要带“帮我、请、谢谢、搜索一下”等口令。
- 每轮最多 2 个 queries，能一个解决就不要给两个。
- 已给出搜索结果时，先判断标题/摘要能否回答当前问题；足够就 answer，不足才补搜。
- 不要为了搜索而搜索；普通闲聊、写作、翻译、代码解释直接 answer。
""".trimIndent()
}
