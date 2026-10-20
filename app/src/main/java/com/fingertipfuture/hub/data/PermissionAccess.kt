package com.fingertipfuture.hub.data

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R

/**
 * 权限页与聊天页共用的一组“真实状态”查询。
 *
 * 这里只读系统状态、不申请权限；申请由页面上的 ActivityResult 发起。
 */
internal object PermissionAccess {

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun grants(context: Context): SystemPermissionGrants = SystemPermissionGrants(
        location = AttachmentStore.hasLocationPermission(context),
        usageAccess = hasUsageAccess(context),
        calendar = has(context, Manifest.permission.READ_CALENDAR),
        contacts = has(context, Manifest.permission.READ_CONTACTS),
        sms = has(context, Manifest.permission.READ_SMS),
        activity = Build.VERSION.SDK_INT < 29 || has(context, Manifest.permission.ACTIVITY_RECOGNITION),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        camera = has(context, Manifest.permission.CAMERA),
        microphone = has(context, Manifest.permission.RECORD_AUDIO),
    )

    /** 用户已经在系统 / 权限页开启过任意一项能力：新手卡就可以退场了。 */
    fun hasAnyCapability(context: Context): Boolean {
        val g = grants(context)
        // Android 13 以前通知本来就不需要运行时授权，不能因此把新手卡直接判成“已开启”。
        val notificationGranted = Build.VERSION.SDK_INT >= 33 && g.notifications
        // 身体活动权限从 Android 10 才存在；更早的系统不能直接算已开启。
        val activityGranted = Build.VERSION.SDK_INT >= 29 && g.activity
        return g.location || g.usageAccess || g.calendar || g.contacts || g.sms ||
            activityGranted || notificationGranted || g.camera || g.microphone
    }

    fun syncAssistantGrants(context: Context) {
        AssistantStore.syncSystemGrants(
            context,
            grants(context),
            disabled = PermissionChoiceStore.disabledKeys(context),
        )
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
            .onFailure { android.widget.Toast.makeText(context, "打不开系统设置页", android.widget.Toast.LENGTH_SHORT).show() }
    }

    /**
     * 通知权限是真会发一条本机通知，不是只改一个开关。
     * 用户授权后点「通知」会看到系统通知栏里出现一条“权限已开启”。
     */
    fun showPermissionNotification(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PERMISSION,
                    "权限提示",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle("指尖Hub")
            .setContentText("通知权限已开启，之后的重要提示会在这里显示。")
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_PERMISSION_ID, notification)
        }
    }

    private const val CHANNEL_PERMISSION = "hub_permission_status"
    private const val NOTIFICATION_PERMISSION_ID = 1001
}
