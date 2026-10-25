package com.fingertipfuture.hub.ui.chat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.data.isFromUser
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.HubShadowColor
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder

internal enum class MessageAction(@StringRes val labelRes: Int) {
    Copy(R.string.message_action_copy),
    Regenerate(R.string.message_action_regenerate),
    Resend(R.string.message_action_resend),
    Edit(R.string.message_action_edit),
    More(R.string.message_action_more),
}

/** 一排小按钮里的一项：动作 + 图标。 */
private data class ActionItem(val action: MessageAction, @DrawableRes val icon: Int)

// 输出区那一排：复制 / 重新生成 / 更多
// （2026-10-15 用户要求去掉「编辑」—— 助手回复不给改，输入区那条还留着编辑）
private val OUTPUT_ACTIONS = listOf(
    ActionItem(MessageAction.Copy, R.drawable.ic_copy),
    ActionItem(MessageAction.Regenerate, R.drawable.ic_regenerate),
    ActionItem(MessageAction.More, R.drawable.ic_more_vertical),
)

private val INPUT_ACTIONS = listOf(
    ActionItem(MessageAction.Copy, R.drawable.ic_copy),
    ActionItem(MessageAction.Resend, R.drawable.ic_regenerate),
    ActionItem(MessageAction.Edit, R.drawable.ic_edit),
    ActionItem(MessageAction.More, R.drawable.ic_more_vertical),
)

/**
 * 时间分隔（日期，一次完整对话的头一行）：字号 11、水平居中，长按看秒。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimeLabel(at: Long, now: Long) {
    var showSeconds by remember(at) { mutableStateOf(false) }
    // 文案按（消息时间 + 今天/昨天这个「天」）缓存：formatChatTime 每次要 new 4 个 Calendar
    // 还要查几次 stringResource，而流式输出时列表每来一片增量都会重组，不缓存就全在每帧重做。
    val labels = rememberChatTimeLabels()
    val label = remember(at, now, labels) { formatChatTime(at, now, labels, withSeconds = false) }
    val labelWithSeconds = remember(at, now, labels) { formatChatTime(at, now, labels, withSeconds = true) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { showSeconds = false },
                onLongClick = { showSeconds = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (showSeconds) labelWithSeconds else label,
            color = HubInkSoft,
            fontSize = ChatMetrics.TIME_SIZE,
        )
    }
}

/**
 * 一条消息：自己的走**输入区**（靠右），AI 的走**输出区**（靠左）。
 *
 * 两块的外边距都是 10dp（头像距本侧界面边缘 10dp，规格），内部按 5dp 叠。
 */
@Composable
internal fun MessageBlock(
    message: HubMessage,
    onAction: (MessageAction, HubMessage) -> Unit,
    onImageClick: (HubAttachment) -> Unit = {},
    /**
     * 这条能不能编辑（只有"我发的最近三条"是 true，用户 2026-10-15 定的）。
     * 不能编辑时输入区那排**不显示「编辑」**（不摆一个点了没反应的死按钮）。
     */
    canEdit: Boolean = false,
) {
    if (message.isFromUser) {
        InputBlock(
            message = message,
            canEdit = canEdit,
            onAction = onAction,
            onImageClick = onImageClick,
        )
    } else {
        OutputBlock(message = message, onAction = onAction, onImageClick = onImageClick)
    }
}

/** 昵称：14 号、软墨色；规格要求它也带一层和胶囊一样的外阴影（文字阴影）。 */
@Composable
private fun Nickname(text: String) {
    val density = LocalDensity.current
    // 主题色只能在组合期读，所以先取出来再进 remember（2026-10-16 加深色模式时改）
    val shadowColor = HubShadowColor
    val shadow = remember(density, shadowColor) {
        with(density) {
            Shadow(color = shadowColor, offset = Offset(0f, 4.dp.toPx()), blurRadius = 4.dp.toPx())
        }
    }
    Text(
        text = text,
        color = HubInkSoft,
        fontSize = ChatMetrics.BODY_NICKNAME_SIZE,
        maxLines = 1,
        style = TextStyle(shadow = shadow),
    )
}

