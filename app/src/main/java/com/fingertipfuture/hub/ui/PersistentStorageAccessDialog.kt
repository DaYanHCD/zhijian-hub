package com.fingertipfuture.hub.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.PersistentChatBackup
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import kotlinx.coroutines.launch

/**
 * “聊天与附件跨卸载保留”所需的公共存储授权。
 *
 * 权限只用于 `Documents/指尖Hub/.persistence/snapshot.zip`，不用于读取用户其它文件。
 */
@Composable
internal fun PersistentStorageAccessDialog() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(PersistentChatBackup.permissionGranted(context))
    }
    var show by rememberSaveable { mutableStateOf(!granted) }

    fun applyGranted() {
        granted = true
        show = false
        lifecycleOwner.lifecycleScope.launch {
            val restored = ChatStore.restorePersistentSnapshotIfNeeded(context)
            if (restored) {
                context.showLocalizedToast("已恢复聊天记录和附件")
            } else {
                PersistentChatBackup.flush(context)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        if (allowed) applyGranted() else context.showLocalizedToast("未授权，聊天无法跨卸载保留")
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = PersistentChatBackup.permissionGranted(context)
                if (nowGranted && !granted) applyGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted && show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("开启聊天持久保存") },
            text = {
                Text(
                    "为了在卸载重装后恢复聊天记录、图片、语音和文件，需要允许指尖Hub访问公共存储。\n\n" +
                        "数据会加密后写入 Documents/指尖Hub，并计入“存储与扩展”的软件占用；" +
                        "提供商、Key 和其他设置不会保存，重装后需要重新添加。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            val opened = runCatching { context.startActivity(intent) }.isSuccess
                            if (!opened) {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                )
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                ) {
                    Text("去授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text("以后")
                }
            },
        )
    }
}
