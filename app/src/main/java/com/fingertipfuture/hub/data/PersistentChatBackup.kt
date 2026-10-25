package com.fingertipfuture.hub.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.core.StorageLocations
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 跨卸载保存聊天与附件。
 *
 * v2 使用增量目录：聊天清单和附件分别加密，未变化附件不重复复制。新清单写成功后才清理
 * 不再引用的附件，避免删除的会话在下次重装时被旧快照复活。
 *
 * 数据位于 `Documents/指尖Hub/.persistence`，不会随应用私有目录一起删除。
 * 提供商、Key 和其它设置不在快照中，因此卸载后仍需用户重新配置。
 */
internal object PersistentChatBackup {
    private const val TAG = "HubPersistence"
    private const val LEGACY_SNAPSHOT_NAME = "snapshot.zip"
    private const val LEGACY_ARCHIVE_ENTRY = "archive.json"
    private const val V2_DIR = "v2"
    private const val V2_FILES_DIR = "files"
    private const val V2_ARCHIVE_NAME = "archive.bin"
    private const val V2_INDEX_NAME = "index.bin"
    private const val WRITE_DEBOUNCE_MS = 10_000L
    private const val MIN_BACKGROUND_INTERVAL_MS = 30_000L
    private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    private const val MAX_FILE_BYTES = 256L * 1024L * 1024L
    private const val MAX_RESTORE_BYTES = 2L * 1024L * 1024L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduleLock = Any()
    private val writeMutex = Mutex()
    private var scheduled: Job? = null
    @Volatile private var restoring = false
    @Volatile private var lastBackgroundWriteAt = 0L

    private data class FileStamp(val size: Long, val modifiedAt: Long)

    fun storedSizeBytes(): Long {
        val root = StorageLocations.persistentRoot()
        return root.takeIf(File::exists)?.walkTopDown()
            ?.filter(File::isFile)
            ?.sumOf(File::length)
            ?: 0L
    }

    fun hasBackup(context: Context): Boolean =
        permissionGranted(context) &&
            (v2Archive().isFile || legacySnapshot().isFile)

