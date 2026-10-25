package com.fingertipfuture.hub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 全局等宽排版。
 *
 * 字距固定为 0；换行采用简单策略 + 宽松断行，超长 URL / 单词宁可在一行内拆开，
 * 也不把整个 token 推到下一行，避免上一行留下大片空白。
 */
private val HubLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Simple,
    strictness = LineBreak.Strictness.Loose,
    wordBreak = LineBreak.WordBreak.Default,
)

internal fun TextStyle.enforceHubTypography(): TextStyle = copy(
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.sp,
    lineBreak = HubLineBreak,
    hyphens = Hyphens.None,
)

private val DefaultTypography = Typography()

internal val HubTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.enforceHubTypography(),
    displayMedium = DefaultTypography.displayMedium.enforceHubTypography(),
    displaySmall = DefaultTypography.displaySmall.enforceHubTypography(),
    headlineLarge = DefaultTypography.headlineLarge.enforceHubTypography(),
    headlineMedium = DefaultTypography.headlineMedium.enforceHubTypography(),
    headlineSmall = DefaultTypography.headlineSmall.enforceHubTypography(),
    titleLarge = DefaultTypography.titleLarge.enforceHubTypography(),
    titleMedium = DefaultTypography.titleMedium.enforceHubTypography(),
    titleSmall = DefaultTypography.titleSmall.enforceHubTypography(),
    bodyLarge = DefaultTypography.bodyLarge.enforceHubTypography(),
    bodyMedium = DefaultTypography.bodyMedium.enforceHubTypography(),
    bodySmall = DefaultTypography.bodySmall.enforceHubTypography(),
    labelLarge = DefaultTypography.labelLarge.enforceHubTypography(),
    labelMedium = DefaultTypography.labelMedium.enforceHubTypography(),
    labelSmall = DefaultTypography.labelSmall.enforceHubTypography(),
)
