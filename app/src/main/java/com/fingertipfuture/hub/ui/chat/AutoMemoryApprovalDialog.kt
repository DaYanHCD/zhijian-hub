package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.hubDialogCard

@Composable
internal fun AutoMemoryApprovalDialog(
    text: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                text = "允许助手记住这条信息？",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column {
                Text(
                    "助手判断这条信息以后可能还会用到。确认后只保存在本机，可随时在记忆设置中修改或删除。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("允许记住") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("不要记住") }
        },
    )
}