    fun permissionGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (restoring || !permissionGranted(app)) return
        synchronized(scheduleLock) {
            if (scheduled?.isActive == true) return
            scheduled = scope.launch {
                delay(WRITE_DEBOUNCE_MS)
                runCatching { writeSnapshotSerialized(app, force = false) }
                    .onFailure { Log.e(TAG, "写入持久化聊天快照失败", it) }
            }
        }
    }

    fun flush(context: Context) {
        val app = context.applicationContext
        if (restoring || !permissionGranted(app)) return
        scope.launch {
            runCatching { writeSnapshotSerialized(app, force = true) }
                .onFailure { Log.e(TAG, "立即写入持久化聊天快照失败", it) }
        }
    }

    /**
     * 解出快照中的附件与存档。必须由 IO 协程调用，调用方随后通过正常导入流程建立数据库记录。
     */
    suspend fun restoreArchive(context: Context): List<ArchivedConversation>? {
        restoring = true
        return try {
            writeMutex.withLock {
                if (!permissionGranted(context)) return@withLock null
                try {
                    if (v2Archive().isFile) restoreV2(context) else restoreLegacy(context)
                } catch (error: Throwable) {
                    Log.e(TAG, "恢复持久化聊天快照失败", error)
                    null
                }
            }
        } finally {
            restoring = false
        }
    }

    private suspend fun writeSnapshotSerialized(context: Context, force: Boolean) {
        writeMutex.withLock {
            if (restoring) return
            val now = System.currentTimeMillis()
            if (!force && now - lastBackgroundWriteAt < MIN_BACKGROUND_INTERVAL_MS) return
            writeSnapshot(context)
            lastBackgroundWriteAt = now
        }
    }

    private fun writeSnapshot(context: Context) {
        val conversations = ChatStore.exportArchive(
            flushFirst = true,
            includeLocalOnly = true,
        )
        val archiveJson = ChatArchive.encodeArchive(conversations)
            .toByteArray(Charsets.UTF_8)
        if (archiveJson.size > MAX_ARCHIVE_BYTES) {
            throw IOException("聊天清单超过持久化允许的大小")
        }

        val v2Root = v2Root(context)
        val filesRoot = File(v2Root, V2_FILES_DIR)
        val currentFiles = referencedFiles(context, conversations)
        val previousIndex = readIndex(context)
        val nextIndex = linkedMapOf<String, FileStamp>()

        currentFiles.forEach { (relative, source) ->
            val destination = File(filesRoot, relative)
            val stamp = FileStamp(source.length(), source.lastModified())
            val unchanged = previousIndex[relative] == stamp && destination.isFile
            if (!unchanged) {
                PersistentSnapshotCrypto.atomicEncryptFile(context, source, destination)
            }
            nextIndex[relative] = stamp
        }

        // 先保证附件完整，再替换聊天清单。中途被杀最多留下无害的新附件。
        PersistentSnapshotCrypto.atomicWrite(
            context,
            gzip(archiveJson),
            File(v2Root, V2_ARCHIVE_NAME),
        )
        PersistentSnapshotCrypto.atomicWrite(
            context,
            encodeIndex(nextIndex),
            File(v2Root, V2_INDEX_NAME),
        )
        removeUnreferencedFiles(filesRoot, nextIndex.keys)
        legacySnapshot().delete()
    }

    private fun restoreV2(context: Context): List<ArchivedConversation>? {
        val archiveFile = v2Archive()
        if (!archiveFile.isFile) return null
        val encrypted = archiveFile.inputStream().use {
            it.readBounded(MAX_ARCHIVE_BYTES)
        }
        val archiveBytes = decodeCompressedOrPlain(
            PersistentSnapshotCrypto.decrypt(context, encrypted),
        )
        val conversations = ChatArchive.decodeArchive(String(archiveBytes, Charsets.UTF_8))
            ?: return null

        val encryptedFiles = File(v2Root(context), V2_FILES_DIR)
        if (encryptedFiles.isDirectory) {
            val allowedFiles = archivedAttachmentPaths(conversations)
            val staging = File(context.cacheDir, "hub-persistence-restore-v2").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                decryptDirectory(context, encryptedFiles, staging, allowedFiles)
                copyDirectory(
                    source = File(staging, "attachments"),
                    target = AttachmentStore.attachmentDirectory(context),
                    allowedFiles = allowedFiles,
                    prefix = "attachments",
                )
                copyDirectory(
                    source = File(staging, "voice"),
                    target = AttachmentStore.voiceDirectory(context),
                    allowedFiles = allowedFiles,
                    prefix = "voice",
                )
            } finally {
                staging.deleteRecursively()
            }
        }
        return conversations
    }

    private fun restoreLegacy(context: Context): List<ArchivedConversation>? {
        val snapshot = legacySnapshot()
        if (!snapshot.isFile) return null
        val staging = File(context.cacheDir, "hub-persistence-restore-legacy").apply {
            deleteRecursively()
            mkdirs()
        }
        var archiveText: String? = null
        var totalBytes = 0L
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(snapshot))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory) continue
                    when {
                        name == LEGACY_ARCHIVE_ENTRY -> {
                            archiveText = zip.readBounded(MAX_ARCHIVE_BYTES).toString(Charsets.UTF_8)
                        }
                        name.startsWith("attachments/") || name.startsWith("voice/") -> {
                            val parts = name.split('/')
                            if (parts.size != 2 || parts[1].isBlank() || parts[1].contains("..")) {
                                throw IllegalArgumentException("快照包含不安全路径：$name")
                            }
                            val target = File(File(staging, parts[0]), parts[1])
                            target.parentFile?.mkdirs()
                            totalBytes += target.outputStream().use { output ->
                                zip.copyBounded(
                                    output,
                                    MAX_FILE_BYTES,
                                    MAX_RESTORE_BYTES - totalBytes,
                                )
                            }
                        }
                    }
                }
            }
            val conversations = archiveText?.let(ChatArchive::decodeArchive) ?: return null
            val allowedFiles = archivedAttachmentPaths(conversations)
            copyDirectory(
                source = File(staging, "attachments"),
                target = AttachmentStore.attachmentDirectory(context),
                allowedFiles = allowedFiles,
                prefix = "attachments",
            )
            copyDirectory(
                source = File(staging, "voice"),
                target = AttachmentStore.voiceDirectory(context),
                allowedFiles = allowedFiles,
                prefix = "voice",
            )
            return conversations
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun referencedFiles(
        context: Context,
        conversations: List<ArchivedConversation>,
    ): Map<String, File> {
        val filesRoot = context.filesDir.canonicalFile
        return conversations.asSequence()
            .flatMap { it.messages.asSequence() }
            .flatMap { it.attachments.asSequence() }
            .mapNotNull { attachment ->
                val owned = AttachmentPathPolicy.ownedPath(context.filesDir, attachment.path)
                    ?: return@mapNotNull null
                val file = File(owned).canonicalFile
                val relative = runCatching {
                    file.relativeTo(filesRoot).invariantSeparatorsPath
                }.getOrNull() ?: return@mapNotNull null
                if (
                    !relative.startsWith("attachments/") &&
                    !relative.startsWith("voice/")
                ) {
                    return@mapNotNull null
                }
                relative to file
            }
            .distinctBy { it.first }
            .toMap()
    }

    private fun decryptDirectory(
        context: Context,
        source: File,
        target: File,
        allowedFiles: Set<String>,
    ) {
        var total = 0L
        source.walkTopDown()
            .filter(File::isFile)
            .forEach { encrypted ->
                val relative = encrypted.relativeTo(source).invariantSeparatorsPath
                if (relative !in allowedFiles) return@forEach
                if (
                    !relative.startsWith("attachments/") &&
                    !relative.startsWith("voice/")
                ) {
                    return@forEach
                }
                val size = encrypted.length()
                if (size > MAX_FILE_BYTES) throw IOException("持久化附件超过允许大小")
                total += size
                if (total > MAX_RESTORE_BYTES) throw IOException("持久化附件总量超过允许大小")
                PersistentSnapshotCrypto.decryptFile(
                    context,
                    encrypted,
                    File(target, relative),
                )
            }
    }

    private fun archivedAttachmentPaths(
        conversations: List<ArchivedConversation>,
    ): Set<String> = conversations.asSequence()
        .flatMap { it.messages.asSequence() }
        .flatMap { it.attachments.asSequence() }
        .mapNotNull { attachment ->
            if (attachment.path.isBlank()) return@mapNotNull null
            val name = runCatching { File(attachment.path).name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val folder = if (attachment.kind == HubAttachment.KIND_VOICE) "voice" else "attachments"
            "$folder/$name"
        }
        .toSet()

    private fun readIndex(context: Context): Map<String, FileStamp> {
        val file = File(v2Root(context), V2_INDEX_NAME)
        if (!file.isFile) return emptyMap()
        return runCatching {
            val encrypted = file.inputStream().use { it.readBounded(MAX_ARCHIVE_BYTES) }
            val plain = decodeCompressedOrPlain(
                PersistentSnapshotCrypto.decrypt(context, encrypted),
            )
            val array = JSONArray(String(plain, Charsets.UTF_8))
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path")
                    if (path.isBlank()) continue
                    put(
                        path,
                        FileStamp(
                            size = item.optLong("size"),
                            modifiedAt = item.optLong("modifiedAt"),
                        ),
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "持久化索引读取失败，将重建附件副本", it)
            emptyMap()
        }
    }

    private fun encodeIndex(index: Map<String, FileStamp>): ByteArray =
        JSONArray().apply {
            index.forEach { (path, stamp) ->
                put(JSONObject().apply {
                    put("path", path)
                    put("size", stamp.size)
                    put("modifiedAt", stamp.modifiedAt)
                })
            }
        }.toString().toByteArray(Charsets.UTF_8)

    private fun removeUnreferencedFiles(root: File, keep: Set<String>) {
        if (!root.isDirectory) return
        root.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (relative !in keep) file.delete()
            }
        root.walkBottomUp()
            .filter { it.isDirectory && it != root && it.list().isNullOrEmpty() }
            .forEach(File::delete)
    }

    private fun copyDirectory(
        source: File,
        target: File,
        allowedFiles: Set<String>,
        prefix: String,
    ) {
        if (!source.isDirectory) return
        target.mkdirs()
        source.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                if ("$prefix/$relative" !in allowedFiles) return@forEach
                val destination = File(target, relative)
                destination.parentFile?.mkdirs()
                file.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
    }

    private fun v2Root(context: Context): File {
        if (!permissionGranted(context)) {
            throw SecurityException("尚未授予持久化存储权限")
        }
        return File(StorageLocations.persistentRoot(), V2_DIR).apply {
            mkdirs()
            File(parentFile, ".nomedia").writeText("")
        }
    }

    private fun v2Archive(): File =
        File(File(StorageLocations.persistentRoot(), V2_DIR), V2_ARCHIVE_NAME)

    private fun legacySnapshot(): File =
        File(StorageLocations.persistentRoot(), LEGACY_SNAPSHOT_NAME)

    private fun InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(output, maxBytes, maxBytes)
        return output.toByteArray()
    }

    private fun InputStream.copyBounded(
        output: java.io.OutputStream,
        maxEntryBytes: Long,
        maxTotalBytes: Long,
    ): Long {
        val limit = minOf(maxEntryBytes, maxTotalBytes)
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("持久化内容超过允许大小")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBounded(MAX_ARCHIVE_BYTES) }

    private fun decodeCompressedOrPlain(bytes: ByteArray): ByteArray =
        runCatching { gunzip(bytes) }.getOrDefault(bytes)
}
