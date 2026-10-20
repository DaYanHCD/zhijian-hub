package com.fingertipfuture.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.HubModelUsage
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import com.fingertipfuture.hub.ui.settings.SETTINGS_BACK_BAR_SPACE
import com.fingertipfuture.hub.ui.settings.SettingsTitleBar
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统计页（2026-10-17 按用户给的图重做）。
 *
 * 数据全部来自本机：全部会话 / 全部消息 / 热力图 / 接口返回的 token 用量。
 * 没有引入图表库，热力图是普通 Compose 方块网格。
 */
@Composable
internal fun StatsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<HubMessage>>(emptyList()) }
    var usage by remember { mutableStateOf(HubTokenUsage()) }
    var modelUsage by remember { mutableStateOf<List<HubModelUsage>>(emptyList()) }
    LaunchedEffect(Unit) {
        messages = runCatching {
            withContext(Dispatchers.IO) {
                ChatStore.allMessages().filterNot { it.localOnly }
            }
        }.getOrDefault(emptyList())
        usage = runCatching { withContext(Dispatchers.IO) { ChatStore.usageTotals() } }
            .getOrDefault(HubTokenUsage())
        modelUsage = runCatching {
            withContext(Dispatchers.IO) { ChatStore.modelUsage().take(6) }
        }.getOrDefault(emptyList())
    }
    val conversationCount = ChatStore.conversations.size
    val estimatedInput = remember(messages) {
        messages.filter { it.role == "user" }.sumOf { estimateTokens(it.text) }
    }
    val estimatedOutput = remember(messages) {
        messages.filter { it.role != "user" }
            .sumOf { estimateTokens(it.text + "\n" + it.reasoning) }
    }
    val inputTokens = if (usage.input > 0) usage.input else estimatedInput
    val outputTokens = if (usage.output > 0) usage.output else estimatedOutput
    // 跟随 App 实际主题，而不是系统开关：用户可在应用内单独选深色。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pageColor = if (dark) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val cardColor = if (dark) {
        lerp(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant,
            0.45f,
        )
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 和 SettingsPage 一样：状态栏 padding 放进滚动内容里，
                // 滚动时卡片可以从透明状态栏下面穿过，再由 TopFadeLayer 渐隐。
                .statusBarsPadding()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN)
                .padding(top = SETTINGS_BACK_BAR_SPACE, bottom = 24.dp),
        ) {
            HeatmapCard(messages = messages, containerColor = cardColor)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    icon = R.drawable.ic_history,
                    value = conversationCount.toString(),
                    label = "总对话数",
                    containerColor = cardColor,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = R.drawable.ic_list,
                    value = messages.size.toString(),
                    label = "总消息数",
                    containerColor = cardColor,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    icon = R.drawable.ic_psychology,
                    value = tokenText(inputTokens),
                    label = "输入 Token",
                    hint = if (usage.input == 0) "本地估算" else null,
                    containerColor = cardColor,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = R.drawable.ic_psychology,
                    value = tokenText(outputTokens),
                    label = "输出 Token",
                    hint = if (usage.output == 0) "本地估算" else null,
                    containerColor = cardColor,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            StatCard(
                icon = R.drawable.ic_bolt,
                value = tokenText(usage.cached),
                label = "缓存节省 Token",
                hint = if (usage.cached == 0) "接口未返回缓存用量" else null,
                containerColor = cardColor,
                modifier = Modifier.fillMaxWidth(),
            )
            if (modelUsage.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ModelUsageCard(usage = modelUsage, containerColor = cardColor)
            }
        }

        TopFadeLayer(
            modifier = Modifier.align(Alignment.TopCenter),
            tail = 24.dp,
        )
        SettingsTitleBar(
            title = "统计",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun ModelUsageCard(
    usage: List<HubModelUsage>,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "模型使用",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            usage.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.model,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        text = buildString {
                            append(item.messages).append(" 次")
                            if (item.usage.input + item.usage.output > 0) {
                                append(" · ")
                                append(tokenText(item.usage.input + item.usage.output))
                                append(" token")
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(messages: List<HubMessage>, containerColor: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "聊天热力图",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Heatmap(messages)
        }
    }
}

private data class HeatWeek(
    val monthLabel: String?,
    val days: List<Int>,
)

@Composable
private fun Heatmap(messages: List<HubMessage>) {
    val counts = remember(messages) {
        messages.groupingBy { dayKey(it.at) }.eachCount()
    }
    val weeks = remember(messages) { buildWeeks(counts) }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 3.dp
        val weekLabelWidth = 14.dp
        val cell = ((maxWidth - weekLabelWidth - gap * 19f) / 20f)
            .coerceIn(7.dp, 14.dp)
        val step = cell + gap
        val gridWidth = cell * 20f + gap * 19f

        Row {
            Column(modifier = Modifier.width(weekLabelWidth)) {
                // 月标签占的那一行，让「一」和第一行格子对齐
                Spacer(Modifier.height(18.dp))
                listOf("一", "", "三", "", "五", "", "").forEach { label ->
                    Box(
                        modifier = Modifier.height(step),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(modifier = Modifier.width(gridWidth)) {
                Box(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                    weeks.forEachIndexed { index, week ->
                        val label = week.monthLabel ?: return@forEachIndexed
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = step * index.toFloat()),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            week.days.forEach { count ->
                                val alpha = when {
                                    count <= 0 -> 0f
                                    count == 1 -> 0.25f
                                    count == 2 -> 0.45f
                                    count <= max / 2 -> 0.65f
                                    else -> 0.9f
                                }
                                Box(
                                    modifier = Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (alpha == 0f) empty else primary.copy(alpha = alpha)),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(0f, 0.25f, 0.45f, 0.65f, 0.9f).forEach { alpha ->
                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (alpha == 0f) empty else primary.copy(alpha = alpha)),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun buildWeeks(counts: Map<Int, Int>): List<HeatWeek> {
    val monday = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -19 * 7)
    }
    var lastMonth = -1
    return (0 until 20).map { weekIndex ->
        var label: String? = null
        val days = (0 until 7).map { dayIndex ->
            val day = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, weekIndex * 7 + dayIndex)
            }
            if (dayIndex == 0 && day.get(Calendar.MONTH) != lastMonth) {
                lastMonth = day.get(Calendar.MONTH)
                label = (lastMonth + 1).toString() + "月"
            }
            counts[dayKey(day.timeInMillis)] ?: 0
        }
        HeatWeek(monthLabel = label, days = days)
    }
}

private fun dayKey(at: Long): Int {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    return day.get(Calendar.YEAR) * 10000 +
        (day.get(Calendar.MONTH) + 1) * 100 +
        day.get(Calendar.DAY_OF_MONTH)
}

@Composable
private fun StatCard(
    icon: Int,
    value: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.hubDropShadow(shape).hubCardBorder(shape),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MaterialSymbol(
                resId = icon,
                contentDescription = null,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private fun tokenText(value: Int): String {
    if (value <= 0) return "—"
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

/**
 * 接口不返回 usage 时的本地估算：中文按字、英文/符号按约 4 字符 1 token。
 * 只用于把空卡片填成一个有参考意义的数字，界面上明确标「本地估算」。
 */
private fun estimateTokens(text: String): Int {
    if (text.isBlank()) return 0
    var cjk = 0
    var other = 0
    text.forEach { ch ->
        if (ch.code in 0x4E00..0x9FFF) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}
