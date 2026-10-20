package com.fingertipfuture.hub.ui.provider

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.chat.ChatMetrics
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDialogCard

/**
 * 「提供商」整页（列表）。
 *
 * 2026-10-13 用户第 N 次给它定形：
 * - 顶栏**真的悬浮**在列表上面（返回 + 标题合成一个胶囊，右边一颗"添加"），列表从它**下面**滑过去；
 * - 状态栏透明，内容一直铺到屏幕最上面；
 * - 搜索框、大标题都删了（标题并进了左边那颗胶囊）。
 *
 * 内置厂商全部列在这里（名字 / 接口地址 / 接口路径 / 官方 Logo 都预设好），
 * 卡片右边是启用开关，点卡片进配置页，⋮ 里是编辑 / 删除。
 */
@Composable
internal fun ProvidersScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onCreateEmpty: () -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HubProvider?>(null) }
    val providers = ProviderStore.providers
    val currentId = ProviderStore.currentProvider?.id
    val statusBarTop = statusBarTopPadding()

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            // 列表铺满整屏（连状态栏那块也铺），顶部的空当留给悬浮栏
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_HORIZONTAL_MARGIN,
                end = PAGE_HORIZONTAL_MARGIN,
                top = statusBarTop + FLOATING_BAR_SPACE,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    isCurrent = provider.id == currentId,
                    onOpen = { onOpenProvider(provider.id) },
                    onToggle = { enabled ->
                        // 提醒只在"云上的厂商"才有意义 —— 本机 / 局域网的服务（Ollama…）本来就不要 Key
                        if (enabled && provider.apiKey.isBlank() && !isLocalUrl(provider.baseUrl)) {
                            Toast.makeText(context, R.string.provider_enable_need_key, Toast.LENGTH_SHORT).show()
                        }
                        ProviderStore.setEnabled(context, provider.id, enabled)
                    },
                    onDelete = { pendingDelete = provider },
                )
            }
        }

        // 状态栏渐隐：列表从悬浮栏下面穿过时慢慢变淡（照「指尖」的设置页）
        TopFadeLayer(modifier = Modifier.align(Alignment.TopCenter))

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = ChatMetrics.TOP_BAR_SIDE_MARGIN,
                    end = ChatMetrics.TOP_BAR_SIDE_MARGIN,
                    top = statusBarTop + FLOATING_BAR_TOP_MARGIN,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingBackBar(title = stringResource(R.string.providers_title), onBack = onBack)
            Spacer(Modifier.weight(1f))
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = stringResource(R.string.provider_add),
                onClick = onCreateEmpty,
            )
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.hubDialogCard(),
            title = { Text(stringResource(R.string.provider_delete)) },
            text = { Text(stringResource(R.string.provider_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    ProviderStore.remove(context, target.id)
                }) {
                    Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** 一张供应商卡片。禁用时整张变红（照参考图）。 */
@Composable
private fun ProviderCard(
    provider: HubProvider,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (provider.enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        color = container,
        shape = shape,
        tonalElevation = 0.dp,
        // 阴影自己画（hubDropShadow）：和设置页列表同一条 x0 / y4 / 模糊 4
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .clip(shape)
            .hubCardBorder(shape)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderAvatar(provider, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name.ifBlank { stringResource(R.string.provider_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (provider.baseUrl.isNotBlank()) {
                    Text(
                        text = provider.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(
                        text = stringResource(
                            if (provider.enabled) R.string.provider_badge_enabled else R.string.provider_badge_disabled,
                        ),
                        color = when {
                            provider.enabled && dark -> Color(0xFF69D39A)
                            provider.enabled -> Color(0xFF1B8A4B)
                            dark -> Color(0xFFFFB177)
                            else -> Color(0xFFB3541E)
                        },
                    )
                    Badge(
                        text = stringResource(R.string.provider_models_count, provider.models.size),
                        color = if (dark) Color(0xFF8FB7FF) else Color(0xFF2A6BD4),
                    )
                    if (isCurrent) {
                        Badge(
                            text = stringResource(R.string.provider_current),
                            color = if (dark) Color(0xFFC0B1FF) else Color(0xFF5B4BD4),
                        )
                    }
                }
            }

            Switch(checked = provider.enabled, onCheckedChange = onToggle)

            Box {
                MaterialSymbol(
                    resId = R.drawable.ic_more_vert,
                    contentDescription = stringResource(R.string.chat_action_more),
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { menu = true }
                        .padding(6.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.provider_edit)) },
                        onClick = {
                            menu = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 供应商头像：白底圆 + 官方 Logo。
 *
 * 官方彩色标志（DeepSeek、Gemini、千问…）直接画原色；本来就单色的（OpenAI、Kimi、Groq、xAI、Ollama）
 * 用前景色画。没有 Logo 的自定义供应商退回"名字首字"。
 */
@Composable
internal fun ProviderAvatar(provider: HubProvider, size: Dp) {
    val icon = brandIcon(provider.logo)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            MaterialSymbol(
                resId = icon.res,
                contentDescription = null,
                size = size * 0.62f,
                tint = if (icon.colored) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = provider.name.trim().take(1).ifBlank { "?" },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

/** 卡片上的一枚小徽标：`启用` / `3 个模型` / `当前在用`。 */
@Composable
internal fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * 本机 / 局域网地址：这类服务（Ollama、LM Studio…）通常连 Key 都不要，
 * 所以"还没填 Key"那句提醒就别对着它们弹。
 */
private fun isLocalUrl(raw: String): Boolean {
    val host = raw.trim().substringAfter("://", raw).substringBefore('/').substringBefore(':').lowercase()
    return host.isEmpty() ||
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "0.0.0.0" ||
        host.startsWith("10.") ||
        host.startsWith("192.168.") ||
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host)
}
