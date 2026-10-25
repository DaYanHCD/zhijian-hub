package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.theme.enforceHubTypography

private const val LINK_TAG = "markdown-link"

private val inlineToken = Regex(
    """(\$\$[^$\n]+\$\$|\$[^$\n]+\$|\\\([\s\S]*?\\\)|\\\[[\s\S]*?\\\]|[A-Za-zρΔ][A-Za-z]?_\([A-Za-z0-9]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~|`[^`\n]+`|\[[^\]\n]+\]\([^)\n]+\)|\*[^*\n]+\*|_[^_\n]+_|https?://[^\s<>()]+)""",
)
private val inlineLink = Regex("""^\[([^\]\n]+)\]\(([^)\n]+)\)$""")
private val bareSubscript = Regex("""^[A-Za-zρΔ][A-Za-z]?_\([A-Za-z0-9]+\)$""")
private val trailingUrlPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？')

/**
 * 聊天回复的 Markdown 渲染器。
 *
 * 设计参考 RikkaHub：标题层级、链接、列表、引用、围栏代码、表格都使用独立视觉块，
 * 不再是整段普通文字。实现完全基于 Compose + 本地解析器，没有 Markdown / WebView 依赖。
 */
@Composable
internal fun MarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = HubInk,
    bodySize: androidx.compose.ui.unit.TextUnit = 16.sp,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit = 24.sp,
    blockGap: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val blocks = remember(text) {
        MarkdownParser.parse(removeSpacesAfterEmoji(text))
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(blockGap),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor,
                        fontSize = bodySize,
                        lineHeight = bodyLineHeight,
                    ),
                )

                is MarkdownBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 24.sp
                        2 -> 21.sp
                        3 -> 19.sp
                        4 -> 17.sp
                        else -> 16.sp
                    }
                    InlineMarkdownText(
                        text = block.text,
                        style = TextStyle(
                            color = if (block.level >= 5) textColor.copy(alpha = 0.78f) else textColor,
                            fontSize = fontSize,
                            lineHeight = fontSize * 1.3f,
                            fontWeight = FontWeight.Bold,
                        ).enforceHubTypography(),
                    )
                }

                is MarkdownBlock.BulletList -> MarkdownList(
                    items = block.items,
                    textColor = textColor,
                    bodySize = bodySize,
                    bodyLineHeight = bodyLineHeight,
                )
                is MarkdownBlock.Quote -> MarkdownQuote(block.text, textColor)
                is MarkdownBlock.CodeBlock -> {
                    if (block.language.lowercase() in setOf("latex", "tex", "math", "katex")) {
                        MathFormulaBlock(block.code)
                    } else {
                        MarkdownCodeBlock(block)
                    }
                }
                is MarkdownBlock.Table -> MarkdownTable(block, textColor)
                is MarkdownBlock.Math -> MathFormulaBlock(block.source)
                MarkdownBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(text: String, style: TextStyle) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val codeColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor, codeBg, codeColor, style.color) {
        buildInlineMarkdown(text, linkColor, codeBg, codeColor, style.color)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    ClickableText(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.let { url -> runCatching { uriHandler.openUri(url) } }
        },
    )
}

