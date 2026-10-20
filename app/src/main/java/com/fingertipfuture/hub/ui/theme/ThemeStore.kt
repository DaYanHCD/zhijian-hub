package com.fingertipfuture.hub.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 主题模式：设置页「主题」那一行的三个选项。 */
enum class HubThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色模式"),
    DARK("dark", "深色模式"),
    ;

    companion object {
        fun fromId(raw: String?): HubThemeMode = values().firstOrNull { it.id == raw } ?: SYSTEM
    }
}

/**
 * 主题设置（2026-10-16 用户要求：设置里能切浅色 / 深色 / 跟随系统）。
 *
 * 存本机 SharedPreferences（`hub_ui`，和「键盘高度」共用一个文件），不联任何后端。
 * 当前值放在 Compose 状态里：MainActivity 里 `HubTheme(darkTheme = ThemeStore.isDark)`，
 * 设置页一改就重组，整个 App 立刻换色，不用重启。
 */
object ThemeStore {

    private const val PREFS = "hub_ui"
    private const val KEY_MODE = "theme_mode"

    /** 当前模式；App 起来时由 [initialize] 读回，没存过就跟随系统（也就是以前的行为）。 */
    var mode: HubThemeMode by mutableStateOf(HubThemeMode.SYSTEM)
        private set

    fun initialize(context: Context) {
        mode = HubThemeMode.fromId(prefs(context).getString(KEY_MODE, null))
    }

    fun set(context: Context, value: HubThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY_MODE, value.id).apply()
    }

    /** 现在该用深色吗。跟随系统时看系统当前是不是深色 —— 系统一切换，读它的界面会一起重组。 */
    val isDark: Boolean
        @Composable get() = when (mode) {
            HubThemeMode.LIGHT -> false
            HubThemeMode.DARK -> true
            HubThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}