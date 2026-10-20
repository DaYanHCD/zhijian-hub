package com.fingertipfuture.hub.ui.chat

import android.content.Context

/**
 * 记住"键盘有多高"。
 *
 * 添加栏要和键盘一样高，而键盘高度只有键盘弹出来之后才知道。以前是每次启动 App 都用
 * 一个写死的兜底值（288dp），于是**每次刚启动的第一次切换**都会因为兜底值和真实键盘不一样弹一下。
 * 量到过就存下来，下次启动直接用。
 */
internal object PanelHeightStore {

    private const val PREFS = "hub_ui"
    private const val KEY_HEIGHT_DP = "attach_panel_height_dp"

    /** 上次量到的键盘高度（dp）；没量到过返回 null。 */
    fun loadDp(context: Context): Float? {
        val value = prefs(context).getFloat(KEY_HEIGHT_DP, 0f)
        return value.takeIf { it > 0f }
    }

    fun saveDp(context: Context, heightDp: Float) {
        if (heightDp <= 0f) return
        prefs(context).edit().putFloat(KEY_HEIGHT_DP, heightDp).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}