package com.fingertipfuture.hub.data

import java.util.Calendar

/**
 * 聊天里识别出来的事项提醒命令。
 *
 * 解析完全在本机完成；[Save] / [Close] / [Delete] 只有经过用户确认后才会修改提醒列表。
 */
internal sealed interface AssistantReminderIntent {
    data class Save(
        val existing: AssistantReminder?,
        val title: String,
        val prompt: String,
        val hour: Int,
        val minute: Int,
        val repeat: ReminderRepeat,
        val scheduledDate: String = "",
        val weekday: Int = Calendar.MONDAY,
        val displayLocation: ReminderDisplayLocation = ReminderDisplayLocation.RECENT_CONVERSATION,
    ) : AssistantReminderIntent

    data class Close(
        val reminder: AssistantReminder,
    ) : AssistantReminderIntent

    data class Delete(
        val reminder: AssistantReminder,
    ) : AssistantReminderIntent

    data class Error(
        val message: String,
    ) : AssistantReminderIntent
}

private data class ClockMatch(
    val hour: Int,
    val minute: Int,
    val raw: String,
)

private data class RepeatMatch(
    val repeat: ReminderRepeat,
    val raw: String,
)

private data class TargetResolution(
    val reminder: AssistantReminder?,
    val error: String = "",
)

private val clockPattern = Regex(
    """(?<![\d])(?:(上午|早上|早晨|凌晨|中午|下午|傍晚|晚间|晚上|夜里|夜间|今早|今晚|明早|明晚)\s*)?""" +
        """([01]?\d|2[0-3]|[零〇一二两三四五六七八九十]{1,3})\s*(?:[:：点时])\s*""" +
        """(?:(半)|(一刻)|(三刻)|([0-5]?\d|[一二三四五六七八九十]{1,3})\s*分?)?""",
)

private val workdayPattern = Regex(
    """(?:每个)?工作日|每天工作日|周一到周五|周一至周五|星期一到星期五|星期一至星期五""",
)

private val dailyPattern = Regex("""每天|每日|天天|每一天|每晚|每早""")

private val titlePattern = Regex(
    """(?:标题|名称|名字)\s*(?:(?:改成|改为|调整为|设置为|叫|是|为))?\s*""" +
        """[：:]?\s*[“"「『]?([^，,。；;\n”"」』]{1,40})""",
)

private val closeAfterPattern = Regex(
    """(?:关闭|停用|暂停|取消|关掉)(?:掉)?\s*(?:名为|叫|标题为)?\s*""" +
        """[“"「『]?(.{0,50}?)[”"」』]?\s*提醒""",
)

private val closeBeforePattern = Regex(
    """(?:把|将)\s*[“"「『]?(.{0,50}?)[”"」』]?\s*""" +
        """提醒\s*(?:关闭|停用|暂停|取消|关掉)(?:掉)?""",
)

private val modifyBeforePattern = Regex(
    """(?:修改|更改|调整|编辑)(?:一下)?\s*[“"「『]?(.{0,50}?)[”"」』]?\s*提醒(.*)""",
)

private val modifyAfterPattern = Regex(
    """(?:把|将)\s*[“"「『]?(.{0,50}?)[”"」』]?\s*""" +
        """提醒(?:\s*的?\s*(?:任务|内容|时间|重复方式|标题|名称|名字))?\s*""" +
        """(?:改为|改成|调整为|调成|换成|修改为|更改为)(.*)""",
)

private val changeTailPattern = Regex(
    """(?:改为|改成|调整为|调成|换成|变成|修改为|更改为)\s*[：:]?(.*)$""",
)

private val taskChangePattern = Regex(
    """(?:任务|内容|要做的事)\s*(?:改为|改成|调整为|调成|换成|变成|设置为|是|为)""" +
        """\s*[：:]?(.+)$""",
)

private val reminderMarkerPattern = Regex("""(?:提醒|闹钟)(?:我|一下|下)?\s*[，,：:\-]?""")

private val scheduleActionPattern = Regex(
    """帮我|帮忙|请|麻烦|给我|替我|主动|自动|定时|记得|""" +
        """查|查询|搜索|查看|看|总结|整理|汇总|抓取|采集|更新|发送|推送|发布|""" +
        """通知|播报|汇报|检查|打开|打卡|记录|获取|关注|监控|浏览|阅读|发新闻|发消息""",
)

