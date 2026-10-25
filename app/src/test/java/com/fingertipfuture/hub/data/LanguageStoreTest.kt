package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
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

    @Test
    fun `offline character map works without Android ICU`() {
        val map = parseTraditionalCharMap("启啟设設导導")

        assertEquals('啟', map['启'])
        assertEquals('設', map['设'])
        assertEquals('導', map['导'])
    }

    @Test
    fun `bundled traditional map covers interface characters on old Android`() {
        val pairs = File("src/main/assets/traditional_zh_tw.txt").readText()
        val map = parseTraditionalCharMap(pairs)

        assertEquals(0, pairs.length % 2)
        assertEquals('啟', map['启'])
        assertEquals('設', map['设'])
        assertEquals('體', map['体'])
        assertEquals('網', map['网'])
    }

    @Test
    fun `traditional mode uses Taiwan interface vocabulary`() {
        val converted = LanguageStore.localize(
            "启用设置服务器界面导出文件",
            AppLanguage.TRADITIONAL_CHINESE,
        )

        assertEquals("啟用設定伺服器介面匯出檔案", converted)
    }
}
