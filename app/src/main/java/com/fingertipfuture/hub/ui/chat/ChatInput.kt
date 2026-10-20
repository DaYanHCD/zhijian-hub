package com.fingertipfuture.hub.ui.chat

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubFieldColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.HubSendColor
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder

/**
 * 悬浮输入胶囊。
 *
 * 规格（用户 2026-10-15）：高 50、宽 = 屏宽 - 20、圆角 25、底色 #E0E8FF，距屏幕底部 10；
 * 内部四个部件——麦克风、输入框、表情、添加——两两间距 10，最左/最右部件距胶囊左右两边 10，
 * 四个部件全部垂直居中。麦克风/表情/添加 24×24；输入框高 40、圆角 25，
 * 宽度 = 胶囊宽 - 122。整颗胶囊带外阴影（x0 / y4 / 模糊 4 / 黑 25%）。
 *
 * 第 4 个部件按状态变（都是 24×24，位置不变）：
 *   - 正在生成 → 「停止」（流式要能随时掐断）
 *   - **输入框有内容 → 「发送」**（Lucide `send-horizontal`，颜色 #6236FF，用户 2026-10-15 指定）
 *   - 其余 → 「＋」（添加栏）
 *
 * **语音态（2026-10-15：只做 UI，不做功能）**：点麦克风 → 左端那颗变成「键盘」、
 * 输入框换成「长按说话」；长按「长按说话」会滑出一个**语音选择框**（转文字 / 发送 / 取消），
 * 手指滑到哪颗上哪颗就高亮，松手收起（不触发任何功能）。
 */
