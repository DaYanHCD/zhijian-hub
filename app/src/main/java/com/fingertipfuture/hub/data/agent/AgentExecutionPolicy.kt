package com.fingertipfuture.hub.data.agent

import com.fingertipfuture.hub.data.FeatureActivation
import org.json.JSONObject

/**
 * 工具规划器漏掉单工具技能时，由安卓确定性补齐。
 *
 * 单工具技能没有“选哪一个”的歧义，例如已安装应用、屏幕时间、日历。
 * 天气等复合任务由 [AgentWeatherPolicy] 单独处理。
 */
internal object AgentExecutionPolicy {

    fun ensureRequiredTools(
        calls: List<AgentToolCall>,
        selectedSkills: List<AgentSkillDefinition>,
        userText: String,
    ): List<AgentToolCall> {
        val result = calls.toMutableList()
        selectedSkills.forEach { skill ->
            val onlyTool = skill.toolIds.singleOrNull() ?: return@forEach
            if (result.any { it.toolId == onlyTool }) return@forEach
            result += AgentToolCall(
                id = "required-${skill.id}",
                toolId = onlyTool,
                arguments = if (onlyTool == AgentToolRegistry.WEB_SEARCH) {
                    JSONObject().put(
                        "query",
                        FeatureActivation.searchQuery(userText, "").ifBlank {
                            userText.trim().take(200)
                        },
                    )
                } else {
                    JSONObject()
                },
                purpose = "执行技能：${skill.name}",
            )
        }
        return result.sortedWith(
            compareBy { call ->
                when (call.toolId) {
                    AgentToolRegistry.MEMORY_LIST -> 0
                    AgentToolRegistry.LOCATION_CURRENT -> 1
                    AgentToolRegistry.WEB_SEARCH -> 2
                    AgentToolRegistry.REMINDER_LIST -> 10
                    else -> 20
                }
            },
        )
    }
}
