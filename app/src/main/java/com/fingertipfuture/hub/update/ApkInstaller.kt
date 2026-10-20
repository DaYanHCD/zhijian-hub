package com.fingertipfuture.hub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 调起系统安装器安装已下载的 APK。
 *
 * Android 7.0 起禁止把 `file://` 交给其它应用，必须通过 FileProvider 暴露 `content://` URI；
 * Android 8.0 起还需要用户为「安装未知应用」单独授权。
 */
object ApkInstaller {

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /**
     * 是否已获得「安装未知应用」权限。
     * Android 8.0 以下无此限制，恒为 true。
     */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * 启动系统安装流程。
     *
     * 调用前应先确认文件存在，并用 [canRequestInstall] 判断权限；
     * 未授权时应先引导用户去 [openUnknownSourcesSettings]。
     */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** 跳转到本应用的「安装未知应用」授权页。 */
    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}