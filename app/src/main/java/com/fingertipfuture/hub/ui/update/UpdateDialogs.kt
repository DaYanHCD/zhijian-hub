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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.update.ApkInstaller
import com.fingertipfuture.hub.update.UpdateController
import com.fingertipfuture.hub.update.UpdateError
import com.fingertipfuture.hub.update.UpdateState
import com.fingertipfuture.hub.update.VersionInfo
import com.fingertipfuture.hub.util.formatBytes
import com.fingertipfuture.hub.util.progressPercent
import com.fingertipfuture.hub.ui.component.hubDialogCard

/**
 * 所有「检查更新」相关弹窗的统一入口。
 *
 * 界面层只负责调 [UpdateController]；具体展示哪种弹窗由它的 [UpdateController.state] 决定。
 * 安装动作也在这一层完成（[ApkInstaller] 要 Context），控制器只负责把包准备好。
 */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    when (val state = UpdateController.state) {
        UpdateState.Hidden -> Unit

        UpdateState.Checking -> MessageDialog(
            title = stringResource(R.string.dialog_checking),
            // 允许返回键 / 点外面关掉并**取消这次请求**：清单拉取带重试，最长可能要一分多钟，
            // 原来这里 onDismiss = {} 会把用户锁在弹窗里。
            onDismiss = { UpdateController.dismiss() },
            showButtons = false,
        )

        is UpdateState.UpToDate -> MessageDialog(
            title = stringResource(R.string.dialog_up_to_date_title),
            message = stringResource(R.string.dialog_up_to_date_message, state.currentVersionName),
            confirmText = stringResource(R.string.action_close),
            // ⚠️ 这个弹窗只有一个「关闭」，它走的是 confirmButton —— 必须把 onConfirm 也接上，
            // 否则按钮是个死的（之前就是这样：点了没任何反应）。
            onConfirm = { UpdateController.dismiss() },
            onDismiss = { UpdateController.dismiss() },
        )

        is UpdateState.Available -> AvailableDialog(
            info = state.info,
            onConfirm = { UpdateController.downloadUpdate(state.info) },
            onDismiss = { UpdateController.dismiss() },
        )

        is UpdateState.Downloading -> DownloadingDialog(
            info = state.info,
            downloaded = state.downloaded,
            total = state.total,
            onCancel = { UpdateController.dismiss() },
        )

        is UpdateState.ReadyToInstall -> {
            val apk = state.apk
            MessageDialog(
                title = stringResource(R.string.dialog_download_done_title),
                message = apk.name,
                confirmText = stringResource(R.string.action_install),
                dismissText = stringResource(R.string.action_later),
                onConfirm = {
                    if (!ApkInstaller.canRequestInstall(context)) {
                        // 没有「安装未知应用」权限：先引导去授权
                        UpdateController.showInstallPermission()
                    } else {
                        val launched = runCatching { ApkInstaller.install(context, apk) }.isSuccess
                        if (launched) {
                            UpdateController.onInstallLaunched()
                        } else {
                            // 别把异常吞掉：否则界面只是"弹窗关了"，用户一脸茫然
                            UpdateController.installLaunchFailed()
                        }
                    }
                },
                onDismiss = { UpdateController.dismiss() },
            )
        }

        is UpdateState.Failed -> MessageDialog(
            title = stringResource(
                when {
                    state.retryWith != null -> R.string.dialog_download_failed_title
                    state.error == UpdateError.INSTALL -> R.string.dialog_install_failed_title
                    else -> R.string.dialog_check_failed_title
                },
            ),
            message = errorText(state.error, state.httpCode),
            confirmText = stringResource(R.string.action_retry),
            dismissText = stringResource(R.string.action_close),
            onConfirm = { UpdateController.retry() },
            onDismiss = { UpdateController.dismiss() },
        )

        UpdateState.NeedInstallPermission -> MessageDialog(
            title = stringResource(R.string.dialog_install_permission_title),
            message = stringResource(R.string.dialog_install_permission_message),
            confirmText = stringResource(R.string.action_go_settings),
            dismissText = stringResource(R.string.action_close),
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
    UpdateError.NETWORK -> stringResource(R.string.error_network)
    UpdateError.SERVER -> stringResource(R.string.error_server, httpCode ?: 0)
    UpdateError.PARSE -> stringResource(R.string.error_parse)
    UpdateError.DOWNLOAD -> stringResource(R.string.error_download)
    UpdateError.VERIFY -> stringResource(R.string.error_verify)
    UpdateError.INSTALL -> stringResource(R.string.error_install)
}

/** 通用消息弹窗；[showButtons] 为 false 时只有一个不可关闭的转圈提示。 */
@Composable
private fun MessageDialog(
    title: String,
    message: String? = null,
    confirmText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
    showButtons: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text(title) },
        text = message?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        confirmButton = {
            if (showButtons && confirmText != null) {
                TextButton(onClick = onConfirm) { Text(confirmText) }
            }
        },
        dismissButton = {
            if (showButtons && dismissText != null) {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            }
        },
    )
}

/** 「发现新版本」：版本号、大小、发布时间、更新内容。 */
@Composable
private fun AvailableDialog(
    info: VersionInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text(stringResource(R.string.dialog_new_version_title, info.versionName)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.dialog_new_version_size,
                        formatBytes(info.apkSize, stringResource(R.string.unknown_size)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.releasedAt.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.dialog_new_version_released, info.releasedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.dialog_changelog),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    info.changelog.forEach { line -> ChangeItem(line) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_update_now)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
        },
    )
}

/** 更新内容里的一条，前面带一个圆点。 */
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

/** 下载中：进度条 + 已下载 / 总大小。 */
@Composable
private fun DownloadingDialog(
    info: VersionInfo,
    downloaded: Long,
    total: Long,
    onCancel: () -> Unit,
) {
    val percent = progressPercent(downloaded, total)
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.hubDialogCard(),
        title = { Text(stringResource(R.string.dialog_downloading_title, info.versionName)) },
        text = {
            Column {
                if (percent >= 0) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 服务器没给 Content-Length 时算不出进度，展示不确定进度条
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.dialog_download_progress,
                        formatBytes(downloaded, stringResource(R.string.unknown_size)),
                        formatBytes(total, stringResource(R.string.unknown_size)),
                    ) + if (percent >= 0) "  ($percent%)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