@Composable
internal fun ChatInputCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    /** 点「＋」：开/关下面的添加栏（照微信那个）。 */
    onAddClick: () -> Unit,
    /** 添加栏是否开着（开着时「＋」用主色，提示当前状态）。 */
    addOpen: Boolean,
    /** 点「表情」：开/关下面的表情栏（照微信那个）。 */
    onEmojiClick: () -> Unit,
    /** 表情栏是否开着（开着时「表情」用主色，提示当前状态）。 */
    emojiOpen: Boolean,
    modifier: Modifier = Modifier,
    /** 隐藏表情按钮；输入框会自动占满它原来的宽度。 */
    showEmojiButton: Boolean = true,
    /** 是不是语音态（语音态：左端是键盘键、中间是「长按说话」，**只做 UI**）。 */
    voiceMode: Boolean,
    /** 切换打字态 / 语音态。 */
    onVoiceModeChange: (Boolean) -> Unit,
    /** 长按「长按说话」→ **开始录音**（外面检查权限、开 MediaRecorder）。 */
    onVoiceStart: () -> Unit = {},
    /** 松手 → 落在哪一颗上（没滑上去 = 发送，和微信一致；[VoiceAction.Cancel] 就是取消）。 */
    onVoiceEnd: (VoiceAction) -> Unit = {},
    /** 输入框重新获得焦点（要打字了）：外面借此把添加栏收起来。 */
    onFieldFocused: () -> Unit = {},
    /** 胶囊**本体**的高度（多行会长高）；外层 Box 里还藏着 100dp 的语音卡片，不能量外层。 */
    onCapsuleHeightChanged: (Dp) -> Unit = {},
    /** 是否已经进入全屏输入（全屏按钮此时不可再点）。 */
    fullScreen: Boolean = false,
    /** 点胶囊左上角的全屏输入按钮。 */
    onFullScreenChange: (Boolean) -> Unit = {},
    /**
     * 胶囊上**按下**的任何一下（点输入框、点图标都算）。
     *
     * 为什么要有这个：外面要"在键盘把胶囊顶起来之前"记下"列表贴不贴着底"。
     * 光靠 `onFieldFocused` 不够 —— 输入框**已经拿着焦点**时，再点一次输入框只会把键盘
     * 唤回来，`onFocusChanged` 不会再触发（2026-10-15 用户反馈过）。
     */
    focusRequester: FocusRequester,
) {
    val capsuleShape = RoundedCornerShape(ChatMetrics.INPUT_CAPSULE_RADIUS)
    val fieldShape = RoundedCornerShape(ChatMetrics.INPUT_FIELD_RADIUS)
    // BoxWithConstraints 的 scope 不是 Density，所以 px 值先在这里算好（手势里要用）
    val density = LocalDensity.current

    // 语音态（只做 UI）由外面的 ChatScreen 持有：这样「＋」那条路径也能知道现在是语音态
    // 长按之后滑出来的语音选择框；以及手指现在压在哪一颗上（松手就清掉）
    var voiceCardOpen by remember { mutableStateOf(false) }
    var voiceHovered by remember { mutableStateOf<VoiceAction?>(null) }
    // BasicTextField 实际排出来的行数（自动换行也算）；到 3 行出现左上角全屏按钮。
    var lineCount by remember { mutableIntStateOf(1) }
    val showFullScreenButton = !voiceMode && lineCount >= ChatMetrics.INPUT_FULLSCREEN_MIN_LINES
    // 左上内缩必须相等：距胶囊左边 10、上边也 10。
    val fullScreenButtonInset = ChatMetrics.INPUT_CAPSULE_CONTENT_PADDING


    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChatMetrics.INPUT_CAPSULE_SIDE_MARGIN,
                end = ChatMetrics.INPUT_CAPSULE_SIDE_MARGIN,
                bottom = ChatMetrics.INPUT_CAPSULE_BOTTOM_MARGIN,
            ),
    ) {
        // 语音选择框和胶囊同宽同左沿 —— 手指从「长按说话」往上滑，滑到哪个三分之一就是哪颗
        val cardWidth = maxWidth
        // 「长按说话」那颗在胶囊里的起点：左内边距 + 左端图标 + 间距
        val holdStartPx = with(density) {
            (ChatMetrics.INPUT_CAPSULE_CONTENT_PADDING + ChatMetrics.INPUT_ICON_SIZE + ChatMetrics.INPUT_PART_GAP).toPx()
        }
        val cardPadPx = with(density) { ChatMetrics.VOICE_CARD_PADDING.toPx() }
        val gapPx = with(density) { ChatMetrics.VOICE_ACTION_GAP.toPx() }
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val buttonWidthPx = (cardWidthPx - cardPadPx * 2 - gapPx * 2) / 3f

        /** 手指在「长按说话」里的 x 换算成"压在语音框哪一颗上"；还没滑到框上就是 null。 */
        fun actionAt(local: Offset): VoiceAction? {
            if (local.y > 0f) return null               // 还在胶囊那一行，没往上滑到框上
            if (buttonWidthPx <= 0f) return null
            val xInCard = local.x + holdStartPx - cardPadPx
            val index = ((xInCard + gapPx) / (buttonWidthPx + gapPx)).toInt().coerceIn(0, 2)
            return VoiceAction.entries[index]
        }

        // 语音选择框：高 100、**底边和胶囊底边对齐** → 它的下半截正好被胶囊挡住
        // （用户 2026-10-15 的要求）。所以它要画在胶囊**之前**（在下层），
        // 三颗按钮放在卡片的上半部分（= 露在胶囊上面的那 50dp 里）。
        // 卡片是"**从输入胶囊下面钻出来**"的（用户 2026-10-15）：
        //   起点/终点 = 再往下藏一个胶囊的高度（50dp）—— 这时它整个躲在胶囊背后，
        //   滑上来的过程就是"从胶囊底下露出来"。
        // ⚠️ 别写成 `initialOffsetY = { it }`（it = 整张卡片的高度 100dp）：那样它是从
        //   屏幕最底下往上滑的，看着像"从界面底部出现"，而不是从胶囊下面钻出来（踩过）。
        val hiddenOffsetPx = with(density) { ChatMetrics.INPUT_CAPSULE_HEIGHT.roundToPx() }
        // 卡片"最终位置"是 100dp 高、贴底（下半截和胶囊重叠）。外层这个 Box 只负责定位。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(ChatMetrics.VOICE_CARD_HEIGHT),
        ) {
            // 可视区域 = **胶囊上轮廓以上的整块地方**（见 VoiceCardWindowShape）：
            //   · 中间是一条直线（正好是胶囊顶边），两端顺着胶囊自己的圆角往下走 25dp；
            //   · 于是卡片的下边界和胶囊的上边界**完全贴合** —— 没有缝、没有直角、
            //     也不是"两端半圆的胶囊形"，看着就是卡片从胶囊里长出来（用户要的"无感"）。
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(ChatMetrics.VOICE_CARD_HEIGHT)
                    .clip(VoiceCardWindowShape()),
            ) {
                AnimatedVisibility(
                    visible = voiceCardOpen,
                    // 纯位移、不带淡入淡出：从窗口下面滑上来，"半截露出来"才像实物
                    enter = slideInVertically(initialOffsetY = { hiddenOffsetPx }, animationSpec = tween(220)),
                    exit = slideOutVertically(targetOffsetY = { hiddenOffsetPx }, animationSpec = tween(220)),
                ) {
                    VoiceActionCard(width = cardWidth, height = ChatMetrics.VOICE_CARD_HEIGHT, hovered = voiceHovered)
                }
            }
        }
        Surface(
                color = HubCapsuleColor,
                shape = capsuleShape,
                tonalElevation = 0.dp,
                // 阴影自己画（hubDropShadow）：Material 的 elevation 推不出"x0 y4 模糊4"
                shadowElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hubDropShadow(capsuleShape)
                    .hubCardBorder(capsuleShape)
                    .fillMaxWidth()
                    .onSizeChanged { onCapsuleHeightChanged(with(density) { it.height.toDp() }) }
                    // 单行时正好 50（规格）；输入框写到多行时整颗胶囊跟着长高
                    .heightIn(min = ChatMetrics.INPUT_CAPSULE_HEIGHT),
            ) {
                Box {
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ChatMetrics.INPUT_CAPSULE_CONTENT_PADDING,
                            vertical = ChatMetrics.INPUT_CAPSULE_VERTICAL_PADDING,
                        ),
                    // ⚠️ 图标绑在胶囊的「下底」：多行变高时麦克风/表情/加号跟着最后一行走，
                    // 不跟着顶部往下飘（用户 2026-10-15：要像「指尖」app 那个输入框）。
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // ① 左端：麦克风 ↔ 键盘（滑动切换；见 MicKeyboardSwap，不用 AnimatedContent）
                    // ⚠️ 下面这颗 Box 的 8dp 下内边距：图标是**贴胶囊下底**对齐的（多行时跟着最后一行走），
                    // 但单行/空态时它必须仍然**落在胶囊正中间**（用户 2026-10-15：位置无论如何不能变），
                    // 5(胶囊内边距) + 8 + 24/2 = 25 = 50 高胶囊的正中。
                    Box(Modifier.padding(bottom = ICON_BOTTOM_LIFT)) {
                        MicKeyboardSwap(voiceMode = voiceMode, onVoiceModeChange = onVoiceModeChange)
                    }
                    Spacer(Modifier.width(ChatMetrics.INPUT_PART_GAP))

                    // ② 中间：输入框 ↔「长按说话」
                    if (voiceMode) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(ChatMetrics.VOICE_HOLD_HEIGHT)
                                .clip(fieldShape)
                                .background(HubFieldColor)
                                .hubCardBorder(fieldShape)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            voiceCardOpen = true
                                            voiceHovered = actionAt(offset)
                                            onVoiceStart()          // 开录
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            voiceHovered = actionAt(change.position)
                                        },
                                        // 松手：**必须滑到某颗按钮上才算数**（用户 2026-10-15 定）——
                                        // 滑到「发送」发语音、滑到「转文字」转成文字直接发；
                                        // 没滑上去（还在胶囊那一行）或滑到「取消」= 默认取消，什么都不发。
                                        onDragEnd = {
                                            val action = voiceHovered ?: VoiceAction.Cancel
                                            voiceCardOpen = false
                                            voiceHovered = null
                                            onVoiceEnd(action)
                                        },
                                        onDragCancel = {
                                            voiceCardOpen = false
                                            voiceHovered = null
                                            onVoiceEnd(VoiceAction.Cancel)
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_voice_hold),
                                color = HubInk,
                                fontSize = ChatMetrics.INPUT_TEXT_SIZE,
                            )
                        }
                    } else {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            maxLines = ChatMetrics.INPUT_FIELD_MAX_LINES,
                            textStyle = TextStyle(
                                color = HubInk,
                                fontSize = ChatMetrics.INPUT_TEXT_SIZE,
                                lineHeight = ChatMetrics.INPUT_TEXT_LINE,
                            ),
                            cursorBrush = SolidColor(HubInk),
                            // 键盘右下角显示「发送」，按它就真的发出去
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            onTextLayout = { lineCount = it.lineCount },
                            modifier = Modifier
                                // 剩下的宽度正好是"胶囊宽 - 122"（其它部件都是定值）
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) onFieldFocused() }
                                .focusRequester(focusRequester)
                                .clip(fieldShape)
                                .background(HubFieldColor)
                                .hubCardBorder(fieldShape),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = ChatMetrics.INPUT_FIELD_TEXT_PADDING,
                                            vertical = 10.dp,
                                        ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    // 空态用占位文字撑出一行高（20dp），避免「一输入就变矮」
                                    Box(
                                        modifier = Modifier.heightIn(min = 20.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.chat_hint),
                                                color = HubInkSoft,
                                                fontSize = ChatMetrics.INPUT_TEXT_SIZE,
                                                lineHeight = ChatMetrics.INPUT_TEXT_LINE,
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(ChatMetrics.INPUT_PART_GAP))

                    // ③ 表情（同样贴着下底 + ICON_BOTTOM_LIFT，空态时落在胶囊正中）
                    // 隐藏时整个图标和它后面的间距都不占位，输入框的 weight(1f) 会自然补上。
                    if (showEmojiButton) {
                        MaterialSymbol(
                            resId = R.drawable.ic_emoji_add,
                            contentDescription = stringResource(R.string.chat_action_emoji),
                            modifier = Modifier
                                .padding(bottom = ICON_BOTTOM_LIFT)
                                .clickable(onClick = onEmojiClick),
                            size = ChatMetrics.INPUT_ICON_SIZE,
                            tint = if (emojiOpen) MaterialTheme.colorScheme.primary else HubInk,
                        )
                        Spacer(Modifier.width(ChatMetrics.INPUT_PART_GAP))
                    }

                    // ④ 第 4 个部件：停止 / 发送 / 添加（都是 24×24，位置不变）
                    // 第 4 颗：停止 / 发送 / 添加（滑动切换；见 FourthSlotSwap）
                    Box(Modifier.padding(bottom = ICON_BOTTOM_LIFT)) {
                        FourthSlotSwap(
                        slot = when {
                            streaming -> InputSlot.Stop
                            !voiceMode && value.isNotEmpty() -> InputSlot.Send
                            else -> InputSlot.Add
                        },
                        addOpen = addOpen,
                        onStop = onStop,
                        onSend = onSend,
                            onAddClick = onAddClick,
                        )
                    }
                }
                    // 输入排到 3 行：左上角出现和麦克风同尺寸的全屏输入按钮。
                    // 它浮在 Row 上面，不参与排布，不会顶动麦克风 / 输入框 / 表情 / 发送。
                    if (showFullScreenButton) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = fullScreenButtonInset,
                                    top = fullScreenButtonInset,
                                )
                                .size(ChatMetrics.INPUT_ICON_SIZE)
                                .clickable(enabled = !fullScreen) { onFullScreenChange(true) },
                        ) {
                            MaterialSymbol(
                                resId = R.drawable.ic_open_in_full,
                                contentDescription = stringResource(R.string.chat_action_fullscreen),
                                size = ChatMetrics.INPUT_ICON_SIZE,
                                tint = HubInk,
                            )
                        }
                    }
                }
            }
    }
}

