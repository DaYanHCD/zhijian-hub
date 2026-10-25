package com.fingertipfuture.hub.floating

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.chat.MarkdownBody
import com.fingertipfuture.hub.ui.theme.ThemeStore

internal data class FloatingSearchAnswer(
    val question: String,
    val message: String = "",
    val answer: String = "",
)

internal const val FLOATING_BUBBLE_SIZE_DP = 40

@Composable
internal fun FloatingOverlayTheme(content: @Composable () -> Unit) {
    val dark = ThemeStore.isDark
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFB6C4FF),
            onPrimary = Color(0xFF002A75),
            surface = Color(0xF2121318),
            onSurface = Color(0xFFE7E5EC),
            surfaceVariant = Color(0xFF2A2C35),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF777986),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3B5BDB),
            onPrimary = Color.White,
            surface = Color(0xFAFBF9FF),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE0E8FF),
            onSurfaceVariant = Color(0xFF45464F),
            outline = Color(0xFF9B9CA6),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 只有这颗圆钮会常驻在其他应用上方。 */
@Composable
internal fun FloatingSearchBubble(
    onClick: () -> Unit,
    onCloseGesture: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    FloatingOverlayTheme {
        val style = FloatingSearchSession.bubbleStyle
        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (style == FloatingBubbleStyle.STANDARD) 1f else 0.84f,
        )
        Surface(
            modifier = Modifier
                .size(FLOATING_BUBBLE_SIZE_DP.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onDoubleTap = { onCloseGesture() },
                        onLongPress = { onCloseGesture() },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = CircleShape,
            color = bubbleColor,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
            tonalElevation = 0.dp,
            // 悬浮窗大小就是这颗圆钮本身，Material 的 elevation 会被窗口边界裁成
            // 一圈方形黑影。这里不用 elevation，只保留圆形描边。
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol(
                    resId = R.drawable.ic_floating_search,
                    contentDescription = "悬浮搜题：单击截图，双击或长按关闭",
                    size = 19.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 截屏后的自由圈选层。
 *
 * 手指轨迹和返回给服务的边界都使用 0..1 归一化坐标，不同截图尺寸不需要另一套算法。
 * 用户沿题目边缘画任意闭合轨迹，服务按轨迹的包围范围裁图后交给模型。
 */
@Composable
internal fun FloatingSearchCrop(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onSolve: (Rect) -> Unit,
) {
    FloatingOverlayTheme {
        var viewport by remember { mutableStateOf(IntSize.Zero) }
        var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
        var drawing by remember { mutableStateOf(false) }
        val selection = points.boundsOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewport = it }
                .pointerInput(viewport) {
                    if (viewport.width <= 0 || viewport.height <= 0) return@pointerInput
                    detectDragGestures(
                        onDragStart = { point ->
                            drawing = true
                            points = listOf(point.normalized(viewport))
                        },
                        onDragEnd = {
                            drawing = false
                            if (points.boundsOrNull() == null) points = emptyList()
                        },
                        onDragCancel = {
                            drawing = false
                            if (points.boundsOrNull() == null) points = emptyList()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val next = change.position.normalized(viewport)
                            val last = points.lastOrNull()
                            val minStep = 1.5f / viewport.width.coerceAtLeast(1)
                            if (last == null || runCatching { (next - last).getDistance() }.getOrDefault(1f) >= minStep) {
                                points = points + next
                            }
                        },
                    )
                },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            SelectionMask(
                points = points,
                closed = !drawing && selection != null,
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 5.dp,
            ) {
                Text(
                    text = if (points.isEmpty()) {
                        "用手指沿题目边缘任意画一圈"
                    } else {
                        "已圈选题目，可直接开始解题或重新画一圈"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 30.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                // 半透明，避免用户圈到按钮下方时完全看不见被遮住的题目。
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                ),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "自由圈选题目区域",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = { selection?.let(onSolve) },
                            enabled = selection != null,
                            modifier = Modifier.weight(1.7f).height(48.dp),
                        ) {
                            Text("开始解题")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionMask(
    points: List<Offset>,
    closed: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.isEmpty()) {
            drawRect(Color.Black.copy(alpha = 0.34f))
            return@Canvas
        }
        val lasso = Path().apply {
            val first = points.first()
            moveTo(first.x * size.width, first.y * size.height)
            points.drop(1).forEach { point ->
                lineTo(point.x * size.width, point.y * size.height)
            }
            if (closed) close()
        }
        if (closed) {
            val outside = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addPath(lasso)
            }
            drawPath(outside, Color.Black.copy(alpha = 0.55f))
        }
        drawPath(
            path = lasso,
            color = Color(0xFF79A7FF),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
internal fun FloatingSearchResults(
    answer: FloatingSearchAnswer,
    title: String,
    loading: Boolean = false,
    onClose: () -> Unit,
) {
    FloatingOverlayTheme {
        val sections = remember(answer.answer) { parseAnswerSections(answer.answer) }
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                            .size(width = 42.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape,
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MaterialSymbol(
                                    resId = R.drawable.ic_floating_search,
                                    contentDescription = null,
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (loading) {
                                    "正在识别题目、公式和图表"
                                } else {
                                    "答案优先显示 · 解析随后展开"
                                },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable(onClick = onClose),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "×",
                                    fontSize = 22.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (loading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "AI 正在解题",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "马上给出答案和解题步骤",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (answer.answer.isNotBlank()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            sections.lead?.let { lead ->
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        ),
                                    ) {
                                        Column(modifier = Modifier.padding(13.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "最终答案",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Surface(
                                                    modifier = Modifier.clickable {
                                                        clipboard.setText(AnnotatedString(lead))
                                                        context.showLocalizedToast("最终答案已复制")
                                                    },
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(
                                                            horizontal = 9.dp,
                                                            vertical = 5.dp,
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        MaterialSymbol(
                                                            resId = R.drawable.ic_copy,
                                                            contentDescription = null,
                                                            size = 13.dp,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = "复制",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            SelectionContainer {
                                                MarkdownBody(
                                                    text = lead,
                                                    textColor = MaterialTheme.colorScheme.onSurface,
                                                    bodySize = 16.sp,
                                                    bodyLineHeight = 23.sp,
                                                    blockGap = 6.dp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                SelectionContainer {
                                    MarkdownBody(
                                        text = sections.detail,
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        bodySize = 14.5.sp,
                                        bodyLineHeight = 22.sp,
                                        blockGap = 8.dp,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = answer.message.ifBlank { "模型没有返回解题结果" },
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AnswerSections(
    val lead: String?,
    val detail: String,
)

/** 模型被要求先输出“答案：”。拆出来单独置顶；找不到标记时仍完整显示原答案。 */
private fun parseAnswerSections(raw: String): AnswerSections {
    val text = readablePlainMath(raw.trim())
    if (text.isEmpty()) return AnswerSections(null, "")
    val answerLine = Regex(
        """(?m)^\s*(?:\*\*)?答案\s*[:：]\s*(?:\*\*)?\s*(.+?)\s*(?:\*\*)?\s*$""",
    ).find(text) ?: return AnswerSections(null, text)
    val lead = answerLine.groupValues[1].trim().trim('*').trim().takeIf { it.isNotBlank() }
        ?: return AnswerSections(null, text)
    val detail = text.removeRange(answerLine.range).trim()
    return AnswerSections(lead, detail)
}

/**
 * 模型偶尔会把公式当普通文本返回，例如 `ax^2` 或 `√(b^2-4ac)`。
 * 这里先兼容最常见、无需完整 TeX 的上标和比较符；标准 LaTeX 仍交给 MarkdownBody。
 */
private fun readablePlainMath(text: String): String {
    val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ',
    )
    return text
        .replace(
            Regex("""([A-Za-z0-9)])\^\{?([0-9+\-n]+)\}?"""),
        ) { match ->
            match.groupValues[1] + match.groupValues[2].map { superscripts[it] ?: it }.joinToString("")
        }
        .replace("!=", "≠")
        .replace("<=", "≤")
        .replace(">=", "≥")
}

private fun Offset.normalized(size: IntSize): Offset = Offset(
    x = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
    y = (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
)

private fun List<Offset>.boundsOrNull(): Rect? {
    if (size < 4) return null
    val left = minOf { it.x }
    val top = minOf { it.y }
    val right = maxOf { it.x }
    val bottom = maxOf { it.y }
    return Rect(left, top, right, bottom).takeIf {
        it.width >= 0.02f && it.height >= 0.015f
    }
}