private val questionPattern = Regex(
    """是什么|为什么|怎么|如何|哪里|哪个|哪些|多少|多久|多远|多重|""" +
        """是否|有没有|能不能|可不可以|会不会|要不要|吗|呢|[?？]\s*$""",
)

private val createCommandPattern = Regex(
    """提醒我|记得提醒|闹钟|定时|""" +
        """(?:设置|创建|添加|新增|增加|安排|建立|新建|设|定)(?:一个|个|一项|项)?""" +
        """.{0,12}(?:提醒|闹钟|事项|任务)""",
)

/**
 * 返回 null 表示这句话不是明确的提醒命令，继续走普通聊天。
 *
 * [reminders] 是当前助手的提醒列表；修改和关闭命令会在其中按标题、任务匹配。
 */
internal fun parseAssistantReminderIntent(
    text: String,
    reminders: List<AssistantReminder>,
): AssistantReminderIntent? {
    val raw = text.trim()
    if (raw.isBlank()) return null
    val clock = extractClock(raw)
    val repeat = extractRepeat(raw)
    val mentionsReminder = "提醒" in raw || "闹钟" in raw || "定时" in raw || "事项" in raw
    val hasScheduleSignal = clock != null || repeat != null
    if (!mentionsReminder && !hasScheduleSignal) return null

    closeMatch(raw)?.let { (query, _) ->
        val target = resolveReminderTarget(query, reminders)
        return target.reminder?.let(AssistantReminderIntent::Close)
            ?: AssistantReminderIntent.Error(target.error)
    }

    val modifyMatch = modifyBeforePattern.find(raw) ?: modifyAfterPattern.find(raw)
    if (modifyMatch != null) {
        val target = resolveReminderTarget(modifyMatch.groupValues[1], reminders)
        val existing = target.reminder
            ?: return AssistantReminderIntent.Error(target.error)
        val title = extractTitle(raw)
        val prompt = extractChangedPrompt(raw, clock, repeat)
        if (clock == null && repeat == null && title == null && prompt == null) {
            return AssistantReminderIntent.Error(
                "识别到了修改提醒的意图，但没有找到新的时间、重复方式或任务。" +
                    "可以这样说：“把今日新闻提醒改成工作日 9:00”。",
            )
        }
        return AssistantReminderIntent.Save(
            existing = existing,
            title = title ?: existing.title,
            prompt = prompt ?: existing.prompt,
            hour = clock?.hour ?: existing.hour,
            minute = clock?.minute ?: existing.minute,
            repeat = repeat?.repeat ?: existing.repeat,
        )
    }

    val createCommand = createCommandPattern.containsMatchIn(raw)
    val actionCommand = hasScheduleSignal && scheduleActionPattern.containsMatchIn(raw)
    val looksLikeQuestion = questionPattern.containsMatchIn(raw)
    if (looksLikeQuestion && !mentionsReminder && !createCommand) return null
    if (!mentionsReminder && !hasScheduleSignal && !createCommand && !actionCommand) return null

    val title = extractTitle(raw)
    val prompt = extractCreatePrompt(raw, clock, repeat)
    if (prompt.isNullOrBlank()) {
        return AssistantReminderIntent.Error(
            "识别到了创建提醒的意图，但没有找到到点后要执行的任务。" +
                "可以这样说：“每天早上 8 点提醒我查询今天的新闻”。",
        )
    }
    return AssistantReminderIntent.Save(
        existing = null,
        title = title ?: defaultTitle(prompt, repeat?.repeat ?: ReminderRepeat.DAILY),
        prompt = prompt,
        hour = clock?.hour ?: 8,
        minute = clock?.minute ?: 0,
        repeat = repeat?.repeat ?: ReminderRepeat.DAILY,
    )
}

internal fun parseReminderClockInput(value: String): Pair<Int, Int>? =
    Regex("""^\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*$""")
        .matchEntire(value)
        ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

private fun closeMatch(text: String): Pair<String, String>? {
    closeAfterPattern.find(text)?.let { return it.groupValues[1] to it.value }
    closeBeforePattern.find(text)?.let { return it.groupValues[1] to it.value }
    return null
}