/**
 * 全屏输入：照用户 2026-10-17 给的图 —— 聊天页整体压暗，白色顶部圆角面板从顶栏下面升起，
 * 面板里只有右上角「保存」和大块输入区。
 *
 * 不另开 Window / Activity；和胶囊共用同一个 `value / onValueChange`，
 * 所以点「保存」或系统返回后，内容已经同步在原输入胶囊里。
 */
/** 输入胶囊第 4 颗的三种状态（互相之间都走"旧的右滑走、新的左滑入"）。 */
private enum class InputSlot { Stop, Send, Add }

/** 三个图标相对胶囊下底要抬高多少（让空态/单行时它们落在胶囊正中间，和原来一致）。 */
private val ICON_BOTTOM_LIFT = 8.dp

/**
 * 胶囊里图标互相切换时的参数（两处共用：麦克风↔键盘、停止/发送/添加）。
 *
 * 位移只走**半个图标宽**：两颗图标在同一个 24dp 的坑里换，位移走满的话，一半路程上它们会
 * **正好叠在一起**（旧的往右、新的从左，谁也没让开）→ 看着像"两个图标糊成一团"
 * （用户 2026-10-15 说的"动画有很大问题"就是这个）。
 * 所以：位移减半 + **配一层淡出淡入**（[ICON_SWAP_FADE_SPEC]，稍快一点先走完），
 * 交叉那一瞬间新图标已经基本实、旧图标已经基本透明 —— 干净的一次"换"。
 *
 * ⚠️ 实现必须是 **AnimatedVisibility（不裁切）**，不能用 AnimatedContent ——
 * 后者把内容裁在自己那个 24dp 的框里，滑动时图标正跑在框外，会被切掉一半。
 */
