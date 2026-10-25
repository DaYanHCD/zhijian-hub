package com.fingertipfuture.hub.data.agent

import com.fingertipfuture.hub.data.HubTokenUsage
import org.json.JSONObject

/** 第一阶段只负责决定走哪条链路，具体怎么做由技能说明决定。 */
internal enum class AgentRoute {
    ANSWER,
    SKILLS,
    CLARIFY,
}

internal data class AgentRoutePlan(
    val route: AgentRoute,
    val skillIds: List<String> = emptyList(),
    val reason: String = "",
    val question: String = "",
)

/**
 * 给模型看的技能说明。
 *
 * 目录阶段只展示 [name] / [description] / [whenToUse]；选中后才把 [prompt]
 * 和 [toolIds] 对应工具的完整参数说明交给工具规划器。
 */
internal data class AgentSkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val whenToUse: String,
    val prompt: String,
    val toolIds: List<String> = emptyList(),
    val permissionLabel: String = "",
    val builtIn: Boolean = false,
)

internal data class AgentToolSpec(
    val id: String,
    val name: String,
    val description: String,
    /** 一个紧凑 JSON 对象，用来告诉模型 arguments 需要哪些字段。 */
    val parameters: String,
    val risk: String,
)

internal data class AgentToolCall(
    val id: String,
    val toolId: String,
    val arguments: JSONObject,
    val purpose: String = "",
)

internal enum class AgentToolStatus {
    SUCCESS,
    SKIPPED,
    REJECTED,
    UNAVAILABLE,
    ERROR,
}

/**
 * 工具执行结果。
 *
 * [contextText] 会作为“不可信工具资料”交给最终回答模型；[data] 保留结构化字段，
 * 方便以后做更严格的校验和 UI 展示。
 */
internal data class AgentToolResult(
    val callId: String,
    val toolId: String,
    val status: AgentToolStatus,
    val title: String,
    val contextText: String = "",
    val error: String = "",
    val data: JSONObject = JSONObject(),
    val verified: Boolean = false,
)

internal data class AgentPlanningResult<T>(
    val value: T,
    val usage: HubTokenUsage = HubTokenUsage(),
)
