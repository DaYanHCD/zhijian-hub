package com.fingertipfuture.hub.data.agent

import com.fingertipfuture.hub.data.FeatureActivation
import com.fingertipfuture.hub.data.HubAssistant
import org.json.JSONObject

/**
 * 天气查询的确定性兜底。
 *
 * 工具规划模型可能只返回定位、甚至返回空调用；天气是实时信息，不能因此直接
 * 依赖模型知识回答。这里保证本轮一定规划 web.search，并在需要当前位置时先定位。
 */
internal object AgentWeatherPolicy {
    fun isWeatherRequest(userText: String): Boolean = FeatureActivation.isWeatherQuery(userText)

    fun enrichCalls(
        calls: List<AgentToolCall>,
        userText: String,
        assistant: HubAssistant,
        memoryPlace: String = "",
    ): List<AgentToolCall> {
        if (!isWeatherRequest(userText)) return calls
        val result = calls.toMutableList()
        val explicitPlace = FeatureActivation.explicitWeatherPlace(userText)
        val knownPlace = explicitPlace.ifBlank { memoryPlace.trim() }
        if (knownPlace.isBlank() && !assistant.permLocation) {
            // 没有地点、也没有定位能力时，天气搜索只会生成无意义查询。
            return result.filterNot {
                it.toolId == AgentToolRegistry.WEB_SEARCH ||
                    it.toolId == AgentToolRegistry.LOCATION_CURRENT
            }
        }
        if (knownPlace.isNotBlank()) {
            result.removeAll { it.toolId == AgentToolRegistry.LOCATION_CURRENT }
        }
        val locationIndex = result.indexOfFirst {
            it.toolId == AgentToolRegistry.LOCATION_CURRENT
        }
        val searchIndex = result.indexOfFirst {
            it.toolId == AgentToolRegistry.WEB_SEARCH
        }

        val needCurrentLocation = knownPlace.isBlank() && assistant.permLocation
        if (needCurrentLocation && locationIndex < 0) {
            result.add(
                0,
                AgentToolCall(
                    id = "weather-location-auto",
                    toolId = AgentToolRegistry.LOCATION_CURRENT,
                    arguments = JSONObject(),
                    purpose = "天气问题未指定地点，读取当前位置",
                ),
            )
        }

        if (searchIndex < 0) {
            result += AgentToolCall(
                id = "weather-search-auto",
                toolId = AgentToolRegistry.WEB_SEARCH,
                arguments = JSONObject().apply {
                    put(
                        "query",
                        FeatureActivation.searchQuery(userText, knownPlace),
                    )
                },
                purpose = "天气属于实时信息，执行联网搜索",
            )
        } else if (needCurrentLocation && locationIndex < 0) {
            // 如果规划器先给搜索、后给定位，把定位挪到搜索前面，保证搜索能拿到地名。
            val locationCall = result.removeAt(0)
            result.add(searchIndex, locationCall)
        } else if (needCurrentLocation && locationIndex > searchIndex) {
            val locationCall = result.removeAt(locationIndex)
            result.add(searchIndex, locationCall)
        }
        return result
    }

    fun applyResolvedPlace(
        call: AgentToolCall,
        userText: String,
        resolvedPlace: String,
        memoryPlace: String = "",
    ): AgentToolCall {
        if (call.toolId != AgentToolRegistry.WEB_SEARCH || !isWeatherRequest(userText)) {
            return call
        }
        val explicitPlace = FeatureActivation.explicitWeatherPlace(userText)
        val place = explicitPlace.ifBlank {
            resolvedPlace.trim().ifBlank { memoryPlace.trim() }
        }
        if (place.isBlank()) return call
        return call.copy(
            arguments = JSONObject(call.arguments.toString()).apply {
                put("query", FeatureActivation.searchQuery(userText, place))
            },
        )
    }
}
