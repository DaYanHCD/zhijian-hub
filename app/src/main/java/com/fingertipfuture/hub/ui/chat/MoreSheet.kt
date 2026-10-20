package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubCardBorder

/**
 * 消息那一排的「更多」弹出来的**更多栏**（2026-10-15 用户给的图）。
 *
 * 规格（用户口述 + 设计稿量出来的）：
 * - **从界面下方滑出**（位移动画在 [com.fingertipfuture.hub.ui.ChatScreen] 里，220ms）；
 * - **上方两个角 25 的圆角**（[ChatMetrics.MORE_SHEET_RADIUS]）；
 * - 面板**中间有一颗黑色小条**（[ChatMetrics.MORE_SHEET_HANDLE_WIDTH] × 高 6、圆角取一半、
 *   距上边 6）—— 设计稿里就这一颗，面板里填什么内容还没给，先照图留空；
 * - 面板高 = 屏高的 55%（[ChatMetrics.MORE_SHEET_HEIGHT_FRACTION]），起来之后把输入胶囊盖住，
 *   和设计稿一致（稿子里面板起来时看不到输入胶囊）。
 *
 * 底色用的是和添加栏 / 表情栏同一个 `surfaceVariant`（这两块也是"从下面升起来的面板"，
 * 免得一个灰一个白）；设计稿那块灰是占位用的填色。
 */
@Composable
internal fun ChatMoreSheet(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(ChatMetrics.MORE_SHEET_HEIGHT_FRACTION)
            .clip(
                RoundedCornerShape(
                    topStart = ChatMetrics.MORE_SHEET_RADIUS,
                    topEnd = ChatMetrics.MORE_SHEET_RADIUS,
                ),
            )
            .hubCardBorder(
                RoundedCornerShape(
                    topStart = ChatMetrics.MORE_SHEET_RADIUS,
                    topEnd = ChatMetrics.MORE_SHEET_RADIUS,
                ),
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // 点面板本身不该把它关掉（点面板以外那层才是关闭）—— 用一个无涟漪的点击把事件吃掉
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = ChatMetrics.MORE_SHEET_HANDLE_TOP)
                .size(
                    width = ChatMetrics.MORE_SHEET_HANDLE_WIDTH,
                    height = ChatMetrics.MORE_SHEET_HANDLE_HEIGHT,
                )
                .clip(RoundedCornerShape(ChatMetrics.MORE_SHEET_HANDLE_HEIGHT / 2))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )

        // 面板里的第一项：**删除这条消息**（用户 2026-10-15 要的 —— 只删点「更多」的那一条，
        // 前后的输入/输出都不动）。一行「图标 + 文字」，点了就删、面板自己收掉。
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = MORE_DELETE_TOP, start = MORE_DELETE_SIDE)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDelete() }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_more_delete,
                contentDescription = null,
                size = 24.dp,
                tint = HubInk,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.more_delete_message),
                fontSize = 16.sp,
                color = HubInk,
            )
        }
    }
}

/** 「删除这条消息」那一行距面板上边 / 左边的距离（小条下面一点，别顶着圆角）。 */
private val MORE_DELETE_TOP = 44.dp
private val MORE_DELETE_SIDE = 10.dp
