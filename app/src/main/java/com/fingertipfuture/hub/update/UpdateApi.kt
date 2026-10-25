package com.fingertipfuture.hub.update

import com.fingertipfuture.hub.data.NetworkGuard
import org.json.JSONException
import org.json.JSONObject

/**
 * 更新清单的解析与获取。
 *
 * Beta 读取 update/version-debug.json，正式渠道读取 update/version.json。
 * 历史条目可以没有下载地址；只有真正要安装的 latest 必须包含 HTTPS 地址和 SHA-256。
 */
internal object UpdateApi {

    suspend fun fetchManifest(manifestUrl: String): UpdateManifest =
        parseManifest(HttpClient.getString(manifestUrl))

    fun parseManifest(raw: String): UpdateManifest {
        val root = JSONObject(raw)
        return UpdateManifest(
            latest = parseVersion(root.getJSONObject("latest")),
            generatedAt = root.optString("generatedAt").takeIf { it.isNotBlank() && it != "null" },
            history = parseHistory(root),
            schemaVersion = root.optInt("schemaVersion", 0),
            channel = root.optString("channel").trim(),
            packageName = root.optString("packageName").trim(),
        )
    }

    private fun parseVersion(json: JSONObject): VersionInfo {
        val apkUrl = json.optString("apkUrl")
        val apkUrlHttps = json.optString("apkUrlHttps").takeIf { it.isNotBlank() && it != "null" }
        val sha256 = json.optString("sha256").trim().lowercase()

        val downloadUrl = apkUrlHttps ?: apkUrl.takeIf { it.isNotBlank() }
            ?: throw JSONException("更新清单里没有下载地址")
        if (!isAllowedDownloadUrl(downloadUrl)) {
            throw JSONException("下载地址必须使用 HTTPS（本机 / 局域网的调试地址除外）")
        }
        if (!sha256.matches(Regex("^[0-9a-f]{64}$"))) {
            throw JSONException("更新清单缺少有效的 sha256")
        }

        return VersionInfo(
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = apkUrl,
            apkUrlHttps = apkUrlHttps,
            apkSize = json.optLong("apkSize", 0L),
            sha256 = sha256,
            releasedAt = json.optString("releasedAt", ""),
            changelog = json.optJSONArray("changelog")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            }.orEmpty(),
            forceUpdate = json.optBoolean("forceUpdate", false),
        )
    }

    private fun parseHistory(root: JSONObject): List<VersionInfo> {
        val array = root.optJSONArray("history") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val versionCode = item.optLong("versionCode", 0L)
            if (versionCode <= 0L) return@mapNotNull null
            VersionInfo(
                versionCode = versionCode,
                versionName = item.optString("versionName"),
                apkUrl = item.optString("apkUrl"),
                releasedAt = item.optString("releasedAt", ""),
                changelog = item.optJSONArray("changelog")?.let { entries ->
                    (0 until entries.length()).map { entries.getString(it) }
                }.orEmpty(),
                forceUpdate = item.optBoolean("forceUpdate", false),
            )
        }
    }
}

internal fun isAllowedDownloadUrl(url: String): Boolean =
    runCatching { NetworkGuard.requireAllowed(url) }.isSuccess

internal fun requireMatchingUpdateChannel(
    manifest: UpdateManifest,
    expectedChannel: String,
    expectedPackage: String,
    requireExplicit: Boolean,
) {
    val channelMismatch = manifest.channel.isNotBlank() && manifest.channel != expectedChannel
    val packageMismatch = manifest.packageName.isNotBlank() &&
        manifest.packageName != expectedPackage
    if (channelMismatch || packageMismatch) {
        throw UpdateChannelMismatchException(
            "更新清单渠道不匹配：channel=${manifest.channel}, package=${manifest.packageName}",
        )
    }
    if (
        requireExplicit &&
        (
            manifest.schemaVersion != 1 ||
                manifest.channel != expectedChannel ||
                manifest.packageName != expectedPackage
            )
    ) {
        throw UpdateChannelMismatchException("更新清单缺少有效的渠道标识")
    }
}

internal fun Throwable.toUpdateError(): UpdateError = when (this) {
    is HttpStatusException -> UpdateError.SERVER
    is JSONException -> UpdateError.PARSE
    is VerificationException -> UpdateError.VERIFY
    is UpdateChannelMismatchException -> UpdateError.CHANNEL
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is java.io.IOException -> UpdateError.NETWORK
    else -> UpdateError.NETWORK
}
