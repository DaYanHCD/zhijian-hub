package com.fingertipfuture.hub.ui.chat

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubThinkingStep
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.SearchProvider
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import kotlinx.coroutines.delay

/**
 * 思考框。
 *
 * 默认只显示一条「思考了 X 秒」摘要，不露任何时间线内容。
 * 用户点这条摘要后才展开完整 ChainOfThought：搜索 / 技能 / 位置 / 记忆 / reasoning
 * 都按真实执行顺序排列。
 */
@Composable
internal fun ThinkBox(message: HubMessage, open: Boolean, onToggle: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expandedStepIds by remember(message.at) { mutableStateOf(emptySet<String>()) }
    val storedSteps = remember(message.thinkingSteps, message.skillsUsed, message.searchTraces) {
        realThinkingSteps(message)
    }
    LaunchedEffect(message.streaming, open) {
        while (message.streaming && !open) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    val steps = when {
        storedSteps.isNotEmpty() -> storedSteps
        message.streaming -> listOf(
            HubThinkingStep(
                id = "reasoning-pending-${message.at}",
                kind = "reasoning",
                title = "思考",
                status = HubThinkingStep.STATUS_PENDING,
                durationMs = (now - message.at).coerceAtLeast(1L),
            ),
        )
        message.thoughtMs > 0L -> listOf(
            HubThinkingStep(
                id = "reasoning-empty-${message.at}",
                kind = "reasoning",
                title = "思考",
                status = HubThinkingStep.STATUS_DONE,
                durationMs = message.thoughtMs,
            ),
        )
        else -> emptyList()
    }
    val expanded = open
    val fullWidth = expanded || expandedStepIds.isNotEmpty()
    val elapsedMs = when {
        message.thoughtMs > 0L -> message.thoughtMs
        message.streaming -> (now - message.at).coerceAtLeast(1L)
        else -> steps.sumOf { it.durationMs }.coerceAtLeast(0L)
    }
    val seconds = String.format(
        java.util.Locale.US,
        "%.1f",
        elapsedMs / 1000.0,
    )
    val live = message.streaming && message.thoughtMs <= 0L
    val shape = RoundedCornerShape(16.dp)
    val cardColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.5f,
    )

    Surface(
        color = cardColor,
        shape = shape,
        tonalElevation = 0.dp,
        // 阴影自己画（hubDropShadow）：和标题栏 / 输入胶囊 / 功能栏同一条
        shadowElevation = 0.dp,
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .then(
                if (fullWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    // 折叠态不再铺满整行：只有一行 reasoning 时收成小胶囊，
                    // 有搜索等长内容时最多到正文气泡的宽度。
                    Modifier.widthIn(
                        min = ChatMetrics.THINK_BOX_WIDTH,
                        max = ChatMetrics.BODY_BOX_MAX_WIDTH,
                    )
                },
            )
            .heightIn(min = ChatMetrics.THINK_BOX_HEIGHT),
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(vertical = 4.dp),
        ) {
            if (!expanded) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onToggle)
                        .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol(
                        resId = if (live) R.drawable.ic_psychology else R.drawable.ic_bulb,
                        contentDescription = null,
                        size = 16.dp,
                        tint = if (live) MaterialTheme.colorScheme.primary else HubInkSoft,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (live) {
                            "正在思考 $seconds 秒"
                        } else {
                            "思考了 $seconds 秒"
                        },
                        color = HubInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(6.dp))
                    MaterialSymbol(
                        resId = R.drawable.ic_expand,
                        contentDescription = stringResource(R.string.message_thought_expand),
                        size = 16.dp,
                        tint = HubInk,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedStepIds = emptySet()
                            onToggle()
                        }
                        .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MaterialSymbol(
                            resId = R.drawable.ic_expand,
                            contentDescription = null,
                            modifier = Modifier.rotate(180f),
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "收起",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (steps.isNotEmpty()) {
                    ThinkingTimeline(
                        message = message,
                        steps = steps,
                        fillWidth = fullWidth,
                        expandedStepIds = expandedStepIds,
                        onStepExpandedChange = { id, isExpanded ->
                            expandedStepIds = if (isExpanded) {
                                expandedStepIds + id
                            } else {
                                expandedStepIds - id
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 只保留真正需要展示的链路：reasoning / 搜索 / 技能 / 位置 / 记忆。
 * 本地「理解问题 / 模型规划 / 组织回答」只是内部状态，不冒充模型的思考步骤。
 */
private fun realThinkingSteps(message: HubMessage): List<HubThinkingStep> {
    val visibleKinds = setOf(
        "reasoning", "search", "skill", "location", "memory",
        "time", "screen_time", "calendar", "apps", "contacts", "sms", "mms",
    )
    val steps = message.thinkingSteps
        .filter { it.kind in visibleKinds }
        .toMutableList()
    if (steps.isEmpty()) {
        message.skillsUsed.forEachIndexed { index, skill ->
            steps +=
                HubThinkingStep(
                    id = "legacy-skill-${message.at}-$index",
                    kind = "skill",
                    title = "使用技能",
                    detail = skill.name,
                )
        }
        message.searchTraces.forEachIndexed { index, trace ->
            steps +=
                HubThinkingStep(
                    id = "legacy-search-${message.at}-$index",
                    kind = "search",
                    title = "搜索网页",
                    detail = trace.query,
                    status = when {
                        trace.pending -> HubThinkingStep.STATUS_PENDING
                        trace.error.isNotBlank() -> HubThinkingStep.STATUS_ERROR
                        else -> HubThinkingStep.STATUS_DONE
                    },
                    items = trace.results,
                    cached = trace.cached,
                    providerKinds = trace.providerKinds,
                )
        }
    }
    // 老消息只有一整段 reasoning；新消息已经按每次模型调用拆成了时间线步骤。
    if (steps.none { it.kind == "reasoning" } && message.reasoning.isNotBlank()) {
        steps += HubThinkingStep(
            id = "legacy-reasoning-${message.at}",
            kind = "reasoning",
            title = "思考",
            detail = message.reasoning,
            durationMs = message.thoughtMs,
        )
    }
    return steps
}

@Composable
private fun ThinkingTimeline(
    message: HubMessage,
    steps: List<HubThinkingStep>,
    fillWidth: Boolean,
    expandedStepIds: Set<String>,
    onStepExpandedChange: (String, Boolean) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
    ) {
        steps.forEachIndexed { index, step ->
            val pending = step.status == HubThinkingStep.STATUS_PENDING
            val failed = step.status == HubThinkingStep.STATUS_ERROR
            val trace = message.searchTraces.firstOrNull {
                it.query == step.detail || it.query == step.title
            }
            val content: (@Composable () -> Unit)? = when {
                step.kind == "search" && step.items.isNotEmpty() -> {
                    val scroll = rememberScrollState()
                    val block: @Composable () -> Unit = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            step.items.forEach { result ->
                                val clickable = result.url.startsWith("http://") ||
                                    result.url.startsWith("https://")
                                val host = remember(result.url) {
                                    runCatching { java.net.URI(result.url).host }
                                        .getOrNull()
                                        .orEmpty()
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (clickable) {
                                                Modifier.clickable {
                                                    runCatching { uriHandler.openUri(result.url) }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Text(
                                        text = result.title.ifBlank { result.url },
                                        color = if (clickable) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            HubInkSoft
                                        },
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                    )
                                    if (host.isNotBlank()) {
                                        Text(
                                            text = host,
                                            color = HubInkSoft.copy(alpha = 0.75f),
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            localize = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    block
                }
                step.kind == "reasoning" && (step.detail.isNotBlank() || pending) -> {
                    {
                        ReasoningStepContent(
                            text = step.detail,
                            waiting = pending && step.detail.isBlank(),
                        )
                    }
                }
                step.kind != "search" && step.kind != "reasoning" &&
                    step.detail.isNotBlank() -> {
                    {
                        Text(
                            text = step.detail,
                            color = HubInkSoft,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            localize = false,
                        )
                    }
                }
                else -> null
            }

            ThinkingStep(
                isFirst = index == 0,
                isLast = index == steps.lastIndex,
                expanded = step.id in expandedStepIds,
                onExpandedChange = { onStepExpandedChange(step.id, it) },
                icon = when (step.kind) {
                    "reasoning" -> R.drawable.ic_bulb
                    "memory" -> R.drawable.ic_psychology
                    "location" -> R.drawable.ic_location_on
                    "search" -> R.drawable.ic_search
                    "skill" -> R.drawable.ic_attach_lite_puzzle
                    "time" -> R.drawable.ic_history
                    "screen_time" -> R.drawable.ic_history
                    "calendar" -> R.drawable.ic_settings_file
                    "apps" -> R.drawable.ic_attach_lite_puzzle
                    "contacts" -> R.drawable.ic_contact_mail
                    "sms", "mms" -> R.drawable.ic_attach_lite_phone
                    else -> R.drawable.ic_bulb
                },
                label = {
                    if (step.kind == "search") {
                        Column {
                            val query = step.detail.ifBlank { step.title }
                            Text(
                                text = "搜索网页：" + query,
                                color = HubInk,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                val kinds = trace?.queriedProviderKinds
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: trace?.providerKinds
                                    ?: step.providerKinds
                                if (kinds.isNotEmpty()) SearchProviderBadges(kinds)
                                val count = trace?.resultCount?.takeIf { it > 0 }
                                    ?: step.items.size
                                Text(
                                    text = when {
                                        pending -> "正在搜索…"
                                        failed -> "搜索失败"
                                        step.cached -> "缓存结果 · 共 $count 条"
                                        trace?.cached == true ->
                                            "缓存结果 · 共 $count 条"
                                        count > 0 -> "共 $count 条结果"
                                        else -> "已搜索"
                                    },
                                    color = if (failed) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        HubInkSoft
                                    },
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    } else if (step.kind == "reasoning") {
                        val seconds = String.format(
                            java.util.Locale.US,
                            "%.1f",
                            step.durationMs.coerceAtLeast(0L) / 1000.0,
                        )
                        Text(
                            text = if (pending) {
                                "正在思考 $seconds 秒"
                            } else {
                                "思考了 $seconds 秒"
                            },
                            color = HubInk,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                        )
                    } else {
                        Column {
                            Text(
                                text = when (step.kind) {
                                    "skill" -> "使用技能 · " + step.detail
                                    else -> step.title
                                },
                                color = HubInk,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (pending || failed) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (pending) "进行中…" else "未能完成",
                                    color = if (failed) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        HubInkSoft
                                    },
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                },
                content = content,
                lineColor = lineColor,
                fillWidth = fillWidth,
            )
        }
    }
}

@Composable
private fun ReasoningStepContent(text: String, waiting: Boolean) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(scroll),
    ) {
        if (waiting || text.isBlank()) {
            Text(
                text = "正在思考…",
                color = HubInkSoft,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
            )
        } else {
            MarkdownBody(
                text = text,
                textColor = HubInkSoft,
                bodySize = 12.5.sp,
                bodyLineHeight = 19.sp,
                blockGap = 7.dp,
            )
        }
    }
}

@Composable
private fun SearchProviderBadges(kinds: List<String>) {
    val distinct = kinds.filter { it.isNotBlank() }.distinct().take(3)
    if (distinct.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        distinct.forEach { kind ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            ) {
                MaterialSymbol(
                    resId = thinkingSearchIcon(kind),
                    contentDescription = null,
                    size = 16.dp,
                    tint = if (thinkingSearchColored(kind)) Color.Unspecified else HubInkSoft,
                )
            }
        }
    }
}

private fun thinkingSearchIcon(kind: String): Int = when (kind) {
    SearchProvider.KIND_BING -> R.drawable.ic_search_bing
    SearchProvider.KIND_DEEPSEEK -> R.drawable.ic_brand_deepseek
    SearchProvider.KIND_DOUBAO -> R.drawable.ic_search_doubao
    SearchProvider.KIND_TAVILY -> R.drawable.ic_search_tavily
    SearchProvider.KIND_FIRECRAWL -> R.drawable.ic_search_firecrawl
    else -> R.drawable.ic_search
}

private fun thinkingSearchColored(kind: String): Boolean = kind in setOf(
    SearchProvider.KIND_BING,
    SearchProvider.KIND_DEEPSEEK,
    SearchProvider.KIND_DOUBAO,
    SearchProvider.KIND_TAVILY,
    SearchProvider.KIND_FIRECRAWL,
)

@Composable
private fun ThinkingStep(
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int,
    label: @Composable () -> Unit,
    content: (@Composable () -> Unit)?,
    lineColor: Color,
    fillWidth: Boolean,
) {
    val hasContent = content != null

    Column(modifier = Modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)) {
        Row(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .drawBehind {
                    val x = 12.dp.toPx()
                    val centerY = size.height / 2
                    val gap = 10.dp.toPx()
                    val stroke = 1.dp.toPx()
                    if (!isFirst) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, centerY - gap), stroke)
                    }
                    if (!isLast) {
                        drawLine(lineColor, Offset(x, centerY + gap), Offset(x, size.height), stroke)
                    }
                }
                .then(
                    if (hasContent) {
                        Modifier.clickable { onExpandedChange(!expanded) }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 16.dp,
                    tint = HubInkSoft,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = if (fillWidth) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                },
            ) { label() }
            if (hasContent) {
                Spacer(Modifier.width(4.dp))
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    size = 16.dp,
                    tint = HubInkSoft,
                )
            }
        }

        if (expanded && content != null) {
            Box(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .drawBehind {
                        if (!isLast) {
                            val x = 12.dp.toPx()
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                    .padding(start = 32.dp, end = 4.dp, bottom = 8.dp),
            ) {
                content()
            }
        }
    }
}
