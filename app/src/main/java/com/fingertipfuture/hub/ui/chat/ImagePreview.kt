package com.fingertipfuture.hub.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.ui.component.hubCardBorder
import java.io.File

/**
 * 点图片气泡后的**全屏看图**（纯 Compose 画的一层，不是新 Activity、更不是 WebView）。
 *
 * 退出方式：点任意位置、点右上角「✕」、按返回键 —— 三种都行（返回键平时是最顺手的那个）。
 */
@Composable
internal fun ImagePreviewOverlay(attachment: HubAttachment, onClose: () -> Unit) {
    BackHandler { onClose() }

    val bitmap = remember(attachment.path) {
        runCatching {
            val file = File(attachment.path)
            if (file.isFile) BitmapFactory.decodeFile(attachment.path) else null
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 点哪儿都能关；不要水波纹（看图时出现涟漪很怪）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "图片已不在本机", color = Color.White, fontSize = 14.sp)
        }

        // 右上角一个「✕」：不是所有人都习惯用返回键
        Surface(
            color = Color.White.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .hubCardBorder(CircleShape)
                .clickable(onClick = onClose),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "✕", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
