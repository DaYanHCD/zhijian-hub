package com.fingertipfuture.hub.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 极简 WebDAV 客户端（2026-10-16：把「WebDAV 备份」做成真能用的）。
 *
 * 只用 `HttpURLConnection`，不引任何库。用到四个方法：
 * - `PROPFIND`（Depth: 1）—— 列目录 / 测连接（207 或 200 算通）；
 * - `PUT` —— 上传备份文件；
 * - `GET` —— 下载备份文件；
 * - `MKCOL` —— 需要时建目录（失败不管，很多网盘默认就有那层）。
 *
 * 认证只用 HTTP Basic（坚果云、Nextcloud、群晖这类都支持「应用密码」）。
 */
object WebDavClient {

    private const val MAX_PROPFIND_BYTES = 4 * 1024 * 1024
    private const val MAX_GET_BYTES = 64 * 1024 * 1024
    private const val MAX_STREAM_BYTES = 512L * 1024 * 1024

    /** 有没有配好能用的一整套（地址必填；用户名密码可以留空给匿名服务）。 */
    fun isConfigured(settings: BackupStore.WebDav): Boolean =
        settings.url.isNotBlank() && settings.path.isNotBlank()

    private fun base(settings: BackupStore.WebDav): String {
        val root = settings.url.trim().trimEnd('/')
        val path = settings.path.trim().trim('/')
        if (path.isEmpty()) return root + "/"
        val encodedPath = URI(null, null, "/" + path, null).rawPath
        return root + encodedPath + "/"
    }

    private fun open(url: String, method: String, settings: BackupStore.WebDav): HttpURLConnection =
        run {
            NetworkGuard.requireAllowed(url)
            requireSecureCredentials(url, settings)
            (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
            if (settings.user.isNotBlank()) {
                val token = Base64.encodeToString(
                    (settings.user + ":" + settings.password).toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                setRequestProperty("Authorization", "Basic " + token)
            }
        }
        }

    private fun read(conn: HttpURLConnection, okCodes: IntRange): String {
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in okCodes) throw IOException("HTTP " + status + " · " + text.take(160))
        return text
    }

