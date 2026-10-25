package com.fingertipfuture.hub.ui

import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubFolder
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.hubDialogCard

@Composable
internal fun DeleteFolderDialog(
    folder: HubFolder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除文件夹？") },
        text = { Text("「" + folder.name + "」里的会话会移回未分类，会话本身不会删除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun DeleteConversationDialog(
    conversation: HubConversation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这段对话？") },
        text = { Text("「" + conversation.title.ifBlank { "新聊天" } + "」会从本机删除，且无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
