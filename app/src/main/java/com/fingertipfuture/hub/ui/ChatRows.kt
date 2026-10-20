package com.fingertipfuture.hub.ui

import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.ui.chat.shouldShowTimeSeparator

/** 聊天正文的一行：要么是时间分隔，要么是一条消息。 */
internal sealed interface ChatRow {
    val key: String

    data class Time(val at: Long, override val key: String) : ChatRow

    data class Msg(val message: HubMessage, val index: Int, override val key: String) : ChatRow
}

/**
 * 按「超过 3 分钟出现 / 密集时每 30 分钟出现一次」的规则，
 * 把消息列表展开成「时间 + 消息」的行列表（规则与「指尖」一致）。
 */
internal fun buildChatRows(messages: List<HubMessage>): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size * 2)
    var previousAt: Long? = null
    var lastSeparatorAt: Long? = null
    messages.forEachIndexed { index, message ->
        if (shouldShowTimeSeparator(previousAt, lastSeparatorAt, message.at)) {
            rows += ChatRow.Time(at = message.at, key = "t-$index")
            lastSeparatorAt = message.at
        }
        val stableId = message.id.ifBlank { index.toString() }
        rows += ChatRow.Msg(message = message, index = index, key = "m-$stableId")
        previousAt = message.at
    }
    return rows
}
