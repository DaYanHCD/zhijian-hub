package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveChatSettingsTest {

    @Test
    fun `assistant auto follows the global thinking depth`() {
        assertEquals(
            ThinkingDepthConfig.HIGH,
            resolvedThinkingDepth(ThinkingDepthConfig.AUTO, ThinkingDepthConfig.HIGH),
        )
    }

    @Test
    fun `assistant explicit thinking depth wins over global`() {
        assertEquals(
            ThinkingDepthConfig.LOW,
            resolvedThinkingDepth(ThinkingDepthConfig.LOW, ThinkingDepthConfig.MAX),
        )
    }

    @Test
    fun `skill selected in chat is merged into the request skills`() {
        assertEquals(
            listOf("translate", "custom-skill"),
            resolvedSkillIds(listOf("translate", ""), "custom-skill"),
        )
    }

    @Test
    fun `blank selected skill does not change assistant skills`() {
        assertEquals(
            listOf("translate"),
            resolvedSkillIds(listOf("translate"), ""),
        )
    }
}
