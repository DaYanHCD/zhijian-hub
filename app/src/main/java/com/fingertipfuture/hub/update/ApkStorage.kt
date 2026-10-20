package com.fingertipfuture.hub.update

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 安装包的存放与清理。
 *
 * 关于「什么时候才能删」：
 * 拉起系统安装器后**不能立刻删除**安装包 —— 安装器是异步读取该文件的，
 * 立刻删会导致安装失败。因此清理只发生在两个安全时机：
 * 1. 应用启动时（升级成功后系统会杀掉应用并以新版本重启，旧包已完成使命）
 * 2. 下一次下载之前（清掉上一次的残留）
 */
object ApkStorage {

    /** 下载文件的固定前缀，用于识别「这是本应用下载的包」，避免误删目录内其它文件。 */
    private const val FILE_PREFIX = "hub-"

    private const val PART_SUFFIX = ".part"

    /**
     * 安装包下载目录。
     *
     * 用应用的外部私有目录：无需存储权限，且应用被卸载时会随之清除。
     * 注意 FileProvider 的路径声明必须与这里对应（见 res/xml/file_paths.xml）。
     */
    fun downloadDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "apk_downloads").apply { mkdirs() }

    /**
     * 清理历史安装包与下载残留。
     *
     * 只处理 `hub-*` 开头的文件，不会碰同目录下的其它内容。
     *
     * @param keep 需要保留的文件（正在安装的那个）；传 null 表示全部清理
     * @return 实际删除的文件数量
     */
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
