package com.fingertipfuture.hub.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.VoicePlayer
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubCardBorder
import java.io.File
import java.util.Locale

/**
 * 附件气泡（图片 / 文件 / 位置）。
 *
 * 样式照微信（用户 2026-10-14 给的参考图）：
 *
 * - **图片**：**就是一张图**（固定 220dp 宽、按比例、圆角 10dp），不带文件名/大小/说明带；
 *   点一下进全屏看图（[ImagePreviewOverlay]）。
 * - **文件**：圆角卡片，左边文件名 + "大小 已发送"，右边一张**白色文稿图标**
 *   （右上角折角 + 彩色扩展名，按类型换颜色：TXT 蓝 / PDF 红 / XLS 绿 …）。
 * - **位置**：同一套卡片，标题 + 经纬度，点一下交给地图 App（不是内嵌地图）。
 *
 * 三张卡片都**不是**主题气泡色 —— 这是微信的做法：附件自己就是气泡，
 * 只有纯文字才用彩色气泡（见 [MessageRow]）。
 *
 * 纯安卓原生渲染：图片用 `BitmapFactory` 解出来走 Compose 的 `Image`
 * （**不是 WebView、不是任何图片库**）；文稿图标是用 `Canvas` 画的。
 */

/** 卡片圆角：比文字气泡（22dp）小，和微信那个观感一致。 */
private val CARD_CORNER = 10.dp

/** 图片圆角（用户 2026-10-14 定：就 10dp，干干净净一张图）。 */
private val IMAGE_CORNER = 10.dp

/** 图片卡片的宽度（和微信一样是固定宽度，图片按比例缩放在里面）。 */
private val IMAGE_CARD_WIDTH = 220.dp

/** 图片卡片高度上下限（保持比例，太扁/太长的都夹到区间里）。 */
private val IMAGE_MIN_HEIGHT = 110.dp
private val IMAGE_MAX_HEIGHT = 280.dp

/** 文件卡片宽度（微信也是固定宽度，名字长了折两行）。 */
private val FILE_CARD_WIDTH = 236.dp

@Composable
internal fun MessageAttachments(
    attachments: List<HubAttachment>,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    onImageClick: (HubAttachment) -> Unit = {},
) {
    if (attachments.isEmpty()) return
    Column(
        // 不要 fillMaxWidth：卡片要按内容贴边（自己在 Row 里靠左/靠右）
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { attachment ->
            when {
                attachment.isImage -> ImageAttachment(attachment, onImageClick)
                attachment.isFile -> FileAttachment(attachment, fromUser)
                attachment.isLocation -> LocationAttachment(attachment)
                // 语音：点一下播放（再点一下停）
                attachment.isVoice -> VoiceAttachment(attachment)
            }
        }
    }
}

/**
 * 语音气泡（A 方案）：圆形喇叭 + 五根波形 + `m:ss` 时长。
 *
 * 时长按 1~60 秒线性映射到气泡宽度：短语音保持紧凑，越接近 60 秒气泡越长。
 * 点一下播放、再点一下停；播放时波形呼吸。全 App 只有一个播放器（[VoicePlayer]）。
 */
