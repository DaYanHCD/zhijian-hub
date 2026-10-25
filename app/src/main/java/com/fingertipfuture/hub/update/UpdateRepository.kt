package com.fingertipfuture.hub.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VerificationException(message: String) : Exception(message)

/** 更新数据源：读取服务器清单、下载安装包并校验完整性。 */
class UpdateRepository(private val manifestUrl: String) {

    suspend fun fetchManifest(): UpdateManifest = withContext(Dispatchers.IO) {
        UpdateApi.fetchManifest(manifestUrl)
    }

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
                throw VerificationException("安装包大小不符")
            }
            val expected = info.sha256 ?: throw VerificationException("清单缺少 sha256，无法校验")
            val actual = sha256Of(dest)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw VerificationException("安装包校验失败")
            }
            dest
        } catch (t: Throwable) {
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
