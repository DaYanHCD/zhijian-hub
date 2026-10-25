package com.fingertipfuture.hub.update

import com.fingertipfuture.hub.data.NetworkGuard
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay

/** 服务器返回了非 2xx 状态码。 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * 更新清单、公告和 APK 共用的极简 HTTP 客户端。
 *
 * 仅使用 Android 原生 HttpURLConnection；不引第三方网络库。
 */
internal object HttpClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val GET_MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 400L
    private const val MAX_TEXT_CHARS = 1_000_000
    private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024

    suspend fun getString(url: String): String {
        var lastError: IOException? = null
        for (attempt in 1..GET_MAX_ATTEMPTS) {
            try {
                return getStringOnce(url)
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < GET_MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
            }
        }
        throw lastError ?: IOException("请求失败: $url")
    }

    private fun getStringOnce(url: String): String {
        val conn = open(url)
        return try {
            requireSuccess(conn)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    out.append(buffer, 0, read)
                    if (out.length > MAX_TEXT_CHARS) throw IOException("响应内容过大")
                }
                out.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download(
        url: String,
        dest: File,
        maxBytes: Long = MAX_DOWNLOAD_BYTES,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val conn = open(url)
        var downloadedSoFar = 0L
        var totalSoFar = -1L
        try {
            requireSuccess(conn)
            val total = conn.contentLengthLong
            totalSoFar = total
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, dest.name + ".part")

            conn.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > maxBytes) throw IOException("下载内容超过允许的大小")
                        downloadedSoFar = downloaded
                        onProgress(downloaded, total)
                    }
                }
            }

            if (dest.exists() && !dest.delete()) throw IOException("无法覆盖已存在的文件: ${dest.name}")
            if (!part.renameTo(dest)) throw IOException("无法重命名临时文件: ${part.name}")
        } catch (t: Throwable) {
            android.util.Log.e(
                "HubUpdate",
                "下载中断: 已收 $downloadedSoFar / 声明 $totalSoFar 字节",
                t,
            )
            throw t
        } finally {
            conn.disconnect()
        }
    }

    private fun requireSuccess(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) throw HttpStatusException(code)
    }

    private fun open(url: String): HttpURLConnection {
        NetworkGuard.requireAllowed(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ZhijianHub-Android")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
    }
}
