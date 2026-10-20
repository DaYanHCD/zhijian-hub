package com.fingertipfuture.hub.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fingertipfuture.hub.R
import java.util.Calendar

/** 与上一条消息间隔超过 3 分钟，就重新出现时间分隔。 */
internal const val CHAT_TIME_GROUP_GAP_MS = 3 * 60 * 1000L

/** 密集消息（间隔不足 3 分钟）每 30 分钟也出现一次时间分隔。 */
internal const val CHAT_TIME_SEPARATOR_INTERVAL_MS = 30 * 60 * 1000L

/**
 * 是否需要在 [currentAt] 这条消息之前插入时间分隔。
 *
 * 规则（照搬「指尖」，用户给定的）：
 * - 第一条消息永远出现；
 * - 与上一条消息间隔**超过 3 分钟**时出现；
 * - 如果和上一条没超过 3 分钟，则距离**上一次时间分隔满 30 分钟**时出现一次。
 */
internal fun shouldShowTimeSeparator(
    previousMessageAt: Long?,
    lastSeparatorAt: Long?,
    currentAt: Long,
): Boolean {
    if (previousMessageAt == null) return true
    if (currentAt - previousMessageAt > CHAT_TIME_GROUP_GAP_MS) return true
    val sinceSeparator = lastSeparatorAt?.let { currentAt - it } ?: return false
    return sinceSeparator >= CHAT_TIME_SEPARATOR_INTERVAL_MS
}

/**
 * 聊天时间分隔文案。
 *
 * | 日期 | 文案 |
 * |---|---|
 * | 今天 | `上午 10:06` |
 * | 昨天 | `昨天 上午 10:06` |
 * | 前天及更早 | `10月4日 上午 10:06` |
 * | 去年及更早 | `2025年10月4日 上午 10:06` |
 *
 * 日期与「上午/下午」之间、以及「上午/下午」与时分之间都要留一个空格：
 * 「昨天上午10:06」整串挤在一起不好认。
 *
 * [withSeconds] 为 true 时补上秒（长按日期查看具体秒数）。
 *
 * 这里用 [Calendar] 而不是 `java.time`：minSdk 24，没开 core library desugaring 时
 * `java.time` 在 API 24/25 会崩。
 */
internal fun formatChatTime(
    at: Long,
    now: Long,
    labels: ChatTimeLabels,
    withSeconds: Boolean = false,
): String {
    val target = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = today.copyWithDayOffset(-1)
    val beforeYesterday = today.copyWithDayOffset(-2)

    val hour24 = target.get(Calendar.HOUR_OF_DAY)
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val clock = buildString {
        append(if (hour24 < 12) labels.morning else labels.afternoon)
        append(' ')
        append(hour12)
        append(':')
        append(target.get(Calendar.MINUTE).toString().padStart(2, '0'))
        if (withSeconds) {
            append(':')
            append(target.get(Calendar.SECOND).toString().padStart(2, '0'))
        }
    }

    val month = target.get(Calendar.MONTH) + 1
    val dayOfMonth = target.get(Calendar.DAY_OF_MONTH)
    val dayPrefix = when {
        target.isSameDayAs(today) -> ""
        target.isSameDayAs(yesterday) -> labels.yesterday
        target.isSameDayAs(beforeYesterday) -> labels.monthDayFormat.format(month, dayOfMonth)
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> labels.monthDayFormat.format(month, dayOfMonth)
        else -> labels.yearMonthDayFormat.format(target.get(Calendar.YEAR), month, dayOfMonth)
    }
    return if (dayPrefix.isEmpty()) clock else "$dayPrefix $clock"
}

private fun Calendar.copyWithDayOffset(days: Int): Calendar =
    (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }

private fun Calendar.isSameDayAs(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

/** 聊天时间分隔用到的文案（放 strings.xml，纯函数才能被单元测试直接调用）。 */
internal data class ChatTimeLabels(
    val morning: String,
    val afternoon: String,
    val yesterday: String,
    val monthDayFormat: String,
    val yearMonthDayFormat: String,
)

@Composable
internal fun rememberChatTimeLabels(): ChatTimeLabels = ChatTimeLabels(
    morning = stringResource(R.string.chat_time_morning),
    afternoon = stringResource(R.string.chat_time_afternoon),
    yesterday = stringResource(R.string.chat_time_yesterday),
    monthDayFormat = stringResource(R.string.chat_time_month_day),
    yearMonthDayFormat = stringResource(R.string.chat_time_year_month_day),
)