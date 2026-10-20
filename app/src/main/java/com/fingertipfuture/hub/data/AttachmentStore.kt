package com.fingertipfuture.hub.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 附件的落地：选图 / 拍照 / 选文件 / 定位 / 导出 / 导入。
 *
 * 全部是安卓原生 API：`ContentResolver` + `BitmapFactory` + `LocationManager` +
 * `MediaStore`（导出到系统「下载」目录）。**没有任何 WebView、没有任何第三方库**。
 *
 * 图片统一**压到最长边 1280、JPEG 85%** 再存：一是发给模型时 base64 不至于几 MB，
 * 二是气泡渲染也快。原文件不保留（聊天记录里要的是能看能发的那份）。
 */
object AttachmentStore {

    private const val MAX_IMAGE_EDGE = 1280
    private const val JPEG_QUALITY = 85
    /** 文本类文件最多读这么多字符塞进提示词。 */
    private const val MAX_TEXT_CHARS = 20000
    private const val MAX_IMPORT_BYTES = 16L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    /** 取一次当前位置的整体上限（GPS 和网络定位是**并行**跑的，谁先回来用谁）。 */
    private const val LOCATION_TIMEOUT_MS = 8_000L

    /** 反查地名最多等这么久，超了就不带地名（只是锦上添花）。 */
    private const val GEOCODE_TIMEOUT_MS = 2_000L

