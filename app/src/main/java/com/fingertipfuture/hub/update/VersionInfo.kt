package com.fingertipfuture.hub.update

/** 更新清单或本地更新日志中的一个版本。 */
data class VersionInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String = "",
    val apkUrlHttps: String? = null,
    val apkSize: Long = 0L,
    val sha256: String? = null,
    val releasedAt: String = "",
    val changelog: List<String> = emptyList(),
    val forceUpdate: Boolean = false,
)

/** 当前版本与它之前的全部更新记录。 */
data class UpdateManifest(
    val latest: VersionInfo,
    val history: List<VersionInfo> = emptyList(),
    val generatedAt: String? = null,
    val schemaVersion: Int = 0,
    val channel: String = "",
    val packageName: String = "",
)

/** 检查更新 / 下载失败的原因，用来决定弹窗文案。 */
enum class UpdateError { NETWORK, SERVER, PARSE, CHANNEL, DOWNLOAD, VERIFY, INSTALL }

/** 更新清单渠道与当前安装包不匹配。 */
class UpdateChannelMismatchException(message: String) : Exception(message)
