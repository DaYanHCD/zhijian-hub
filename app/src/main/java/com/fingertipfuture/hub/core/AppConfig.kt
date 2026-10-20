package com.fingertipfuture.hub.core

import com.fingertipfuture.hub.BuildConfig

/**
 * 应用级常量集中在此，避免散落在各处。
 */
object AppConfig {

    /** 更新服务根地址，必须以 "/" 结尾（见 app/build.gradle.kts 的 buildConfigField）。 */
    val UPDATE_BASE_URL: String = BuildConfig.UPDATE_BASE_URL

    /** 当前安装包的版本名，如 "0.2.0"。 */
    val CURRENT_VERSION_NAME: String = BuildConfig.VERSION_NAME

    /** 当前安装包的版本号，用于与服务器清单比较。 */
    val CURRENT_VERSION_CODE: Long = BuildConfig.VERSION_CODE.toLong()

    /** 是否 debug 构建。 */
    val IS_DEBUG: Boolean = BuildConfig.DEBUG

    /**
     * 调试用的更新地址覆盖（**只在 debug 包里生效**）。
     *
     * 为什么要留这个口子：装机验证「检查更新 → 下载 → 安装」这条链路时，
     * 不可能每次都真去发一版到服务器上。debug 包只要把
     * `hub_update_debug` 这个 SharedPreferences 的 `update_base_url` 写成
     * `http://10.0.2.2:8098/hub/`，就能拿本机的 mock 服务端跑完整流程
     * （见 03_临时工作区/临时文件/mock-update-server.ps1）。
     * 正式包不看这个键 —— 免得被人改本机偏好就把用户引到别的更新源。
     */
    const val DEBUG_OVERRIDE_PREFS = "hub_update_debug"
    const val DEBUG_OVERRIDE_KEY = "update_base_url"

    /**
     * 更新清单地址。
     *
     * 正式包读 `update/version.json`，测试包读 `update/version-debug.json`，
     * 两套渠道的版本线互不干扰（与「指尖」同款约定）。
     */
    fun manifestUrl(baseUrlOverride: String? = null): String {
        val raw = baseUrlOverride?.takeIf { it.isNotBlank() } ?: UPDATE_BASE_URL
        val base = if (raw.endsWith("/")) raw else "$raw/"
        return base + if (BuildConfig.TEST_BUILD) "update/version-debug.json" else "update/version.json"
    }
}