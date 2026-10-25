package com.fingertipfuture.hub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.AppConfig
import com.fingertipfuture.hub.core.AppInfo
import com.fingertipfuture.hub.util.formatBytes
import com.fingertipfuture.hub.ui.component.hubDialogCard

/**
 * 「关于」：版本 + 安装包大小 + 一句话说明。
 *
 * 入口在侧栏（用户要求把检查更新 / 更新日志 / 关于都放到侧栏），
 * 所以这里只做"展示"；检查更新和更新日志是侧栏里各自独立的两项。
 */
@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sizeLabel = remember {
        formatBytes(
            AppInfo.installedSizeBytes(context),
            context.getString(R.string.unknown_size),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_name) + " " + AppConfig.CURRENT_VERSION_NAME,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.about_size) + " " + sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.about_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