private fun extractClock(text: String): ClockMatch? {
    val match = clockPattern.find(text) ?: return null
    val period = match.groupValues[1]
    var hour = parseChineseNumber(match.groupValues[2]) ?: return null
    val minute = when {
        match.groupValues[3] == "半" -> 30
        match.groupValues[4] == "一刻" -> 15
        match.groupValues[5] == "三刻" -> 45
        match.groupValues[6].isNotBlank() -> parseChineseNumber(match.groupValues[6]) ?: 0
        else -> 0
    }
    if (period in setOf("下午", "傍晚", "晚上", "夜里") && hour < 12) hour += 12
    if (period == "中午" && hour < 11) hour += 12
    if (period == "凌晨" && hour == 12) hour = 0
    if (hour !in 0..23) return null
    return ClockMatch(hour = hour, minute = minute, raw = match.value)
}

private fun extractRepeat(text: String): RepeatMatch? {
    workdayPattern.find(text)?.let {
        return RepeatMatch(ReminderRepeat.WEEKDAYS, it.value)
    }
    dailyPattern.find(text)?.let {
        return RepeatMatch(ReminderRepeat.DAILY, it.value)
    }
    return null
}

private fun extractTitle(text: String): String? =
    titlePattern.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::cleanTitle)
        ?.takeIf { it.isNotBlank() }

