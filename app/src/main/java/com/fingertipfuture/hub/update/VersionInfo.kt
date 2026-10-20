package com.fingertipfuture.hub.update

/**
 * 更新清单里的一个版本。
 *
 * `sha256` 与 `apkUrlHttps` 允许为空，是为了兼容清单里的**历史版本**条目
 * （那些 APK 已经删了，只留版本号和更新内容）。真正要下载的 [latest] 必须两者齐全。
 */
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

/** 整份更新清单。 */
data class UpdateManifest(
    val latest: VersionInfo,
    val history: List<VersionInfo> = emptyList(),
    val generatedAt: String? = null,
)

/** 检查更新 / 下载失败的原因，用来决定弹窗文案。 */
enum class UpdateError { NETWORK, SERVER, PARSE, DOWNLOAD, VERIFY, INSTALL }
