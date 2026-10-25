package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
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
    if (!ok) context.showLocalizedToast("打不开系统设置页")
}

/**
 * 默认模型设置：聊天 / 快速 / 图像理解 / 压缩模型 + 思考强度。
 *
 * 2026-10-16 用户要求「各个选择都真的能选」：三个角色模型行**都能点开选**（快速 / 图像理解 / 压缩存到
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
        SettingsGroup(title = null) {
            DefaultModelChoiceRow(
                title = "默认聊天模型",
                subtitle = "对话使用的模型",
                value = if (provider == null) "选择模型" else chatLabel,
                topDivider = false,
                onClick = {
                    if (provider == null) {
                        context.showLocalizedToast("先去「提供商」配一家")
                    } else {
                        showChat = true
                    }
                },
            )
            DefaultModelChoiceRow(
                title = "快速模型",
                subtitle = "生成会话标题与后台任务",
                value = ModelRoleStore.label(ModelRoleStore.fast),
                onClick = { showFast = true },
            )
            DefaultModelChoiceRow(
                title = "图像理解模型",
                subtitle = "主模型不能看图时交给它",
                value = ModelRoleStore.label(ModelRoleStore.image),
                onClick = { showImage = true },
            )
            DefaultModelChoiceRow(
                title = "压缩模型",
                subtitle = "对话太长时用它压缩历史",
                value = ModelRoleStore.label(ModelRoleStore.compact),
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

@Composable
private fun DefaultModelChoiceRow(
    title: String,
    subtitle: String,
    value: String,
    topDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = capsuleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    // 点击范围和涟漪都限制在模型名称胶囊内；如果挂在外层列表行上，
                    // 水波会按整块矩形区域扩散。
                    .clip(capsuleShape)
                    .clickable(onClick = onClick),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    com.fingertipfuture.hub.ui.component.MaterialSymbol(
                        resId = R.drawable.ic_expand,
                        contentDescription = null,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