private fun cleanTitle(value: String): String =
    value.trim()
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""(?:的)?提醒$"""), "")
        .trim(' ', '，', ',', '。', '.', '：', ':', '“', '”', '"', '「', '」', '『', '』')
        .take(40)

private fun extractCreatePrompt(
    raw: String,
    clock: ClockMatch?,
    repeat: RepeatMatch?,
): String? {
    val marker = reminderMarkerPattern.find(raw)
    val afterMarker = marker?.let { raw.substring(it.range.last + 1) }.orEmpty()
    cleanTask(afterMarker, clock, repeat)?.let { return it }

    var fallback = stripTitleClause(raw)
    fallback = removeCreateCommand(fallback)
    return cleanTask(fallback, clock, repeat)
        ?: if ("新闻" in raw) "查询当天的新闻" else null
}

private fun extractChangedPrompt(
    raw: String,
    clock: ClockMatch?,
    repeat: RepeatMatch?,
): String? {
    taskChangePattern.find(raw)?.groupValues?.getOrNull(1)?.let { value ->
        cleanTask(value, clock, repeat)?.let { return it }
    }
    if (
        Regex("""(?:标题|名称|名字).{0,8}(?:改为|改成|调整为|调成|换成|变成)""")
            .containsMatchIn(raw)
    ) {
        return null
    }
    val tail = changeTailPattern.find(raw)?.groupValues?.getOrNull(1) ?: return null
    return cleanTask(tail, clock, repeat)
}

private fun cleanTask(
    value: String,
    clock: ClockMatch?,
    repeat: RepeatMatch?,
): String? {
    var task = stripTitleClause(value)
    if (clock != null) task = task.replace(clock.raw, " ")
    if (repeat != null) task = task.replace(repeat.raw, " ")
    task = task.replace("的时候", " ")
    task = Regex("""^\s*(?:这个|那个)?(?:定时|提醒)?(?:事项|任务|闹钟)\s*[，,：:]?\s*""")
        .replace(task, "")
    task = reminderMarkerPattern.replace(task, " ")
    task = Regex("""^\s*我\s*""").replace(task, "")
    task = Regex(
        """^\s*(?:(?:请|请你|麻烦|麻烦你|帮我|帮忙|给我|替我|我想|想要|需要|执行|自动|主动|以后|从今以后|然后|并|再)\s*)+""",
    ).replace(task, "")
    task = Regex("""^\s*(?:任务|内容)\s*(?:是|为)?\s*[：:]?\s*""").replace(task, "")
    task = Regex("""^\s*(?:的|，|,|。|：|:|-|—)\s*""").replace(task, "")
    task = Regex("""(?:这个|这件|一下|的事|的事项|这个事项|即可)\s*$""").replace(task, " ")
    task = task.replace(Regex("""\s+"""), " ")
        .trim(' ', '，', ',', '。', '.', '；', ';', '：', ':', '“', '”', '"')
    if (task.isBlank()) return null
    if (Regex("""^(?:标题|重复方式|时间)\s*(?:是|为|改成|改为)?""").containsMatchIn(task)) {
        return null
    }
    if (
        !scheduleActionPattern.containsMatchIn(task) &&
        Regex("""新闻|资讯|天气|邮件""").containsMatchIn(task)
    ) {
        task = "查询$task"
    }
    return task.take(2_000)
}

private fun stripTitleClause(value: String): String =
    titlePattern.replace(value, " ")

private fun removeCreateCommand(value: String): String =
    Regex(
        """^\s*(?:(?:请|请你|麻烦|麻烦你|帮我|帮忙|给我|替我|我想|想要|需要)\s*)*""" +
            """(?:设置|设定|创建|添加|新增|增加|安排|建立|新建|设|定|定时)""" +
            """(?:一个|个|一项|项)?\s*(?:(?:定时)?(?:事项|任务)\s*[，,：:]?)?""",
    ).replace(value, " ")

private fun parseChineseNumber(value: String): Int? {
    val clean = value.trim()
    if (clean.isBlank()) return null
    clean.toIntOrNull()?.let { return it }
    val digits = mapOf(
        '零' to 0,
        '〇' to 0,
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
    if ('十' in clean) {
        val parts = clean.split('十', limit = 2)
        val tens = if (parts[0].isBlank()) 1 else parts[0].singleOrNull()?.let(digits::get) ?: return null
        val ones = if (parts.getOrNull(1).isNullOrBlank()) {
            0
        } else {
            parts[1].singleOrNull()?.let(digits::get) ?: return null
        }
        return tens * 10 + ones
    }
    if (clean.length == 1) return digits[clean[0]]
    return null
}

private fun defaultTitle(prompt: String, repeat: ReminderRepeat): String {
    if ("新闻" in prompt) return "今日新闻"
    if ("邮件" in prompt) return "邮件提醒"
    if ("天气" in prompt) return "天气提醒"
    val prefix = if (repeat == ReminderRepeat.WEEKDAYS) "工作日" else ""
    return (prefix + prompt).take(20)
}

private fun resolveReminderTarget(
    rawQuery: String,
    reminders: List<AssistantReminder>,
): TargetResolution {
    if (reminders.isEmpty()) {
        return TargetResolution(null, "目前还没有可以修改或关闭的提醒事项。")
    }
    val query = normalizeReference(rawQuery)
    if (query.isBlank()) {
        return if (reminders.size == 1) {
            TargetResolution(reminders.first())
        } else {
            TargetResolution(
                null,
                "你有多条提醒，请说出要操作的那一条。当前提醒：" +
                    reminders.joinToString("、") { "“${it.title}”" } + "。",
            )
        }
    }

    val exact = reminders.filter { normalizeReference(it.title) == query }
    if (exact.size == 1) return TargetResolution(exact.first())
    if (exact.size > 1) return TargetResolution(null, ambiguousMessage(exact))

    val matches = reminders.filter { reminder ->
        val title = normalizeReference(reminder.title)
        val prompt = normalizeReference(reminder.prompt)
        title.contains(query) || query.contains(title) || prompt.contains(query)
    }.distinctBy { it.id }
    return when (matches.size) {
        1 -> TargetResolution(matches.first())
        0 -> TargetResolution(
            null,
            "没有找到与“${rawQuery.trim()}”匹配的提醒。当前提醒：" +
                reminders.joinToString("、") { "“${it.title}”" } + "。",
        )
        else -> TargetResolution(null, ambiguousMessage(matches))
    }
}

private fun normalizeReference(value: String): String =
    value.lowercase()
        .replace(Regex("""\s+"""), "")
        .replace("今日", "今天")
        .replace(Regex("""每日|每天|这个|那个|事项|提醒"""), "")
        .replace("的", "")
        .trim('的', '，', ',', '。', '.', '：', ':', '“', '”', '"', '「', '」', '『', '』')

private fun ambiguousMessage(reminders: List<AssistantReminder>): String =
    "匹配到了多条提醒，请说得更具体一些。候选：" +
        reminders.joinToString("、") { "“${it.title}”" } + "。"
