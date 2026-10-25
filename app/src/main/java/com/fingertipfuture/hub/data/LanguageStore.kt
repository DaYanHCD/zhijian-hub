package com.fingertipfuture.hub.data

import android.content.Context
import android.content.res.Configuration
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
        TraditionalConverter.load(context)
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
        "剪贴板" to "剪貼簿",
        "文件夹" to "資料夾",
        "字符串" to "字串",
        "服务器" to "伺服器",
        "自动化任务" to "自動化任務",
        "为什么" to "為什麼",
        "什么" to "什麼",
        "怎么" to "怎麼",
        "这么" to "這麼",
        "那么" to "那麼",
        "多么" to "多麼",
        "注册" to "註冊",
        "内置" to "內建",
        "控件" to "控制項",
        "相册" to "相簿",
        "实时" to "即時",
        "点赞" to "按讚",
        "赞赏作者" to "贊助作者",
        "关于" to "關於",
        "占用" to "佔用",
        "联系" to "聯絡",
        "线程" to "執行緒",
        "内存" to "記憶體",
        "磁盘" to "磁碟",
        "设备" to "裝置",
        "运行中" to "執行中",
        "运行" to "執行",
        "状态" to "狀態",
        "路径" to "路徑",
        "字节" to "位元組",
        "对话框" to "對話框",
        "余额" to "餘額",
        "复制" to "複製",
        "重复" to "重複",
        "复杂" to "複雜",
        "复习" to "複習",
        "对于" to "對於",
        "由于" to "由於",
        "基于" to "基於",
        "属于" to "屬於",
        "等于" to "等於",
        "互联网" to "網際網路",
        "干什么" to "幹什麼",
        "头发" to "頭髮",
        "制作" to "製作",
        "之后" to "之後",
        "以后" to "以後",
        "后来" to "後來",
        "最后" to "最後",
        "后台" to "後台",
        "后退" to "後退",
        "周一" to "週一",
        "周二" to "週二",
        "周三" to "週三",
        "周四" to "週四",
        "周五" to "週五",
        "周六" to "週六",
        "周日" to "週日",
        "每周" to "每週",
        "本周" to "本週",
        "下周" to "下週",
        "周末" to "週末",
        "设置" to "設定",
        "界面" to "介面",
        "接口" to "介面",
        "导出" to "匯出",
        "导入" to "匯入",
        "存储" to "儲存",
        "本地" to "本機",
        "在线" to "線上",
        "粘贴" to "貼上",
        "打印" to "列印",
        "鼠标" to "滑鼠",
        "短信" to "簡訊",
        "彩信" to "多媒體訊息",
        "代码" to "程式碼",
        "字符" to "字元",
        "这里" to "這裡",
        "里面" to "裡面",
        "哪里" to "哪裡",
        "消息" to "訊息",
        "菜单" to "選單",
        "账户" to "帳戶",
        "启动" to "啟動",
        "启用" to "啟用",
        "激活" to "啟用",
        "添加" to "新增",
        "关闭" to "關閉",
        "删除" to "刪除",
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

    @Volatile
    private var charMap: Map<Char, Char> = emptyMap()

    fun load(context: Context) {
        charMap = runCatching {
            val pairs = context.applicationContext.assets
                .open("traditional_zh_tw.txt")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseTraditionalCharMap(pairs)
        }.getOrDefault(emptyMap())
    }

    fun convert(text: String): String {
        var value = text
        phrases.forEach { (from, to) ->
            if (value.contains(from)) value = value.replace(from, to)
        }
        if (charMap.isNotEmpty()) {
            value = buildString(value.length) {
                value.forEach { append(charMap[it] ?: it) }
            }
        }
        // 个别旧系统转换表会使用日本字形「啓」；台湾标准统一为「啟」。
        return value.replace('啓', '啟')
    }
}

/** 本地逐字转换表是「简体字符 + 繁体目标」连续成对存储，避免在旧系统依赖 ICU。 */
internal fun parseTraditionalCharMap(pairs: String): Map<Char, Char> = buildMap {
    var index = 0
    while (index + 1 < pairs.length) {
        put(pairs[index], pairs[index + 1])
        index += 2
    }
}