    /** 测连接：PROPFIND 一次目录。返回目录里的文件数。 */
    suspend fun test(settings: BackupStore.WebDav): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val names = propfind(settings)
            "连接成功，目录里有 " + names.size + " 项"
        }
    }

    /** 列目录里的文件名（只留最后一段，去重、排序）。 */
    suspend fun list(settings: BackupStore.WebDav): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching { propfind(settings) }
    }

    /**
     * 发一个 PROPFIND。
     *
     * ⚠️ 这里**不能用 HttpURLConnection**：安卓的 `setRequestMethod` 只认
     * GET/POST/HEAD/OPTIONS/PUT/DELETE/TRACE，传 "PROPFIND" 会直接抛 ProtocolException
     * （2026-10-16 实测：PUT 备份能成，PROPFIND 一个请求都发不出去）。
     * 所以 PROPFIND 走**原生 socket**（https 用 SSLSocket），自己拼 HTTP 报文。
     */
    private fun propfind(settings: BackupStore.WebDav): List<String> {
        val rawUrl = base(settings)
        NetworkGuard.requireAllowed(rawUrl)
        requireSecureCredentials(rawUrl, settings)
        val url = URL(rawUrl)
        val https = url.protocol.equals("https", ignoreCase = true)
        val port = if (url.port > 0) url.port else if (https) 443 else 80
        val socket = openSocket(url.host, port, https)
        return socket.use { sock ->
            val body = PROPFIND_BODY.toByteArray(Charsets.UTF_8)
            val path = url.path.ifBlank { "/" } + if (url.query.isNullOrBlank()) "" else "?" + url.query
            val head = StringBuilder()
            head.append("PROPFIND ").append(path).append(" HTTP/1.1\r\n")
            head.append("Host: ").append(hostHeader(url, port)).append("\r\n")
            head.append("Depth: 1\r\n")
            head.append("Content-Type: application/xml; charset=utf-8\r\n")
            head.append("Content-Length: ").append(body.size).append("\r\n")
            head.append("Connection: close\r\n")
            head.append("User-Agent: ZhijianHub-Android\r\n")
            if (settings.user.isNotBlank()) {
                val token = Base64.encodeToString(
                    (settings.user + ":" + settings.password).toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                head.append("Authorization: Basic ").append(token).append("\r\n")
            }
            head.append("\r\n")
            val out = sock.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.UTF_8))
            out.write(body)
            out.flush()
            val raw = sock.getInputStream().readLimited(MAX_PROPFIND_BYTES).toString(Charsets.UTF_8)
            val status = raw.lineSequence().firstOrNull().orEmpty()
            if (!status.contains(" 2")) throw IOException("服务器返回：" + status.take(60))
            parseHrefs(raw)
        }
    }

    /**
     * PROPFIND 的底层连接。HTTPS 必须显式开启主机名校验：
     * 裸 [SSLSocket] 只校验证书链，不会自动确认证书属于当前域名。
     */
    private fun openSocket(host: String, port: Int, https: Boolean): Socket {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 20_000)
        raw.soTimeout = 30_000
        if (!https) return raw
        return try {
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, port, true) as SSLSocket
            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            runCatching { params.serverNames = listOf(SNIHostName(host)) }
            ssl.sslParameters = params
            ssl.soTimeout = 30_000
            ssl.startHandshake()
            ssl
        } catch (t: Throwable) {
            runCatching { raw.close() }
            throw t
        }
    }

    /** 带凭据时禁止发到远程明文 HTTP；仅允许回环地址访问本机服务。 */
    private fun requireSecureCredentials(url: String, settings: BackupStore.WebDav) {
        val hasCredentials = settings.user.isNotBlank() || settings.password.isNotBlank()
        if (isWebDavCredentialTransportAllowed(url, hasCredentials)) return
        throw IOException("包含用户名或密码的 WebDAV 必须使用 HTTPS")
    }

    internal fun isWebDavCredentialTransportAllowed(
        url: String,
        hasCredentials: Boolean,
    ): Boolean {
        if (!hasCredentials) return true
        val uri = URL(url)
        if (uri.protocol.equals("https", ignoreCase = true)) return true
        val host = uri.host.removeSurrounding("[", "]").lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    internal fun hostHeader(url: URL, port: Int): String {
        val host = if (url.host.contains(':') && !url.host.startsWith("[")) {
            "[${url.host}]"
        } else {
            url.host
        }
        val defaultPort = (url.protocol.equals("https", ignoreCase = true) && port == 443) ||
            (url.protocol.equals("http", ignoreCase = true) && port == 80)
        return if (defaultPort) host else "$host:$port"
    }

    suspend fun put(
        settings: BackupStore.WebDav,
        name: String,
        bytes: ByteArray,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(base(settings) + name, "PUT", settings).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            conn.outputStream.use { it.write(bytes) }
            read(conn, 200..299)
            runCatching { conn.disconnect() }
            Unit
        }
    }

    /** 流式 PUT，避免把整个附件 ZIP 先读进 ByteArray。 */
    suspend fun putFile(
        settings: BackupStore.WebDav,
        name: String,
        file: File,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(base(settings) + name, "PUT", settings).apply {
                doOutput = true
                setFixedLengthStreamingMode(file.length())
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            conn.outputStream.use { output ->
                file.inputStream().buffered().use { input -> input.copyTo(output) }
            }
            read(conn, 200..299)
            runCatching { conn.disconnect() }
            Unit
        }
    }

    suspend fun get(settings: BackupStore.WebDav, name: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(base(settings) + name, "GET", settings)
                val status = conn.responseCode
                if (status !in 200..299) {
                    val msg = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    runCatching { conn.disconnect() }
                    throw IOException("下载失败 HTTP " + status + " · " + msg.orEmpty().take(120))
                }
                val bytes = conn.inputStream.use { it.readLimited(MAX_GET_BYTES) }
                runCatching { conn.disconnect() }
                bytes
            }
        }

    /** 流式 GET 到临时文件；恢复大 ZIP 时不让整份响应进内存。 */
    suspend fun getToFile(
        settings: BackupStore.WebDav,
        name: String,
        dest: File,
        maxBytes: Long = MAX_STREAM_BYTES,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(base(settings) + name, "GET", settings)
            val status = conn.responseCode
            if (status !in 200..299) {
                val msg = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                runCatching { conn.disconnect() }
                throw IOException("下载失败 HTTP " + status + " · " + msg.orEmpty().take(120))
            }
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("下载内容超过允许的大小")
                        output.write(buffer, 0, read)
                    }
                }
            }
            runCatching { conn.disconnect() }
            dest
        }
    }

    /** 需要时建目录（失败就算了，很多服务默认就有那层）。 */
    suspend fun mkcol(settings: BackupStore.WebDav): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val url = (settings.url.trim().trimEnd('/') + "/" + settings.path.trim().trim('/'))
            val conn = open(url, "MKCOL", settings)
            conn.responseCode
            runCatching { conn.disconnect() }
        }
        Unit
    }

    /** PROPFIND 的响应里把 href 抠出来，取最后一段当文件名。 */
    private fun parseHrefs(xml: String): List<String> =
        Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1].trim() }
            .map { it.trimEnd('/').substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()

    private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("响应内容过大")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
  </d:prop>
</d:propfind>"""
}
