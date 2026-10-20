package com.fingertipfuture.hub.update

import com.fingertipfuture.hub.core.AppConfig

/**
 * 内置更新日志：服务器清单拉不到时用来兜底，保证更新日志页不会空白。
 *
 * 服务器可用时仍以服务器清单为准；这里只保留当前版本这一条，
 * 不复制整套历史，免得客户端和服务端两份内容长期漂移。
 */
object LocalChangelog {

    fun entries(): List<VersionInfo> = listOf(
        VersionInfo(
            versionCode = AppConfig.CURRENT_VERSION_CODE,
            versionName = AppConfig.CURRENT_VERSION_NAME,
            releasedAt = "2026-10-20",
            changelog = listOf(
                "更新：指尖Hub的第一个开源版本",
            ),
        ),
    )
}
