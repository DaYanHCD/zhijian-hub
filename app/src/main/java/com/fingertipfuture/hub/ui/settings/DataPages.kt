package com.fingertipfuture.hub.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.core.AppInfo
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.BackupRunner
import com.fingertipfuture.hub.data.BackupStore
import com.fingertipfuture.hub.data.ChatArchive
import com.fingertipfuture.hub.data.EmojiExtensionStore
import com.fingertipfuture.hub.data.WebDavClient
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.VoicePlayer
import com.fingertipfuture.hub.ui.chat.ImagePreviewOverlay
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.AnimatedSegmentedTabs
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.ui.provider.FloatingCircleAction
import com.fingertipfuture.hub.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一个「确定 / 取消」的确认框（删附件这种不可恢复的操作先问一句）。 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onConfirm) { Text(confirmText) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private enum class BackupSection { LOCAL, WEBDAV }

/** 数据备份：通过顶部切换栏在本机备份和 WebDAV 备份之间切换。 */
@Composable
internal fun BackupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = BackupStore.webdav
    val servers = BackupStore.servers
    var section by remember { mutableStateOf(BackupSection.LOCAL) }
    var busy by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    val lastTime = remember(BackupStore.lastBackupAt) {
        if (BackupStore.lastBackupAt == 0L) "还没备份过" else timeFormat.format(Date(BackupStore.lastBackupAt))
    }
    val scopeLabels = listOf("只备份聊天记录", "只备份文件", "聊天记录 + 文件")
    val scopeValue = when (BackupStore.scope) {
        BackupStore.SCOPE_FILES -> scopeLabels[1]
        BackupStore.SCOPE_BOTH -> scopeLabels[2]
        else -> scopeLabels[0]
    }

    fun chooseScope(index: Int) {
        BackupStore.setScope(
            context,
            when (index) {
                1 -> BackupStore.SCOPE_FILES
                2 -> BackupStore.SCOPE_BOTH
                else -> BackupStore.SCOPE_CHAT
            },
        )
    }

    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = AttachmentStore.readText(context, uri)
                    val imported = withContext(Dispatchers.IO) {
                        text?.let { ChatArchive.decodeArchive(it) }
                    }
                    if (imported.isNullOrEmpty()) {
                        context.showLocalizedToast(
                            "导入失败：这不是本 App 导出的对话文件（要选 .json）",
                            Toast.LENGTH_LONG,
                        )
                    } else {
                        val count = withContext(Dispatchers.IO) { ChatStore.importArchive(imported) }
                        context.showLocalizedToast("已导入 " + count + " 段会话")
                    }
                } catch (t: Throwable) {
                    context.showLocalizedToast(
                        "导入失败：" + (t.message ?: "未知错误"),
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }
    }

    fun export() {
        scope.launch {
            try {
                val archive = withContext(Dispatchers.IO) { ChatStore.exportArchive() }
                if (archive.isEmpty()) {
                    context.showLocalizedToast("还没有可以导出的对话")
                    return@launch
                }
                val messageCount = archive.sumOf { it.messages.size }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
                val name = "指尖Hub-对话-" + stamp + ".json"
                val json = withContext(Dispatchers.IO) { ChatArchive.encodeArchive(archive) }
                val (path, uri) = AttachmentStore.exportText(
                    context = context,
                    text = json,
                    fileName = name,
                    mime = "application/json",
                )
                context.showLocalizedToast(
                    "已导出 " + archive.size + " 段会话（" + messageCount + " 条消息）→ " + path,
                    Toast.LENGTH_LONG,
                )
                if (uri != null) {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                AttachmentStore.shareIntent(uri, "application/json"),
                                "分享对话",
                            ),
                        )
                    }
                }
            } catch (t: Throwable) {
                context.showLocalizedToast(
                    "导出失败：" + (t.message ?: "未知错误"),
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    SettingsPage(
        title = "数据备份",
        onBack = onBack,
        topAction = if (section == BackupSection.WEBDAV) {
            {
                FloatingCircleAction(
                    icon = R.drawable.ic_add,
                    contentDescription = "添加服务器",
                    onClick = { BackupStore.addServer(context) },
                )
            }
        } else {
            null
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BackupSectionTabs(selected = section, onSelect = { section = it })

            if (section == BackupSection.LOCAL) {
                SettingsGroup(title = "本机备份") {
                SettingsSlidingChoiceRow(
                    title = "备份什么",
                    value = scopeValue,
                    options = scopeLabels,
                    topDivider = false,
                    onPick = ::chooseScope,
                )
                SettingsItem(
                    title = "导出文件",
                    subtitle = "导出本机会话文件，可保存或分享",
                    icon = R.drawable.ic_settings_download,
                    onClick = { export() },
                )
                SettingsItem(
                    title = "导入备份文件",
                    subtitle = "选择本 App 导出的备份文件，按会话分别导入",
                    icon = R.drawable.ic_settings_import,
                    onClick = { pickArchive.launch(arrayOf("application/json")) },
                )
            }
            } else {
            SettingsGroup(title = "WebDAV 备份") {
                SettingsSlidingChoiceRow(
                    title = "备份什么",
                    value = scopeValue,
                    options = scopeLabels,
                    topDivider = false,
                    onPick = ::chooseScope,
                )
                SettingsItem(
                    title = "备份时间",
                    icon = R.drawable.ic_history,
                    value = lastTime,
                    topDivider = true,
                )
            }

            SettingsGroup(title = "服务器列表（" + servers.size + "）") {
                if (servers.isEmpty()) {
                    SettingsItem(
                        title = "还没有服务器",
                        subtitle = "点右上角按钮添加一台 WebDAV",
                        icon = R.drawable.ic_regenerate,
                        topDivider = false,
                    )
                }
                servers.forEachIndexed { index, server ->
                    SettingsItem(
                        title = server.title,
                        subtitle = when {
                            !server.configured -> "还没填地址或路径"
                            server.path.isNotBlank() -> server.url + " · " + server.path
                            else -> server.url
                        },
                        icon = R.drawable.ic_regenerate,
                        value = if (server.id == backup.id) "当前" else null,
                        topDivider = index != 0,
                        onClick = { BackupStore.setCurrentServer(context, server.id) },
                    )
                }
            }

            SettingsGroup(title = "服务器添加") {
                WebDavFieldListRow(
                    title = "WebDAV 服务器地址",
                    value = backup.url,
                    placeholder = "https://example.com/dav",
                    topDivider = false,
                    onValueChange = { BackupStore.save(context, backup.copy(url = it)) },
                )
                WebDavFieldListRow(
                    title = "用户名",
                    value = backup.user,
                    placeholder = "用户名",
                    onValueChange = { BackupStore.save(context, backup.copy(user = it)) },
                )
                WebDavFieldListRow(
                    title = "密码",
                    value = backup.password,
                    placeholder = "密码",
                    password = true,
                    passwordVisible = passwordVisible,
                    onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                    onValueChange = { BackupStore.save(context, backup.copy(password = it)) },
                )
                WebDavFieldListRow(
                    title = "路径",
                    value = backup.path,
                    placeholder = "指尖Hub/备份",
                    onValueChange = { BackupStore.save(context, backup.copy(path = it)) },
                )
            }

            SettingsGroup(title = "WebDAV 操作") {
                SettingsItem(
                    title = if (busy == "test") "正在测试…" else "测试连接",
                    icon = R.drawable.ic_refresh,
                    topDivider = false,
                    onClick = {
                        if (busy.isBlank()) {
                            busy = "test"
                            scope.launch {
                                val result = WebDavClient.test(BackupStore.webdav)
                                busy = ""
                                context.showLocalizedToast(
                                    result.getOrElse { "没连上：" + (it.message ?: "未知错误") },
                                    Toast.LENGTH_LONG,
                                )
                            }
                        }
                    },
                )
                SettingsItem(
                    title = if (busy == "backup") "正在备份…" else "立即备份",
                    icon = R.drawable.ic_settings_download,
                    onClick = {
                        if (busy.isBlank()) {
                            busy = "backup"
                            scope.launch {
                                val result = BackupRunner.backup(context)
                                busy = ""
                                context.showLocalizedToast(
                                    result.getOrElse { "备份失败：" + (it.message ?: "未知错误") },
                                    Toast.LENGTH_LONG,
                                )
                            }
                        }
                    },
                )
                SettingsItem(
                    title = if (busy == "restore") "正在恢复…" else "恢复备份",
                    icon = R.drawable.ic_settings_import,
                    onClick = {
                        if (busy.isBlank()) {
                            busy = "restore"
                            scope.launch {
                                val result = BackupRunner.restore(context)
                                busy = ""
                                context.showLocalizedToast(
                                    result.getOrElse { "恢复失败：" + (it.message ?: "未知错误") },
                                    Toast.LENGTH_LONG,
                                )
                            }
                        }
                    },
                )
                if (backup.id.isNotBlank()) {
                    SettingsItem(
                        title = "删除服务器",
                        subtitle = "只删除本机保存的服务器配置，不删除云端备份",
                        icon = R.drawable.ic_delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { BackupStore.removeServer(context, backup.id) },
                    )
                }
            }
            }
        }
    }
}

/** 与提供商编辑页一致的顶部切换栏。 */
@Composable
private fun BackupSectionTabs(
    selected: BackupSection,
    onSelect: (BackupSection) -> Unit,
) {
    AnimatedSegmentedTabs(
        labels = listOf("本机备份", "WebDAV 备份"),
        selectedIndex = BackupSection.entries.indexOf(selected),
        onSelect = { index -> onSelect(BackupSection.entries[index]) },
    )
}

/** “服务器添加”大列表里的单行：字段标签 + 输入框，共享同一张卡片。 */
@Composable
private fun WebDavFieldListRow(
    title: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: () -> Unit = {},
    topDivider: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                visualTransformation = if (password && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                trailingIcon = if (password) {
                    {
                        MaterialSymbol(
                            resId = if (passwordVisible) {
                                R.drawable.ic_visibility_slash_simple
                            } else {
                                R.drawable.ic_visibility_simple
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            size = 22.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onPasswordVisibilityChange)
                                .padding(6.dp),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal enum class StorageAttachmentCategory(
    val title: String,
    val emptyTitle: String,
) {
    IMAGES("图片", "还没有图片"),
    VOICES("语音", "还没有语音"),
    FILES("文件", "还没有文件"),
}

private data class StoragePageSnapshot(
    val files: List<AttachmentStore.StoredAttachment>,
    val appStorage: AppInfo.StorageUsage,
    val phoneTotalBytes: Long,
    val phoneFreeBytes: Long,
)

/** 页面销毁后仍保留上个快照，再次进入时先显示稳定数值，再静默刷新。 */
private var lastStorageSnapshot: StoragePageSnapshot? = null

/**
 * 存储与扩展总览。
 *
 * 软件占用和可下载扩展使用完整卡片；图片、语音、文件只显示汇总列表，
 * 进入各自明细页后才展示具体内容。
 */
@Composable
internal fun DataManagementScreen(
    onBack: () -> Unit,
    onOpenAttachmentCategory: (StorageAttachmentCategory) -> Unit,
) {
    val context = LocalContext.current
    var storageRefresh by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(lastStorageSnapshot) }
    LaunchedEffect(storageRefresh) {
        val loaded = withContext(Dispatchers.IO) {
            val stat = StatFs(context.filesDir.absolutePath)
            StoragePageSnapshot(
                files = AttachmentStore.listAttachments(context),
                appStorage = AppInfo.storageUsage(context),
                phoneTotalBytes = stat.totalBytes,
                phoneFreeBytes = stat.availableBytes,
            )
        }
        lastStorageSnapshot = loaded
        snapshot = loaded
    }
    val current = snapshot
    val files = current?.files.orEmpty()
    val images = files.filter { it.isImage }
    val voices = files.filter { it.isVoice }
    val regularFiles = files.filterNot { it.isImage || it.isVoice }
    val extensionBytes = remember(EmojiExtensionStore.isInstalled, storageRefresh) {
        if (EmojiExtensionStore.isInstalled) {
            EmojiExtensionStore.installedSizeBytes(context)
        } else {
            0L
        }
    }

    SettingsPage(title = "存储与扩展", onBack = onBack) {
        if (current == null) {
            SoftwareStorageLoadingCard()
        } else {
            SoftwareStorageCard(
                usage = current.appStorage,
                phoneTotalBytes = current.phoneTotalBytes,
                phoneFreeBytes = current.phoneFreeBytes,
                extensionBytes = extensionBytes,
            )
        }

        DownloadableExtensionsSection(
            onStorageChanged = { storageRefresh++ },
        )

        SettingsGroup(title = "本机附件") {
            StorageSummaryItem(
                title = "图片",
                icon = R.drawable.ic_attach_lite_image_v2,
                files = images,
                loading = current == null,
                onClick = { onOpenAttachmentCategory(StorageAttachmentCategory.IMAGES) },
            )
            StorageSummaryItem(
                title = "语音",
                icon = R.drawable.ic_voice_speaker,
                files = voices,
                loading = current == null,
                onClick = { onOpenAttachmentCategory(StorageAttachmentCategory.VOICES) },
            )
            StorageSummaryItem(
                title = "文件",
                icon = R.drawable.ic_settings_file,
                files = regularFiles,
                loading = current == null,
                onClick = { onOpenAttachmentCategory(StorageAttachmentCategory.FILES) },
            )
        }
    }
}

@Composable
private fun SoftwareStorageLoadingCard() {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .fillMaxWidth()
            .height(330.dp),
        shape = shape,
        color = settingsCardColor(),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Text(
                text = "本软件已用空间",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(24.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "正在读取空间信息…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 本软件占用卡：不是列表行，直接展示安装包、附件和其他数据的构成。 */
@Composable
private fun SoftwareStorageCard(
    usage: AppInfo.StorageUsage,
    phoneTotalBytes: Long,
    phoneFreeBytes: Long,
    extensionBytes: Long,
) {
    val shape = RoundedCornerShape(22.dp)
    val appColor = Color(0xFF0F9D68)
    val otherColor = Color(0xFFFFAA00)
    val freeColor = Color(0xFFB8BEC9)
    val otherUsedBytes = (phoneTotalBytes - phoneFreeBytes - usage.totalBytes)
        .coerceAtLeast(0L)
    val appWeight = usage.totalBytes.toFloat().coerceAtLeast(
        if (phoneTotalBytes > 0L) phoneTotalBytes / 100f else 1f,
    )
    val otherWeight = otherUsedBytes.toFloat().coerceAtLeast(1f)
    val freeWeight = phoneFreeBytes.toFloat().coerceAtLeast(1f)
    val phonePercent = if (phoneTotalBytes > 0L) {
        String.format(
            Locale.US,
            "%.2f",
            usage.totalBytes.toDouble() / phoneTotalBytes.toDouble() * 100.0,
        )
    } else {
        "--"
    }
    val dataBytes = (usage.totalBytes - usage.packageBytes - extensionBytes).coerceAtLeast(0L)

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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_settings_file,
                        contentDescription = null,
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "本软件已用空间",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(17.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .weight(appWeight)
                        .height(16.dp)
                        .background(appColor),
                )
                Box(
                    modifier = Modifier
                        .weight(otherWeight)
                        .height(16.dp)
                        .background(otherColor.copy(alpha = 0.88f)),
                )
                Box(
                    modifier = Modifier
                        .weight(freeWeight)
                        .height(16.dp)
                        .background(freeColor),
                )
            }
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppStorageLegend(
                    color = appColor,
                    label = "本软件已用",
                    modifier = Modifier.weight(1f),
                )
                AppStorageLegend(
                    color = otherColor.copy(alpha = 0.88f),
                    label = "其他已用",
                    modifier = Modifier.weight(1f),
                )
                AppStorageLegend(
                    color = freeColor,
                    label = "手机剩余",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatBytes(usage.totalBytes),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "占手机 $phonePercent% 储存空间",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                StorageBreakdownValue(
                    label = "安装包",
                    value = formatBytes(usage.packageBytes),
                    modifier = Modifier.weight(1f),
                )
                StorageBreakdownValue(
                    label = "数据",
                    value = formatBytes(dataBytes),
                    modifier = Modifier.weight(1f),
                )
                StorageBreakdownValue(
                    label = "扩展",
                    value = formatBytes(extensionBytes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StorageBreakdownValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AppStorageLegend(
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
private fun StorageSummaryItem(
    title: String,
    icon: Int,
    files: List<AttachmentStore.StoredAttachment>,
    loading: Boolean,
    onClick: () -> Unit,
) {
    SettingsItem(
        title = title,
        icon = icon,
        value = if (loading) {
            "正在读取…"
        } else {
            "${files.size} 个 · ${formatBytes(files.sumOf { it.sizeBytes })}"
        },
        chevron = true,
        onClick = onClick,
    )
}

/** 图片 / 语音 / 文件第二界面：只在这里展示具体内容。 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StorageAttachmentDetailScreen(
    category: StorageAttachmentCategory,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<AttachmentStore.StoredAttachment?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<HubAttachment?>(null) }
    var allFiles by remember { mutableStateOf<List<AttachmentStore.StoredAttachment>>(emptyList()) }
    LaunchedEffect(tick) {
        allFiles = withContext(Dispatchers.IO) { AttachmentStore.listAttachments(context) }
    }
    val files = when (category) {
        StorageAttachmentCategory.IMAGES -> allFiles.filter { it.isImage }
        StorageAttachmentCategory.VOICES -> allFiles.filter { it.isVoice }
        StorageAttachmentCategory.FILES -> allFiles.filterNot { it.isImage || it.isVoice }
    }

    SettingsPage(
        title = category.title,
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_delete,
                contentDescription = "删除全部${category.title}",
                onClick = {
                    if (files.isEmpty()) {
                        context.showLocalizedToast("还没有${category.title}")
                    } else {
                        confirmDeleteAll = true
                    }
                },
            )
        },
    ) {
        if (files.isEmpty()) {
            SettingsGroup {
                SettingsItem(
                    title = category.emptyTitle,
                    subtitle = "这里只显示已经保存在本机的附件",
                    icon = R.drawable.ic_settings_file,
                    topDivider = false,
                )
            }
        } else {
            StorageAttachmentSection(
                title = "${files.size} 个 · ${formatBytes(files.sumOf { it.sizeBytes })}",
                files = files,
                onClick = { file ->
                    when (category) {
                        StorageAttachmentCategory.IMAGES -> {
                            preview = HubAttachment(
                                kind = HubAttachment.KIND_IMAGE,
                                path = file.path,
                                name = file.name,
                                size = file.sizeBytes,
                                mime = storageMimeType(file),
                            )
                        }
                        StorageAttachmentCategory.VOICES -> VoicePlayer.toggle(file.path)
                        StorageAttachmentCategory.FILES -> Unit
                    }
                },
                onDelete = { pendingDelete = it },
            )
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "删除全部${category.title}？",
            message = "会删除 ${files.size} 个文件（共 ${formatBytes(files.sumOf { it.sizeBytes })}），" +
                "并清除聊天中的对应引用。",
            confirmText = "全部删除",
            onConfirm = {
                confirmDeleteAll = false
                val targets = files
                scope.launch {
                    withContext(Dispatchers.IO) {
                        targets.forEach { ChatStore.deleteAttachment(context, it.path) }
                    }
                    ChatStore.reloadCurrent()
                    preview = null
                    tick++
                    context.showLocalizedToast("已删除全部${category.title}")
                }
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除这个附件？",
            message = "删除后聊天中的引用也会一并清除。",
            confirmText = "确认",
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        ChatStore.deleteAttachment(context, target.path)
                    }
                    if (preview?.path == target.path) preview = null
                    tick++
                    context.showLocalizedToast(
                        if (deleted) "附件已删除" else "附件删除失败",
                    )
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    preview?.let { attachment ->
        ImagePreviewOverlay(attachment = attachment, onClose = { preview = null })
    }
}

/** 图片、语音、文件共用的双列卡片区。 */
@Composable
private fun StorageAttachmentSection(
    title: String,
    files: List<AttachmentStore.StoredAttachment>,
    onClick: (AttachmentStore.StoredAttachment) -> Unit,
    onDelete: (AttachmentStore.StoredAttachment) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        files.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { file ->
                    StorageAttachmentCard(
                        file = file,
                        modifier = Modifier.weight(1f),
                        onClick = { onClick(file) },
                        onDelete = { onDelete(file) },
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StorageAttachmentCard(
    file: AttachmentStore.StoredAttachment,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val playing = file.isVoice && VoicePlayer.playingPath == file.path
    val bitmap = remember(file.path, file.modifiedAt, file.sizeBytes) {
        if (!file.isImage) {
            null
        } else {
            runCatching {
                val source = java.io.File(file.path)
                if (!source.isFile) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var scale = 1
                while (bounds.outWidth > 0 && bounds.outWidth / scale > 720) scale *= 2
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply { inSampleSize = scale },
                )
            }.getOrNull()
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }

    Surface(
        color = settingsCardColor(),
        shape = shape,
        shadowElevation = 0.dp,
        modifier = modifier
            .hubCardBorder(shape)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete,
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    MaterialSymbol(
                        resId = if (file.isVoice) R.drawable.ic_voice_speaker else R.drawable.ic_settings_file,
                        contentDescription = null,
                        size = 52.dp,
                        tint = if (playing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_delete,
                        contentDescription = "删除附件",
                        size = 21.dp,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (file.isImage) file.name else file.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = storageMimeType(file) + " · " + formatBytes(file.sizeBytes),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun storageMimeType(file: AttachmentStore.StoredAttachment): String {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.isImage -> when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
        file.isVoice -> when (extension) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "audio/mp4"
        }
        extension == "pdf" -> "application/pdf"
        extension in setOf("txt", "md", "csv", "log") -> "text/plain"
        extension in setOf("zip", "rar", "7z") -> "application/zip"
        extension == "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
