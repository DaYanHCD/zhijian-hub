package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageStoreTest {

    @Test
    fun `traditional chinese system uses traditional`() {
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLocale(Locale.forLanguageTag("zh-TW")),
        )
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLocale(Locale.forLanguageTag("zh-HK")),
        )
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLocale(Locale.forLanguageTag("zh-Hant-CN")),
        )
    }

    @Test
    fun `simplified chinese system uses simplified`() {
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocale(Locale.forLanguageTag("zh-CN")),
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocale(Locale.forLanguageTag("zh-Hans-TW")),
        )
    }

    @Test
    fun `unsupported system language falls back to simplified chinese`() {
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocale(Locale.US),
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocale(Locale.JAPAN),
        )
    }

    @Test
    fun `traditional mode adds assistant language constraint`() {
        assertEquals(
            "",
            LanguageStore.assistantLanguageInstruction(AppLanguage.SIMPLIFIED_CHINESE),
        )
        val rule = LanguageStore.assistantLanguageInstruction(AppLanguage.TRADITIONAL_CHINESE)
        assertEquals(true, rule.contains("繁體中文"))
        assertEquals(true, rule.contains("明確指定"))
        assertEquals(true, rule.contains("用戶指定的語言"))
    }
}
