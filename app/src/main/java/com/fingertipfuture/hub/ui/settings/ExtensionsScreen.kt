package com.fingertipfuture.hub.ui.settings

import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.EmojiExtensionStore
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

private data class ExtensionStorageSummary(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val extensionBytes: Long = 0L,
) {
    val otherUsedBytes: Long
        get() = max(0L, totalBytes - freeBytes - extensionBytes)
}

/**
 * 合并页里的「可下载的扩展」模块。
 *
 * 这里不是列表行：扩展的下载、启用开关和删除操作都直接展开在卡片内。
 */
@Composable
internal fun DownloadableExtensionsSection(
    onStorageChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    SettingsGroup(title = "可下载的扩展") {
        ExtensionPackagePanel(
            installed = EmojiExtensionStore.isInstalled,
            downloading = EmojiExtensionStore.isDownloading,
            progress = EmojiExtensionStore.downloadProgress,
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            onDownload = {
                scope.launch {
                    EmojiExtensionStore.downloadAndInstall(context).fold(
                        onSuccess = {
                            onStorageChanged()
                            context.showLocalizedToast("动态表情已下载并自动启用")
                        },
                        onFailure = { error ->
                            context.showLocalizedToast(
                                "下载失败：" + (error.message ?: "未知错误"),
                            )
                        },
                    )
                }
            },
            onDelete = { confirmDelete = true },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    text = "删除动态表情扩展？",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = "会删除本机约 ${formatBytes(EmojiExtensionStore.INSTALLED_BYTES)} 的动画资源。" +
                        "静态表情不受影响，之后仍可重新下载。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            if (EmojiExtensionStore.delete(context)) {
                                onStorageChanged()
                                context.showLocalizedToast("动态表情扩展已删除")
                            } else {
                                context.showLocalizedToast("扩展正在下载，暂时不能删除")
                            }
                        },
                    ) {
                        Text("删除")
                    }
                }
            },
        )
    }
}

/**
 * 数据与扩展：按需组件统一放在这里。
 *
 * 当前只有一个动态表情扩展。静态表情始终可用；下载、启用、删除都由用户明确控制。
 */
@Composable
internal fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf(ExtensionStorageSummary()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken, EmojiExtensionStore.isInstalled) {
        summary = withContext(Dispatchers.IO) {
            val stat = StatFs(context.filesDir.absolutePath)
            ExtensionStorageSummary(
                totalBytes = stat.totalBytes,
                freeBytes = stat.availableBytes,
                extensionBytes = EmojiExtensionStore.installedSizeBytes(context),
            )
        }
    }

    SettingsPage(title = "扩展管理", onBack = onBack) {
        ExtensionStorageCard(summary)

        SettingsGroup(title = "可下载扩展") {
            ExtensionPackagePanel(
                installed = EmojiExtensionStore.isInstalled,
                downloading = EmojiExtensionStore.isDownloading,
                progress = EmojiExtensionStore.downloadProgress,
                supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                onDownload = {
                    scope.launch {
                        EmojiExtensionStore.downloadAndInstall(context).fold(
                            onSuccess = {
                                refreshToken++
                                context.showLocalizedToast("动态表情已下载并自动启用")
                            },
                            onFailure = { error ->
                                context.showLocalizedToast(
                                    "下载失败：" + (error.message ?: "未知错误"),
                                )
                            },
                        )
                    }
                },
                onDelete = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    text = "删除动态表情扩展？",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = "会删除本机约 ${formatBytes(EmojiExtensionStore.INSTALLED_BYTES)} 的动画资源。" +
                        "静态表情不受影响，之后仍可重新下载。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            if (EmojiExtensionStore.delete(context)) {
                                refreshToken++
                                context.showLocalizedToast("动态表情扩展已删除")
                            } else {
                                context.showLocalizedToast("扩展正在下载，暂时不能删除")
                            }
                        },
                    ) {
                        Text("删除")
                    }
                }
            },
        )
    }
}

@Composable
private fun ExtensionStorageCard(summary: ExtensionStorageSummary) {
    val shape = RoundedCornerShape(22.dp)
    val extensionColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.tertiary
    val freeColor = MaterialTheme.colorScheme.surfaceVariant
    val extensionVisible = EmojiExtensionStore.isInstalled &&
        summary.extensionBytes > 0L &&
        summary.totalBytes > 0L
    val extensionWeight = if (extensionVisible) {
        // 扩展相对整机通常很小；至少保留 1% 的可视宽度，避免图例有色条却看不见。
        summary.extensionBytes.toFloat().coerceAtLeast(summary.totalBytes / 100f)
    } else {
        0f
    }
    val otherWeight = summary.otherUsedBytes.toFloat().coerceAtLeast(1f)
    val freeWeight = summary.freeBytes.toFloat().coerceAtLeast(1f)
    val displayedBytes = if (EmojiExtensionStore.isInstalled) {
        summary.extensionBytes
    } else {
        EmojiExtensionStore.INSTALLED_BYTES
    }
    val percentage = phoneStoragePercent(displayedBytes, summary.totalBytes)

    Surface(
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .fillMaxWidth(),
        shape = shape,
        color = settingsCardColor(),
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
                        resId = R.drawable.ic_settings_file,
                        contentDescription = null,
                        size = 21.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "手机存储",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "扩展按需下载，只占用实际安装的空间",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp)),
            ) {
                if (extensionWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(extensionWeight)
                            .height(18.dp)
                            .background(extensionColor),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(otherWeight)
                        .height(18.dp)
                        .background(otherColor.copy(alpha = 0.9f)),
                )
                Box(
                    modifier = Modifier
                        .weight(freeWeight)
                        .height(18.dp)
                        .background(freeColor),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StorageLegend(
                    color = extensionColor,
                    label = "扩展已用",
                    modifier = Modifier.weight(1f),
                )
                StorageLegend(
                    color = otherColor.copy(alpha = 0.9f),
                    label = "其他已用",
                    modifier = Modifier.weight(1f),
                )
                StorageLegend(
                    color = freeColor,
                    label = "剩余可用",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "动态表情扩展",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (EmojiExtensionStore.isInstalled) {
                    formatBytes(summary.extensionBytes)
                } else {
                    "未安装"
                },
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "占用手机 $percentage% 储存空间",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageLegend(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExtensionPackagePanel(
    installed: Boolean,
    downloading: Boolean,
    progress: Int,
    supported: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_tab_smile,
                    contentDescription = null,
                    size = 27.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "动态表情播放",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    SettingsTag(
                        when {
                            downloading -> "下载中"
                            installed -> "已启用"
                            else -> "可下载"
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Noto Emoji 动画资源",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "静态表情默认可用。动态扩展只影响单表情消息播放，" +
                "未安装或删除时会自动回退到静态表情。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!supported) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "当前系统版本低于 Android 9，系统不能播放动画 WebP。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (downloading) {
            Spacer(Modifier.height(15.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "正在下载并校验 · $progress%",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (installed) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_delete,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(7.dp))
                Text("删除扩展", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Spacer(Modifier.height(15.dp))
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_settings_download,
                    contentDescription = null,
                    size = 18.dp,
                )
                Spacer(Modifier.width(7.dp))
                Text("下载动态表情")
            }
        }
    }
}

private fun phoneStoragePercent(bytes: Long, totalBytes: Long): String =
    if (bytes <= 0L || totalBytes <= 0L) {
        "--"
    } else {
        String.format(
            Locale.US,
            "%.2f",
            bytes.toDouble() / totalBytes.toDouble() * 100.0,
        )
    }
