package com.fingertipfuture.hub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftLineBreakTest {

    @Test
    fun addsInvisibleBreakOpportunitiesInsideWords() {
        val result = "completions".withCharacterBreakOpportunities()
        assertTrue(result.contains('\u200B'))
        assertEquals("completions", result.replace("\u200B", ""))
    }

    @Test
    fun leavesShortTokensAndNormalSpacesIntact() {
        assertEquals("a b 1 2", "a b 1 2".withCharacterBreakOpportunities())
    }

    @Test
    fun keepsVisuallySameText() {
        val source = "API 路径默认是「/chat/completions」"
        assertEquals(
            source,
            source.withCharacterBreakOpportunities()
                .replace("\u200B", "")
                .replace("\u2060", ""),
        )
    }

}
