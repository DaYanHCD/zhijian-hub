package com.fingertipfuture.hub.core

import com.fingertipfuture.hub.BuildConfig

/**
 * 应用级常量集中在此，避免散落在各处。
 */
object AppConfig {

    /** 自有站点根地址，版本清单和公告都位于其 update/ 目录。 */
    val UPDATE_BASE_URL: String = BuildConfig.UPDATE_BASE_URL
    val OPEN_UPDATE_BASE_URL: String = BuildConfig.OPEN_UPDATE_BASE_URL
    val UPDATE_CHANNEL: String = BuildConfig.UPDATE_CHANNEL
    val APPLICATION_ID: String = BuildConfig.APPLICATION_ID

    /** 当前安装包的版本名，如 "0.2.0"。 */
    val CURRENT_VERSION_NAME: String = BuildConfig.VERSION_NAME

    /** 当前安装包的版本号，用于与服务器清单比较。 */
    val CURRENT_VERSION_CODE: Long = BuildConfig.VERSION_CODE.toLong()

    /** 是否 debug 构建。 */
    val IS_DEBUG: Boolean = BuildConfig.DEBUG

    /** OS 版不读取闭源渠道的版本清单，避免把 Beta/正式版当成自己的更新。 */
    val OPEN_SOURCE_BUILD: Boolean = BuildConfig.OPEN_SOURCE_BUILD

    /** debug 包验证更新流程时使用的本机服务端覆盖地址。 */
    const val DEBUG_OVERRIDE_PREFS = "hub_update_debug"
    const val DEBUG_OVERRIDE_KEY = "update_base_url"

    fun manifestUrl(
        baseUrlOverride: String? = null,
        cacheBust: Long = System.currentTimeMillis(),
    ): String {
        val raw = baseUrlOverride?.takeIf { it.isNotBlank() }
            ?: if (OPEN_SOURCE_BUILD) OPEN_UPDATE_BASE_URL else UPDATE_BASE_URL
        val base = if (raw.endsWith("/")) raw else "$raw/"
        val path = when {
            OPEN_SOURCE_BUILD -> "update/version.json"
            BuildConfig.TEST_BUILD -> "update/version-debug.json"
            else -> "update/version.json"
        }
        val url = base + path
        return if (OPEN_SOURCE_BUILD) "$url?t=$cacheBust" else url
    }

    fun announcementUrl(cacheBust: Long = System.currentTimeMillis()): String {
        val raw = if (OPEN_SOURCE_BUILD) OPEN_UPDATE_BASE_URL else UPDATE_BASE_URL
        val base = if (raw.endsWith("/")) raw else "$raw/"
        return base + "update/announcement.json?t=$cacheBust"
    }

}
