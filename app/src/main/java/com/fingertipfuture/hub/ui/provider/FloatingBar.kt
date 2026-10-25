package com.fingertipfuture.hub.ui.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.chat.ChatMetrics
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder

/**
 * 提供商这几页共用的「悬浮」零件。
 *
 * 颜色和阴影**跟设置页 / 聊天页那套完全一致**：底色 `HubCapsuleColor`，
 * 外阴影用同一条 `hubDropShadow`（x0 / y4 / 模糊 4），不再用 Material 的 8dp elevation。
 */

/**
 * 悬浮栏高度（两侧的按钮要跟它对齐）。
 *
 * ⚠️ 2026-10-15：聊天页那两颗胶囊按用户的新规格改成了 50 / 圆角 25 / 图标 24（见 [ChatMetrics]），
 * 提供商这几页**没有一起改** —— 这里原先借的就是聊天页的常量，现在改成自己的字面值，
 * 免得"改聊天页顺手把提供商页也改了"。要一起改的话再明确说。
 */
internal val FLOATING_BAR_HEIGHT = 52.dp

/** 悬浮栏圆角。 */
internal val FLOATING_BAR_RADIUS = 26.dp

/** 悬浮栏里的图标尺寸。 */
internal val FLOATING_BAR_ICON_SIZE = 22.dp

/** 悬浮栏距状态栏下沿的距离（与聊天页一致）。 */
internal val FLOATING_BAR_TOP_MARGIN = ChatMetrics.TOP_BAR_TOP_MARGIN

/** 正文要空出来的顶部高度：悬浮栏 + 一点呼吸位。 */
internal val FLOATING_BAR_SPACE = FLOATING_BAR_TOP_MARGIN + FLOATING_BAR_HEIGHT + 10.dp

/** 渐隐层在悬浮栏下沿之外多留的尾巴（照「指尖」的设置页）。 */
private val TOP_FADE_TAIL = 56.dp

/** 状态栏高度。 */
@Composable
internal fun statusBarTopPadding() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * 状态栏渐隐层：顶部是**50% 底色**（上限，规矩），往下渐渐变透明。
 * 内容从悬浮栏下面穿过去的时候就会"慢慢变淡"（照「指尖」的设置类页面）。
 *
 * @param extraTop 状态栏下面再压多高（一般 = 悬浮栏高度 + 一点余量）。
 * @param tail     渐隐尾巴还要往下延伸多少（越大越"化得开"）。
 */
@Composable
internal fun TopFadeLayer(
    modifier: Modifier = Modifier,
    extraTop: Dp = 68.dp,
    tail: Dp = TOP_FADE_TAIL,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTopPadding() + extraTop + tail)
            .background(
                brush = Brush.verticalGradient(
                    // ⚠️ 规矩（用户 2026-10-14 亲自定的）：**顶部最多 50% 底色**，不能更浓。
                    //    我一度擅自改成"顶部完全不透明"，被骂回来了 —— 别再动这个值。
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    0.28f to MaterialTheme.colorScheme.background.copy(alpha = 0.38f),
                    0.58f to MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                    0.82f to MaterialTheme.colorScheme.background.copy(alpha = 0.06f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** 「← 标题」一整颗胶囊，整颗可点，点了返回。 */
@Composable
internal fun FloatingBackBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FLOATING_BAR_RADIUS)
    Surface(
        color = HubCapsuleColor,
        shape = shape,
        tonalElevation = 0.dp,
        // 阴影自己画（hubDropShadow）：和设置页列表 / 聊天页胶囊同一条
        shadowElevation = 0.dp,
        modifier = modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .height(FLOATING_BAR_HEIGHT)
            .clickable(onClick = onBack),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.action_back),
                size = FLOATING_BAR_ICON_SIZE,
                tint = HubInk,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 右上角那颗圆形按钮（同样的底色 + 同样的悬浮阴影）。 */
@Composable
internal fun FloatingCircleAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = HubCapsuleColor,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .hubDropShadow(CircleShape)
            .hubCardBorder(CircleShape)
            .size(FLOATING_BAR_HEIGHT)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MaterialSymbol(
                resId = icon,
                contentDescription = contentDescription,
                size = FLOATING_BAR_ICON_SIZE,
                tint = HubInk,
            )
        }
    }
}
