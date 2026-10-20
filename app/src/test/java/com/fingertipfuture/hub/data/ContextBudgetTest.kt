package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {

    @Test
    fun `old turns are dropped once the character budget is exceeded`() {
        val turns = listOf(
            HubTurn(role = "user", content = "a".repeat(80)),
            HubTurn(role = "assistant", content = "b".repeat(80)),
            HubTurn(role = "user", content = "c".repeat(80)),
        )
        val selected = budgetedTurns(turns, maxChars = 170)
        assertEquals(2, selected.size)
        assertEquals("c".repeat(80), selected.last().content)
    }

    @Test
    fun `the newest turn is always kept even if it alone exceeds the budget`() {
        val turns = listOf(
            HubTurn(role = "user", content = "a".repeat(10)),
            HubTurn(role = "user", content = "b".repeat(500)),
        )
        val selected = budgetedTurns(turns, maxChars = 20)
        assertEquals(1, selected.size)
        assertTrue(selected.single().content.startsWith("b"))
    }
}