    /** "最后一次已知位置" 在这个时间内就当是新鲜的，直接用、不等（基本 0 延迟）。 */
    private const val FRESH_LOCATION_MS = 5 * 60_000L

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "log", "xml", "yml", "yaml",
        "kt", "java", "py", "js", "ts", "c", "h", "cpp", "cs", "go", "rs", "rb",
        "php", "swift", "sql", "sh", "bat", "ps1", "html", "css", "ini", "conf", "toml",
    )

    private fun attachmentDir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }
    // ---------------- 数据管理（2026-10-16：设置 › 数据 › 数据管理） ----------------

    /** 附件目录里的一个文件。 */
    data class StoredAttachment(
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val kind: String,
    ) {
        val isImage: Boolean get() = kind == HubAttachment.KIND_IMAGE
        val isVoice: Boolean get() = kind == HubAttachment.KIND_VOICE

        /** 去掉导入文件时的 `file_<时间戳>_` 前缀，展示真实文件名。 */
        val displayName: String
            get() = when {
                isImage -> "图片"
                isVoice -> "语音消息"
                else -> name.replaceFirst(Regex("^file_\\d+_"), "").ifBlank { name }
            }
    }

    /** 列出附件与语音目录里的所有用户文件（新的在前）。 */
    fun listAttachments(context: Context): List<StoredAttachment> =
        listOf(
            attachmentDir(context),
            File(context.filesDir, "voice").apply { mkdirs() },
        ).flatMap { dir -> dir.listFiles().orEmpty().asList() }
            .filter { it.isFile }
            .map {
                StoredAttachment(
                    name = it.name,
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    modifiedAt = it.lastModified(),
                    kind = when {
                        it.parentFile?.name == "voice" -> HubAttachment.KIND_VOICE
                        it.extension.lowercase() in IMAGE_EXTENSIONS -> HubAttachment.KIND_IMAGE
                        else -> HubAttachment.KIND_FILE
                    },
                )
            }
            .sortedByDescending { it.modifiedAt }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /** 附件占用的总字节数。 */
    fun attachmentBytes(context: Context): Long =
        listOf(attachmentDir(context), File(context.filesDir, "voice"))
            .flatMap { dir -> dir.listFiles().orEmpty().asList() }
            .sumOf { if (it.isFile) it.length() else 0L }

    /** 删掉一个附件文件。返回是否真的删掉了。 */
    fun deleteAttachment(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /**
     * 只删 App 自己目录里的附件（`files/attachments`、`files/voice`）。
     *
     * 删会话 / 删消息时必须走这个：消息里存的是绝对路径，不能因为一条旧数据
     * 或手滑把用户相册里的原图删掉。
     */
    fun deleteOwnedAttachment(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            val root = context.filesDir.canonicalFile
            val file = File(path).canonicalFile
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (!relative.startsWith("attachments/") && !relative.startsWith("voice/")) {
                return@runCatching false
            }
            file.isFile && file.delete()
        }.getOrDefault(false)
    }

    // ---------------- 选择 / 拍照 ----------------

    /** 相册选图（或拍照）：把图压好存进私有目录，返回附件。 */
    suspend fun importImage(context: Context, uri: Uri): HubAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeDownscaled(context, uri) ?: return@runCatching null
            val file = File(attachmentDir(context), "img_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            HubAttachment(
                kind = HubAttachment.KIND_IMAGE,
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                mime = "image/jpeg",
            )
        }.getOrNull()
    }

    /** 选文件：内容拷进私有目录。 */
    suspend fun importFile(context: Context, uri: Uri): HubAttachment? = withContext(Dispatchers.IO) {
        val name = queryDisplayName(context, uri) ?: "文件"
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val file = File(attachmentDir(context), "file_${System.currentTimeMillis()}_$safe")
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        // 先限流再落盘：以前是整份拷完才检查 64 MB，
                        // 用户选一个超大视频会先把手机存储写满。
                        if (total > MAX_IMPORT_BYTES * 4) {
                            throw java.io.IOException("文件大于 64 MB")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatching null
            HubAttachment(
                kind = HubAttachment.KIND_FILE,
                path = file.absolutePath,
                name = name,
                size = file.length(),
                mime = context.contentResolver.getType(uri).orEmpty(),
            )
        }
        if (result.getOrNull() == null) runCatching { file.delete() }
        result.getOrNull()
    }

    /** 文本类文件的内容（给模型看）；读不到或不是文本就返回空串。 */
    suspend fun readTextContent(attachment: HubAttachment): String = withContext(Dispatchers.IO) {
        if (!attachment.isFile) return@withContext ""
        val file = File(attachment.path)
        if (!file.isFile) return@withContext ""
        val ext = file.extension.lowercase()
        val looksText = ext in TEXT_EXTENSIONS || attachment.mime.startsWith("text/")
        if (!looksText) return@withContext ""
        runCatching {
            file.bufferedReader().use { reader ->
                val chars = CharArray(MAX_TEXT_CHARS)
                val read = reader.read(chars)
                if (read <= 0) "" else String(chars, 0, read)
            }
        }.getOrDefault("")
    }

    /** 发给模型用的图片 data URL（base64），失败返回 null。 */
    suspend fun imageDataUrl(attachment: HubAttachment): String? = withContext(Dispatchers.IO) {
        if (!attachment.isImage) return@withContext null
        runCatching {
            val file = File(attachment.path)
            if (!file.isFile || file.length() > MAX_IMAGE_BYTES) return@runCatching null
            val bytes = file.readBytes()
            // 不能一律写 image/jpeg：导入存档里可能是 png/webp，部分供应商会按
            // data URL 的 MIME 解析，标签错了就会直接拒绝这张图。
            val mime = attachment.mime
                .takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
            "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decodeDownscaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var scale = 1
        while (bounds.outWidth / scale > MAX_IMAGE_EDGE * 2 && bounds.outHeight / scale > MAX_IMAGE_EDGE * 2) {
            scale *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = scale }
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= MAX_IMAGE_EDGE) return decoded
        val ratio = MAX_IMAGE_EDGE.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    // ---------------- 位置 ----------------

    /**
     * 删掉一条语音的文件。
     *
     * 什么时候用：删消息 / 编辑重发截断 / 清空对话时 —— 那条语音的 m4a 也该跟着走，
     * 不然 `files/voice` 会越攒越多（2026-10-15 自查时发现的垃圾）。
     *
     * ⚠️ **只删我们自己 `files/voice` 目录里的文件**：路径不对就什么都不做，
     * 免得将来手滑删到用户相册里的原图。
     */
    fun deleteVoiceFile(path: String) {
        if (path.isBlank()) return
        runCatching {
            val file = File(path)
            if (file.isFile && file.parentFile?.name == "voice") file.delete()
        }
    }

    /** 录音权限（语音消息 / 转文字都要）。 */
    fun hasRecordPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 当前位置：拿不到就回退到"最后一次已知位置"。
     *
     * ⚠️ 整段必须跑在 IO 线程上：反查地名用的 `Geocoder.getFromLocation` 是**阻塞**的
     * （要联网），在主线程上会直接把界面卡死 —— 用户 2026-10-14 反馈的
     * 「点发送位置整个界面都卡住了」根因就在这里（原来没切线程，而且整段没超时上限，
     * 两个 provider 各能等 30 秒）。现在：切线程 + 每个 provider 限时 [LOCATION_TIMEOUT_MS]。
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): HubAttachment? = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        fun log(source: String, attachment: HubAttachment?) {
            if (com.fingertipfuture.hub.core.AppConfig.IS_DEBUG) {
                android.util.Log.i(
                    "HubLocation",
                    "source=" + source + " took=" + (SystemClock.elapsedRealtime() - startedAt) + "ms " +
                        "lat=" + attachment?.lat + " lon=" + attachment?.lon,
                )
            }
        }
        if (!hasLocationPermission(context)) {
            log("no-permission", null)
            return@withContext null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        // 网络定位优先：室内和模拟器里它最快，GPS 常常要等卫星（8 秒上限白等）
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        fun lastKnown(): Location? =
            runCatching { manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
                ?: providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }

        // 1) 最近的"最后一次已知位置"直接用 —— 绝大多数情况这里是 0 等待（跟微信一个套路）
        val recent = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .plus(listOfNotNull(runCatching { manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()))
            .filter { System.currentTimeMillis() - it.time <= FRESH_LOCATION_MS }
            .maxByOrNull { it.time }
        if (recent != null) {
            val attachment = toAttachment(context, recent)
            log("last-known-recent", attachment)
            return@withContext attachment
        }

        // 2) 现取一次：GPS 和网络定位**同时**发请求，谁先回来用谁（串行等两个 provider
        //    在室内/模拟器上要白等 8 秒，用户会觉得"点完没反应"）。整体上限 LOCATION_TIMEOUT_MS。
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val fresh2 = awaitFirstLocation(manager, providers, executor)
        if (fresh2 != null) {
            val attachment = toAttachment(context, fresh2)
            log("requested", attachment)
            return@withContext attachment
        }

        // 3) 全超时：退到"最后一次已知位置"（哪怕很旧也比没有强）
        val fallback = lastKnown()?.let { toAttachment(context, it) }
        log("last-known-stale", fallback)
        fallback
    }

    /**
     * 并行问几个 provider，谁先给出位置就用谁，剩下的立刻取消。
     *
     * 为什么不用 for 循环串行：室内/模拟器里 GPS 常常要等到超时才回来，
     * 串行等于"8 秒 + 8 秒"地白等；并行最坏也只是 8 秒。
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFirstLocation(
        manager: LocationManager,
        providers: List<String>,
        executor: Executor,
    ): Location? = coroutineScope {
        if (providers.isEmpty()) return@coroutineScope null
        val jobs = providers.map { provider -> async { awaitLocation(manager, provider, executor) } }
        val first = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            select { jobs.forEach { job -> job.onAwait { it } } }
        }
        jobs.forEach { it.cancel() }
        first
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(
        manager: LocationManager,
        provider: String,
        executor: Executor,
    ): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { runCatching { signal.cancel() } }
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                executor,
            ) { location -> if (cont.isActive) cont.resume(location) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private suspend fun toAttachment(context: Context, location: Location): HubAttachment {
        // 反查地名是锦上添花：Geocoder 是阻塞 API，最多等 GEOCODE_TIMEOUT_MS，
        // 超了就直接不带地名（超时的那次调用在 IO 线程上自己跑完，不影响界面）。
        val label = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val address = android.location.Geocoder(context, java.util.Locale.getDefault())
                        .getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                    address?.let { listOfNotNull(it.locality, it.subLocality, it.thoroughfare).joinToString("") }
                }.getOrNull().orEmpty()
            }
        }.orEmpty()
        return HubAttachment(
            kind = HubAttachment.KIND_LOCATION,
            lat = location.latitude,
            lon = location.longitude,
            label = label.take(60),
        )
    }

    /** 打开外部地图（不是 WebView，是交给系统/地图 App）。 */
    fun mapIntent(lat: Double, lon: Double): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))

    // ---------------- 导出 / 导入 ----------------

    /**
     * 把一段文本写成一个文件放在系统「下载」目录（Android 10+ 不用权限）。
     * 返回：给用户看的路径 + 分享用的 Uri（写不进下载目录时落到 App 私有目录）。
     */
    suspend fun exportText(
        context: Context,
        text: String,
        fileName: String,
        mime: String,
    ): Pair<String, Uri?> = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val uri = runCatching { writeToDownloads(context, fileName, bytes, mime) }.getOrNull()
        if (uri != null) {
            "下载/$fileName" to uri
        } else {
            val file = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
            file.writeBytes(bytes)
            file.absolutePath to null
        }
    }

    /** 读一个用户选中的文件（导入对话用）。 */
    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) return@runCatching null
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun writeToDownloads(context: Context, fileName: String, bytes: ByteArray, mime: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun shareIntent(uri: Uri, mime: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
