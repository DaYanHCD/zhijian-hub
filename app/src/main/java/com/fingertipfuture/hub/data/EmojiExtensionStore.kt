package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 可下载的动态表情扩展。
 *
 * 静态表情仍随基础 APK 打包，保证未安装扩展时所有设备都能正常显示；
 * 约 4.8 MB 的动态 WebP 只在用户主动下载后落到应用私有目录。
 */
object EmojiExtensionStore {
    const val DOWNLOAD_URL =
        "https://fingertipfuture.top/downloads/extensions/noto-emoji-animated-v1.zip"
    const val ARCHIVE_BYTES = 4_651_786L
    const val INSTALLED_BYTES = 4_795_564L
    const val EXPECTED_ASSET_COUNT = 210

    private const val EXPECTED_SHA256 =
        "32F33F5B52522090ABB35F9D095445A6B048BBD9CDEB676CA5A427A91F1DE8A6"
    private const val MAX_DOWNLOAD_BYTES = 12L * 1024L * 1024L
    private const val PREFS = "hub_extensions"
    private const val KEY_ANIMATED_EMOJI_ENABLED = "animated_emoji_enabled"
    private const val MARKER = ".complete-v1"
    private const val ARCHIVE_NAME = "noto-emoji-animated-v1.zip"

    var isInstalled by mutableStateOf(false)
        private set

    /** 扩展安装完成后自动启用；删除扩展时关闭。 */
    var isEnabled by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var downloadProgress by mutableIntStateOf(0)
        private set

    fun initialize(context: Context) {
        val installed = isInstallationComplete(context)
        isInstalled = installed
        isEnabled = installed
        prefs(context).edit().putBoolean(KEY_ANIMATED_EMOJI_ENABLED, installed).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val next = enabled && isInstalled
        isEnabled = next
        prefs(context).edit().putBoolean(KEY_ANIMATED_EMOJI_ENABLED, next).apply()
    }

    fun hasAnimatedAsset(context: Context, assetName: String): Boolean =
        isInstalled && File(assetsDir(context), "$assetName.webp").isFile

    fun installedSizeBytes(context: Context): Long =
        if (!isInstalled) 0L else directoryBytes(assetsDir(context))

    /** 下载、校验并解压扩展；成功后自动启用动画。 */
    suspend fun downloadAndInstall(context: Context): Result<Unit> {
        if (isDownloading) return Result.failure(IOException("扩展正在下载"))
        isDownloading = true
        downloadProgress = 0
        return try {
            withContext(Dispatchers.IO) {
                installBlocking(context.applicationContext)
            }
            isInstalled = true
            setEnabled(context, true)
            downloadProgress = 100
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            isDownloading = false
        }
    }

    fun delete(context: Context): Boolean {
        if (isDownloading) return false
        val extensionRoot = extensionRoot(context)
        val deleted = !extensionRoot.exists() || extensionRoot.deleteRecursively()
        if (!deleted) return false
        isInstalled = false
        isEnabled = false
        prefs(context).edit().putBoolean(KEY_ANIMATED_EMOJI_ENABLED, false).apply()
        return true
    }

    private fun installBlocking(context: Context) {
        val downloadDir = File(context.cacheDir, "emoji-extension").apply { mkdirs() }
        val archive = File(downloadDir, ARCHIVE_NAME)
        val partial = File(downloadDir, "$ARCHIVE_NAME.part")
        partial.delete()
        archive.delete()
        try {
            download(partial)
            downloadProgress = 92
            verifyArchive(partial)
            extractAndInstall(context, partial)
            downloadProgress = 100
        } finally {
            partial.delete()
            archive.delete()
        }
    }

    private fun download(target: File) {
        val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/zip")
            setRequestProperty("User-Agent", "ZhijianHub-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("下载失败 HTTP $status")
            val total = connection.contentLengthLong
            if (total > MAX_DOWNLOAD_BYTES) throw IOException("扩展包超过允许的大小")
            var received = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (received > MAX_DOWNLOAD_BYTES) {
                            throw IOException("扩展包超过允许的大小")
                        }
                        output.write(buffer, 0, read)
                        if (total > 0) {
                            downloadProgress = ((received * 88) / total).toInt().coerceIn(0, 88)
                        }
                    }
                }
            }
            if (total > 0 && received != total) {
                throw IOException("下载不完整：已收 $received / $total 字节")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyArchive(archive: File) {
        val actual = sha256(archive)
        if (!actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
            throw IOException("扩展包校验失败")
        }
    }

    private fun extractAndInstall(context: Context, archive: File) {
        val root = extensionRoot(context)
        val staging = File(root, "v1-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val finalDir = assetsDir(context)
        var extracted = 0
        try {
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory) continue
                    if (
                        name.contains('/') ||
                        !name.matches(Regex("[0-9a-f-]+\\.webp"))
                    ) {
                        throw IOException("扩展包包含不安全或未知文件：$name")
                    }
                    File(staging, name).outputStream().use { output -> zip.copyTo(output) }
                    extracted++
                }
            }
            if (extracted != EXPECTED_ASSET_COUNT) {
                throw IOException("扩展资源数量不对：$extracted / $EXPECTED_ASSET_COUNT")
            }
            File(staging, MARKER).writeText("v1:$EXPECTED_ASSET_COUNT", Charsets.UTF_8)
            finalDir.deleteRecursively()
            if (!staging.renameTo(finalDir)) {
                throw IOException("无法保存扩展资源")
            }
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    private fun isInstallationComplete(context: Context): Boolean {
        val dir = assetsDir(context)
        if (!File(dir, MARKER).isFile) return false
        return dir.listFiles { file -> file.isFile && file.extension == "webp" }
            ?.size == EXPECTED_ASSET_COUNT
    }

    private fun extensionRoot(context: Context): File =
        File(context.filesDir, "extensions/noto-emoji-animated")

    private fun assetsDir(context: Context): File =
        File(extensionRoot(context), "v1")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xff)
        }
    }

    private fun directoryBytes(dir: File): Long =
        dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
}
