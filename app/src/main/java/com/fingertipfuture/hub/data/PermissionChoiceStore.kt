package com.fingertipfuture.hub.data

import android.content.Context

/**
 * 用户对「系统支持的能力」的明确开关选择。
 *
 * 系统权限状态和用户意愿必须分开存：系统仍然授权，不代表用户还想让助手读取。
 * 之前 syncSystemGrants 每次回前台都用 OR 把开关重新打开，导致用户关不掉。
 */
internal object PermissionChoiceStore {

    private const val PREFS = "hub_permission_choices"

    fun isDisabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean(key, false)

    fun setDisabled(context: Context, key: String, disabled: Boolean) {
        prefs(context).edit().putBoolean(key, disabled).apply()
    }

    fun disabledKeys(context: Context): Set<String> = setOf(
        "location",
        "calendar",
        "contacts",
        "sms",
        "mms",
        "activity",
        "screen_time",
    ).filter { isDisabled(context, it) }.toSet()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
