package com.fingertipfuture.hub.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.BackupRunner
import com.fingertipfuture.hub.data.BackupStore
import com.fingertipfuture.hub.data.ChatArchive
import com.fingertipfuture.hub.data.ChatDatabase
import com.fingertipfuture.hub.data.WebDavClient
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.ui.chat.ChatRuntime
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
        modifier = Modifier.hubDialogCard(),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一行文字输入的弹框（WebDAV 那几个字段用）。 */
@Composable
private fun TextRowDialog(
    title: String,
    initial: String,
    hint: String = "",
    numeric: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 数据备份：本机导出 / 导入 + WebDAV 备份（能用的）。 */
@Composable
internal fun BackupScreen(
    onBack: () -> Unit,
    onOpenWebDav: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = BackupStore.webdav
    val servers = BackupStore.servers
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    val lastTime = remember(BackupStore.lastBackupAt) {
        if (BackupStore.lastBackupAt == 0L) "还没备份过" else timeFormat.format(Date(BackupStore.lastBackupAt))
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
                        Toast.makeText(
                            context,
                            "导入失败：这不是本 App 导出的对话文件（要选 .json）",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        val count = withContext(Dispatchers.IO) { ChatStore.importArchive(imported) }
                        Toast.makeText(context, "已导入 " + count + " 段会话", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "导入失败：" + (t.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun export() {
        scope.launch {
            try {
                val archive = withContext(Dispatchers.IO) { ChatStore.exportArchive() }
                if (archive.isEmpty()) {
                    Toast.makeText(context, "还没有可以导出的对话", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(
                    context,
                    "已导出 " + archive.size + " 段会话（" + messageCount + " 条消息）→ " + path,
                    Toast.LENGTH_LONG,
                ).show()
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
                Toast.makeText(context, "导出失败：" + (t.message ?: "未知错误"), Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsPage(title = "数据备份", onBack = onBack) {
        SettingsGroup(title = "本机") {
            SettingsItem(
                title = "导出全部会话",
                subtitle = "保存为 .json，可分享（含全部会话和标题；不含附件文件）",
                icon = R.drawable.ic_settings_download,
                topDivider = false,
                onClick = { export() },
            )
            SettingsItem(
                title = "导入会话",
                subtitle = "选一个本 App 导出的 .json，按会话分别导入",
                icon = R.drawable.ic_settings_import,
                onClick = { pickArchive.launch(arrayOf("application/json")) },
            )
        }
        SettingsGroup(title = "WebDAV 服务器（" + servers.size + "）") {
            if (servers.isEmpty()) {
                SettingsItem(
                    title = "还没有服务器",
                    subtitle = "添加一台 WebDAV，用来备份聊天记录与文件",
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
                    chevron = true,
                    topDivider = index != 0,
                    onClick = {
                        BackupStore.setCurrentServer(context, server.id)
                        onOpenWebDav()
                    },
                )
            }
            SettingsItem(
                title = "添加服务器",
                subtitle = "可以保存多台 WebDAV，随时切换",
                icon = R.drawable.ic_add,
                topDivider = servers.isNotEmpty(),
                onClick = {
                    BackupStore.addServer(context)
                    onOpenWebDav()
                },
            )
            SettingsItem(
                title = "上次备份",
                icon = R.drawable.ic_history,
                value = lastTime,
                topDivider = true,
            )
        }
    }
}

/** WebDAV 备份页：地址 / 账号 / 路径 / 备份内容 / 测试连接 / 备份 / 恢复。 */
@Composable
internal fun WebDavBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    val current = BackupStore.webdav
    val scopeLabels = listOf("只备份聊天记录", "只备份文件", "聊天记录 + 文件")

    SettingsPage(title = current.title, onBack = onBack) {
        SettingsGroup(title = "最新备份") {
            SettingsItem(
                title = "备份时间",
                icon = R.drawable.ic_history,
                value = if (BackupStore.lastBackupAt == 0L) {
                    "还没备份过"
                } else {
                    timeFormat.format(Date(BackupStore.lastBackupAt))
                },
                topDivider = false,
            )
            if (BackupStore.lastBackupName.isNotBlank()) {
                SettingsItem(
                    title = "备份文件",
                    subtitle = BackupStore.lastBackupName,
                    icon = R.drawable.ic_settings_file,
                )
            }
        }

        SettingsGroup(title = "服务器") {
            SettingsItem(
                title = "名称",
                subtitle = "只在本机显示，方便区分多台服务器",
                icon = R.drawable.ic_edit,
                value = current.name.ifBlank { "未命名" },
                chevron = true,
                topDivider = false,
                onClick = { editing = "name" },
            )
            SettingsItem(
                title = "服务器地址",
                subtitle = "WebDAV 根地址，如 https://dav.jianguoyun.com/dav",
                icon = R.drawable.ic_regenerate,
                value = current.url.ifBlank { "未填" },
                chevron = true,
                topDivider = true,
                onClick = { editing = "url" },
            )
            SettingsItem(
                title = "用户名",
                icon = R.drawable.ic_edit,
                value = current.user.ifBlank { "未填" },
                chevron = true,
                onClick = { editing = "user" },
            )
            SettingsItem(
                title = "密码",
                subtitle = "坚果云这类要用「应用密码」",
                icon = R.drawable.ic_edit,
                value = if (current.password.isBlank()) "未填" else "••••••",
                chevron = true,
                onClick = { editing = "password" },
            )
            SettingsItem(
                title = "路径",
                subtitle = "远程目录，如 指尖Hub/备份",
                icon = R.drawable.ic_create_folder,
                value = current.path.ifBlank { "未填" },
                chevron = true,
                onClick = { editing = "path" },
            )
        }

        SettingsGroup(title = "备份内容") {
            SettingsChoiceRow(
                title = "备份什么",
                value = when (BackupStore.scope) {
                    BackupStore.SCOPE_FILES -> scopeLabels[1]
                    BackupStore.SCOPE_BOTH -> scopeLabels[2]
                    else -> scopeLabels[0]
                },
                options = scopeLabels,
                topDivider = false,
                onPick = { index ->
                    BackupStore.setScope(
                        context,
                        when (index) {
                            1 -> BackupStore.SCOPE_FILES
                            2 -> BackupStore.SCOPE_BOTH
                            else -> BackupStore.SCOPE_CHAT
                        },
                    )
                },
            )
        }

        SettingsGroup(title = "操作") {
            SettingsItem(
                title = if (busy == "test") "正在测试…" else "测试连接",
                subtitle = "检查地址、账号和路径",
                icon = R.drawable.ic_refresh,
                topDivider = false,
                onClick = {
                    if (busy.isBlank()) {
                        busy = "test"
                        scope.launch {
                            val result = WebDavClient.test(BackupStore.webdav)
                            busy = ""
                            Toast.makeText(
                                context,
                                result.getOrElse { "没连上：" + (it.message ?: "未知错误") },
                                Toast.LENGTH_LONG,
                            ).show()
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
                            Toast.makeText(
                                context,
                                result.getOrElse { "备份失败：" + (it.message ?: "未知错误") },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
            SettingsItem(
                title = if (busy == "restore") "正在恢复…" else "从备份恢复",
                subtitle = "取回最新备份，记录会追加",
                icon = R.drawable.ic_settings_import,
                onClick = {
                    if (busy.isBlank()) {
                        busy = "restore"
                        scope.launch {
                            val result = BackupRunner.restore(context)
                            busy = ""
                            Toast.makeText(
                                context,
                                result.getOrElse { "恢复失败：" + (it.message ?: "未知错误") },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
            if (current.id.isNotBlank()) {
                SettingsItem(
                    title = "删除这台服务器",
                    subtitle = "只删除本机保存的地址与密码，不动服务器上的备份",
                    icon = R.drawable.ic_delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = {
                        BackupStore.removeServer(context, current.id)
                        onBack()
                    },
                )
            }
        }
    }

    when (editing) {
        "name" -> TextRowDialog(
            title = "服务器名称",
            initial = current.name,
            hint = "例如：坚果云",
            onConfirm = { BackupStore.save(context, current.copy(name = it)) },
            onDismiss = { editing = "" },
        )
        "url" -> TextRowDialog(
            title = "服务器地址",
            initial = current.url,
            hint = "https://dav.jianguoyun.com/dav",
            onConfirm = { BackupStore.save(context, current.copy(url = it)) },
            onDismiss = { editing = "" },
        )
        "user" -> TextRowDialog(
            title = "用户名",
            initial = current.user,
            onConfirm = { BackupStore.save(context, current.copy(user = it)) },
            onDismiss = { editing = "" },
        )
        "password" -> TextRowDialog(
            title = "密码",
            initial = current.password,
            onConfirm = { BackupStore.save(context, current.copy(password = it)) },
            onDismiss = { editing = "" },
        )
        "path" -> TextRowDialog(
            title = "路径",
            initial = current.path,
            hint = "指尖Hub/备份",
            onConfirm = { BackupStore.save(context, current.copy(path = it)) },
            onDismiss = { editing = "" },
        )
    }
}

/** 数据管理：本地占用、删除所有对话、进入附件总览。 */
@Composable
internal fun DataManagementScreen(
    onBack: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenChatSearch: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    // 文件枚举 + 排序不要放在组合阶段；附件多时首帧会明显卡。
    var files by remember { mutableStateOf<List<AttachmentStore.StoredAttachment>>(emptyList()) }
    LaunchedEffect(tick) {
        files = withContext(Dispatchers.IO) { AttachmentStore.listAttachments(context) }
    }
    val total = files.sumOf { it.sizeBytes }
    val conversationCount = ChatStore.conversations.size
    val messageCount = remember(tick, ChatStore.conversations) { ChatStore.messageCount() }
    val dbBytes = remember(tick) {
        runCatching { context.getDatabasePath(ChatDatabase.FILE_NAME).length() }.getOrDefault(0L)
    }
    SettingsPage(title = "数据管理", onBack = onBack) {
        SettingsGroup(title = "本地数据") {
            SettingsItem(
                title = "对话",
                subtitle = conversationCount.toString() + " 段对话 · " + messageCount + " 条消息",
                icon = R.drawable.ic_history,
                value = formatBytes(dbBytes),
                topDivider = false,
                chevron = true,
                onClick = onOpenChatSearch,
            )
            SettingsItem(
                title = "附件",
                subtitle = files.size.toString() + " 个文件",
                icon = R.drawable.ic_settings_file,
                value = formatBytes(total),
                chevron = true,
                onClick = onOpenAttachments,
            )
            SettingsItem(
                title = "卸载提醒",
                subtitle = "本机数据通常随卸载删除；重要内容请先导出或 WebDAV 备份",
                icon = R.drawable.ic_info,
            )
        }
        SettingsGroup(title = "清理") {
            SettingsItem(
                title = "删除所有对话",
                subtitle = if (conversationCount == 0) {
                    "当前没有对话"
                } else {
                    "会同时删除对话中的附件，无法恢复"
                },
                icon = R.drawable.ic_delete,
                iconTint = MaterialTheme.colorScheme.error,
                topDivider = false,
                onClick = {
                    if (conversationCount == 0) {
                        Toast.makeText(context, "当前没有对话", Toast.LENGTH_SHORT).show()
                    } else {
                        confirmDeleteAll = true
                    }
                },
            )
            if (files.isNotEmpty()) {
                SettingsItem(
                    title = "清空全部附件",
                    subtitle = "删除 " + files.size + " 个文件（" + formatBytes(total) + "）",
                    icon = R.drawable.ic_create_folder,
                    onClick = { confirmClearAll = true },
                )
            }
        }
    }
    if (confirmClearAll) {
        ConfirmDialog(
            title = "清空全部附件？",
            message = "会删掉 " + files.size + " 个文件（共 " + formatBytes(total) + "）。",
            confirmText = "全部清空",
            onConfirm = {
                confirmClearAll = false
                scope.launch {
                    withContext(Dispatchers.IO) { ChatStore.clearAllAttachments(context) }
                    ChatStore.reloadCurrent()
                    tick++
                }
            },
            onDismiss = { confirmClearAll = false },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "删除所有对话？",
            message = "会删除 " + conversationCount + " 段对话、" + messageCount +
                " 条消息，以及这些对话中的附件。\n\n此操作无法恢复。",
            confirmText = "全部删除",
            onConfirm = {
                ChatRuntime.stopCurrent()
                ChatStore.deleteAllConversations()
                confirmDeleteAll = false
                tick++
                Toast.makeText(context, "已删除所有对话", Toast.LENGTH_SHORT).show()
                scope.launch(Dispatchers.IO) {
                    ChatDatabase.vacuum()
                    withContext(Dispatchers.Main.immediate) { tick++ }
                }
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}
