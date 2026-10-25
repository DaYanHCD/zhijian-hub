package com.fingertipfuture.hub.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.ArrayDeque

/**
 * 本机安装包的体积信息，「关于」里显示「安装包大小」用。
 *
 * 量的是**本机已安装的 APK**，不是服务器清单里的 apkSize：清单可能拉不到（离线），
 * 而且清单记的是上传时的大小，装到手机上后不一定完全一致。
 */
object AppInfo {

    data class StorageUsage(
        val packageBytes: Long,
        val internalBytes: Long,
        val externalBytes: Long,
    ) {
        val totalBytes: Long
            get() = packageBytes + internalBytes + externalBytes
    }

    /** 已安装 APK 的总体积（字节）。取不到时返回 0，由界面回落到「未知大小」。 */
    fun installedSizeBytes(context: Context): Long {
        val pm = context.packageManager
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val app = info.applicationInfo ?: return@runCatching 0L
            // split APK 安装时体积分散在多个文件里，base 与所有拆分包都要算上
            buildList {
                app.sourceDir?.let(::add)
                app.splitSourceDirs?.forEach { add(it) }
            }
                .distinct()
                .sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }
        }.getOrDefault(0L)
    }

    /**
     * 本软件实际占用的空间：安装包 + 应用私有目录 + 应用外部私有目录。
     *
     * 这里统计的是整个 App，而不是手机全盘。缓存也计入，方便用户在「存储管理」
     * 直接看到软件当前真实占用；附件只是其中的一部分。
     */
    fun storageUsage(context: Context): StorageUsage {
        val packageBytes = installedSizeBytes(context)
        val dataDir = context.applicationInfo.dataDir
            ?.let(::File)
        val externalRoot = context.getExternalFilesDir(null)?.parentFile
        val persistentRoot = StorageLocations.persistentRoot()
        val internalBytes = dataDir?.let(::directoryBytes) ?: 0L
        val externalBytes = listOfNotNull(externalRoot, persistentRoot)
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sumOf(::directoryBytes)
        return StorageUsage(
            packageBytes = packageBytes,
            internalBytes = internalBytes,
            externalBytes = externalBytes,
        )
    }

    private fun directoryBytes(root: File): Long {
        if (!root.exists()) return 0L
        var total = 0L
        val pending = ArrayDeque<File>().apply { add(root) }
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            children.forEach { child ->
                if (child.isDirectory) {
                    pending.add(child)
                } else if (child.isFile) {
                    total += runCatching { child.length() }.getOrDefault(0L)
                }
            }
        }
        return total
    }
}
