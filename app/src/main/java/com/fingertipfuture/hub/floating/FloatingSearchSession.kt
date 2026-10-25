package com.fingertipfuture.hub.floating

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** 悬浮球外观。标准模式始终不透明；轻透模式只给需要更弱视觉存在的用户。 */
enum class FloatingBubbleStyle(
    val key: String,
    val label: String,
) {
    STANDARD("standard", "标准"),
    LIGHT("light", "半透明"),
    ;

    companion object {
        fun fromKey(raw: String?): FloatingBubbleStyle =
            entries.firstOrNull { it.key == raw } ?: STANDARD
    }
}

/**
 * 悬浮搜题会话的轻量状态。
 *
 * 真正的屏幕捕获与悬浮窗由 [FloatingSearchService] 持有；这里只让设置页能同步显示
 * “是否正在运行”，并统一封装启动 / 停止，避免页面直接拼装 Service Intent。
 */
object FloatingSearchSession {

    private const val PREFS = "floating_search"
    private const val KEY_BUBBLE_STYLE = "bubble_style"

    var isRunning by mutableStateOf(false)
        private set

    var bubbleStyle by mutableStateOf(FloatingBubbleStyle.STANDARD)
        private set

    fun refresh(context: Context) {
        bubbleStyle = FloatingBubbleStyle.fromKey(
            prefs(context).getString(KEY_BUBBLE_STYLE, null),
        )
    }

    fun setBubbleStyle(context: Context, style: FloatingBubbleStyle) {
        bubbleStyle = style
        prefs(context).edit().putString(KEY_BUBBLE_STYLE, style.key).apply()
    }

    fun start(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, FloatingSearchService::class.java).apply {
            action = FloatingSearchService.ACTION_START
            putExtra(FloatingSearchService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingSearchService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(context, intent)
        setRunning(true)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingSearchService::class.java))
        setRunning(false)
    }

    internal fun setRunning(running: Boolean) {
        isRunning = running
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
