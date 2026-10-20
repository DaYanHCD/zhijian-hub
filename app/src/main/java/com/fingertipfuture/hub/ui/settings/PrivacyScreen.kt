package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.PermissionAccess
import com.fingertipfuture.hub.data.PermissionChoiceStore
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
@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
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
            Toast.makeText(context, "系统没有授予权限，开关保持关闭", Toast.LENGTH_SHORT).show()
        }
        revision++
    }

    fun request(kind: String, vararg permissions: String) {
        val ready = when (kind) {
            "location" -> PermissionAccess.grants(context).location
            "notifications" -> PermissionAccess.grants(context).notifications
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
        SettingsGroup(title = "说明") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "只有你在问题里明确提到位置、日程、短信、步数等内容时，助手才会读取对应数据。普通聊天和翻译不会调用任何权限。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsGroup(title = "助手可读取的信息") {
            SettingsSwitchRow(
                title = "时间信息",
                subtitle = "只在询问日期、星期、几点时提供当前时间",
                checked = perms.permTime,
                topDivider = false,
                onCheckedChange = { on -> applyBoth { it.copy(permTime = on) } },
            )
            SettingsSwitchRow(
                title = "位置",
                subtitle = if (grants.location) "系统已授权 · 仅天气、附近、导航等问题使用" else "需要系统定位权限",
                checked = perms.permLocation && grants.location,
                onCheckedChange = { on ->
                    if (on) {
                        request(
                            "location",
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    } else {
                        PermissionChoiceStore.setDisabled(context, "location", true)
                        applyBoth { it.copy(permLocation = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "屏幕时间",
                subtitle = if (grants.usageAccess) "系统已授权「使用情况访问」" else "需要系统「使用情况访问」权限",
                checked = perms.permScreenTime && grants.usageAccess,
                onCheckedChange = { on ->
                    applyBoth { it.copy(permScreenTime = on) }
                    PermissionChoiceStore.setDisabled(context, "screen_time", !on)
                    if (on && !PermissionAccess.hasUsageAccess(context)) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }.onFailure {
                            Toast.makeText(context, "打不开使用情况访问设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            SettingsSwitchRow(
                title = "今日步数",
                subtitle = if (grants.activity) "系统已授权「身体活动」" else "读取系统计步器，需要「身体活动」权限",
                checked = perms.permActivity && grants.activity,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 29) {
                            request("activity", Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            grantAssistant("activity")
                        }
                    } else {
                        PermissionChoiceStore.setDisabled(context, "activity", true)
                        applyBoth { it.copy(permActivity = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "读取已安装的应用",
                subtitle = "只在你问“装了什么应用 / 有没有可疑软件”时读取",
                checked = perms.permInstalledApps,
                onCheckedChange = { on -> applyBoth { it.copy(permInstalledApps = on) } },
            )
        }

        SettingsGroup(title = "个人数据") {
            SettingsSwitchRow(
                title = "日历",
                subtitle = "读取未来 7 天的日程",
                checked = perms.permCalendar && grants.calendar,
                topDivider = false,
                onCheckedChange = { on ->
                    if (on) {
                        request("calendar", Manifest.permission.READ_CALENDAR)
                    } else {
                        PermissionChoiceStore.setDisabled(context, "calendar", true)
                        applyBoth { it.copy(permCalendar = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "联系人",
                subtitle = "读取联系人姓名与号码",
                checked = perms.permContacts && grants.contacts,
                onCheckedChange = { on ->
                    if (on) {
                        request("contacts", Manifest.permission.READ_CONTACTS)
                    } else {
                        PermissionChoiceStore.setDisabled(context, "contacts", true)
                        applyBoth { it.copy(permContacts = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "短信",
                subtitle = "读取最近短信",
                checked = perms.permSms && grants.sms,
                onCheckedChange = { on ->
                    if (on) {
                        request("sms", Manifest.permission.READ_SMS)
                    } else {
                        PermissionChoiceStore.setDisabled(context, "sms", true)
                        applyBoth { it.copy(permSms = false) }
                    }
                },
            )
            SettingsSwitchRow(
                title = "彩信",
                subtitle = "读取最近彩信的主题与时间",
                checked = perms.permMms && grants.sms,
                onCheckedChange = { on ->
                    if (on) {
                        request("mms", Manifest.permission.READ_SMS)
                    } else {
                        PermissionChoiceStore.setDisabled(context, "mms", true)
                        applyBoth { it.copy(permMms = false) }
                    }
                },
            )
        }

        SettingsGroup(title = "应用权限") {
            SettingsSwitchRow(
                title = "通知",
                subtitle = "授权后会发一条测试通知，确认通知真的能到",
                checked = grants.notifications,
                topDivider = false,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            request("notifications", Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionAccess.showPermissionNotification(context)
                            revision++
                        }
                    } else {
                        Toast.makeText(context, "通知权限请到系统设置里关闭", Toast.LENGTH_SHORT).show()
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsSwitchRow(
                title = "相机",
                subtitle = "用于 App 内的拍照页",
                checked = grants.camera,
                onCheckedChange = { on ->
                    if (on) request("camera", Manifest.permission.CAMERA)
                    else {
                        Toast.makeText(context, "相机权限请到系统设置里关闭", Toast.LENGTH_SHORT).show()
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsSwitchRow(
                title = "麦克风",
                subtitle = "用于长按说话与语音消息",
                checked = grants.microphone,
                onCheckedChange = { on ->
                    if (on) request("microphone", Manifest.permission.RECORD_AUDIO)
                    else {
                        Toast.makeText(context, "麦克风权限请到系统设置里关闭", Toast.LENGTH_SHORT).show()
                        PermissionAccess.openAppSettings(context)
                    }
                },
            )
            SettingsItem(
                title = "打开系统应用权限页",
                subtitle = "在上面修改本 App 的全部系统授权",
                icon = R.drawable.ic_settings_location,
                chevron = true,
                onClick = { PermissionAccess.openAppSettings(context) },
            )
        }
    }
}