private fun buildInlineMarkdown(
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
    mathColor: Color,
): AnnotatedString {
    val text = raw
        .replace("<br>", "\n", ignoreCase = true)
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
    return buildAnnotatedString {
        var cursor = 0
        inlineToken.findAll(text).forEach { match ->
            if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
            val value = match.value
            when {
                value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(value.substring(2, value.length - 2))
                }
                value.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(value.substring(2, value.length - 2))
                }
                value.startsWith("~~") -> withStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                ) { append(value.substring(2, value.length - 2)) }
                value.startsWith("`") -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeColor,
                        background = codeBg,
                    ),
                ) { append(value.substring(1, value.length - 1)) }
                value.startsWith("$$") -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Normal,
                        fontWeight = FontWeight.Medium,
                        color = mathColor,
                    ),
                ) {
                    append(MathText.format(value.substring(2, value.length - 2)))
                }
                value.startsWith("$") -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Normal,
                        fontWeight = FontWeight.Medium,
                        color = mathColor,
                    ),
                ) {
                    append(MathText.format(value.substring(1, value.length - 1)))
                }
                value.startsWith("\\(") || value.startsWith("\\[") -> {
                    val closing = if (value.startsWith("\\(")) "\\)" else "\\]"
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Normal,
                            fontWeight = FontWeight.Medium,
                            color = mathColor,
                        ),
                    ) {
                        append(
                            MathText.format(
                                value.removePrefix(if (value.startsWith("\\(")) "\\(" else "\\[")
                                    .removeSuffix(closing),
                            ),
                        )
                    }
                }
                bareSubscript.matches(value) -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Normal,
                        fontWeight = FontWeight.Medium,
                        color = mathColor,
                    ),
                ) {
                    append(MathText.format(value))
                }
                value.startsWith("[") -> {
                    val link = inlineLink.matchEntire(value)
                    if (link == null) {
                        append(value)
                    } else {
                        val label = link.groupValues[1]
                        val url = link.groupValues[2].trim()
                        pushStringAnnotation(LINK_TAG, url)
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                                background = linkColor.copy(alpha = 0.08f),
                            ),
                        ) { append(label) }
                        pop()
                    }
                }
                value.startsWith("http://") || value.startsWith("https://") -> {
                    val url = value.trimEnd(*trailingUrlPunctuation)
                    pushStringAnnotation(LINK_TAG, url)
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                            background = linkColor.copy(alpha = 0.08f),
                        ),
                    ) { append(url) }
                    pop()
                    if (url.length < value.length) append(value.substring(url.length))
                }
                value.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(value.substring(1, value.length - 1))
                }
                value.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(value.substring(1, value.length - 1))
                }
                else -> append(value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

@Composable
private fun MarkdownList(
    items: List<MarkdownListItem>,
    textColor: Color,
    bodySize: androidx.compose.ui.unit.TextUnit,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width((item.level * 16).dp))
                Text(
                    text = item.marker,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                Spacer(Modifier.width(4.dp))
                InlineMarkdownText(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor,
                        fontSize = bodySize,
                        lineHeight = bodyLineHeight,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(text: String, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
        )
        Spacer(Modifier.width(10.dp))
        InlineMarkdownText(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = textColor.copy(alpha = 0.78f),
                fontSize = 15.sp,
                lineHeight = 23.sp,
            ),
        )
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val panel = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.45f,
    )
    val header = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.7f,
    )
    val scroll = rememberScrollState()

    Surface(
        color = panel,
        shape = shape,
        border = if (UiPrefs.cardBorders) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(header)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    color = HubInkSoft,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(block.code))
                            context.showLocalizedToast("代码已复制")
                        }
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_copy,
                        contentDescription = "复制代码",
                        size = 14.dp,
                        tint = HubInkSoft,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制", color = HubInkSoft, fontSize = 11.5.sp)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = block.code.ifEmpty { " " },
                    color = HubInk,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table, textColor: Color) {
    if (table.header.isEmpty()) return
    val outerShape = RoundedCornerShape(12.dp)
    val tableBg = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.35f,
    )
    val headBg = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.75f,
    )
    val cellBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Surface(
        color = tableBg,
        shape = outerShape,
        border = if (UiPrefs.cardBorders) BorderStroke(1.dp, cellBorder) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = table.header.size
            val cellWidth = (maxWidth / columns.toFloat()).coerceAtLeast(94.dp)
            val tableWidth = cellWidth * columns.toFloat()
            val scroll = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Column(modifier = Modifier.width(tableWidth)) {
                    Row(modifier = Modifier.fillMaxWidth().background(headBg)) {
                        table.header.forEach { value ->
                            TableCell(
                                value = value,
                                width = cellWidth,
                                header = true,
                                borderColor = cellBorder,
                                textColor = textColor,
                            )
                        }
                    }
                    table.rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex == 0) {
                            HorizontalDivider(color = cellBorder)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (rowIndex % 2 == 0) tableBg
                                    else MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                ),
                        ) {
                            row.forEach { value ->
                                TableCell(
                                    value = value,
                                    width = cellWidth,
                                    header = false,
                                    borderColor = cellBorder,
                                    textColor = textColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    value: String,
    width: androidx.compose.ui.unit.Dp,
    header: Boolean,
    borderColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .width(width)
            .border(0.5.dp, borderColor)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        InlineMarkdownText(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (header) textColor else textColor.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
