package com.fingertipfuture.hub.update

import org.json.JSONException
import org.json.JSONObject
import com.fingertipfuture.hub.data.NetworkGuard

/**
 * 更新清单的解析与获取。
 *
 * 服务器清单格式（update/version.json）：
 * ```json
 * {
 *   "generatedAt": "2026-10-13T12:00:00+08:00",
 *   "latest": {
 *     "versionCode": 2,
 *     "versionName": "0.2.0",
 *     "apkUrl": "https://fingertipfuture.com/hub/update/apk/hub-0.2.0.apk",
 *     "apkUrlHttps": "https://fingertipfuture.com/hub/update/apk/hub-0.2.0.apk",
 *     "apkSize": 8616798,
 *     "sha256": "…64 位小写十六进制…",
 *     "releasedAt": "2026-10-13",
 *     "changelog": ["首个版本发布"],
 *     "forceUpdate": false
 *   },
 *   "history": [ { "versionCode": 1, "versionName": "0.1.0", "releasedAt": "…", "changelog": [...] } ]
 * }
 * ```
 *
 * [history] 是更早版本（APK 已删除，所以没有下载地址和 sha256）。
 */
internal object UpdateApi {

    /** 拉取并解析更新清单。会抛网络 / HTTP / 解析异常，由上层归类。 */
    suspend fun fetchManifest(manifestUrl: String): UpdateManifest =
        parseManifest(HttpClient.getString(manifestUrl))

    fun parseManifest(raw: String): UpdateManifest {
        val root = JSONObject(raw)
        return UpdateManifest(
            latest = parseVersion(root.getJSONObject("latest")),
            generatedAt = root.optString("generatedAt").takeIf { it.isNotBlank() && it != "null" },
            history = parseHistory(root),
        )
    }

    /** 要下载的那个版本：必须能算出下载地址，并且带 sha256 校验值。 */
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

    /**
     * 解析历史版本列表。
     *
     * 历史条目没有安装包，所以不能走 [parseVersion]。任何一条解析失败只跳过该条，
     * 不能让整份清单作废 —— 否则「检查更新」会被历史数据拖垮。
     */
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

/**
 * 下载地址是否可信。
 *
 * 正式渠道一律要求 HTTPS：清单是明文 JSON，允许 `http://` 就等于给中间人留了
 * 「把下载地址换成自己的包」的口子（虽然还有 sha256 兜底，但没必要开这个口）。
 * 例外只给本机 / 局域网地址 —— 那是开发调试用的模拟服务端（见技术说明）。
 */
internal fun isAllowedDownloadUrl(url: String): Boolean {
    // 与运行时网络入口共用一套白名单，避免更新模块和 NetworkGuard 规则漂移。
    return runCatching { NetworkGuard.requireAllowed(url) }.isSuccess
}

/** 把异常归类成界面能识别的失败原因。 */
internal fun Throwable.toUpdateError(): UpdateError = when (this) {
    is HttpStatusException -> UpdateError.SERVER
    is JSONException -> UpdateError.PARSE
    is VerificationException -> UpdateError.VERIFY
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is java.io.IOException -> UpdateError.NETWORK
    else -> UpdateError.NETWORK
}