/** 消息下方那一排小按钮（图标 22×22）。 */
@Composable
private fun ActionRow(
    actions: List<ActionItem>,
    gap: Dp,
    atEnd: Boolean,
    onAction: (MessageAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (atEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.width(gap))
            MaterialSymbol(
                resId = item.icon,
                contentDescription = stringResource(item.action.labelRes),
                modifier = Modifier.clickable { onAction(item.action) },
                size = ChatMetrics.ACTION_ICON_SIZE,
                tint = HubInk,
            )
        }
    }
}

/**
 * 输出区（AI 的回复）：头像 + 昵称 + 思考框 + 回复内容 + 一排按钮，全部靠左。
 */
@Composable
private fun OutputBlock(
    message: HubMessage,
    onAction: (MessageAction, HubMessage) -> Unit,
    onImageClick: (HubAttachment) -> Unit,
) {
    // 默认展不展开由「设置 › 界面 › 展示思考内容」决定；
    // key 用 message.at（这条消息的时间戳，唯一且不随流式变化）—— 以前 key 是 reasoning，
    // 流式每来一片就会把状态重置一次，用户手动收起也会被弹回来。
    var reasoningOpen by remember(message.at) { mutableStateOf(UiPrefs.showThinking) }
    // 思考正文到达时**不要**替用户展开。RikkaHub 的思考卡片默认保持时间线，
    // 只有用户点标题才展开；否则第一段 reasoning 一来就把工具 / 搜索行挤出屏幕。
    LaunchedEffect(message.streaming) {
        if (!message.streaming && UiPrefs.autoCollapseThinking) reasoningOpen = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChatMetrics.BODY_SIDE_MARGIN,
                end = ChatMetrics.BODY_SIDE_MARGIN,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 没有模型 Logo 时 MessageAvatar 会退回默认圆底机器人，头像不会消失。
            MessageAvatar(isSelf = false)
            Spacer(Modifier.width(ChatMetrics.BODY_NICKNAME_GAP))
            // 昵称用**当前助手**的名字（设置 › 助手管理 里能改）
            Nickname(text = AssistantStore.current.name)
        }

        // 思考框：只要这条在流式生成中就**立刻**占位（还没量到时长时显示「思考中…」）。
        // 以前是等 thoughtMs/reasoning 到了才插进来，晚一步出现会把下面的内容整体顶下去
        // （用户 2026-10-17：延迟出现，然后把内容挤到输入胶囊下面）。
        // 老消息没有时长、也不是流式 → 不占地方。
        if (!UiPrefs.hideThinking && hasThinkingContent(message)) {
            Spacer(Modifier.height(ChatMetrics.BODY_BLOCK_GAP))
            ThinkBox(
                message = message,
                open = reasoningOpen,
                onToggle = { reasoningOpen = !reasoningOpen },
            )

        }

        if (message.attachments.isNotEmpty()) {
            Spacer(Modifier.height(ChatMetrics.BODY_BLOCK_GAP))
            MessageAttachments(
                attachments = message.attachments,
                fromUser = false,
                modifier = Modifier.widthIn(max = ChatMetrics.BODY_BOX_MAX_WIDTH),
                onImageClick = onImageClick,
            )
        }

        if (message.text.isNotBlank() || (message.streaming && message.attachments.isEmpty())) {
            Spacer(Modifier.height(ChatMetrics.BODY_BLOCK_GAP))
            if (message.streaming && message.text.isEmpty()) {
                // 已经开始生成但还没有内容时，先显示一个省略号占位
                Text(
                    text = "…",
                    color = HubInk,
                    fontSize = ChatMetrics.BODY_TEXT_SIZE,
                    lineHeight = ChatMetrics.BODY_TEXT_LINE,
                )
            } else {
                MarkdownBody(text = message.text)
            }
        }

        Spacer(Modifier.height(ChatMetrics.ACTION_TOP_GAP))
        ActionRow(
            actions = OUTPUT_ACTIONS,
            gap = ChatMetrics.ACTION_GAP_OUTPUT,
            atEnd = false,
            onAction = { onAction(it, message) },
        )
    }
}

