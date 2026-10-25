package com.fingertipfuture.hub.ui.chat

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.ReminderDisplayLocation
import com.fingertipfuture.hub.data.ReminderRepeat
import com.fingertipfuture.hub.data.localReminderDateKey
import com.fingertipfuture.hub.data.nextReminderTriggerAtMillis
import com.fingertipfuture.hub.data.parseReminderClockInput
import com.fingertipfuture.hub.data.reminderWeekdayLabel
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDialogCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun AssistantReminderIntentDialog(
    intent: AssistantReminderIntent,
    onDismiss: () -> Unit,
    onSave: (AssistantReminderIntent.Save) -> Unit,
    onClose: (AssistantReminderIntent.Close) -> Unit,
    onDelete: (AssistantReminderIntent.Delete) -> Unit,
) {
    when (intent) {
        is AssistantReminderIntent.Save -> ReminderSaveDialog(
            initial = intent,
            onDismiss = onDismiss,
            onConfirm = onSave,
        )
        is AssistantReminderIntent.Close -> ReminderCloseDialog(
            intent = intent,
            onDismiss = onDismiss,
            onConfirm = onClose,
        )
        is AssistantReminderIntent.Delete -> ReminderDeleteDialog(
            intent = intent,
            onDismiss = onDismiss,
            onConfirm = onDelete,
        )
        is AssistantReminderIntent.Error -> Unit
    }
}

