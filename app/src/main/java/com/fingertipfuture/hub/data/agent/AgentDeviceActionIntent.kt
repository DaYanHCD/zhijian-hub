package com.fingertipfuture.hub.data.agent

enum class AgentDeviceActionKind {
    CALENDAR_EVENT,
    CONTACT,
}

/** 模型准备写入系统数据前，必须先交给用户逐次确认。 */
data class AgentDeviceActionIntent(
    val kind: AgentDeviceActionKind,
    val title: String,
    val details: List<String>,
)
