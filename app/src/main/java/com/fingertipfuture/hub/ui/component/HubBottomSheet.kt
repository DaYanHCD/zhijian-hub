package com.fingertipfuture.hub.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 底部弹层（自己画的，不用 Material3 的 ModalBottomSheet）。
 *
 * 为什么自己画：M3 的 ModalBottomSheet 是**另开一个窗口**，在边到边（内容铺到状态栏/导航栏）的
 * 情况下窗口位置算不准，会出现「弹层底下还能看见聊天界面」的缝，而且多一层窗口本身也费性能
 * （用户 2026-10-14 反馈）。自己画就是这一层里的普通 Compose：遮罩 + 贴着屏幕底边的卡片，
 * 底部不额外避让导航栏，内容一直铺到手势区域；导航栏本身透明。
 *
 * 关掉的方式：点遮罩、按返回键、或调用方自己把 visible 置 false。
 */
@Composable
internal fun HubBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // 从下面滑上来（用固定位移做，不需要知道内容多高）
    val offset by animateDpAsState(if (shown) 0.dp else 520.dp, label = "sheet-offset")
    val scrim by animateFloatAsState(if (shown) 1f else 0f, label = "sheet-scrim")
    val sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    BackHandler { onDismiss() }

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩：点一下关掉
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f * scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = sheetShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hubCardBorder(sheetShape)
                .offset(y = offset),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 拖动条（装饰用：目前靠点遮罩/返回关）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                )
                content()
            }
        }
    }
}
