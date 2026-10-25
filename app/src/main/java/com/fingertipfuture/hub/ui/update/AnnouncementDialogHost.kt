package com.fingertipfuture.hub.ui.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.core.AppConfig
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.update.AnnouncementApi
import com.fingertipfuture.hub.update.AnnouncementStore
import com.fingertipfuture.hub.update.HubAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全局公告弹窗：启动时查询服务器；同一 id 只弹一次。 */
@Composable
internal fun AnnouncementDialogHost() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var current by remember { mutableStateOf<HubAnnouncement?>(null) }

    LaunchedEffect(Unit) {
        val announcement = withContext(Dispatchers.IO) {
            runCatching {
                AnnouncementApi.fetch(AppConfig.announcementUrl())
            }.getOrNull()
        }
        if (announcement != null &&
            announcement.isCurrentlyVisible() &&
            announcement.id != AnnouncementStore.lastSeen(context)
        ) {
            current = announcement
        }
    }

    current?.let { announcement ->
        fun dismiss() {
            AnnouncementStore.markSeen(context, announcement.id)
            current = null
        }
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = {
                Text(
                    text = announcement.title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = if (announcement.level == "error") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            text = {
                Text(
                    text = announcement.message,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { dismiss() }) { Text("知道了") }
            },
            dismissButton = announcement.url.takeIf { it.isNotBlank() }?.let { url ->
                {
                    TextButton(
                        onClick = {
                            runCatching { uriHandler.openUri(url) }
                            dismiss()
                        },
                    ) { Text("查看详情") }
                }
            },
        )
    }
}
