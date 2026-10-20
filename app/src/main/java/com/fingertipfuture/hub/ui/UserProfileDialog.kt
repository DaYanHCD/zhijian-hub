package com.fingertipfuture.hub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.data.AvatarKind
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.UiPrefs

/**
 * 「我的资料」：换头像 + 改昵称（2026-10-16 用户要求）。
 *
 * 入口在**副屏用户区**（头像和昵称上有那颗可修改的小铅笔）。
 * 助手那头像不在这儿改 —— 助手头像在「设置 › 通用设置 › 助手管理 › 基础设定」里，
 * 所以这里只管「我」这一边。
 */
@Composable
internal fun UserProfileDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(UiPrefs.userName) }
    val version = AvatarStore.version
    val bitmap = remember(version) { AvatarStore.load(AvatarKind.SELF) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) AvatarStore.save(context, AvatarKind.SELF, uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text("我的资料") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bitmap != null) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                        )
                    } else {
                        MaterialSymbol(
                            resId = com.fingertipfuture.hub.R.drawable.ic_mood,
                            contentDescription = null,
                            size = 56.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "头像",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "换一张",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { pickAvatar.launch("image/*") }
                                    .padding(end = 16.dp),
                            )
                            if (bitmap != null) {
                                Text(
                                    text = "恢复默认",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { AvatarStore.clear(AvatarKind.SELF) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(text = "昵称", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("我", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UiPrefs.setUserName(context, name)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
