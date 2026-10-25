package com.fingertipfuture.hub.update

import android.content.Context
import android.os.Environment
import java.io.File

/** 安装包下载目录与安全清理。 */
object ApkStorage {

    private const val FILE_PREFIX = "hub-"
    private const val PART_SUFFIX = ".part"

    fun downloadDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "apk_downloads").apply { mkdirs() }

    fun clearDownloadedApks(context: Context, keep: File? = null): Int {
        val files = downloadDir(context).listFiles() ?: return 0
        var removed = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            if (!isOurFile(file)) return@forEach
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            if (file.delete()) removed++
        }
        return removed
    }

    private fun isOurFile(file: File): Boolean {
        val name = file.name
        if (!name.startsWith(FILE_PREFIX)) return false
        return name.endsWith(".apk") || name.endsWith(PART_SUFFIX)
    }
}
