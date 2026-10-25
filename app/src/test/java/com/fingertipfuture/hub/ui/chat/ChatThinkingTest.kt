package com.fingertipfuture.hub.ui.chat

import com.fingertipfuture.hub.data.HubMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatThinkingTest {

    @Test
    fun completedReplyKeepsThinkingSummaryWithoutTimelineSteps() {
        val message = HubMessage(
            role = "assistant",
            text = "回复",
            at = 1L,
            thoughtMs = 838L,
        )

        assertTrue(hasThinkingContent(message))
    }

    @Test
    fun messageWithoutThinkingDataDoesNotCreateThinkingBox() {
        val message = HubMessage(
            role = "assistant",
            text = "回复",
            at = 1L,
        )

        assertFalse(hasThinkingContent(message))
    }

    @Test
    fun thinkingDurationHasTenthSecondMinimum() {
        assertEquals("思考了 0.1 秒", thinkingDurationLabel(1L, pending = false))
        assertEquals("正在思考 10.0 秒", thinkingDurationLabel(9_999L, pending = true))
    }

    @Test
    fun primaryInputActionUsesCurrentNonBlankText() {
        assertTrue(shouldSubmitFromInput("刚输入的文字", voiceMode = false))
        assertFalse(shouldSubmitFromInput("   ", voiceMode = false))
        assertFalse(shouldSubmitFromInput("文字", voiceMode = true))
    }
}
