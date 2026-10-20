package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.ModelPickerDialog
import com.fingertipfuture.hub.ui.chat.THINKING_DEPTHS
import com.fingertipfuture.hub.ui.chat.ThinkingDepthDialog

/** 跳到本 App 在系统里的设置页（权限、通知都在那儿）。 */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    val ok = runCatching { context.startActivity(intent) }.isSuccess
    if (!ok) Toast.makeText(context, "打不开系统设置页", Toast.LENGTH_SHORT).show()
}

/**
 * 默认模型设置：聊天 / 快速 / 图像 / 压缩模型 + 思考强度。
 *
 * 2026-10-16 用户要求「各个选择都真的能选」：四个模型行**都能点开选**（快速 / 图像 / 压缩存到
 * [ModelRoleStore]，默认聊天模型还是走「提供商」那套全局选中项）。
 */
@Composable
internal fun ModelDefaultsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showChat by remember { mutableStateOf(false) }
    var showFast by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(false) }
    var showCompact by remember { mutableStateOf(false) }
    var showDepth by remember { mutableStateOf(false) }

    val provider = ProviderStore.currentProvider
    // 右侧只显示模型名（2026-10-17 用户要求），供应商名在选模型时自然能看到
    val chatLabel = if (provider == null) "未配置" else ProviderStore.currentModel
    val depth = ProviderStore.chatSettings.thinkingDepth.coerceIn(0, THINKING_DEPTHS.lastIndex)

    SettingsPage(title = "默认模型设置", onBack = onBack) {
        SettingsGroup(title = "模型") {
            SettingsItem(
                title = "默认聊天模型",
                subtitle = "对话使用的模型",
                icon = R.drawable.ic_provider,
                value = chatLabel,
                chevron = true,
                topDivider = false,
                onClick = {
                    if (provider == null) {
                        Toast.makeText(context, "先去「提供商」配一家", Toast.LENGTH_SHORT).show()
                    } else {
                        showChat = true
                    }
                },
            )
            SettingsItem(
                title = "快速模型",
                subtitle = "生成会话标题与后台任务",
                icon = R.drawable.ic_regenerate,
                value = ModelRoleStore.label(ModelRoleStore.fast),
                chevron = true,
                onClick = { showFast = true },
            )
            SettingsItem(
                title = "图像模型",
                subtitle = "主模型不能看图时交给它",
                icon = R.drawable.ic_settings_image,
                value = ModelRoleStore.label(ModelRoleStore.image),
                chevron = true,
                onClick = { showImage = true },
            )
            SettingsItem(
                title = "压缩模型",
                subtitle = "对话太长时用它压缩历史",
                icon = R.drawable.ic_settings_depth,
                value = ModelRoleStore.label(ModelRoleStore.compact),
                chevron = true,
                onClick = { showCompact = true },
            )
            SettingsItem(
                title = "思考强度",
                subtitle = "当前模型的思考强度",
                icon = R.drawable.ic_psychology,
                value = THINKING_DEPTHS[depth].name,
                chevron = true,
                onClick = { showDepth = true },
            )
        }
    }

    if (showChat) ModelPickerDialog(onDismiss = { showChat = false })
    if (showFast) {
        ModelPickerDialog(
            onDismiss = { showFast = false },
            onPick = { providerId, model -> ModelRoleStore.setFast(context, ModelRoleStore.Role(providerId, model)) },
            highlight = ModelRoleStore.fast.providerId to ModelRoleStore.fast.model,
        )
    }
    if (showImage) {
        ModelPickerDialog(
            onDismiss = { showImage = false },
            onPick = { providerId, model -> ModelRoleStore.setImage(context, ModelRoleStore.Role(providerId, model)) },
            highlight = ModelRoleStore.image.providerId to ModelRoleStore.image.model,
        )
    }
    if (showCompact) {
        ModelPickerDialog(
            onDismiss = { showCompact = false },
            onPick = { providerId, model -> ModelRoleStore.setCompact(context, ModelRoleStore.Role(providerId, model)) },
            highlight = ModelRoleStore.compact.providerId to ModelRoleStore.compact.model,
        )
    }
    if (showDepth) {
        ThinkingDepthDialog(
            current = depth,
            onPick = { value ->
                ProviderStore.saveChatSettings(
                    context,
                    ProviderStore.chatSettings.copy(thinkingDepth = value),
                )
            },
            onDismiss = { showDepth = false },
        )
    }
}