/**
 * 输入区（我发的）：昵称 + 头像靠右，下面是**内容框**（宽高自适应内容、圆角 10），
 * 再下面一排按钮靠右。
 */
@Composable
private fun InputBlock(
    message: HubMessage,
    canEdit: Boolean,
    onAction: (MessageAction, HubMessage) -> Unit,
    onImageClick: (HubAttachment) -> Unit,
) {
    // 单表情最终高度必须与普通文字气泡一致：内边距相同，内容高度取正文行高。
    // 用 sp → dp 换算，系统字体放大时也会跟普通文字气泡一起变化。
    val normalEmojiSize = with(LocalDensity.current) {
        ChatMetrics.BODY_TEXT_LINE.toDp()
    }
    // 不能编辑时把「编辑」摘掉，其余照旧（这一排是右对齐，少一颗只是整体往右缩）
    val actions = if (canEdit) INPUT_ACTIONS else INPUT_ACTIONS.filterNot { it.action == MessageAction.Edit }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChatMetrics.BODY_SIDE_MARGIN,
                end = ChatMetrics.BODY_SIDE_MARGIN,
            ),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Nickname(text = stringResource(R.string.avatar_me))
            // 「设置 › 界面 › 显示用户头像」关掉就不画这颗（昵称留着）
            if (UiPrefs.showUserAvatar) {
                Spacer(Modifier.width(ChatMetrics.BODY_NICKNAME_GAP))
                MessageAvatar(isSelf = true)
            }
        }

        Spacer(Modifier.height(ChatMetrics.BODY_BLOCK_GAP))

        if (message.attachments.isNotEmpty() && message.text.isBlank()) {
            // 只有附件、没有文字：附件自己就是内容（图片卡片 / 文件卡片），不再套一层框
            MessageAttachments(
                attachments = message.attachments,
                fromUser = true,
                modifier = Modifier.widthIn(max = ChatMetrics.BODY_BOX_MAX_WIDTH),
                onImageClick = onImageClick,
            )
        } else {
            val boxShape = RoundedCornerShape(ChatMetrics.BODY_BOX_RADIUS)
            Surface(
                color = HubCapsuleColor,
                shape = boxShape,
                tonalElevation = 0.dp,
                // 外阴影自己画（hubDropShadow）：与标题栏 / 功能栏 / 输入胶囊 / 思考框同一条
                shadowElevation = 0.dp,
                modifier = Modifier
                    .hubDropShadow(boxShape)
                    .hubCardBorder(boxShape)
                    .widthIn(max = ChatMetrics.BODY_BOX_MAX_WIDTH),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = ChatMetrics.BODY_BOX_PADDING_H,
                        vertical = ChatMetrics.BODY_BOX_PADDING_V,
                    ),
                ) {
                    MessageAttachments(
                        attachments = message.attachments,
                        fromUser = true,
                        onImageClick = onImageClick,
                    )
                    if (message.attachments.isNotEmpty() && message.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    }
                    if (message.text.isNotBlank() || message.streaming) {
                        val singleEmoji = if (
                            !message.streaming &&
                            message.attachments.isEmpty()
                        ) {
                            singleEmojiOrNull(message.text)
                        } else {
                            null
                        }
                        if (singleEmoji != null) {
                            NotoEmojiGlyph(
                                symbol = singleEmoji,
                                size = 52.dp,
                                normalSize = normalEmojiSize,
                                animateOnce = message.playEmojiOnce,
                                playbackKey = message.id,
                            )
                        } else {
                            Text(
                                text = if (message.streaming && message.text.isEmpty()) "…" else message.text,
                                color = HubInk,
                                fontSize = ChatMetrics.BODY_TEXT_SIZE,
                                lineHeight = ChatMetrics.BODY_TEXT_LINE,
                                localize = false,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ChatMetrics.ACTION_TOP_GAP))
        ActionRow(
            actions = actions,
            gap = ChatMetrics.ACTION_GAP_INPUT,
            atEnd = true,
            onAction = { onAction(it, message) },
        )
    }
}
