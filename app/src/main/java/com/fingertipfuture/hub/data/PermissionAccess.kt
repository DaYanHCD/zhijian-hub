package com.fingertipfuture.hub.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.localizedUiString
import com.fingertipfuture.hub.core.showLocalizedToast

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

    @SuppressLint("InlinedApi")
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
        exactAlarm = canScheduleExactAlarms(context),
        calendarWrite = has(context, Manifest.permission.WRITE_CALENDAR),
        bluetooth = hasBluetoothPermissions(context),
        notificationRead = hasNotificationListenerAccess(context),
        contactsWrite = has(context, Manifest.permission.WRITE_CONTACTS),
        backgroundPower = isIgnoringBatteryOptimizations(context),
        wifi = hasWifiDetails(context),
        overlay = Settings.canDrawOverlays(context),
        media = hasMediaAccess(context),
    )

    /** 用户已经在系统 / 权限页开启过任意一项能力：新手卡就可以退场了。 */
    fun hasAnyCapability(context: Context): Boolean {
        val g = grants(context)
        // Android 13 以前通知本来就不需要运行时授权，不能因此把新手卡直接判成“已开启”。
        val notificationGranted = Build.VERSION.SDK_INT >= 33 && g.notifications
        // 身体活动权限从 Android 10 才存在；更早的系统不能直接算已开启。
        val activityGranted = Build.VERSION.SDK_INT >= 29 && g.activity
        return g.location || g.usageAccess || g.calendar || g.contacts || g.sms ||
            activityGranted || notificationGranted || g.camera || g.microphone || g.exactAlarm ||
            g.calendarWrite || g.bluetooth || g.notificationRead || g.contactsWrite ||
            g.wifi || g.overlay || g.media
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
            .onFailure { context.showLocalizedToast("打不开系统设置页") }
    }

    fun hasBluetoothPermissions(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            has(context, Manifest.permission.BLUETOOTH_SCAN) &&
                has(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }

    fun hasNotificationListenerAccess(context: Context): Boolean =
        Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty().contains(context.packageName, ignoreCase = true)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(direct)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                context.showLocalizedToast("打不开后台耗电设置")
            }
        }
    }

    fun hasWifiDetails(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            has(context, Manifest.permission.NEARBY_WIFI_DEVICES)) &&
            has(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasFullMediaAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(context, Manifest.permission.READ_MEDIA_IMAGES) &&
                has(context, Manifest.permission.READ_MEDIA_VIDEO) &&
                has(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasMediaAccess(context: Context): Boolean {
        if (hasFullMediaAccess(context)) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun openNotificationListenerSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            context.showLocalizedToast("打不开通知使用权设置")
        }
    }

    fun openOverlaySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            context.showLocalizedToast("打不开悬浮窗设置")
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    fun requestExactAlarmAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.showLocalizedToast("打不开精确闹钟设置页")
            }
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
                    context.localizedUiString(R.string.permission_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(context.localizedUiString(R.string.app_name))
            .setContentText(
                context.localizedUiString(R.string.permission_notification_enabled),
            )
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_PERMISSION_ID, notification)
        }
    }

    private const val CHANNEL_PERMISSION = "hub_permission_status"
    private const val NOTIFICATION_PERMISSION_ID = 1001
}
