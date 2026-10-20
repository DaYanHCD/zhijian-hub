package com.fingertipfuture.hub.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 标题栏配色。
 *
 * 与「指尖」的设置 / 聊天标题栏同一套算法：浅色主题取「背景 ↔ primaryContainer」
 * 的 82% 混合（看起来是一层很淡的主题色），深色主题直接用 surfaceVariant。
 * 两个 App 的顶栏因此观感一致。
 */
@Composable
fun settingsChromeColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        scheme.surfaceVariant
    } else {
        lerp(scheme.background, scheme.primaryContainer, 0.82f)
    }
}
