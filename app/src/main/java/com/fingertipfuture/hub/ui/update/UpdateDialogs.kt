package com.fingertipfuture.hub.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.update.ApkInstaller
import com.fingertipfuture.hub.update.UpdateController
import com.fingertipfuture.hub.update.UpdateError
import com.fingertipfuture.hub.update.UpdateState
import com.fingertipfuture.hub.update.VersionInfo
import com.fingertipfuture.hub.util.formatBytes
import com.fingertipfuture.hub.util.progressPercent

/** 全局更新弹窗：检查、新版本、下载、安装和错误反馈。 */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    when (val state = UpdateController.state) {
        UpdateState.Hidden -> Unit

        UpdateState.Checking -> MessageDialog(
            title = "正在检查更新…",
            confirmText = "关闭",
            onConfirm = { UpdateController.dismiss() },
        )

        is UpdateState.UpToDate -> MessageDialog(
            title = "已是最新版本",
            message = "当前版本 ${state.currentVersionName}，服务器上没有更新的版本。",
            confirmText = "关闭",
            onConfirm = { UpdateController.dismiss() },
        )

        is UpdateState.Available -> AvailableDialog(
            info = state.info,
            onConfirm = { UpdateController.downloadUpdate(state.info) },
            onDismiss = { UpdateController.dismiss() },
        )

        is UpdateState.Downloading -> DownloadingDialog(
            downloaded = state.downloaded,
            total = state.total,
            onCancel = { UpdateController.dismiss() },
        )

        is UpdateState.ReadyToInstall -> MessageDialog(
            title = "下载完成",
            message = state.apk.name,
            confirmText = "立即安装",
            dismissText = "以后再说",
            onConfirm = {
                if (!ApkInstaller.canRequestInstall(context)) {
                    UpdateController.showInstallPermission()
                } else {
                    val launched = runCatching { ApkInstaller.install(context, state.apk) }.isSuccess
                    if (launched) {
                        UpdateController.onInstallLaunched()
                    } else {
                        UpdateController.installLaunchFailed()
                    }
                }
            },
            onDismiss = { UpdateController.dismiss() },
        )

        is UpdateState.Failed -> MessageDialog(
            title = when {
                state.retryWith != null -> "下载失败"
                state.error == UpdateError.INSTALL -> "安装失败"
                else -> "检查更新失败"
            },
            message = errorText(state.error, state.httpCode),
            confirmText = "重试",
            dismissText = "关闭",
            onConfirm = { UpdateController.retry() },
            onDismiss = { UpdateController.dismiss() },
        )

        UpdateState.NeedInstallPermission -> MessageDialog(
            title = "需要「安装未知应用」权限",
            message = "系统会打开本应用的权限页，请把「允许安装未知应用」打开，然后回到这里点「立即安装」。",
            confirmText = "去设置",
            dismissText = "关闭",
            onConfirm = {
                ApkInstaller.openUnknownSourcesSettings(context)
                UpdateController.dismiss()
            },
            onDismiss = { UpdateController.dismiss() },
        )
    }
}

@Composable
private fun errorText(error: UpdateError, httpCode: Int?): String = when (error) {
    UpdateError.NETWORK -> "网络不通或服务器连不上，请检查网络后重试。"
    UpdateError.SERVER -> "服务器返回错误（HTTP ${httpCode ?: 0}）。"
    UpdateError.PARSE -> "更新清单解析失败，请稍后再试。"
    UpdateError.CHANNEL -> "更新清单渠道不匹配，已停止更新。请检查服务器配置。"
    UpdateError.DOWNLOAD -> "下载安装包失败，请稍后重试。"
    UpdateError.VERIFY -> "安装包校验没通过，可能下载不完整，请重新下载。"
    UpdateError.INSTALL -> "安装包已下载，但系统没有打开安装界面。请确认「安装未知应用」权限后重试。"
}

@Composable
private fun MessageDialog(
    title: String,
    message: String? = null,
    confirmText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { value ->
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = confirmText?.let { label ->
            { TextButton(onClick = onConfirm) { Text(label) } }
        },
        dismissButton = dismissText?.let { label ->
            { TextButton(onClick = onDismiss) { Text(label) } }
        },
    )
}

@Composable
private fun AvailableDialog(
    info: VersionInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "安装包大小：${formatBytes(info.apkSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.releasedAt.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "发布时间：${info.releasedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                listOf(
                    "更新了一些新功能~",
                    "优化了一些旧效果~",
                    "重构了部分旧界面~",
                    "修复了部分已知问题~",
                ).forEach { line -> ChangeItem(line) }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "具体更新内容请到“设置-关于-更新日志”中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("以后再说") }
        },
    )
}

@Composable
private fun ChangeItem(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DownloadingDialog(
    downloaded: Long,
    total: Long,
    onCancel: () -> Unit,
) {
    val percent = progressPercent(downloaded, total)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("正在下载更新") },
        text = {
            Column {
                if (percent >= 0) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = formatBytes(downloaded) + if (total > 0) {
                        " / " + formatBytes(total)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}
