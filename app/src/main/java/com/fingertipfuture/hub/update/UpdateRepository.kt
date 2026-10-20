package com.fingertipfuture.hub.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** 校验失败时抛出，交由上层归类为 [UpdateError.VERIFY]。 */
class VerificationException(message: String) : Exception(message)

/**
 * 更新数据源：读服务器清单、下载安装包、校验完整性。
 * 所有方法都是挂起函数，内部切到 IO 线程。
 */
class UpdateRepository(private val manifestUrl: String) {

    /** 读取服务器上的更新清单。 */
    suspend fun fetchManifest(): UpdateManifest = withContext(Dispatchers.IO) {
        UpdateApi.fetchManifest(manifestUrl)
    }

    /**
     * 下载安装包并校验。
     *
     * 失败或中途取消时会把已写出的文件（含 `.part` 半截文件）删掉，不留垃圾。
     *
     * @param onProgress 进度回调，(已下载, 总大小)，总大小未知时为 -1
     */
    suspend fun downloadApk(
        info: VersionInfo,
        destDir: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val fileName = safeApkFileName(info.versionName)
        val root = destDir.canonicalFile
        val dest = File(root, fileName).canonicalFile
        val relative = runCatching { dest.relativeTo(root).invariantSeparatorsPath }.getOrNull()
        if (relative == null || relative.startsWith("../") || relative == "..") {
            throw VerificationException("安装包文件名不合法")
        }
        val part = File(root, fileName + ".part")

        try {
            val downloadUrl = (info.apkUrlHttps ?: info.apkUrl)
                .takeIf { it.isNotBlank() && isAllowedDownloadUrl(it) }
                ?: throw VerificationException("下载地址不可用")
            HttpClient.download(
                url = downloadUrl,
                dest = dest,
                maxBytes = if (info.apkSize > 0) info.apkSize + 1024 * 1024 else 512L * 1024 * 1024,
                onProgress = onProgress,
            )

            if (info.apkSize > 0 && dest.length() != info.apkSize) {
                throw VerificationException("安装包大小不符（应为 ${info.apkSize} 字节，实际 ${dest.length()} 字节）")
            }
            val expected = info.sha256 ?: throw VerificationException("清单缺少 sha256，无法校验")
            val actual = sha256Of(dest)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw VerificationException("安装包校验失败（期望 $expected，实际 $actual）")
            }
            dest
        } catch (t: Throwable) {
            // 无论失败还是被取消，都不要留下半截文件
            part.delete()
            dest.delete()
            throw t
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 清单里的版本名不可信，不能直接拼进文件路径。 */
        internal fun safeApkFileName(versionName: String): String {
            val safe = versionName
                .trim()
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("\\.{2,}"), "_")
                .trim('.', '_')
                .take(64)
                .ifBlank { "unknown" }
            return "hub-$safe.apk"
        }
    }
}
