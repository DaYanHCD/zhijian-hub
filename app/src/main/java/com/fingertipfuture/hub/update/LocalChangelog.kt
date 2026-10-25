package com.fingertipfuture.hub.update

import android.content.Context
import com.fingertipfuture.hub.core.AppConfig
import org.json.JSONObject

/**
 * 内置更新日志。
 *
 * 历史版本从随 APK 打包的 [BUNDLED_HISTORY] 读取；当前版本的文案只在本文件维护。
 * 运行时不访问网络，也不会出现加载状态。
 */
object LocalChangelog {

    private const val BETA_BUNDLED_HISTORY = "version-debug.json"
    private const val OPEN_BUNDLED_HISTORY = "changelog-os.json"
    internal const val DEFAULT_CHANGELOG = "修复了一些已知问题~"

    /**
     * 每次发版由开发者在这里提供日志。没有对应条目时自动使用默认文案。
     * 用户通知“默认”时，保持 [DEFAULT_CHANGELOG] 即可。
     */
    private val authoredReleases = mapOf(
        63L to VersionInfo(
            versionCode = 63L,
            versionName = "1.0.55",
            releasedAt = "2026-10-24",
            changelog = listOf(DEFAULT_CHANGELOG),
        ),
        64L to VersionInfo(
            versionCode = 64L,
            versionName = "1.0.56",
            releasedAt = "2026-10-24",
            changelog = listOf(DEFAULT_CHANGELOG),
        ),
        65L to VersionInfo(
            versionCode = 65L,
            versionName = "1.0.57",
            releasedAt = "2026-10-25",
            changelog = listOf(
                "恢复启动时和手动点击时的服务器版本检查，发现新版本后支持下载、校验并安装",
                "公告改为从服务器读取，可随时发布或停用，同一公告只显示一次",
                "更新日志继续随安装包保存在本机",
            ),
        ),
    )

    private var bundledHistory: List<VersionInfo> = emptyList()

    @Synchronized
    fun initialize(context: Context) {
        val bundledHistoryName = if (AppConfig.OPEN_SOURCE_BUILD) {
            OPEN_BUNDLED_HISTORY
        } else {
            BETA_BUNDLED_HISTORY
        }
        val asset = runCatching {
            context.assets.open(bundledHistoryName).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        bundledHistory = runCatching { parseBundledHistory(asset) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun manifest(): UpdateManifest {
        return assembleLocalManifest(
            currentCode = AppConfig.CURRENT_VERSION_CODE,
            currentName = AppConfig.CURRENT_VERSION_NAME.substringBefore("-debug"),
            bundledEntries = bundledHistory,
            authored = selectAuthoredReleases(AppConfig.OPEN_SOURCE_BUILD, authoredReleases),
        )
    }
}

/** 开源版不能合并 Beta 的硬编码历史，否则会把 1.0.55–1.0.57 排到 1.0.x 前面。 */
internal fun selectAuthoredReleases(
    openSourceBuild: Boolean,
    betaReleases: Map<Long, VersionInfo>,
): Map<Long, VersionInfo> = if (openSourceBuild) emptyMap() else betaReleases

/**
 * 解析随 APK 打包的版本清单。
 *
 * 本地日志只需要版本号、名称、日期和文案；旧清单里的下载地址、APK 大小、校验值
 * 一律忽略，不能因为它们缺失或失效而让历史日志消失。
 */
internal fun parseBundledHistory(raw: String): List<VersionInfo> {
    val root = JSONObject(raw)
    val parsed = mutableListOf<VersionInfo>()
    root.optJSONObject("latest")?.let(::parseBundledVersion)?.let(parsed::add)
    val history = root.optJSONArray("history")
    if (history != null) {
        for (index in 0 until history.length()) {
            history.optJSONObject(index)?.let(::parseBundledVersion)?.let(parsed::add)
        }
    }
    return parsed
        .distinctBy { it.versionCode }
        .sortedByDescending { it.versionCode }
}

private fun parseBundledVersion(json: JSONObject): VersionInfo? {
    val versionCode = json.optLong("versionCode", 0L)
    if (versionCode <= 0L) return null
    val changelog = json.optJSONArray("changelog")?.let { entries ->
        val lines = mutableListOf<String>()
        for (index in 0 until entries.length()) {
            entries.optString(index).trim().takeIf { it.isNotEmpty() }?.let(lines::add)
        }
        lines
    }.orEmpty()
    return VersionInfo(
        versionCode = versionCode,
        versionName = json.optString("versionName").trim().ifBlank { "#$versionCode" },
        releasedAt = json.optString("releasedAt").trim(),
        changelog = changelog,
    )
}

internal fun assembleLocalManifest(
    currentCode: Long,
    currentName: String,
    bundledEntries: List<VersionInfo>,
    authored: Map<Long, VersionInfo>,
): UpdateManifest {
    val current = authored[currentCode]
        ?: bundledEntries.firstOrNull { it.versionCode == currentCode }
        ?: VersionInfo(
        versionCode = currentCode,
        versionName = currentName,
        changelog = listOf(LocalChangelog.DEFAULT_CHANGELOG),
    )
    val history = (bundledEntries + authored.values)
        .filterNot { it.versionCode == current.versionCode }
        .distinctBy { it.versionCode }
        .sortedByDescending { it.versionCode }
    return UpdateManifest(latest = current, history = history)
}
