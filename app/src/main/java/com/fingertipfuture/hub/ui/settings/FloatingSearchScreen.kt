package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.floating.FloatingBubbleStyle
import com.fingertipfuture.hub.floating.FloatingSearchSession
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubOuterShadow
import com.fingertipfuture.hub.ui.component.settingsCardColor

/**
 * 悬浮搜题入口页。
 *
 * 权限顺序有意固定为：悬浮窗 → 通知 → 系统屏幕捕获。屏幕捕获只交给前台服务，
 * 不在 Activity 内偷偷保存画面；用户在悬浮球上的每一次点击才触发一次截图。
 */
@Composable
internal fun FloatingSearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingStartAfterOverlay by remember { mutableStateOf(false) }
    var showImageModelDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(context) {
        FloatingSearchSession.refresh(context)
        overlayAllowed = Settings.canDrawOverlays(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            FloatingSearchSession.start(context, result.resultCode, data)
            context.showLocalizedToast("悬浮搜题已启动，点悬浮球框选题目")
        } else {
            context.showLocalizedToast("没有授权屏幕捕获，悬浮搜题未启动")
        }
    }

    val startCapture = {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 悬浮搜题跨多个 App，必须请求整屏；Android 14+ 默认会建议“仅共享一个应用”。
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay(),
            )
        } else {
            manager.createScreenCaptureIntent()
        }
        captureLauncher.launch(intent)
    }

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // 通知权限被拒也不阻断核心功能；Android 仍会保留前台服务本身。
        startCapture()
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (pendingStartAfterOverlay) {
            pendingStartAfterOverlay = false
            if (overlayAllowed) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startCapture()
                }
            } else {
                context.showLocalizedToast("悬浮窗权限未开启")
            }
        }
    }

    fun begin() {
        if (
            ModelRoleStore.route(ModelRoleStore.image) == null &&
            ProviderStore.currentProvider == null
        ) {
            showImageModelDialog = true
            return
        }
        overlayAllowed = Settings.canDrawOverlays(context)
        if (!overlayAllowed) {
            pendingStartAfterOverlay = true
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startCapture()
        }
    }

    SettingsPage(title = "悬浮搜题", onBack = onBack) {
        FloatingSearchHero(
            running = FloatingSearchSession.isRunning,
            style = FloatingSearchSession.bubbleStyle,
            enabled = true,
            onToggle = { enabled ->
                if (enabled) {
                    begin()
                } else {
                    FloatingSearchSession.stop(context)
                    context.showLocalizedToast("悬浮搜题已停止")
                }
            },
            onStyle = { FloatingSearchSession.setBubbleStyle(context, it) },
        )

        FloatingSearchOperationGuide()

        FloatingSearchPrivacy()

        Spacer(Modifier.height(20.dp))
    }

    if (showImageModelDialog) {
        AlertDialog(
            onDismissRequest = { showImageModelDialog = false },
            title = { Text("还没有可用的图像模型") },
            text = {
                Text(
                    "悬浮搜题需要把框选图片交给支持图片输入的模型。请先在" +
                        "「默认模型设置 › 图像模型」中选择一个模型，或者在提供商中启用并选择支持看图的默认模型。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showImageModelDialog = false }) { Text("知道了") }
            },
        )
    }
}

/** 参考图里的主开关卡：标题、开关和两种悬浮球外观放在同一张卡里。 */
@Composable
private fun FloatingSearchHero(
    running: Boolean,
    style: FloatingBubbleStyle,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStyle: (FloatingBubbleStyle) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val cardColor = settingsCardColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = cardColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "悬浮搜题",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "点击悬浮球，框选题目后交给图像模型直接解答",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Switch(
                    checked = running,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FloatingBubbleStyleCard(
                    title = "标准",
                    subtitle = "不透明",
                    icon = R.drawable.ic_floating_search,
                    selected = style == FloatingBubbleStyle.STANDARD,
                    modifier = Modifier.weight(1f),
                    onClick = { onStyle(FloatingBubbleStyle.STANDARD) },
                )
                FloatingBubbleStyleCard(
                    title = "半透明",
                    subtitle = "轻量显示",
                    icon = R.drawable.ic_visibility_simple,
                    selected = style == FloatingBubbleStyle.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { onStyle(FloatingBubbleStyle.LIGHT) },
                )
            }
        }
    }
}

@Composable
private fun FloatingBubbleStyleCard(
    title: String,
    subtitle: String,
    icon: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = if (selected) {
            accent.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (selected) accent.copy(alpha = 0.15f) else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}

/** 参考图底部的“操作方式”三格。 */
@Composable
private fun FloatingSearchOperationGuide() {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        modifier = Modifier
            .hubOuterShadow(shape)
            .fillMaxWidth(),
        shape = shape,
        color = settingsCardColor(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "操作方式",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FloatingOperationCard(
                    icon = R.drawable.ic_floating_search,
                    title = "点击",
                    subtitle = "截取屏幕",
                    modifier = Modifier.weight(1f),
                )
                FloatingOperationCard(
                    icon = R.drawable.ic_psychology,
                    title = "框选",
                    subtitle = "选中题目",
                    modifier = Modifier.weight(1f),
                )
                FloatingOperationCard(
                    icon = R.drawable.ic_bolt,
                    title = "解题",
                    subtitle = "模型作答",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FloatingOperationCard(
                    icon = R.drawable.ic_floating_search,
                    title = "双击",
                    subtitle = "关闭悬浮球",
                    modifier = Modifier.weight(1f),
                )
                FloatingOperationCard(
                    icon = R.drawable.ic_stop,
                    title = "长按",
                    subtitle = "关闭悬浮球",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FloatingOperationCard(
    icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MaterialSymbol(
                resId = icon,
                contentDescription = null,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 悬浮搜题的数据边界。只描述当前代码真正发生的行为，避免“绝不上传”这类错误承诺。 */
@Composable
private fun FloatingSearchPrivacy() {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        modifier = Modifier
            .hubOuterShadow(shape)
            .fillMaxWidth(),
        shape = shape,
        color = settingsCardColor(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_info,
                        contentDescription = null,
                        size = 21.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "关于隐私",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "只处理你主动框选的内容",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            FloatingPrivacyPoint("只有点击悬浮球时才截取当前屏幕的一帧，不会后台持续录屏。")
            FloatingPrivacyPoint("圈选过程只在本机完成。点“开始解题”后，仅框选图片会发送给图像模型。")
            FloatingPrivacyPoint(
                "图片直连你配置的模型服务，不经过指尖Hub服务器；解题结束后删除本机临时文件。",
            )
            FloatingPrivacyPoint(
                "模型服务可能按其隐私政策处理图片；请先遮住姓名、账号、验证码等敏感内容。",
            )
        }
    }
}

@Composable
private fun FloatingPrivacyPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

