package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ReminderRepeat(val key: String, val label: String) {
    DAILY("daily", "每天"),
    ONCE("once", "指定日期"),
    WEEKLY("weekly", "每周"),
    /** 旧版本留下的“工作日”数据仍可执行，但设置界面不再提供这个选项。 */
    WEEKDAYS("weekdays", "工作日"),
    ;

    companion object {
        fun fromKey(value: String?): ReminderRepeat =
            entries.firstOrNull { it.key == value } ?: DAILY
    }
}

enum class ReminderDisplayLocation(val key: String, val label: String) {
    RECENT_CONVERSATION("recent", "最近的会话提醒"),
    NEW_CONVERSATION("new", "新建会话"),
    ;

    companion object {
        fun fromKey(value: String?): ReminderDisplayLocation =
            entries.firstOrNull { it.key == value } ?: RECENT_CONVERSATION
    }
}

data class AssistantReminder(
    val id: String,
    val assistantId: String,
    val title: String,
    val prompt: String,
    val hour: Int,
    val minute: Int,
    val repeat: ReminderRepeat = ReminderRepeat.DAILY,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val lastResult: String = "",
    /** 从聊天里创建时绑定的会话；执行结果会写回这里，旧数据为空。 */
    val conversationId: String = "",
    /** 一次性任务的本机日期，格式 yyyy-MM-dd。 */
    val scheduledDate: String = "",
    /** 每周任务的星期，ISO 1=周一 … 7=周日。 */
    val weekday: Int = Calendar.MONDAY,
    /** 每周任务可同时选择多天；旧数据继续由 [weekday] 兼容。 */
    val weekdays: List<Int> = listOf(weekday),
    val displayLocation: ReminderDisplayLocation = ReminderDisplayLocation.RECENT_CONVERSATION,
) {
    val timeLabel: String
        get() = "%02d:%02d".format(hour, minute)

    val scheduleLabel: String
        get() = when (repeat) {
            ReminderRepeat.DAILY -> "每天"
            ReminderRepeat.WEEKDAYS -> "工作日"
            ReminderRepeat.WEEKLY -> {
                val days = weekdays.distinct().sorted().ifEmpty { listOf(weekday) }
                "每周" + days.joinToString("、") { reminderWeekdayLabel(it) }
            }
            ReminderRepeat.ONCE -> {
                val today = localReminderDateKey()
                val tomorrow = localReminderDateKey(System.currentTimeMillis() + 24 * 60 * 60_000L)
                when (scheduledDate) {
                    today -> "今天"
                    tomorrow -> "明天"
                    else -> scheduledDate.ifBlank { "指定日期" }
                }
            }
        }
}

/** 每个助手自己的主动事项，持久化在本机。 */
object AssistantReminderStore {

    private const val PREFS = "hub_assistant_reminders"
    private const val KEY_ITEMS = "items"

    var items: List<AssistantReminder> by mutableStateOf(emptyList())
        private set

