package com.fingertipfuture.hub.data.agent

import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.HubApiMessage
import com.fingertipfuture.hub.data.HubApiTool
import com.fingertipfuture.hub.data.HubApiToolCall
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.HubChatSettings
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.plus
import org.json.JSONObject

internal data class NativeAgentOutcome(
    val text: String,
    val usage: HubTokenUsage,
    val toolResults: List<AgentToolResult>,
)

/**
 * 与 RikkaHub 相同的基本逻辑：模型原生决定回答或调用工具，安卓执行后把结果
 * 交回模型，循环到模型不再调用工具。
 */
internal object NativeAgentLoop {
    private const val MAX_ROUNDS = 8
    private const val USE_SKILL = "use_skill"

    suspend fun run(
        context: android.content.Context,
        provider: HubProvider,
        model: String,
        assistant: HubAssistant,
        turns: List<HubTurn>,
        skills: List<AgentSkillDefinition>,
        settings: HubChatSettings,
        handle: AiClient.StreamHandle,
        cancelRequested: () -> Boolean,
        confirmReminder: suspend (AssistantReminderIntent) -> AssistantReminder?,
        confirmMemory: suspend (String) -> Boolean,
        confirmDeviceAction: suspend (AgentDeviceActionIntent) -> Boolean,
        onReasoning: suspend (round: Int, text: String) -> Unit,
        onReasoningEnd: suspend (round: Int, text: String) -> Unit = { _, _ -> },
        onText: suspend (accumulated: String) -> Unit,
        onToolStart: suspend (AgentToolCall, String) -> Unit,
        onToolResult: suspend (AgentToolCall, AgentToolResult) -> Unit,
    ): Result<NativeAgentOutcome> {
        val toolSpecs = AgentToolRegistry.forSkills(skills)
        val apiTools = buildList {
            toolSpecs.forEach { spec ->
                add(
                    HubApiTool(
                        name = spec.id.apiToolName(),
                        description = spec.description,
                        parameters = runCatching { JSONObject(spec.parameters) }.getOrDefault(JSONObject()),
                    ),
                )
            }
            if (skills.isNotEmpty()) {
                add(
                    HubApiTool(
                        name = USE_SKILL,
                        description = "加载一个可用技能目录项的完整 SKILL 说明。需要某个技能时先调用。",
                        parameters = JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().put(
                                "name",
                                JSONObject().apply {
                                    put("type", "string")
                                    put("description", "技能名称，必须来自系统提示中的可用技能目录")
                                },
                            ))
                            put("required", org.json.JSONArray().put("name"))
                        },
                    ),
                )
            }
        }
        val byApiName = toolSpecs.associateBy { it.id.apiToolName() }
        val extraMessages = mutableListOf<HubApiMessage>()
        val allResults = mutableListOf<AgentToolResult>()
        val visibleText = StringBuilder()
        var usage = HubTokenUsage()
        val session = AgentToolExecutor.newSession()

        for (round in 0 until MAX_ROUNDS) {
            if (cancelRequested()) break
            val textBeforeRound = visibleText.toString()
            val reply = AiClient.streamAgent(
                provider = provider,
                model = model,
                turns = turns,
                extraMessages = extraMessages,
                tools = apiTools,
                settings = settings,
                streaming = true,
                handle = handle,
                onReasoning = { onReasoning(round, it) },
                onDelta = {
                    onText(textBeforeRound + it)
                },
                onUsage = { usage += it },
            ).getOrElse { return Result.failure(it) }
            visibleText.clear()
            visibleText.append(textBeforeRound).append(reply.content)
            if (reply.reasoning.isNotBlank()) {
                onReasoningEnd(round, reply.reasoning)
            }

            if (reply.toolCalls.isEmpty()) {
                return Result.success(
                    NativeAgentOutcome(
                        text = visibleText.toString(),
                        usage = usage,
                        toolResults = allResults,
                    ),
                )
            }

            extraMessages += HubApiMessage(
                role = "assistant",
                content = reply.content,
                toolCalls = reply.toolCalls,
            )
            reply.toolCalls.forEach { apiCall ->
                if (apiCall.name == USE_SKILL) {
                    val skillName = runCatching {
                        JSONObject(apiCall.arguments).optString("name").trim()
                    }.getOrDefault("")
                    val skill = skills.firstOrNull { it.name == skillName || it.id == skillName }
                    val useSkillCall = AgentToolCall(
                        id = apiCall.id,
                        toolId = USE_SKILL,
                        arguments = JSONObject().put("name", skillName),
                        purpose = "加载技能说明：$skillName",
                    )
                    onToolStart(useSkillCall, "加载技能 · $skillName")
                    val result = if (skill == null) {
                        AgentToolResult(
                            callId = apiCall.id,
                            toolId = USE_SKILL,
                            status = AgentToolStatus.ERROR,
                            title = "加载技能 · $skillName",
                            error = "技能不存在或未启用",
                        )
                    } else {
                        AgentToolResult(
                            callId = apiCall.id,
                            toolId = USE_SKILL,
                            status = AgentToolStatus.SUCCESS,
                            title = "加载技能 · ${skill.name}",
                            contextText = skill.prompt,
                            verified = true,
                        )
                    }
                    onToolResult(useSkillCall, result)
                    allResults += result
                    extraMessages += HubApiMessage(
                        role = "tool",
                        content = result.toApiContent(),
                        toolCallId = apiCall.id,
                    )
                } else {
                    val spec = byApiName[apiCall.name]
                    val args = runCatching { JSONObject(apiCall.arguments.ifBlank { "{}" }) }
                        .getOrElse {
                            return Result.failure(IllegalArgumentException("工具参数不是 JSON: ${apiCall.name}"))
                        }
                    val call = AgentToolCall(
                        id = apiCall.id,
                        toolId = spec?.id.orEmpty(),
                        arguments = args,
                        purpose = spec?.name.orEmpty(),
                    )
                    onToolStart(call, toolDisplayTitle(call.toolId))
                    val result = if (call.toolId.isBlank()) {
                        AgentToolResult(
                            callId = call.id,
                            toolId = apiCall.name,
                            status = AgentToolStatus.ERROR,
                            title = apiCall.name,
                            error = "模型调用了未开放的工具：${apiCall.name}",
                        )
                    } else {
                        AgentToolExecutor.execute(
                            context = context,
                            assistant = assistant,
                            userText = turns.lastOrNull { it.role == "user" }?.content.orEmpty(),
                            call = call,
                            session = session,
                            confirmReminder = confirmReminder,
                            confirmMemory = confirmMemory,
                            confirmDeviceAction = confirmDeviceAction,
                        )
                    }
                    allResults += result
                    onToolResult(call, result)
                    extraMessages += HubApiMessage(
                        role = "tool",
                        content = result.toApiContent(),
                        toolCallId = apiCall.id,
                    )
                }
            }
        }

        return Result.success(
            NativeAgentOutcome(
                text = visibleText.toString(),
                usage = usage,
                toolResults = allResults,
            ),
        )
    }

    private fun AgentToolResult.toApiContent(): String = JSONObject().apply {
        put("status", status.name.lowercase())
        if (contextText.isNotBlank()) put("result", contextText)
        if (error.isNotBlank()) put("error", error)
        if (data.length() > 0) put("data", data)
        put("verified", verified)
    }.toString()

    private fun String.apiToolName(): String = replace('.', '_')

    private fun toolDisplayTitle(toolId: String): String = when (toolId) {
        AgentToolRegistry.MEMORY_LIST -> "使用记忆"
        AgentToolRegistry.LOCATION_CURRENT -> "使用技能 · 位置信息"
        AgentToolRegistry.WEB_SEARCH -> "使用技能 · 联网查询"
        AgentToolRegistry.REMINDER_LIST,
        AgentToolRegistry.REMINDER_SAVE,
        AgentToolRegistry.REMINDER_CLOSE,
        AgentToolRegistry.REMINDER_DELETE -> "使用技能 · 自动化任务"
        else -> "使用技能 · " + (AgentToolRegistry.byId(toolId)?.name ?: toolId)
    }
}
