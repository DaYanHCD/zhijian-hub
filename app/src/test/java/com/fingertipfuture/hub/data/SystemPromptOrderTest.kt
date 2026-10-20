package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptOrderTest {

    @Test
    fun `dynamic time stays out of the stable assistant prefix`() {
        val assistant = HubAssistant(
            id = "prompt-test",
            name = "AI",
            systemPrompt = "稳定规则",
            permTime = true,
        )

        val stable = AssistantStore.stableSystemPrompt(assistant, emptyList())
        assertEquals("稳定规则", stable)

        val dynamic = AssistantStore.dynamicSystemPrompt(assistant, "现在几点")
        assertTrue(dynamic.startsWith("现在是 "))
    }
}
