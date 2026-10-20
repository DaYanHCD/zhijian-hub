package com.fingertipfuture.hub.ui.chat

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubCardBorder
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 拖过卡片宽度的 40% 即可翻页，缩短滑出距离。 */
private const val SWIPE_OUT_FRACTION = 0.40f

internal enum class ChatRecommendationKind {
    Provider,
    Search,
    Permissions,
    WeatherPrompt,
    UsagePrompt,
    VirusPrompt,
}

internal enum class ChatRecommendationDestination {
    Providers,
    SearchSettings,
}

internal data class ChatRecommendationCard(
    val kind: ChatRecommendationKind,
    val number: Int,
    val eyebrow: String,
    val title: String,
    val body: String,
    val actionLabel: String,
    @DrawableRes val icon: Int,
)

internal fun buildChatRecommendations(
    hasProvider: Boolean,
    hasSearchProvider: Boolean,
    hasAnyPermission: Boolean,
): List<ChatRecommendationCard> {
    // 只要用户在「权限与隐私」里开启过任意一项，这张引导卡就退场；
    // 权限页自己会同步系统授权，避免“明明开了卡还在”的假状态。
    val permissionsReady = hasAnyPermission

    val guide = buildList {
        if (!hasProvider) {
            add(
                ChatRecommendationCard(
                    kind = ChatRecommendationKind.Provider,
                    number = 0,
                    eyebrow = "新手引导 · 模型",
                    title = "先接入一个模型",
                    body = "指尖Hub 不内置 Key。添加供应商并选好模型，就可以开始聊天。",
                    actionLabel = "添加供应商",
                    icon = R.drawable.ic_provider,
                ),
            )
        }
        if (!hasSearchProvider) {
            add(
                ChatRecommendationCard(
                    kind = ChatRecommendationKind.Search,
                    number = 0,
                    eyebrow = "新手引导 · 搜索",
                    title = "添加搜索供应商",
                    body = "接入搜索后，助手才能查最新消息、网页和实时信息，而不只依赖模型记忆。",
                    actionLabel = "去添加",
                    icon = R.drawable.ic_search,
                ),
            )
        }
        if (!permissionsReady) {
            add(
                ChatRecommendationCard(
                    kind = ChatRecommendationKind.Permissions,
                    number = 0,
                    eyebrow = "新手引导 · 权限",
                    title = "开启应用权限",
                    body = "允许位置、通知、相机、麦克风等权限。助手只在你主动询问时使用对应能力，普通聊天不会读取设备信息。",
                    actionLabel = "去设置",
                    icon = R.drawable.ic_attach_lite_permission,
                ),
            )
        }
        add(
            ChatRecommendationCard(
                kind = ChatRecommendationKind.WeatherPrompt,
                number = 0,
                eyebrow = "试着问问",
                title = "现在天气怎么样",
                body = "试试助手能不能结合位置和联网搜索，回答你所在地的实时天气。",
                actionLabel = "问天气",
                icon = R.drawable.ic_brightness_6,
            ),
        )
        add(
            ChatRecommendationCard(
                kind = ChatRecommendationKind.UsagePrompt,
                number = 0,
                eyebrow = "试着问问",
                title = "看看今天用了多久的设备",
                body = "试试读取屏幕时间，看看今天用了多久，以及哪些应用花的时间最多。",
                actionLabel = "查看使用时间",
                icon = R.drawable.ic_history,
            ),
        )
        add(
            ChatRecommendationCard(
                kind = ChatRecommendationKind.VirusPrompt,
                number = 0,
                eyebrow = "试着问问",
                title = "手机里有没有病毒软件呢？",
                body = "试试让助手结合已安装应用和搜索结果，判断手机里有没有可疑软件。",
                actionLabel = "检查一下",
                icon = R.drawable.ic_attach_lite_permission,
            ),
        )
    }

    return guide.take(3).mapIndexed { index, card -> card.copy(number = index + 1) }
}

