package com.fingertipfuture.hub.update

import java.io.File

/**
 * 「检查更新」流程的状态机。
 *
 * ```
 * Hidden -> Checking -> UpToDate | Available | Failed
 * Available -> Downloading -> ReadyToInstall | Failed
 * ```
 */
sealed interface UpdateState {

    /** 无弹窗。 */
    data object Hidden : UpdateState

    /** 正在请求服务器清单。 */
    data object Checking : UpdateState

    /** 服务器上没有比当前更新的版本。 */
    data class UpToDate(val currentVersionName: String) : UpdateState

    /** 服务器上存在更新的版本，等待用户选择。 */
    data class Available(val info: VersionInfo) : UpdateState

    /** 正在下载安装包。 */
    data class Downloading(
        val info: VersionInfo,
        val downloaded: Long,
        val total: Long,
    ) : UpdateState

    /** 下载并校验完成，等待拉起系统安装器。 */
    data class ReadyToInstall(val info: VersionInfo, val apk: File) : UpdateState

    /** 失败。[retryWith] 不为空表示「重试」应重新下载该版本，为空表示重新检查更新。 */
    data class Failed(
        val error: UpdateError,
        val httpCode: Int? = null,
        val retryWith: VersionInfo? = null,
    ) : UpdateState

    /** 缺少「安装未知应用」权限。 */
    data object NeedInstallPermission : UpdateState
}