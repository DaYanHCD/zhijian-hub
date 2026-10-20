package com.fingertipfuture.hub.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.VoicePlayer
import com.fingertipfuture.hub.ui.chat.ImagePreviewOverlay
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.util.formatBytes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 数据管理 › 附件：图片直接显示，文件显示真实文件名；不再展示内部编号。 */
@Composable
internal fun AttachmentLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<AttachmentStore.StoredAttachment>>(emptyList()) }
    LaunchedEffect(Unit) {
        files = withContext(Dispatchers.IO) { AttachmentStore.listAttachments(context) }
    }
    val images = files.filter { it.isImage }
    val voices = files.filter { it.isVoice }
    val regularFiles = files.filterNot { it.isImage || it.isVoice }
    val total = files.sumOf { it.sizeBytes }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    var preview by remember { mutableStateOf<HubAttachment?>(null) }

    SettingsPage(title = "附件", onBack = onBack) {
        SettingsGroup(title = "本机附件") {
            SettingsItem(
                title = files.size.toString() + " 个文件",
                subtitle = if (files.isEmpty()) "还没有附件" else "共占用 " + formatBytes(total),
                icon = R.drawable.ic_settings_file,
                topDivider = false,
            )
        }

        if (images.isNotEmpty()) {
            SettingsGroup(title = "图片") {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    images.forEach { file ->
                        ImageFileCard(
                            file = file,
                            subtitle = fmt.format(Date(file.modifiedAt)) + " · " +
                                formatBytes(file.sizeBytes),
                            onClick = {
                                preview = HubAttachment(
                                    kind = HubAttachment.KIND_IMAGE,
                                    path = file.path,
                                    name = file.name,
                                    size = file.sizeBytes,
                                    mime = "image/jpeg",
                                )
                            },
                        )
                    }
                }
            }
        }

        if (voices.isNotEmpty()) {
            SettingsGroup(title = "语音") {
                voices.forEachIndexed { index, file ->
                    val playing = VoicePlayer.playingPath == file.path
                    SettingsItem(
                        title = if (playing) "正在播放的语音消息" else "语音消息",
                        subtitle = fmt.format(Date(file.modifiedAt)) + " · " +
                            formatBytes(file.sizeBytes),
                        icon = R.drawable.ic_voice_speaker,
                        topDivider = index != 0,
                        onClick = { VoicePlayer.toggle(file.path) },
                    )
                }
            }
        }

        if (regularFiles.isNotEmpty()) {
            SettingsGroup(title = "文件") {
                regularFiles.forEachIndexed { index, file ->
                    SettingsItem(
                        title = file.displayName,
                        subtitle = fmt.format(Date(file.modifiedAt)) + " · " +
                            formatBytes(file.sizeBytes),
                        icon = R.drawable.ic_settings_file,
                        topDivider = index != 0,
                    )
                }
            }
        }

        if (files.isEmpty()) {
            SettingsGroup(title = "说明") {
                SettingsItem(
                    title = "这里会显示聊天里发送过的图片和文件",
                    icon = R.drawable.ic_info,
                    topDivider = false,
                )
            }
        }
    }

    preview?.let { attachment ->
        ImagePreviewOverlay(attachment = attachment, onClose = { preview = null })
    }
}

@Composable
private fun ImageFileCard(
    file: AttachmentStore.StoredAttachment,
    subtitle: String,
    onClick: () -> Unit,
) {
    val bitmap = remember(file.path) {
        runCatching {
            if (!File(file.path).isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var scale = 1
            while (bounds.outWidth > 0 && bounds.outWidth / scale > 1080) scale *= 2
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = scale },
            )
        }.getOrNull()
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(14.dp))
                .hubCardBorder(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(text = "图片已不在本机", color = HubInkSoft, fontSize = 13.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "图片",
                color = HubInk,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                color = HubInkSoft,
                fontSize = 11.5.sp,
            )
        }
    }
}