private val ICON_SWAP_SPEC = tween<IntOffset>(durationMillis = 300)
private val ICON_SWAP_FADE_SPEC = tween<Float>(durationMillis = 180)

/** 语音选择框里的三颗按钮（只做 UI，点到什么都不做）。 */
internal enum class VoiceAction(@StringRes val label: Int) {
    ToText(R.string.chat_voice_to_text),
    Send(R.string.chat_voice_send),
    Cancel(R.string.chat_voice_cancel),
}

/**
 * 语音选择框：灰色卡片 + 三颗淡紫胶囊（转文字 / 发送 / 取消）。
 *
 * 外观照用户 2026-10-15 给的图：卡片用和输入框同一支灰蓝，三颗按钮等分、间距 10；
 * 手指压住的那颗用发送色淡淡高亮一下（图里没有这一层，是给"滑到哪颗"用的反馈）。
 * 卡片自带和胶囊同一条外阴影，并且压在输入胶囊上（重叠见 VOICE_CARD_OVERLAP）。
 */
@Composable
private fun VoiceActionCard(
    width: Dp,
    height: Dp,
    hovered: VoiceAction?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ChatMetrics.VOICE_CARD_RADIUS)
    Surface(
        color = HubFieldColor,
        shape = shape,
        tonalElevation = 0.dp,
        // 用户 2026-10-15：语音选择框**不要外阴影**（它是从胶囊下面钻出来的，
        // 有阴影反而会在胶囊圆角外面留一圈灰影）
        shadowElevation = 0.dp,
        modifier = modifier
            .width(width)
            .height(height)
            .hubCardBorder(shape),
    ) {
        // ⚠️ Surface 的 content 不是 BoxScope（没有 align），所以自己套一层 Box 再贴顶
        Box {
          Row(
            // 三颗按钮贴在卡片**上半部分**（下半截会被输入胶囊挡住，不能放东西）
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = ChatMetrics.VOICE_CARD_PADDING,
                    end = ChatMetrics.VOICE_CARD_PADDING,
                    top = ChatMetrics.VOICE_ACTION_TOP_PADDING,
                ),
            horizontalArrangement = Arrangement.spacedBy(ChatMetrics.VOICE_ACTION_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceAction.entries.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ChatMetrics.VOICE_ACTION_HEIGHT)
                        .clip(RoundedCornerShape(ChatMetrics.VOICE_ACTION_RADIUS))
                        .background(
                            if (item == hovered) {
                                HubSendColor.copy(alpha = 0.25f)
                            } else {
                                HubCapsuleColor
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(item.label),
                        color = HubInk,
                        fontSize = 14.sp,
                    )
                }
            }
          }
        }
    }
}

