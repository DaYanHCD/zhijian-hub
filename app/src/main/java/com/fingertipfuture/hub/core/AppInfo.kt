package com.fingertipfuture.hub.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * 本机安装包的体积信息，「关于」里显示「安装包大小」用。
 *
 * 量的是**本机已安装的 APK**，不是服务器清单里的 apkSize：清单可能拉不到（离线），
 * 而且清单记的是上传时的大小，装到手机上后不一定完全一致。
 */
object AppInfo {

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
}