@Composable
internal fun ChatRecommendationDeck(
    cards: List<ChatRecommendationCard>,
    onAction: (ChatRecommendationKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val cardWidth = minOf(
            maxWidth - 42.dp,
            330.dp,
            maxHeight / 1.48f,
        ).coerceAtLeast(190.dp)
        val cardHeight = cardWidth * 1.395f
        val compact = cardWidth < 285.dp
        val density = LocalDensity.current
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardKeys = cards.map { it.kind }

        var order by remember(cardKeys) { mutableStateOf(cards.indices.toList()) }
        var dragX by remember { mutableFloatStateOf(0f) }
        var animationJob by remember { mutableStateOf<Job?>(null) }
        var exiting by remember { mutableStateOf(false) }
        var entered by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(cardKeys) {
            animationJob?.cancel()
            animationJob = null
            dragX = 0f
            exiting = false
            entered = true
        }

        val entrance by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "recommendation-deck-entrance",
        )

        fun cancelAnimation() {
            animationJob?.cancel()
            animationJob = null
        }

        fun settleBack() {
            if (dragX == 0f) return
            cancelAnimation()
            exiting = false
            animationJob = scope.launch {
                animate(
                    initialValue = dragX,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ -> dragX = value }
            }
        }

        fun moveFrontToBack() {
            val direction = if (dragX == 0f) 1f else sign(dragX)
            cancelAnimation()
            exiting = true
            animationJob = scope.launch {
                animate(
                    initialValue = dragX,
                    targetValue = direction * cardWidthPx * 1.65f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ) { value, _ -> dragX = value }
                if (order.size > 1) {
                    order = order.drop(1) + order.first()
                }
                dragX = 0f
                exiting = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance
                    scaleX = 0.96f + 0.04f * entrance
                    scaleY = 0.96f + 0.04f * entrance
                },
            contentAlignment = Alignment.Center,
        ) {
            order.forEachIndexed { depth, cardIndex ->
                val card = cards.getOrNull(cardIndex) ?: return@forEachIndexed
                key(card.kind) {
                    val front = depth == 0
                    val targetScale = 1f - depth * 0.008f
                    val targetRotation = when {
                        depth == 0 -> 0f
                        depth % 2 == 0 -> depth * 0.45f
                        else -> -depth * 0.36f
                    }
                    val targetY = (-depth * 4f).dp
                    val targetX = (depth * 1.6f).dp
                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "card-scale-${card.kind}",
                    )
                    val rotation by animateFloatAsState(
                        targetValue = targetRotation,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "card-rotation-${card.kind}",
                    )
                    val shiftY by animateDpAsState(
                        targetValue = targetY,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "card-y-${card.kind}",
                    )
                    val shiftX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "card-x-${card.kind}",
                    )
                    val contentAlpha by animateFloatAsState(
                        // 下一张提前把内容铺好：滑走的牌保持不透明，牌面移开时直接露出下一张，
                        // 不会先看到空白卡，再等内容“弹”出来。
                        targetValue = if (depth <= 1) 1f else 0f,
                        animationSpec = tween(if (front) 220 else 100),
                        label = "card-content-${card.kind}",
                    )
                    val xPx = with(density) { shiftX.toPx() }
                    val yPx = with(density) { shiftY.toPx() }
                    val currentX = if (front) dragX else xPx
                    val currentRotation = if (front) {
                        (dragX / cardWidthPx) * 10f
                    } else {
                        rotation
                    }
                    val dragModifier = if (front) {
                        // 不要把动画状态放进 key：回弹/滑出期间也要能立刻接住下一次拖动。
                        Modifier.pointerInput(order.firstOrNull(), cardWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    // 新手势优先：取消还在进行的回弹或滑出动画，
                                    // 从卡片当前位移继续跟手。
                                    cancelAnimation()
                                    exiting = false
                                },
                                onDragEnd = {
                                    if (abs(dragX) > cardWidthPx * SWIPE_OUT_FRACTION) {
                                        moveFrontToBack()
                                    } else {
                                        settleBack()
                                    }
                                },
                                onDragCancel = {
                                    // 滑出动画已经启动时，取消事件不应把它拉回来。
                                    if (!exiting && animationJob?.isActive != true && dragX != 0f) {
                                        settleBack()
                                    }
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    dragX = (dragX + amount).coerceIn(
                                        -cardWidthPx * 1.65f,
                                        cardWidthPx * 1.65f,
                                    )
                                },
                            )
                        }
                    } else {
                        Modifier
                    }

                    RecommendationCardFace(
                        card = card,
                        total = cards.size,
                        compact = compact,
                        interactive = front,
                        contentAlpha = contentAlpha,
                        onAction = onAction,
                        modifier = Modifier
                            .size(width = cardWidth, height = cardHeight)
                            .zIndex((cards.size - depth).toFloat())
                            .graphicsLayer {
                                translationX = currentX
                                translationY = yPx
                                scaleX = scale
                                scaleY = scale
                                rotationZ = currentRotation
                            }
                            .then(dragModifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCardFace(
    card: ChatRecommendationCard,
    total: Int,
    compact: Boolean,
    interactive: Boolean,
    contentAlpha: Float,
    onAction: (ChatRecommendationKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 和聊天页标题栏共用同一套主题色，不再单独写死黑白。
    val cardColor = HubCapsuleColor
    val ink = HubInk
    val muted = HubInkSoft
    val shape = RoundedCornerShape(if (compact) 24.dp else 29.dp)
    val innerShape = RoundedCornerShape(if (compact) 18.dp else 22.dp)

    Box(
        modifier = modifier
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
            .shadow(
                elevation = if (interactive) 20.dp else 6.dp,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(cardColor)
            .hubCardBorder(shape, width = 1.4.dp, color = ink.copy(alpha = 0.92f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .hubCardBorder(innerShape, width = 1.dp, color = ink.copy(alpha = 0.17f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 18.dp else 22.dp,
                    vertical = if (compact) 18.dp else 22.dp,
                )
                .graphicsLayer { alpha = contentAlpha },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.eyebrow,
                    color = muted,
                    fontSize = if (compact) 9.5.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    text = card.number.toString().padStart(2, '0') + " / " +
                        total.toString().padStart(2, '0'),
                    color = muted,
                    fontSize = if (compact) 9.5.sp else 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 49.dp else 61.dp)
                        .shadow(10.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(cardColor)
                        .hubCardBorder(CircleShape, width = 1.4.dp, color = ink),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = card.icon,
                        contentDescription = null,
                        size = if (compact) 24.dp else 28.dp,
                        tint = ink,
                    )
                }
                Spacer(Modifier.height(if (compact) 18.dp else 25.dp))
                Text(
                    text = card.title,
                    color = ink,
                    fontSize = if (compact) 21.sp else 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = if (compact) 25.sp else 32.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 2,
                )
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    text = card.body,
                    color = muted,
                    fontSize = if (compact) 12.5.sp else 14.sp,
                    lineHeight = if (compact) 18.sp else 24.sp,
                    maxLines = if (compact) 3 else 4,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 42.dp else 47.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .hubCardBorder(CircleShape, width = 1.4.dp, color = ink)
                    .clickable(enabled = interactive) { onAction(card.kind) }
                    .padding(start = if (compact) 14.dp else 16.dp, end = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.actionLabel,
                        color = ink,
                        fontSize = if (compact) 12.5.sp else 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(if (compact) 24.dp else 27.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "→",
                            color = ink,
                            fontSize = if (compact) 15.sp else 17.sp,
                        )
                    }
                }
            }
        }
    }
}
