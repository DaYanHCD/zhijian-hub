package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder

/**
 * 顶栏：左边标题栏 + 右边功能栏，两颗都是悬浮胶囊。
 *
 * 规格（用户 2026-10-15 给定，见 [ChatMetrics]）：
 *   - 标题栏**宽度自适应内容**（上限 136dp，见下）：内容短则窄、超过上限就打省略号；
 *     高 50、圆角 25、底色 #E0E8FF；
 *     距状态栏下沿 5、距屏幕左边 10。
 *     里面三部分：展开按钮（24×24，距标题栏左边 7、垂直居中）、
 *     当前会话名（14 号）、当前会话模型（12 号）——后两者上下排列、间距 0，
 *     整体在展开按钮右侧 10。
 *   - 两颗胶囊都带同一条外阴影（x0 / y4 / 模糊 4 / 扩展 0 / 黑 25%）。
 *   - 图标全部是 Lucide（`开发脚本\资源\生成Lucide图标.js` 从官方 SVG 转的）。
 *
 * 整颗标题栏可点 = 打开左侧抽屉（和 RikkaHub 一样：点标题进侧栏）。
 */
@Composable
internal fun ChatTopBar(
    title: String,
    subtitle: String,
    onTitleClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSearchClick: () -> Unit,
    /**
     * 外部修饰符（**真悬浮**用）：聊天页会传 `Modifier.align(TopCenter).statusBarsPadding()`，
     * 让顶栏浮在消息列表上面、列表从它底下穿过去。
     */
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(ChatMetrics.TOP_BAR_RADIUS)

    BoxWithConstraints(
        modifier = modifier
            .padding(
                start = ChatMetrics.TOP_BAR_SIDE_MARGIN,
                end = ChatMetrics.TOP_BAR_SIDE_MARGIN,
                top = ChatMetrics.TOP_BAR_TOP_MARGIN,
            )
            .fillMaxWidth(),
    ) {
        // 标题栏**宽度跟着内容走**（用户 2026-10-15：长度自适应），但有一道上限：
        //   ① 上限 = ChatMetrics.TITLE_BAR_WIDTH_MAX（136dp）；
        //   ② 分屏 / 折叠屏这种极窄窗口再让一让，别把右边功能栏挤出屏幕，最少留 80dp。
        // 超出上限的文字由下面两个 Text 自己打省略号（maxLines = 1 + Ellipsis）。
        val titleMaxWidth = ChatMetrics.TITLE_BAR_WIDTH_MAX
            .coerceAtMost(maxWidth - ChatMetrics.FUNCTION_BAR_WIDTH - 8.dp)
            .coerceAtLeast(80.dp)

        Row(
            // IntrinsicSize.Min：系统字体放大时两颗胶囊一起长高（仍然一样高）
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ① 标题栏
            Surface(
                color = HubCapsuleColor,
                shape = capsuleShape,
                tonalElevation = 0.dp,
                // 阴影自己画（hubDropShadow）：Material 的 elevation 推不出"x0 y4 模糊4"
                shadowElevation = 0.dp,
                modifier = Modifier
                    .hubDropShadow(capsuleShape)
                    .hubCardBorder(capsuleShape)
                    // widthIn(max)：内容多宽就多宽，封顶 titleMaxWidth
                    .widthIn(max = titleMaxWidth)
                    // heightIn(min) 而不是 height()：系统字体放大时胶囊跟着长高，
                    // 写死高度会把两行文字挤扁（这个坑踩过）
                    .heightIn(min = ChatMetrics.TITLE_BAR_HEIGHT)
                    .fillMaxHeight(),
            ) {
                // ⚠️ 这里**不能** fillMaxWidth()：那样胶囊永远顶到上限，就谈不上"自适应"了。
                // 让 Row 自己 wrap 内容（图标 + 间隔 + 两行文字），Surface 跟着它量。
                Row(
                    modifier = Modifier
                        .clickable(onClick = onTitleClick)
                        // 右侧留白不能省：胶囊自适应内容 + 按圆角裁切，没这段留白最长的那行会被圆角切掉
                        .padding(
                            start = ChatMetrics.TITLE_BAR_ICON_INSET,
                            end = ChatMetrics.TITLE_BAR_TEXT_END_PADDING,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_sidebar,
                        contentDescription = stringResource(R.string.chat_action_drawer),
                        size = ChatMetrics.TOP_BAR_ICON_SIZE,
                        tint = HubInk,
                    )
                    Spacer(Modifier.width(ChatMetrics.TITLE_BAR_TEXT_GAP))
                    // 两行（会话名 + 模型名），行间不加间距。
                    // 可用宽度由外层封顶推出来：最多 136 - 7 - 24 - 10 - 10 = 85dp，
                    // 名字/模型谁超了谁自己打省略号（maxLines = 1 + Ellipsis），短了则整颗胶囊跟着收窄。
                    Column {
                        Text(
                            text = title,
                            color = HubInk,
                            fontSize = ChatMetrics.TITLE_BAR_NAME_SIZE,
                            fontWeight = FontWeight.Medium,
                            lineHeight = ChatMetrics.TITLE_BAR_NAME_LINE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            color = HubInkSoft,
                            fontSize = ChatMetrics.TITLE_BAR_MODEL_SIZE,
                            lineHeight = ChatMetrics.TITLE_BAR_MODEL_LINE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ② 功能栏：新对话 + 搜索（78×50，同底色、同阴影）
            Surface(
                color = HubCapsuleColor,
                shape = capsuleShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .hubDropShadow(capsuleShape)
                    .hubCardBorder(capsuleShape)
                    .width(ChatMetrics.FUNCTION_BAR_WIDTH)
                    .heightIn(min = ChatMetrics.FUNCTION_BAR_HEIGHT)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChatMetrics.FUNCTION_BAR_SIDE_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_new_chat,
                        contentDescription = stringResource(R.string.chat_action_new_chat),
                        modifier = Modifier.clickable(onClick = onNewChatClick),
                        size = ChatMetrics.TOP_BAR_ICON_SIZE,
                        tint = HubInk,
                    )
                    Spacer(Modifier.width(ChatMetrics.FUNCTION_BAR_ICON_GAP))
                    MaterialSymbol(
                        resId = R.drawable.ic_search,
                        contentDescription = stringResource(R.string.chat_action_search),
                        modifier = Modifier.clickable(onClick = onSearchClick),
                        size = ChatMetrics.TOP_BAR_ICON_SIZE,
                        tint = HubInk,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 正文：一次完整对话 = 日期 + 输入区（我）+ 输出区（AI）
// ---------------------------------------------------------------------------

/**
 * 消息下面那一排小按钮。
 *
 * 2026-10-15 的规格里，输入区是「复制 / 重新发送 / 编辑 / 更多」，
 * 输出区是「复制 / 重新生成 / 翻译 / 编辑 / 更多」。
 * 这里的按钮只负责把动作交给 ChatScreen；复制、重新生成、重发、编辑和更多都已接入。
 */
