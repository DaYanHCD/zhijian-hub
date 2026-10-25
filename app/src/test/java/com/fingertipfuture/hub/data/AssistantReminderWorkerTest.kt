package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReminderWorkerTest {

    @Test
    fun `background reminder preserves search trace in thinking timeline`() {
        val trace = HubSearchTrace(
            query = "今天的新闻",
            resultCount = 2,
            providerKinds = listOf("bing"),
            titles = listOf("新闻一", "新闻二"),
        )
        val steps = reminderSearchThinkingSteps(
            reminderId = "r-1",
            startedAt = 123L,
            trace = trace,
            evidence = listOf(
                SearchEvidence("今天的新闻", "新闻一", "https://example.com/1", "摘要"),
            ),
        )

        assertEquals(1, steps.size)
        assertEquals("search", steps.single().kind)
        assertEquals("https://example.com/1", steps.single().items.single().url)
        assertTrue(steps.single().detail.contains("2 条"))
    }
}