/**
 * 语音卡片可视区域：**胶囊上轮廓以上的整块地方**。
 *
 * 为什么必须自定义 Shape：
 *   - 卡片和胶囊**等宽**、胶囊两端是**半圆** —— 卡片直接铺那片区域时，它两条直边必然从胶囊
 *     两端圆角外侧露出来，看着像"直角从胶囊圆角里透出来"；
 *   - 换成圆角矩形窗口裁，卡片又会变成**胶囊形**（两端成半圆）而且和胶囊之间留一条缝。
 * 正解是让可视区域的**下边界 = 胶囊的上轮廓**：中间直线、两端顺着胶囊的圆角往下走。
 * 这样卡片的下边和胶囊的上边完全重合，既没缝也没直角。
 *
 * 尺寸都从 [ChatMetrics] 取（胶囊高度 50、圆角 25），别写死。
 */
private class VoiceCardWindowShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { ChatMetrics.TOP_BAR_RADIUS.toPx() }   // 25 = 胶囊圆角
        val capTop = size.height - with(density) { ChatMetrics.INPUT_CAPSULE_HEIGHT.toPx() }
        val path = Path().apply {
            moveTo(0f, capTop + r)
            // 左上：从胶囊左端(0, capTop+r)顺着圆角到(r, capTop)
            arcTo(Rect(0f, capTop, 2 * r, capTop + 2 * r), 180f, 90f, false)
            lineTo(size.width - r, capTop)
            // 右上：从(width-r, capTop)顺着圆角到(width, capTop+r)
            arcTo(Rect(size.width - 2 * r, capTop, size.width, capTop + 2 * r), 270f, 90f, false)
            lineTo(size.width, 0f)
            lineTo(0f, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 左端那颗：麦克风 ↔ 键盘，**滑动切换**（旧的往右滑走、新的从左滑进来）。
 *
 * ⚠️ 用两条 `AnimatedVisibility` 而不是 `AnimatedContent`：后者会把内容裁在自己那个
 * 24dp 的框里，滑动时图标正跑在框外 → 看着就是"图标被裁掉一半"（用户 2026-10-15 反馈）。
 */
@Composable
private fun MicKeyboardSwap(
    voiceMode: Boolean,
    onVoiceModeChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Box(modifier = Modifier.size(ChatMetrics.INPUT_ICON_SIZE)) {
        AnimatedVisibility(
            visible = !voiceMode,
            enter = slideInHorizontally(animationSpec = ICON_SWAP_SPEC) { -it / 2 } +
                fadeIn(animationSpec = ICON_SWAP_FADE_SPEC),
            exit = slideOutHorizontally(animationSpec = ICON_SWAP_SPEC) { it / 2 } +
                fadeOut(animationSpec = ICON_SWAP_FADE_SPEC),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_mic,
                contentDescription = stringResource(R.string.chat_action_voice),
                modifier = Modifier.clickable {
                    focusManager.clearFocus()          // 进语音态：先收键盘
                    onVoiceModeChange(true)
                },
                size = ChatMetrics.INPUT_ICON_SIZE,
                tint = HubInk,
            )
        }
        AnimatedVisibility(
            visible = voiceMode,
            enter = slideInHorizontally(animationSpec = ICON_SWAP_SPEC) { -it / 2 } +
                fadeIn(animationSpec = ICON_SWAP_FADE_SPEC),
            exit = slideOutHorizontally(animationSpec = ICON_SWAP_SPEC) { it / 2 } +
                fadeOut(animationSpec = ICON_SWAP_FADE_SPEC),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_keyboard,
                contentDescription = stringResource(R.string.chat_action_keyboard),
                // 切回打字态**不自动叫键盘**（用户 2026-10-15）：只把输入框换回来，键盘等点输入框再出来。
                // 这里也不 requestFocus()，顺带避开「FocusRequester 还没绑定」那个卡退（见《技术说明》9.4）。
                modifier = Modifier.clickable { onVoiceModeChange(false) },
                size = ChatMetrics.INPUT_ICON_SIZE,
                tint = HubInk,
            )
        }
    }
}

