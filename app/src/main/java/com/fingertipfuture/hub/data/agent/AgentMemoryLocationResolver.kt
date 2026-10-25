package com.fingertipfuture.hub.data.agent

import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.HubChatSettings
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.MemoryEntry
import org.json.JSONObject

/** 本地规则无法提取时，让当前模型阅读用户已启用的全部记忆并找出明确地点。 */
internal object AgentMemoryLocationResolver {

    suspend fun resolve(
        provider: HubProvider,
        model: String,
        userText: String,
        memories: List<MemoryEntry>,
        handle: AiClient.StreamHandle,
    ): Result<AgentPlanningResult<String?>> = runCatching {
        if (memories.isEmpty()) return@runCatching AgentPlanningResult(null)
        val memoryText = memories.take(30).joinToString("\n") { "- ${it.text.take(500)}" }
        var usage = HubTokenUsage()
        val raw = AiClient.stream(
            provider = provider,
            model = model,
            turns = listOf(
                HubTurn(
                    role = "user",
                    content = "用户问题：${userText.take(1_500)}\n\n已启用记忆：\n$memoryText",
                ),
            ),
            settings = HubChatSettings(
                systemPrompt = """
你是记忆地点提取器。请阅读用户主动启用的记忆，只提取明确写出的所在地。

规则：
1. 不依赖固定句式；理解“住在杭州”“我现在在北京”“家在上海”等自然表达。
2. 不把偏好、出生地、老家、公司所在地自动当成当前位置；只有表达当前居住或当前所在地时才采用。
3. 没有明确地点时返回空字符串，不要猜测。
4. 只输出 JSON：{"place":"地点"} 或 {"place":""}。
                """.trimIndent(),
                temperature = 0.0,
                maxTokens = 120,
                thinkingDepth = 0,
            ),
            streaming = false,
            formatResponse = false,
            handle = handle,
            onDelta = {},
            onUsage = { usage = it },
        ).getOrThrow()
        AgentPlanningResult(parsePlace(raw), usage)
    }

    internal fun parsePlace(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            JSONObject(raw.substring(start, end + 1))
                .optString("place")
                .trim()
                .takeIf { it.length in 2..20 }
        }.getOrNull()
    }
}
