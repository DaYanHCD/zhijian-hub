package com.fingertipfuture.hub.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.core.AppConfig
import com.fingertipfuture.hub.update.AnnouncementApi
import com.fingertipfuture.hub.update.AnnouncementStore
import com.fingertipfuture.hub.update.HubAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 全局公告弹窗：启动时拉一次服务端公告；同一 id 只弹一次。
 * 拉不到 / 没有公告 / 已过期时完全静默，不影响正常使用。
 */
@Composable
internal fun AnnouncementDialogHost() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var current by remember { mutableStateOf<HubAnnouncement?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val announcementBase = if (AppConfig.IS_DEBUG) {
                context.getSharedPreferences(
                    AppConfig.DEBUG_OVERRIDE_PREFS,
                    android.content.Context.MODE_PRIVATE,
                ).getString(AppConfig.DEBUG_OVERRIDE_KEY, null)
                    ?.takeIf { it.isNotBlank() }
                    ?: AppConfig.UPDATE_BASE_URL
            } else {
                AppConfig.UPDATE_BASE_URL
            }
            val announcement = withContext(Dispatchers.IO) {
                runCatching { AnnouncementApi.fetch(announcementBase) }.getOrNull()
            }
            if (announcement != null &&
                announcement.isCurrentlyVisible() &&
                announcement.id != AnnouncementStore.lastSeen(context)
            ) {
                current = announcement
            }
            delay(5 * 60_000L)
        }
    }

    current?.let { announcement ->
        fun dismiss() {
            AnnouncementStore.markSeen(context, announcement.id)
            current = null
        }
        AlertDialog(
            onDismissRequest = { dismiss() },
            modifier = Modifier.hubDialogCard(),
            title = {
                Text(
                    text = announcement.title,
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