@Composable
private fun VoiceAttachment(attachment: HubAttachment) {
    val playing = VoicePlayer.playingPath == attachment.path
    val totalSeconds = ((attachment.durationMs + 999L) / 1000L)
        .coerceIn(1L, 60L)
        .toInt()
    val progress = (totalSeconds - 1) / 59f
    val bubbleWidth = VOICE_MIN_WIDTH + (VOICE_MAX_WIDTH - VOICE_MIN_WIDTH) * progress
    val duration = String.format(
        java.util.Locale.CHINA,
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60,
    )
    val pulse by rememberInfiniteTransition(label = "voice").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "voicePulse",
    )
    Row(
        modifier = Modifier
            .width(bubbleWidth)
            .clip(RoundedCornerShape(21.dp))
            .hubCardBorder(RoundedCornerShape(21.dp))
            .background(HubCapsuleColor)
            .clickable { VoicePlayer.toggle(attachment.path) }
            .padding(start = 6.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_voice_speaker,
                contentDescription = if (playing) "暂停语音" else "播放语音",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))
        VoiceWaveform(
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = if (playing) 0.35f + pulse * 0.55f else 0.72f,
            ),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = duration,
            color = HubInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun VoiceWaveform(color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(.30f, .65f, .88f, .55f, .38f).forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((5f + h * 17f).dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private val VOICE_MIN_WIDTH = 150.dp
private val VOICE_MAX_WIDTH = 270.dp
/** "70.51 KB 已发送" —— 微信那句的等价物（我们这边没有"下载"这回事）。 */
private fun statusOf(attachment: HubAttachment, fromUser: Boolean): String =
    HubAttachment.humanSize(attachment.size) + if (fromUser) " 已发送" else " 已收到"

@Composable
private fun ImageAttachment(attachment: HubAttachment, onOpen: (HubAttachment) -> Unit) {
    // 解码一次就缓存住：每帧重新解码大图会把列表拖垮。
    // 按显示尺寸采样（卡片才 220dp 宽，没必要解 1280 的原图）。
    val bitmap = remember(attachment.path) {
        runCatching {
            val file = File(attachment.path)
            if (!file.isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(attachment.path, bounds)
            var scale = 1
            while (bounds.outWidth > 0 && bounds.outWidth / scale > 720) scale *= 2
            BitmapFactory.decodeFile(
                attachment.path,
                BitmapFactory.Options().apply { inSampleSize = scale },
            )
        }.getOrNull()
    }
    if (bitmap == null) {
        MissingAttachment(R.drawable.ic_photo_library, "图片已不在本机")
        return
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    // 一张图，圆角 10dp：宽度固定 220dp，高度按原图比例（夹在上下限之间），点一下全屏看
    val ratio = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width else 1f
    val cardHeight = (IMAGE_CARD_WIDTH.value * ratio)
        .coerceIn(IMAGE_MIN_HEIGHT.value, IMAGE_MAX_HEIGHT.value)
        .dp

    Box(
        modifier = Modifier
            .width(IMAGE_CARD_WIDTH)
            .height(cardHeight)
            .clip(RoundedCornerShape(IMAGE_CORNER))
            .hubCardBorder(RoundedCornerShape(IMAGE_CORNER))
            .clickable { onOpen(attachment) },
    ) {
        Image(
            bitmap = image,
            contentDescription = attachment.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
@Composable
private fun FileAttachment(attachment: HubAttachment, fromUser: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier
            .width(FILE_CARD_WIDTH)
            .hubCardBorder(RoundedCornerShape(CARD_CORNER)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusOf(attachment, fromUser),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            FileTypeIcon(attachment.name)
        }
    }
}

/** 白色文稿图标：右上角折角 + 中间彩色扩展名（微信那个文件图标的画法）。 */
@Composable
private fun FileTypeIcon(fileName: String) {
    val ext = fileName.substringAfterLast('.', "")
        .uppercase()
        .take(4)
        .ifBlank { "FILE" }
    val accent = fileTypeColor(ext)
    val fold = 12.dp

    Box(modifier = Modifier.size(width = 40.dp, height = 48.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val w = size.width
            val h = size.height
            val r = 5.dp.toPx()
            val f = fold.toPx()
            // 纸张：圆角矩形，右上角斜切
            val sheet = Path().apply {
                moveTo(r, 0f)
                lineTo(w - f, 0f)
                lineTo(w, f)
                lineTo(w, h - r)
                quadraticBezierTo(w, h, w - r, h)
                lineTo(r, h)
                quadraticBezierTo(0f, h, 0f, h - r)
                lineTo(0f, r)
                quadraticBezierTo(0f, 0f, r, 0f)
                close()
            }
            drawPath(sheet, Color.White)
            // 折角（浅灰）
            val corner = Path().apply {
                moveTo(w - f, 0f)
                lineTo(w - f, f)
                lineTo(w, f)
                close()
            }
            drawPath(corner, Color(0xFFD7DBE3))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = ext,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(accent),
            )
        }
    }
}

/** 扩展名配色（跟微信一样按类型区分，不认得的给中性灰）。 */
private fun fileTypeColor(ext: String): Color = when (ext) {
    "TXT", "MD", "LOG", "JSON", "XML" -> Color(0xFF4C8DF6)
    "PDF" -> Color(0xFFE5534B)
    "DOC", "DOCX" -> Color(0xFF2B7BD4)
    "XLS", "XLSX", "CSV" -> Color(0xFF2E9E5B)
    "PPT", "PPTX" -> Color(0xFFE08A2E)
    "ZIP", "RAR", "7Z", "GZ", "TAR" -> Color(0xFF8E7CC3)
    "APK" -> Color(0xFF3DBFA0)
    else -> Color(0xFF6B7280)
}

@Composable
private fun LocationAttachment(attachment: HubAttachment) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier
            .width(300.dp)
            .hubCardBorder(shape)
            .clickable {
                // 交给外部地图 App 打开（不是内嵌 WebView）
                runCatching { context.startActivity(AttachmentStore.mapIntent(attachment.lat, attachment.lon)) }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(108.dp)
                    .clipToBounds()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            ) {
                LocationMapLines(
                    modifier = Modifier.fillMaxSize(),
                    lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(108.dp)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = attachment.label.ifBlank { "我的位置" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "%.5f, %.5f",
                        attachment.lat,
                        attachment.lon,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun LocationMapLines(
    modifier: Modifier,
    lineColor: Color,
) {
    Canvas(modifier = modifier) {
        val step = 28.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            x += step
        }
        drawCircle(
            color = lineColor,
            radius = size.minDimension * 0.36f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun MissingAttachment(icon: Int, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CARD_CORNER),
        modifier = Modifier.hubCardBorder(RoundedCornerShape(CARD_CORNER)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(resId = icon, contentDescription = null, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(text = text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