@Composable
private fun ReminderSaveDialog(
    initial: AssistantReminderIntent.Save,
    onDismiss: () -> Unit,
    onConfirm: (AssistantReminderIntent.Save) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(initial) { mutableStateOf(initial.title) }
    var prompt by remember(initial) { mutableStateOf(initial.prompt) }
    var time by remember(initial) {
        mutableStateOf("%02d:%02d".format(initial.hour, initial.minute))
    }
    var repeat by remember(initial) { mutableStateOf(initial.repeat) }
    var scheduledDate by remember(initial) {
        mutableStateOf(initial.scheduledDate.ifBlank { localReminderDateKey() })
    }
    var weekday by remember(initial) { mutableIntStateOf(initial.weekday.coerceIn(1, 7)) }
    var displayLocation by remember(initial) { mutableStateOf(initial.displayLocation) }
    val parsedTime = parseReminderClockInput(time)
    val draft = (initial.existing ?: AssistantReminder(
        id = "draft",
        assistantId = "",
        title = title,
        prompt = prompt,
        hour = initial.hour,
        minute = initial.minute,
    )).copy(
        title = title,
        prompt = prompt,
        hour = parsedTime?.first ?: initial.hour,
        minute = parsedTime?.second ?: initial.minute,
        repeat = repeat,
        scheduledDate = scheduledDate,
        weekday = weekday,
        weekdays = listOf(weekday),
        displayLocation = displayLocation,
    )
    val triggerValid = repeat != ReminderRepeat.ONCE ||
        nextReminderTriggerAtMillis(draft) != null
    val valid = title.isNotBlank() && prompt.isNotBlank() && parsedTime != null && triggerValid
    val editing = initial.existing != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 16.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = if (editing) "编辑自动化任务" else "添加自动化任务",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(60) },
                        label = { Text("标题") },
                        placeholder = { Text("例如：今日新闻") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(2_000) },
                        label = { Text("助手要执行的任务") },
                        placeholder = { Text("例如：查询今天与 AI 相关的重要新闻，列出三条并给出要点") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.take(5) },
                        label = { Text("时间") },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        isError = time.length == 5 && parsedTime == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("执行日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val today = localReminderDateKey()
                    val tomorrow = localReminderDateKey(System.currentTimeMillis() + 24 * 60 * 60_000L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReminderScheduleChoice("每天", repeat == ReminderRepeat.DAILY) {
                            repeat = ReminderRepeat.DAILY
                        }
                        ReminderScheduleChoice(
                            "今天",
                            repeat == ReminderRepeat.ONCE && scheduledDate == today,
                        ) {
                            repeat = ReminderRepeat.ONCE
                            scheduledDate = today
                        }
                        ReminderScheduleChoice(
                            "明天",
                            repeat == ReminderRepeat.ONCE && scheduledDate == tomorrow,
                        ) {
                            repeat = ReminderRepeat.ONCE
                            scheduledDate = tomorrow
                        }
                        ReminderScheduleChoice(
                            "指定日期",
                            repeat == ReminderRepeat.ONCE &&
                                scheduledDate != today &&
                                scheduledDate != tomorrow,
                        ) {
                            repeat = ReminderRepeat.ONCE
                            if (scheduledDate == today || scheduledDate == tomorrow) {
                                scheduledDate = localReminderDateKey(
                                    System.currentTimeMillis() + 2 * 24 * 60 * 60_000L,
                                )
                            }
                            showIntentDatePicker(context, scheduledDate) {
                                repeat = ReminderRepeat.ONCE
                                scheduledDate = it
                            }
                        }
                        ReminderScheduleChoice("每周几", repeat == ReminderRepeat.WEEKLY) {
                            repeat = ReminderRepeat.WEEKLY
                        }
                    }
                    if (repeat == ReminderRepeat.ONCE) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showIntentDatePicker(context, scheduledDate) {
                                        repeat = ReminderRepeat.ONCE
                                        scheduledDate = it
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MaterialSymbol(
                                    resId = R.drawable.ic_alarm,
                                    contentDescription = null,
                                    size = 19.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = formatIntentDateLabel(scheduledDate),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                MaterialSymbol(
                                    resId = R.drawable.ic_expand,
                                    contentDescription = "选择日期",
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!triggerValid) {
                            Text(
                                "选择的时间为过去时间，无法送达",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (repeat == ReminderRepeat.WEEKLY) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            (1..7).forEach { day ->
                                IntentWeekdayChoice(
                                    label = reminderWeekdayLabel(day).removePrefix("周"),
                                    selected = weekday == day,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    weekday = day
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("提醒显示位置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReminderDisplayLocation.entries.forEach { location ->
                            IntentLocationOption(
                                location = location,
                                selected = displayLocation == location,
                                onClick = { displayLocation = location },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        text = "时间按手机本地时区；一次性任务执行后会自动关闭。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = valid,
                            onClick = {
                                val parsed = parsedTime ?: return@TextButton
                                onConfirm(
                                    initial.copy(
                                        title = title.trim(),
                                        prompt = prompt.trim(),
                                        hour = parsed.first,
                                        minute = parsed.second,
                                        repeat = repeat,
                                        scheduledDate = scheduledDate,
                                        weekday = weekday,
                                        displayLocation = displayLocation,
                                    ),
                                )
                            },
                        ) {
                            Text("保存并启用")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderScheduleChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun IntentWeekdayChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun IntentLocationOption(
    location: ReminderDisplayLocation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (location) {
        ReminderDisplayLocation.RECENT_CONVERSATION -> R.drawable.ic_history
        ReminderDisplayLocation.NEW_CONVERSATION -> R.drawable.ic_new_chat
    }
    val subtitle = when (location) {
        ReminderDisplayLocation.RECENT_CONVERSATION -> "写入最近使用的对话"
        ReminderDisplayLocation.NEW_CONVERSATION -> "为本次结果创建新对话"
    }
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .heightIn(min = 92.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.weight(1f))
                RadioButton(selected = selected, onClick = onClick)
            }
            Text(
                text = location.label,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private fun showIntentDatePicker(
    context: Context,
    current: String,
    onPick: (String) -> Unit,
) {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val calendar = Calendar.getInstance().apply {
        runCatching { format.parse(current) }.getOrNull()?.let { time = it }
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            onPick(format.format(selected.time))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun formatIntentDateLabel(value: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val output = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA)
    return runCatching { input.parse(value)?.let(output::format) }.getOrNull() ?: value
}

@Composable
private fun ReminderCloseDialog(
    intent: AssistantReminderIntent.Close,
    onDismiss: () -> Unit,
    onConfirm: (AssistantReminderIntent.Close) -> Unit,
) {
    val reminder = intent.reminder
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关闭自动化任务？") },
        text = {
            Column {
                Text(
                    text = reminder.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${reminder.scheduleLabel} ${reminder.timeLabel} · " +
                        "${reminder.displayLocation.label} · ${reminder.prompt}",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "关闭后不再自动执行，提醒仍会保留，可以稍后重新开启。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(intent) }) {
                Text("确认关闭", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ReminderDeleteDialog(
    intent: AssistantReminderIntent.Delete,
    onDismiss: () -> Unit,
    onConfirm: (AssistantReminderIntent.Delete) -> Unit,
) {
    val reminder = intent.reminder
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除自动化任务？") },
        text = {
            Column {
                Text(
                    text = reminder.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${reminder.scheduleLabel} ${reminder.timeLabel} · " +
                        "${reminder.displayLocation.label} · ${reminder.prompt}",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "删除后该任务会从本机永久移除，之后无法恢复。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(intent) }) {
                Text("确认删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