/**
 * 第 4 颗：停止 / 发送 / 添加，同样**滑动切换**（旧的往右滑走、新的从左滑进来）。
 * 实现理由同 [MicKeyboardSwap]：用 `AnimatedVisibility`，不用 `AnimatedContent`（会裁内容）。
 */
@Composable
private fun FourthSlotSwap(
    slot: InputSlot,
    addOpen: Boolean,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onAddClick: () -> Unit,
) {
    Box(modifier = Modifier.size(ChatMetrics.INPUT_ICON_SIZE)) {
        AnimatedVisibility(
            visible = slot == InputSlot.Stop,
            enter = slideInHorizontally(animationSpec = ICON_SWAP_SPEC) { -it / 2 } +
                fadeIn(animationSpec = ICON_SWAP_FADE_SPEC),
            exit = slideOutHorizontally(animationSpec = ICON_SWAP_SPEC) { it / 2 } +
                fadeOut(animationSpec = ICON_SWAP_FADE_SPEC),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_stop,
                contentDescription = stringResource(R.string.chat_stop),
                modifier = Modifier.clickable(onClick = onStop),
                size = ChatMetrics.INPUT_ICON_SIZE,
                // 和旁边那几颗一样用主墨色（用户 2026-10-15：停止要跟图片里 ⊕ 那排图标同一种样子，
                // 而图片里胶囊上的图标都是黑的；原来用主题 primary 是偏蓝的 #3B5BDB）。
                tint = HubInk,
            )
        }
        // 语音态不显示发送键（用户 2026-10-15：切到语音时「添加」不变成发送）
        AnimatedVisibility(
            visible = slot == InputSlot.Send,
            enter = slideInHorizontally(animationSpec = ICON_SWAP_SPEC) { -it / 2 } +
                fadeIn(animationSpec = ICON_SWAP_FADE_SPEC),
            exit = slideOutHorizontally(animationSpec = ICON_SWAP_SPEC) { it / 2 } +
                fadeOut(animationSpec = ICON_SWAP_FADE_SPEC),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_send,
                contentDescription = stringResource(R.string.chat_send),
                modifier = Modifier.clickable(onClick = onSend),
                size = ChatMetrics.INPUT_ICON_SIZE,
                tint = HubSendColor,
            )
        }
        AnimatedVisibility(
            visible = slot == InputSlot.Add,
            enter = slideInHorizontally(animationSpec = ICON_SWAP_SPEC) { -it / 2 } +
                fadeIn(animationSpec = ICON_SWAP_FADE_SPEC),
            exit = slideOutHorizontally(animationSpec = ICON_SWAP_SPEC) { it / 2 } +
                fadeOut(animationSpec = ICON_SWAP_FADE_SPEC),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_circle_plus,
                contentDescription = stringResource(R.string.chat_action_add),
                modifier = Modifier.clickable(onClick = onAddClick),
                size = ChatMetrics.INPUT_ICON_SIZE,
                tint = if (addOpen) MaterialTheme.colorScheme.primary else HubInk,
            )
        }
    }
}
