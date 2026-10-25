package com.fingertipfuture.hub.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.data.LanguageStore
import com.fingertipfuture.hub.ui.theme.enforceHubTypography

/**
 * 全 App 的统一文字入口。
 *
 * 默认把界面文案按当前 App 语言转换；模型正文 / 代码等动态内容由调用方传 `localize = false`，
 * 避免改写用户内容。迁移时只需要把 `androidx.compose.material3.Text` 的 import 换成这个别名。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    localize: Boolean = true,
) {
    val enforcedStyle = style.enforceHubTypography()
    val shown = if (localize) LanguageStore.localize(text) else text
    Text(
        text = shown.withCharacterBreakOpportunities(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.sp,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout ?: {},
        style = enforcedStyle,
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun LocalizedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    localize: Boolean = true,
) {
    val enforcedStyle = style.enforceHubTypography()
    val shown = if (localize) {
        val converted = LanguageStore.localize(text.text)
        if (converted == text.text) {
            text
        } else if (converted.length == text.text.length) {
            AnnotatedString(converted, text.spanStyles, text.paragraphStyles)
        } else {
            AnnotatedString(converted)
        }
    } else {
        text
    }
    Text(
        text = shown,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.sp,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout ?: {},
        style = enforcedStyle,
    )
}
