package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTokensTest {

    @Test
    fun englishSubstringGetsBigrams() {
        val grams = SearchTokens.grams("multichat")
        assertTrue(grams.contains("mu"))
        assertTrue(grams.contains("ul"))
        assertTrue(grams.contains("ch"))
        assertTrue(grams.contains("at"))
    }

    @Test
    fun chineseSubstringGetsBigrams() {
        val grams = SearchTokens.grams("今天聊天了吗")
        // 中间子串也要能命中，不能只做前缀匹配
        assertTrue(grams.contains("聊天"))
        assertTrue(grams.contains("天了"))
    }

    @Test
    fun caseIsNormalized() {
        assertEquals(SearchTokens.grams("MULTICHAT"), SearchTokens.grams("multichat"))
    }

    @Test
    fun blankAndSingleCharacterHaveNoGrams() {
        assertTrue(SearchTokens.grams("").isEmpty())
        assertTrue(SearchTokens.grams("a").isEmpty())
        assertTrue(SearchTokens.grams("聊").isEmpty())
        assertFalse(SearchTokens.grams("a ").isEmpty())
    }
}
