package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.data.agent.AgentDeviceActionIntent
import com.fingertipfuture.hub.ui.component.HubAlertDialog
import com.fingertipfuture.hub.ui.component.LocalizedText as Text

@Composable
internal fun AgentDeviceActionDialog(
    intent: AgentDeviceActionIntent,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    HubAlertDialog(
        onDismissRequest = onReject,
        title = { Text(intent.title) },
        text = {
            Column {
                Text(
                    text = "请确认是否允许助手写入以下系统数据：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                intent.details.filter { it.isNotBlank() }.forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("允许写入") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("取消") }
        },
    )
}