    @Synchronized
    fun initialize(context: Context) {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: run {
            items = emptyList()
            return
        }
        items = runCatching {
            val array = JSONArray(raw)
            val loaded = mutableListOf<AssistantReminder>()
            for (index in 0 until array.length()) {
                runCatching { fromJson(array.getJSONObject(index)) }.getOrNull()?.let(loaded::add)
            }
            loaded
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun forAssistant(assistantId: String): List<AssistantReminder> =
        items.filter { it.assistantId == assistantId }
            .sortedWith(compareBy({ it.hour }, { it.minute }, { it.title }))

    @Synchronized
    fun byId(id: String): AssistantReminder? = items.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, reminder: AssistantReminder) {
        val index = items.indexOfFirst { it.id == reminder.id }
        items = if (index >= 0) {
            items.toMutableList().also { it[index] = reminder }
        } else {
            items + reminder
        }
        persist(context)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        items = items.filterNot { it.id == id }
        persist(context)
    }

    @Synchronized
    fun deleteAssistant(context: Context, assistantId: String) {
        items = items.filterNot { it.assistantId == assistantId }
        persist(context)
    }

    @Synchronized
    fun markRun(
        context: Context,
        id: String,
        result: String,
        at: Long,
        conversationId: String = "",
    ) {
        val current = byId(id) ?: return
        upsert(
            context,
            current.copy(
                lastRunAt = at,
                lastResult = result.take(1_000),
                conversationId = conversationId.ifBlank { current.conversationId },
            ),
        )
    }

    fun newId(): String = "rem-" + UUID.randomUUID().toString().take(12)

    private fun persist(context: Context) {
        prefs(context).edit().putString(
            KEY_ITEMS,
            JSONArray().apply { items.forEach { put(toJson(it)) } }.toString(),
        ).apply()
    }

    private fun toJson(reminder: AssistantReminder): JSONObject = JSONObject().apply {
        put("id", reminder.id)
        put("assistantId", reminder.assistantId)
        put("title", reminder.title)
        put("prompt", reminder.prompt)
        put("hour", reminder.hour)
        put("minute", reminder.minute)
        put("repeat", reminder.repeat.key)
        put("enabled", reminder.enabled)
        put("lastRunAt", reminder.lastRunAt)
        put("lastResult", reminder.lastResult)
        put("conversationId", reminder.conversationId)
        put("scheduledDate", reminder.scheduledDate)
        put("weekday", reminder.weekday)
        put("weekdays", JSONArray().apply { reminder.weekdays.forEach { put(it) } })
        put("displayLocation", reminder.displayLocation.key)
    }

    private fun fromJson(json: JSONObject): AssistantReminder = AssistantReminder(
        id = json.optString("id"),
        assistantId = json.optString("assistantId"),
        title = json.optString("title").ifBlank { "自动化任务" },
        prompt = json.optString("prompt"),
        hour = json.optInt("hour", 8).coerceIn(0, 23),
        minute = json.optInt("minute", 0).coerceIn(0, 59),
        repeat = ReminderRepeat.fromKey(json.optString("repeat")),
        enabled = json.optBoolean("enabled", true),
        lastRunAt = json.optLong("lastRunAt", 0),
        lastResult = json.optString("lastResult"),
        conversationId = json.optString("conversationId"),
        scheduledDate = json.optString("scheduledDate"),
        weekday = json.optInt("weekday", Calendar.MONDAY).coerceIn(1, 7),
        weekdays = json.optJSONArray("weekdays")?.let { array ->
            (0 until array.length())
                .map { array.optInt(it) }
                .filter { it in 1..7 }
                .distinct()
                .ifEmpty { listOf(json.optInt("weekday", Calendar.MONDAY).coerceIn(1, 7)) }
        } ?: listOf(json.optInt("weekday", Calendar.MONDAY).coerceIn(1, 7)),
        displayLocation = ReminderDisplayLocation.fromKey(json.optString("displayLocation")),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun localReminderDateKey(
    nowMillis: Long = System.currentTimeMillis(),
): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowMillis)

internal fun reminderWeekdayLabel(isoWeekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(isoWeekday.coerceIn(1, 7) - 1) { "周一" }

/**
 * 下一次触发的时间戳。
 *
 * 一次性任务已经过期时返回 null；调用方应停止继续调度。
 * 旧的 WORKDAYS 数据继续兼容，跳过周六、周日。
 */
internal fun nextReminderTriggerAtMillis(
    reminder: AssistantReminder,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    if (reminder.repeat == ReminderRepeat.ONCE) {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
        val date = runCatching { formatter.parse(reminder.scheduledDate) }.getOrNull()
            ?: return null
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, reminder.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, reminder.minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis.takeIf { it > nowMillis }
    }

    val targetDays = when (reminder.repeat) {
        ReminderRepeat.DAILY -> (1..7).toSet()
        ReminderRepeat.WEEKDAYS -> setOf(1, 2, 3, 4, 5)
        ReminderRepeat.WEEKLY -> reminder.weekdays
            .filter { it in 1..7 }
            .ifEmpty { listOf(reminder.weekday.coerceIn(1, 7)) }
            .toSet()
        ReminderRepeat.ONCE -> emptySet()
    }
    val next = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, reminder.hour.coerceIn(0, 23))
        set(Calendar.MINUTE, reminder.minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (true) {
            val isoDay = (get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            if (timeInMillis > nowMillis && isoDay in targetDays) break
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return next.timeInMillis
}
