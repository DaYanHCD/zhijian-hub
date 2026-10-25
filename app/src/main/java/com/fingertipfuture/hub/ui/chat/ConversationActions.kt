package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubFolder
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN

/**
 * 长按副屏会话标题弹出的操作框。
 *
 * 四个动作都是真功能：置顶 / 重新生成标题 / 移到文件夹 / 删除。
 */
@Composable
internal fun ConversationActionsSheet(
    conversation: HubConversation,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onRegenerateTitle: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN).padding(bottom = 14.dp),
        ) {
            Text(
                text = conversation.title.ifBlank { "新聊天" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            ActionRow(
                icon = R.drawable.ic_pin,
                label = if (conversation.pinned) "取消置顶" else "置顶聊天",
                onClick = onTogglePin,
            )
            ActionRow(
                icon = R.drawable.ic_edit,
                label = "重命名",
                onClick = onRename,
            )
            ActionRow(
                icon = R.drawable.ic_regenerate,
                label = "重新生成标题",
                onClick = onRegenerateTitle,
            )
            ActionRow(
                icon = R.drawable.ic_create_folder,
                label = "移动到文件夹",
                onClick = onMoveToFolder,
            )
            ActionRow(
                icon = R.drawable.ic_delete,
                label = "删除",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(
            resId = icon,
            contentDescription = null,
            size = 22.dp,
            tint = tint,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = tint,
        )
    }
}

/**
 * 「移动到文件夹」：列出已有文件夹 + 一个新建入口。
 *
 * 副屏顶部的「创建文件夹」已经按用户要求删掉；新建放在这里，
 * 这样移动和新建是一件事，不会出现"建了文件夹却不知道往哪放"。
 */
@Composable
internal fun MoveToFolderDialog(
    conversation: HubConversation,
    folders: List<HubFolder>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onCreateAndPick: (String) -> Unit,
    onRenameFolder: (HubFolder) -> Unit,
    onDeleteFolder: (HubFolder) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                Text(
                    text = conversation.title.ifBlank { "新聊天" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FolderChoiceRow(
                    label = "不放进文件夹",
                    active = conversation.folderId == null,
                    onClick = { onPick(null) },
                )
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(folders, key = { it.id }) { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FolderChoiceRow(
                                label = folder.name,
                                active = conversation.folderId == folder.id,
                                onClick = { onPick(folder.id) },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onRenameFolder(folder) },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text("改名", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { onDeleteFolder(folder) },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("新建文件夹") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = { onCreateAndPick(newName) },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("新建并移动")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 手动重命名会话（「重新生成标题」的补充，不会被 AI 覆盖，除非再点生成）。 */
@Composable
internal fun RenameConversationDialog(
    conversation: HubConversation,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(conversation.id) { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text("会话标题") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun RenameFolderDialog(
    folder: HubFolder,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("文件夹名") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FolderChoiceRow(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(
            resId = R.drawable.ic_create_folder,
            contentDescription = null,
            size = 20.dp,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label + if (active) "（当前）" else "",
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
