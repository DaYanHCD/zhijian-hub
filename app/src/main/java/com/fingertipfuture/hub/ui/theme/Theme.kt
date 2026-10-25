package com.fingertipfuture.hub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.fingertipfuture.hub.ui.component.DarkHubChrome
import com.fingertipfuture.hub.ui.component.LightHubChrome
import com.fingertipfuture.hub.ui.component.LocalHubChrome

/**
 * 指尖Hub 主题。
 *
 * 纯原生 Material 3：能用系统动态取色（Android 12+）就用，取不到就用一套蓝紫配色兜底，
 * 不引任何第三方 UI 库。**页面底色固定成白色**（用户 2026-10-15 从 ACCEE7 改成白，见 [HubPageBackground]）。
 */

/**
 * App 页面底色。
 *
 * 只覆盖**页面底色**（`colorScheme.background`）—— 顶栏胶囊 / 输入胶囊 / 卡片 / 抽屉
 * 这些浮在上面的"面"仍然走各自主题里的 surface，否则会和底色糊成一片。
 * 深色模式不动：浅蓝底配深色模式的文字和深色面会看不清。
 */
private val HubPageBackground = Color(0xFFFFFFFF)

/** 页面底色上的深色前景（白底上同样用它，对比度足够）。 */
private val HubPageInk = Color(0xFF16232B)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001A55),
    surface = Color(0xFFFBF9FF),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF45464F),
    background = HubPageBackground,
    onBackground = HubPageInk,
    outlineVariant = Color(0xFFC6C6D0),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF002A75),
    primaryContainer = Color(0xFF1B3A8F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF44464F),
    onSurface = Color(0xFFE7E5EC),
    onSurfaceVariant = Color(0xFFCAC4D0),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE7E5EC),
    outlineVariant = Color(0xFF5B5D67),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun HubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    // 浅色模式：页面底色固定成白色（动态取色取回来的也盖掉 —— 用户指定的色不能被壁纸改）
    val scheme = if (darkTheme) {
        base
    } else {
        base.copy(background = HubPageBackground, onBackground = HubPageInk)
    }
    // 状态栏 / 导航栏透明（真·边到边）：内容一直铺到屏幕最上面，
    // 图标颜色跟着主题走，免得深色下黑图标看不见。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 聊天页那套"定值"外观（胶囊底色 / 墨色 / 输入框 / 发送键）也要跟着深浅走：
    // 深色下仍旧用浅蓝胶囊 + 深墨色 = 看不见，见 ui/component/CapsuleStyle.kt 的 HubChrome。
    CompositionLocalProvider(LocalHubChrome provides if (darkTheme) DarkHubChrome else LightHubChrome) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HubTypography,
            content = content,
        )
    }
}
