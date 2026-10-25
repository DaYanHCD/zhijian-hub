package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.PermissionAccess
import com.fingertipfuture.hub.data.PermissionChoiceStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.LocalizedText as Text

/**
 * 全局「权限与隐私」：这里是唯一一处能力授权页。
 *
 * 2026-10-19 用户要求：
 * - 删除助手自己的权限页，只保留这一页；
 * - 在这里开启后，所有助手默认获得对应能力；
 * - 权限必须是真实的系统权限，页面只展示系统真实状态；
 * - 普通聊天不会调用这些能力，只有问题明确需要时才读取。
 */
@SuppressLint("InlinedApi")
@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var confirmApproximateOff by remember { mutableStateOf(false) }
    var showPermissionIntro by remember { mutableStateOf(true) }
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionAccess.syncAssistantGrants(context)
                revision++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val grants = remember(revision) { PermissionAccess.grants(context) }
    val perms = AssistantStore.defaultPermissions

    fun applyBoth(transform: (HubAssistant) -> HubAssistant) {
        AssistantStore.setDefaultPermissions(
            context,
            transform(AssistantStore.defaultPermissions),
        )
        AssistantStore.updateAll(context, transform)
    }

    fun describe(full: String, approximate: String): String =
        if (UiPrefs.approximatePermissions) approximate else full

    var pendingKind by remember { mutableStateOf("") }
    fun grantAssistant(kind: String) {
        PermissionChoiceStore.setDisabled(
            context,
            when (kind) {
                "location", "calendar", "contacts", "sms", "mms", "activity" -> kind
                "screen_time" -> "screen_time"
                else -> kind
            },
            false,
        )
        when (kind) {
            "location" -> applyBoth { it.copy(permLocation = true) }
            "calendar" -> applyBoth { it.copy(permCalendar = true) }
            "contacts" -> applyBoth { it.copy(permContacts = true) }
            "sms" -> applyBoth { it.copy(permSms = true) }
            "mms" -> applyBoth { it.copy(permMms = true) }
            "activity" -> applyBoth { it.copy(permActivity = true) }
            "calendar_write" -> applyBoth { it.copy(permCalendarWrite = true) }
            "bluetooth" -> applyBoth { it.copy(permBluetooth = true) }
            "notifications_read" -> applyBoth { it.copy(permNotificationsRead = true) }
            "contacts_write" -> applyBoth { it.copy(permContactsWrite = true) }
            "wifi" -> applyBoth { it.copy(permWifi = true) }
            "media" -> applyBoth { it.copy(permMedia = true) }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val kind = pendingKind
        pendingKind = ""
        val granted = if (kind == "location") {
            PermissionAccess.grants(context).location
        } else {
            result.values.any { it }
        }
        if (granted) {
            grantAssistant(kind)
            if (kind == "notifications") PermissionAccess.showPermissionNotification(context)
        } else {
            context.showLocalizedToast("系统没有授予权限，开关保持关闭")
        }
        revision++
    }

    fun request(kind: String, vararg permissions: String) {
        val ready = when (kind) {
            "location" -> PermissionAccess.grants(context).location
            "notifications" -> PermissionAccess.grants(context).notifications
            "calendar_write" -> PermissionAccess.grants(context).calendarWrite
            "bluetooth" -> PermissionAccess.grants(context).bluetooth
            "contacts_write" -> PermissionAccess.grants(context).contactsWrite
            "notifications_read" -> PermissionAccess.grants(context).notificationRead
            "wifi" -> PermissionAccess.grants(context).wifi
            "media" -> PermissionAccess.grants(context).media
            else -> permissions.all { PermissionAccess.has(context, it) }
        }
        if (ready) {
            grantAssistant(kind)
            if (kind == "notifications") PermissionAccess.showPermissionNotification(context)
            revision++
        } else {
            pendingKind = kind
            launcher.launch(permissions.toList().toTypedArray())
        }
    }

    SettingsPage(title = "权限与隐私", onBack = onBack) {
        SettingsGroup(title = "特殊权限") {
            SettingsItem(
                title = "打开系统应用权限页",
                subtitle = "统一查看和撤销本 App 的全部系统授权",
                icon = R.drawable.ic_settings_location,
                chevron = true,
                topDivider = false,
                onClick = { PermissionAccess.openAppSettings(context) },
            )
            SettingsSwitchRow(
                title = "大致权限",
                subtitle = "隐私数据只提供摘要或脱敏结果，不返回完整内容",
                checked = UiPrefs.approximatePermissions,
                onCheckedChange = { on ->
                    if (on) {
                        UiPrefs.setApproximatePermissions(context, true)
                    } else {
                        confirmApproximateOff = true
                    }
                },
            )
            SettingsSwitchRow(
                title = "允许后台耗电",
                subtitle = if (grants.backgroundPower) {
                    "系统已允许本 App 在后台持续运行"
                } else {
                    "在系统电池优化设置中允许本 App 后台运行"
                },
                checked = perms.permBackgroundPower && grants.backgroundPower,
                onCheckedChange = { on ->
                    PermissionChoiceStore.setDisabled(context, "background_power", !on)
                    if (!on) applyBoth { it.copy(permBackgroundPower = false) }
                    PermissionAccess.requestIgnoreBatteryOptimizations(context)
                },
            )
        }
        SettingsGroup(title = "所有权限") {
            SettingsSwitchRow(
                title = "时间信息",
                subtitle = "读取设备当前的本地日期、时间和星期，仅用于依赖当前时间的问题",
                checked = perms.permTime,
                topDivider = false,
                onCheckedChange = { on -> applyBoth { it.copy(permTime = on) } },
            )
            SettingsSwitchRow(
                title = "日历",
                subtitle = describe(
                    full = "读取设备日历中全部可读的日程标题、开始结束时间和重复事项",
                    approximate = "只返回未来 7 天内的日程数量，不返回标题和时间",
                ),
                checked = perms.permCalendar && grants.calendar,
                onCheckedChange = { on ->
                    if (on) request("calendar", Manifest.permission.READ_CALENDAR)
                    else {
                        PermissionChoiceStore.setDisabled(context, "calendar", true)
                        applyBoth { it.copy(permCalendar = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "写入日历",
                subtitle = "创建包含标题、开始时间、结束时间和说明的日历事项",
                checked = perms.permCalendarWrite && grants.calendarWrite,
                onCheckedChange = { on ->
                    if (on) request("calendar_write", Manifest.permission.WRITE_CALENDAR)
                    else {
                        PermissionChoiceStore.setDisabled(context, "calendar_write", true)
                        applyBoth { it.copy(permCalendarWrite = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "位置",
                subtitle = describe(
                    full = "读取当前地点名称和精确经纬度，仅用于天气、附近和导航",
                    approximate = "只返回当前地点名称，不返回经纬度",
                ),
                checked = perms.permLocation && grants.location,
                onCheckedChange = { on ->
                    if (on) {
                        request("location", Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        PermissionChoiceStore.setDisabled(context, "location", true)
                        applyBoth { it.copy(permLocation = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "今日步数",
                subtitle = describe(
                    full = "读取系统计步器记录的今日完整步数",
                    approximate = "读取今日步数并按百步取整后提供",
                ),
                checked = perms.permActivity && grants.activity,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 29) request("activity", Manifest.permission.ACTIVITY_RECOGNITION)
                        else grantAssistant("activity")
                    } else {
                        PermissionChoiceStore.setDisabled(context, "activity", true)
                        applyBoth { it.copy(permActivity = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "联系人",
                subtitle = describe(
                    full = "读取全部联系人的姓名和电话号码",
                    approximate = "只返回联系人数量，不返回姓名和电话号码",
                ),
                checked = perms.permContacts && grants.contacts,
                onCheckedChange = { on ->
                    if (on) request("contacts", Manifest.permission.READ_CONTACTS)
                    else {
                        PermissionChoiceStore.setDisabled(context, "contacts", true)
                        applyBoth { it.copy(permContacts = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "写入联系人",
                subtitle = "创建包含姓名和电话号码的新联系人",
                checked = perms.permContactsWrite && grants.contactsWrite,
                onCheckedChange = { on ->
                    if (on) request("contacts_write", Manifest.permission.WRITE_CONTACTS)
                    else {
                        PermissionChoiceStore.setDisabled(context, "contacts_write", true)
                        applyBoth { it.copy(permContactsWrite = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "短信",
                subtitle = describe(
                    full = "读取收件箱中全部短信的号码、时间和完整正文",
                    approximate = "只返回短信数量和来源统计，不返回号码与正文",
                ),
                checked = perms.permSms && grants.sms,
                onCheckedChange = { on ->
                    if (on) request("sms", Manifest.permission.READ_SMS)
                    else {
                        PermissionChoiceStore.setDisabled(context, "sms", true)
                        applyBoth { it.copy(permSms = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "发送短信",
                subtitle = "预填号码和正文并打开系统短信编辑页，由你最终确认发送",
                checked = perms.permSmsSend,
                onCheckedChange = { on -> applyBoth { it.copy(permSmsSend = on) } },
            )
            SettingsSwitchRow(
                title = "彩信",
                subtitle = describe(
                    full = "读取全部彩信的主题和时间",
                    approximate = "只返回彩信数量，不返回主题和时间",
                ),
                checked = perms.permMms && grants.sms,
                onCheckedChange = { on ->
                    if (on) request("mms", Manifest.permission.READ_SMS)
                    else {
                        PermissionChoiceStore.setDisabled(context, "mms", true)
                        applyBoth { it.copy(permMms = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "读取已安装的应用",
                subtitle = describe(
                    full = "读取已安装应用的完整名称列表，并区分用户应用和系统应用",
                    approximate = "只返回用户应用和系统应用的数量，不返回名称列表",
                ),
                checked = perms.permInstalledApps,
                onCheckedChange = { on -> applyBoth { it.copy(permInstalledApps = on) } },
            )
            SettingsSwitchRow(
                title = "屏幕时间",
                subtitle = describe(
                    full = "读取今天总使用时长以及每个有使用记录的应用时长",
                    approximate = "只返回今天总使用时长，不返回具体 App 名称和排名",
                ),
                checked = perms.permScreenTime && grants.usageAccess,
                onCheckedChange = { on ->
                    applyBoth { it.copy(permScreenTime = on) }
                    PermissionChoiceStore.setDisabled(context, "screen_time", !on)
                    if (on && !PermissionAccess.hasUsageAccess(context)) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }.onFailure {
                            context.showLocalizedToast("打不开使用情况访问设置")
                        }
                    }
                },
            )
            SettingsSwitchRow(
                title = "通知",
                subtitle = "接收自动化任务结果、权限测试和本 App 的重要提示",
                checked = grants.notifications,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) request("notifications", Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            PermissionAccess.showPermissionNotification(context)
                            revision++
                        }
                    } else {
                        context.showLocalizedToast("通知权限请到系统设置里关闭")
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsSwitchRow(
                title = "通知读取",
                subtitle = describe(
                    full = "读取当前通知的标题、正文、来源应用和发布时间",
                    approximate = "只返回通知数量和来源应用数量，不返回标题与正文",
                ),
                checked = perms.permNotificationsRead && grants.notificationRead,
                onCheckedChange = { on ->
                    PermissionChoiceStore.setDisabled(context, "notifications_read", !on)
                    if (!on) applyBoth { it.copy(permNotificationsRead = false) }
                    PermissionAccess.openNotificationListenerSettings(context)
                },
            )
            SettingsSwitchRow(
                title = "相机",
                subtitle = "使用 App 内相机拍摄照片和扫描二维码",
                checked = grants.camera,
                onCheckedChange = { on ->
                    if (on) request("camera", Manifest.permission.CAMERA)
                    else {
                        context.showLocalizedToast("相机权限请到系统设置里关闭")
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsSwitchRow(
                title = "麦克风",
                subtitle = "录制长按说话和发送语音消息所需的音频",
                checked = grants.microphone,
                onCheckedChange = { on ->
                    if (on) request("microphone", Manifest.permission.RECORD_AUDIO)
                    else {
                        context.showLocalizedToast("麦克风权限请到系统设置里关闭")
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsSwitchRow(
                title = "剪贴板读取",
                subtitle = describe(
                    full = "读取剪贴板中的完整文本内容",
                    approximate = "只返回条目数和字符数，不返回剪贴板正文",
                ),
                checked = perms.permClipboard,
                onCheckedChange = { on -> applyBoth { it.copy(permClipboard = on) } },
            )
            SettingsSwitchRow(
                title = "蓝牙/附近设备",
                subtitle = describe(
                    full = "读取已配对设备的名称和地址，并扫描附近蓝牙设备",
                    approximate = "只返回设备数量和已配对数量，并隐藏名称与地址",
                ),
                checked = perms.permBluetooth && grants.bluetooth,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            request("bluetooth", Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else grantAssistant("bluetooth")
                    } else {
                        PermissionChoiceStore.setDisabled(context, "bluetooth", true)
                        applyBoth { it.copy(permBluetooth = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "Wi-Fi/网络详情",
                subtitle = describe(
                    full = "读取网络类型、SSID、BSSID、速率、频段和附近 Wi-Fi 热点",
                    approximate = "只返回网络类型、Wi-Fi 开关状态和热点数量",
                ),
                checked = perms.permWifi && grants.wifi,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            request(
                                "wifi",
                                Manifest.permission.NEARBY_WIFI_DEVICES,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        } else {
                            request("wifi", Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    } else {
                        PermissionChoiceStore.setDisabled(context, "wifi", true)
                        applyBoth { it.copy(permWifi = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "悬浮窗与屏幕读取",
                subtitle = "管理悬浮窗授权；屏幕捕获仍需每次由系统弹窗确认",
                checked = perms.permOverlayScreen && grants.overlay,
                onCheckedChange = { on ->
                    PermissionChoiceStore.setDisabled(context, "overlay_screen", !on)
                    if (!on) applyBoth { it.copy(permOverlayScreen = false) }
                    PermissionAccess.openOverlaySettings(context)
                },
            )
            SettingsSwitchRow(
                title = "媒体读取",
                subtitle = when {
                    grants.media && !PermissionAccess.hasFullMediaAccess(context) ->
                        "只读取你在系统弹窗中选择的图片和视频"
                    else -> describe(
                        full = "读取全部图片、视频和音频的文件名、大小、时间和媒体库地址",
                        approximate = "只返回图片、视频和音频各自的数量",
                    )
                },
                checked = perms.permMedia && grants.media,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val mediaPermissions = buildList {
                                add(Manifest.permission.READ_MEDIA_IMAGES)
                                add(Manifest.permission.READ_MEDIA_VIDEO)
                                add(Manifest.permission.READ_MEDIA_AUDIO)
                                if (Build.VERSION.SDK_INT >= 34) {
                                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                }
                            }
                            request(
                                "media",
                                *mediaPermissions.toTypedArray(),
                            )
                        } else {
                            request("media", Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    } else {
                        PermissionChoiceStore.setDisabled(context, "media", true)
                        applyBoth { it.copy(permMedia = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "精确闹钟",
                subtitle = "允许自动化任务在设定时间精确触发，不受普通闹钟延迟影响",
                checked = grants.exactAlarm,
                onCheckedChange = {
                    if (grants.exactAlarm) {
                        context.showLocalizedToast(
                            "如需关闭，请在系统“闹钟和提醒”权限页操作",
                        )
                    }
                    PermissionAccess.requestExactAlarmAccess(context)
                },
            )
        }
    }

    if (showPermissionIntro) {
        AlertDialog(
            onDismissRequest = { showPermissionIntro = false },
            title = { Text("权限说明") },
            text = {
                Text(
                    "权限分为系统授权和软件授权。如果在这里授权后没有反应，" +
                        "请确认系统应用权限页也已授权；仍无反应时，请通过「联系作者」反馈。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionIntro = false }) {
                    Text("知道了")
                }
            },
        )
    }

    if (confirmApproximateOff) {
        AlertDialog(
            onDismissRequest = { confirmApproximateOff = false },
            title = { Text("关闭大致权限？") },
            text = {
                Text(
                    "关闭后，助手可以读取更完整的设备与隐私数据。" +
                        "这项保护建议保持开启，请确认后再关闭。",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmApproximateOff = false }) {
                    Text("保持开启")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        UiPrefs.setApproximatePermissions(context, false)
                        confirmApproximateOff = false
                    },
                ) {
                    Text("仍然关闭", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
