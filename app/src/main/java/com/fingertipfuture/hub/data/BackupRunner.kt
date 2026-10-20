package com.fingertipfuture.hub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 真正干活的备份 / 恢复（2026-10-16：WebDAV 备份从占位变成能用）。
 *
 * - **聊天记录** → `ChatArchive` 的 JSON，传成 `chat-<时间戳>.json`；
 * - **文件** → `filesDir` 下的 `attachments`、`voice`、`avatars`、`assistant_avatars` 打成
 *   `files-<时间戳>.zip`（用 `java.util.zip`，不引第三方库）；
 * - **恢复**：列目录 → 取时间戳最新的那一个 → 下回来 → 聊天记录走 `ChatArchive.decode` +
 *   `ChatStore.appendAll`，文件则解压回 `filesDir`。
 */
object BackupRunner {

    private val stampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA)
    private val fileDirs = listOf("attachments", "voice", "avatars", "assistant_avatars")
    private const val MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_ZIP_TOTAL_BYTES = 512L * 1024 * 1024

    private fun stamp(): String = stampFormat.format(Date())

    fun chatBytes(flushFirst: Boolean = true): ByteArray? {
        val archive = ChatStore.exportArchive(flushFirst = flushFirst)
        if (archive.isEmpty()) return null
        return ChatArchive.encodeArchive(archive).toByteArray(Charsets.UTF_8)
    }

    private fun zipFilesTo(context: Context, dest: File) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            fileDirs.forEach dirLoop@{ dirName ->
                val dir = File(context.filesDir, dirName)
                if (!dir.exists()) return@dirLoop
                dir.walkTopDown().filter { it.isFile }.forEach fileLoop@{ file ->
                    if (!file.isFile) return@fileLoop
                    val relative = file.relativeTo(dir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(dirName + "/" + relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** 按 [BackupStore.scope] 备份一次，返回「传了什么」的描述。 */
    suspend fun backup(context: Context): Result<String> = try {
        backupInternal(context)
    } catch (t: Throwable) {
        // ZIP 打包、本地导出、目录枚举的异常也要变成界面能显示的失败，
        // 不能让磁盘满/权限错误直接崩到点击事件外面。
        Result.failure(t)
    }

    private suspend fun backupInternal(context: Context): Result<String> {
        val settings = BackupStore.webdav
        if (!WebDavClient.isConfigured(settings)) return Result.failure(IllegalStateException("先填 WebDAV 地址和路径"))
        WebDavClient.mkcol(settings)

        val stamp = stamp()
        val done = mutableListOf<String>()
        val scope = BackupStore.scope
        if (scope == BackupStore.SCOPE_CHAT || scope == BackupStore.SCOPE_BOTH) {
            withContext(Dispatchers.IO) { ChatStore.flush() }
            val bytes = withContext(Dispatchers.IO) { chatBytes(flushFirst = false) }
            if (bytes == null && scope == BackupStore.SCOPE_CHAT) {
                return Result.failure(IllegalStateException("还没有可以备份的对话"))
            }
            if (bytes != null) {
                // 聊天 JSON 在离开设备前先加密；旧版明文备份仍可被 restore 读取。
                val encryptedChat = withContext(Dispatchers.IO) {
                    BackupCrypto.encryptBytes(bytes, settings)
                }
                WebDavClient.put(
                    settings,
                    "chat-" + stamp + ".json",
                    encryptedChat,
                )
                    .onFailure { return Result.failure(it) }
                done += "聊天记录"
            }
        }
        if (scope == BackupStore.SCOPE_FILES || scope == BackupStore.SCOPE_BOTH) {
            val temp = File(context.cacheDir, "hub-files-$stamp.zip")
            try {
                val sourceBytes = fileDirs.sumOf { dirName ->
                    File(context.filesDir, dirName).walkTopDown()
                        .filter { it.isFile }
                        .sumOf { it.length() }
                }
                if (sourceBytes > MAX_ZIP_TOTAL_BYTES) {
                    return Result.failure(IllegalStateException("附件超过 512 MB，请清理后再备份"))
                }
                withContext(Dispatchers.IO) { zipFilesTo(context, temp) }
                if (temp.length() <= 0L) {
                    // 默认备份「聊天记录 + 文件」时，如果本机还没附件，不应该让整次备份失败。
                    if (scope == BackupStore.SCOPE_FILES) {
                        return Result.failure(IllegalStateException("没有可备份的文件"))
                    }
                } else {
                    if (temp.length() > MAX_ZIP_TOTAL_BYTES) {
                        return Result.failure(IllegalStateException("附件备份超过 512 MB，请分批清理附件后再试"))
                    }
                    val encrypted = File(context.cacheDir, "hub-files-$stamp.enc")
                    try {
                        withContext(Dispatchers.IO) {
                            BackupCrypto.encryptFileTo(temp, encrypted, settings)
                        }
                        WebDavClient.putFile(settings, "files-" + stamp + ".zip", encrypted)
                            .onFailure { return Result.failure(it) }
                        done += "文件"
                    } finally {
                        encrypted.delete()
                    }
                }
            } finally {
                temp.delete()
            }
        }
        if (done.isEmpty()) return Result.failure(IllegalStateException("没有可备份的内容"))
        val name = buildList {
            if ("聊天记录" in done) add("chat-" + stamp + ".json")
            if ("文件" in done) add("files-" + stamp + ".zip")
        }.joinToString(" + ")
        BackupStore.markBackedUp(context, System.currentTimeMillis(), name)
        return Result.success("已备份：" + done.joinToString(" + "))
    }

    /** 从服务器上最新的备份恢复（聊天记录追加进当前对话；文件解压覆盖）。 */
    suspend fun restore(context: Context): Result<String> = try {
        restoreInternal(context)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun restoreInternal(context: Context): Result<String> {
        val settings = BackupStore.webdav
        if (!WebDavClient.isConfigured(settings)) return Result.failure(IllegalStateException("先填 WebDAV 地址和路径"))
        val names = WebDavClient.list(settings).getOrElse { return Result.failure(it) }
        val restored = mutableListOf<String>()

        // 聊天和文件分别找“各自最新”。以前只取一个全局最新时间戳，
        // 用户最近一次只备份了聊天时，更早的文件备份就永远恢复不到。
        val stamps = backupStamps(names).sortedDescending()
        val latestChatStamp = stamps.firstOrNull { "chat-$it.json" in names }
        val latestFilesStamp = stamps.firstOrNull { "files-$it.zip" in names }
        val latestChat = latestChatStamp?.let { "chat-$it.json" }
        val latestFiles = latestFilesStamp?.let { "files-$it.zip" }

        // 先下载并校验聊天 JSON，确认不是坏档之后再覆盖本机附件；
        // 否则会出现“附件已经覆盖、会话却没恢复”的半恢复状态。
        var archive: List<ArchivedConversation>? = null
        if (latestChat != null) {
            val bytes = WebDavClient.get(settings, latestChat).getOrElse { return Result.failure(it) }
            val decoded = withContext(Dispatchers.IO) {
                val plainBytes = BackupCrypto.decryptBytes(bytes, settings)
                ChatArchive.decodeArchive(String(plainBytes, Charsets.UTF_8))
            }
            if (decoded.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("这份备份读不出消息，已停止恢复，未改动本机文件"))
            }
            archive = decoded
        }

        var stagedFiles: File? = null
        val backupDir = latestFiles?.let {
            File(context.cacheDir, "hub-restore-$it.backup").also { dir -> dir.deleteRecursively() }
        }
        try {
            if (latestFiles != null) {
                val temp = File(context.cacheDir, "hub-restore-$latestFiles")
                try {
                    WebDavClient.getToFile(settings, latestFiles, temp)
                        .getOrElse { return Result.failure(it) }
                    if (temp.length() > MAX_ZIP_TOTAL_BYTES) {
                        return Result.failure(IllegalStateException("备份文件超过 512 MB"))
                    }
                    val plain = File(context.cacheDir, "hub-restore-$latestFiles.plain")
                    val staging = File(context.cacheDir, "hub-restore-$latestFiles.staging")
                        .also { it.deleteRecursively(); it.mkdirs() }
                    stagedFiles = staging
                    try {
                        withContext(Dispatchers.IO) {
                            BackupCrypto.decryptFileTo(temp, plain, settings)
                            plain.inputStream().buffered().use {
                                extractZipSafely(staging, it)
                            }
                        }
                    } finally {
                        plain.delete()
                    }
                } finally {
                    temp.delete()
                }
            }

            var importedIds: List<String> = emptyList()
            archive?.let { imported ->
                val result = withContext(Dispatchers.IO) { ChatStore.importArchiveDetailed(imported) }
                importedIds = result.conversationIds
                val messageCount = imported.sumOf { it.messages.size }
                restored += "会话 " + result.count + " 段 / " + messageCount + " 条消息"
            }

            // 全部解压成功后才碰本机文件；提交阶段失败会按文件回滚。
            try {
                stagedFiles?.let { staging ->
                    val count = withContext(Dispatchers.IO) {
                        commitStagedFiles(context.filesDir, staging, requireNotNull(backupDir))
                    }
                    restored += "文件 " + count + " 个"
                }
            } catch (t: Throwable) {
                // 文件提交失败：把刚刚导入的会话也撤掉，避免“有会话没附件”。
                withContext(Dispatchers.IO) { ChatStore.deleteImportedConversations(importedIds) }
                throw t
            }
        } finally {
            stagedFiles?.deleteRecursively()
        }

        if (restored.isEmpty()) return Result.failure(IllegalStateException("服务器上没有备份（chat-*.json / files-*.zip）"))
        val mixed = latestChatStamp != null && latestFilesStamp != null &&
            latestChatStamp != latestFilesStamp
        return Result.success(
            "已恢复：" + restored.joinToString(" + ") +
                if (mixed) "（聊天与文件来自不同时间的备份）" else "",
        )
    }

    /** 从 `chat-YYYYMMDD-HHmmss.json` / `files-YYYYMMDD-HHmmss.zip` 提取同一次备份的戳。 */
    internal fun backupStamps(names: List<String>): List<String> = names.mapNotNull { name ->
        Regex("""^(?:chat|files)-(\d{8}-\d{6})\.(?:json|zip)$""")
            .matchEntire(name)
            ?.groupValues
            ?.get(1)
    }.distinct()

    private fun extractZipSafely(root: File, input: InputStream): Int {
        var count = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val target = resolveZipTarget(root, entry.name)
                    ?: throw IOException("备份包含不允许的路径：" + entry.name.take(120))
                target.parentFile?.mkdirs()
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > MAX_ZIP_ENTRY_BYTES) {
                            throw IOException("备份内单个文件过大：" + entry.name.take(120))
                        }
                        if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                            throw IOException("备份解压后体积过大")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                count++
                zip.closeEntry()
            }
        }
        return count
    }

    private data class InstalledFile(val target: File, val backup: File?)

    /**
     * 把 staging 里的文件搬到 App 私有目录。
     *
     * 每个旧文件先移到 cache 的备份目录；任意一步失败就把已替换的文件反向恢复。
     * 这样不会出现“半个新包 + 半个旧包”的附件目录。
     */
    internal fun commitStagedFiles(root: File, staging: File, backupRoot: File): Int {
        if (!staging.exists()) return 0
        backupRoot.mkdirs()
        val canonicalRoot = root.canonicalFile
        val installed = mutableListOf<InstalledFile>()
        try {
            staging.walkTopDown()
                .filter { it.isFile }
                .forEach { staged ->
                    val relative = staged.relativeTo(staging).invariantSeparatorsPath
                    val target = File(canonicalRoot, relative).canonicalFile
                    val checked = runCatching {
                        target.relativeTo(canonicalRoot).invariantSeparatorsPath
                    }.getOrNull()
                    if (checked == null || checked == ".." || checked.startsWith("../")) {
                        throw IOException("恢复路径越界：" + relative.take(120))
                    }
                    if (target.exists() && !target.isFile) {
                        throw IOException("恢复目标不是文件：" + relative.take(120))
                    }
                    target.parentFile?.mkdirs()

                    val backup = if (target.isFile) {
                        val old = File(backupRoot, relative).canonicalFile
                        old.parentFile?.mkdirs()
                        if (!target.renameTo(old)) {
                            target.copyTo(old, overwrite = true)
                            if (!target.delete()) throw IOException("无法备份旧文件：" + relative.take(120))
                        }
                        old
                    } else {
                        null
                    }

                    if (!staged.renameTo(target)) {
                        staged.copyTo(target, overwrite = true)
                        if (!staged.delete()) throw IOException("无法移除临时文件：" + relative.take(120))
                    }
                    installed += InstalledFile(target, backup)
                }
            backupRoot.deleteRecursively()
            return installed.size
        } catch (t: Throwable) {
            installed.asReversed().forEach { item ->
                runCatching { item.target.delete() }
                item.backup?.takeIf { it.isFile }?.let { old ->
                    runCatching { old.renameTo(item.target) }
                }
            }
            throw t
        }
    }

    /** 只允许解压到固定的四个用户文件目录，并用 canonical path 防 ../ / 绝对路径穿越。 */
    internal fun resolveZipTarget(root: File, entryName: String): File? {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.contains('\\')) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(canonicalRoot, entryName).canonicalFile }.getOrNull() ?: return null
        val relative = runCatching {
            target.relativeTo(canonicalRoot).invariantSeparatorsPath
        }.getOrNull() ?: return null
        if (relative == ".." || relative.startsWith("../")) return null
        val allowed = fileDirs.any { dir ->
            relative.startsWith(dir + "/") && relative.length > dir.length + 1
        }
        return target.takeIf { allowed }
    }
}
