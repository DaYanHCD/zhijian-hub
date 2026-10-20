package com.fingertipfuture.hub.update

import com.fingertipfuture.hub.data.NetworkGuard
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 服务器返回了非 2xx 状态码。 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * 极简 HTTP 客户端，基于安卓原生 [HttpURLConnection]。
 *
 * 这个 App 与服务器的交互只有两件事：读一个小 JSON 清单、下载一个 APK。
 * 原生 API 足够，所以**不引 OkHttp 之类的第三方网络库**（铁律一）。
 */
internal object HttpClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024

    /** 清单这类小请求的自动重试次数（弱网下响应体被截断很常见）。 */
    private const val GET_MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 400L
    /** 清单/公告都是小 JSON，超过这个大小一定不正常，直接拒绝，避免恶意响应占满内存。 */
    private const val MAX_TEXT_CHARS = 1_000_000
    private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024

    /** GET 一个文本资源（用于读取更新清单）。 */
    suspend fun getString(url: String): String {
        var lastError: IOException? = null
        for (attempt in 1..GET_MAX_ATTEMPTS) {
            try {
                return getStringOnce(url)
            } catch (e: HttpStatusException) {
                // 服务器已经明确答复了（404 / 500…），重试没有任何意义，只会让用户白等
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

    /**
     * 下载文件到 [dest]。
     *
     * 先写 `*.part` 再改名，避免半截文件被当成完整安装包。
     * [onProgress] 在 IO 线程被频繁回调；总字节未知时为 -1。
     * 这里**不做自动重试**：APK 体积大，弱网下反复重试是白烧流量，交给界面上的「重试」按钮。
     */
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
                        if (downloaded > maxBytes) {
                            throw IOException("下载内容超过允许的大小")
                        }
                        downloadedSoFar = downloaded
                        onProgress(downloaded, total)
                    }
                }
            }

            if (dest.exists() && !dest.delete()) throw IOException("无法覆盖已存在的文件: ${dest.name}")
            if (!part.renameTo(dest)) throw IOException("无法重命名临时文件: ${part.name}")
        } catch (t: Throwable) {
            // 排查用：把「读到多少字节就断了」写进日志（大响应被中途掐断时非常有价值）
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
            // NetworkGuard 只校验初始地址。自动跟随重定向会跳过后续地址校验，
            // 可能把 HTTPS 降级成 HTTP 或转到别的主机；统一拒绝 3xx。
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ZhijianHub-Android")
            setRequestProperty("Accept", "*/*")
        }
    }
}
