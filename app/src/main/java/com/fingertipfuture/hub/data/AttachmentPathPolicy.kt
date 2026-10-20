package com.fingertipfuture.hub.data

import java.io.File

/**
 * 导入存档时的附件路径策略。
 *
 * 存档来自用户选择的外部文件，里面的绝对路径不能直接信任。只接受 App 私有
 * `attachments/` 与 `voice/` 下的文件；跨设备时按文件名重新映射；找不到就把路径清空，
 * 避免后续把设备上的任意文件读进模型。
 */
internal object AttachmentPathPolicy {

    private val allowedDirs = setOf("attachments", "voice")

    fun ownedPath(filesDir: File, rawPath: String): String? {
        if (rawPath.isBlank()) return null
        val root = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrNull()
            ?: return null
        val top = relative.substringBefore('/')
        if (top !in allowedDirs || relative == top || relative.startsWith("../")) return null
        return file.takeIf { it.isFile }?.absolutePath
    }

    fun remapImported(filesDir: File, rawPath: String): String? {
        ownedPath(filesDir, rawPath)?.let { return it }
        if (rawPath.isBlank()) return null
        val name = runCatching { File(rawPath).name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return allowedDirs
            .asSequence()
            .map { File(filesDir, "$it/$name") }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }
}
