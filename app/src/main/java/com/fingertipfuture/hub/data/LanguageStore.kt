package com.fingertipfuture.hub.data

import android.content.Context
import android.content.res.Configuration
import android.icu.text.Transliterator
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class AppLanguage(
    val tag: String,
    val label: String,
) {
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    ;

    companion object {
        /** 系统语言 → App 支持的语言；不支持的一律简体中文。 */
        fun fromLocale(locale: Locale): AppLanguage {
            if (locale.language.equals("zh", ignoreCase = true)) {
                if (locale.script.equals("Hans", ignoreCase = true)) return SIMPLIFIED_CHINESE
                val traditional = locale.script.equals("Hant", ignoreCase = true) ||
                    locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
                return if (traditional) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
            }
            return SIMPLIFIED_CHINESE
        }
    }
}

/**
 * App 内语言。
 *
 * - 第一次启动按系统语言自动选择；繁体系统用繁體中文，其它不支持的语言回落到简体中文。
 * - 用户手动选择后一直沿用，不再被系统语言覆盖。
 * - 文案转换在 [com.fingertipfuture.hub.ui.component.LocalizedText] 层完成，
 *   所以现有大量硬编码文案也能随语言切换。
 */
object LanguageStore {

    private const val PREFS = "hub_language"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_INITIALIZED = "initialized"

    var current: AppLanguage by mutableStateOf(AppLanguage.SIMPLIFIED_CHINESE)
        private set

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val detected = AppLanguage.fromLocale(systemLocale(context))
            prefs.edit()
                .putString(KEY_LANGUAGE, detected.tag)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            detected
        } else {
            AppLanguage.entries.firstOrNull {
                it.tag == prefs.getString(KEY_LANGUAGE, AppLanguage.SIMPLIFIED_CHINESE.tag)
            } ?: AppLanguage.SIMPLIFIED_CHINESE
        }
        applyLocale(context, current)
    }

    fun select(context: Context, language: AppLanguage) {
        if (current == language) return
        current = language
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
        applyLocale(context, language)
    }

    fun localize(text: String, language: AppLanguage = current): String {
        if (language == AppLanguage.SIMPLIFIED_CHINESE || text.isBlank()) return text
        if (text.none { it.code in 0x3400..0x9FFF }) return text
        return TraditionalConverter.convert(text)
    }

    /**
     * 发给模型的回答语言约束。
     *
     * 繁體模式默认使用繁體中文；用户明确指定其他语言时，用户要求优先。
     */
    fun assistantLanguageInstruction(language: AppLanguage = current): String =
        if (language == AppLanguage.TRADITIONAL_CHINESE) {
            "回答語言：除非用戶明確指定其他語言，否則一律使用繁體中文（臺灣用語）回答。" +
                "即使用戶用英文或其他語言提問，也用繁體中文回答；" +
                "只有用戶明確要求某種語言時，才切換到用戶指定的語言。"
        } else {
            ""
        }

    private fun systemLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.size() > 0) locales[0] else Locale.getDefault()
    }

    private fun applyLocale(context: Context, language: AppLanguage) {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val resources = context.applicationContext.resources
        val config = Configuration(resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

private object TraditionalConverter {

    private val phrases = mapOf(
        "视频通话" to "視訊通話",
        "语音通话" to "語音通話",
        "视频扩展" to "視訊擴充",
        "屏幕时间" to "螢幕使用時間",
        "文件夹" to "資料夾",
        "软件" to "軟體",
        "网络" to "網路",
        "视频" to "影片",
        "音频" to "音訊",
        "文件" to "檔案",
        "保存" to "儲存",
        "搜索" to "搜尋",
        "加载" to "載入",
        "信息" to "資訊",
        "数据" to "資料",
        "创建" to "建立",
        "默认" to "預設",
        "账号" to "帳號",
        "登录" to "登入",
        "用户" to "使用者",
        "缓存" to "快取",
        "支持" to "支援",
        "程序" to "程式",
        "质量" to "品質",
        "当前" to "目前",
        "麦克风" to "麥克風",
        "屏幕" to "螢幕",
        "恢复" to "還原",
        "录音" to "錄音",
        "视频号" to "影音號",
        "网络搜索" to "網路搜尋",
        "联网搜索" to "聯網搜尋",
    ).entries.sortedByDescending { it.key.length }

    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Transliterator.getInstance("Simplified-Traditional") }.getOrNull()
        } else {
            // API 24~28 没有 android.icu.text.Transliterator；仍会使用上面的常用词表转换。
            null
        }
    }

    fun convert(text: String): String {
        var value = text
        phrases.forEach { (from, to) ->
            if (value.contains(from)) value = value.replace(from, to)
        }
        return transliterateIfSupported(value)
    }

    private fun transliterateIfSupported(value: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return value
        return transliterator?.transliterate(value) ?: value
    }
}
