package com.fingertipfuture.hub.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 设置类页面统一的卡片底色。
 *
 * 50% 混合后既能和页面背景分开，又不会像纯 surfaceVariant 在深色模式下那样突然变亮。
 */
@Composable
fun settingsCardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return lerp(scheme.background, scheme.surfaceVariant, 0.5f)
}
