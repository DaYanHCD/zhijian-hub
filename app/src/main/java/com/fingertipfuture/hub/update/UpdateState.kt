package com.fingertipfuture.hub.update

import java.io.File

/** 检查更新与下载安装的状态机。 */
sealed interface UpdateState {

    data object Hidden : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val currentVersionName: String) : UpdateState

    data class Available(val info: VersionInfo) : UpdateState

    data class Downloading(
        val info: VersionInfo,
        val downloaded: Long,
        val total: Long,
    ) : UpdateState

    data class ReadyToInstall(val info: VersionInfo, val apk: File) : UpdateState

    data class Failed(
        val error: UpdateError,
        val httpCode: Int? = null,
        val retryWith: VersionInfo? = null,
    ) : UpdateState

    data object NeedInstallPermission : UpdateState
}
