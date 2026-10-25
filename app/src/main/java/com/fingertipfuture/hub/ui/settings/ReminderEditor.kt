package com.fingertipfuture.hub.ui.settings

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderScheduler
import com.fingertipfuture.hub.data.AssistantReminderStore
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.ReminderDisplayLocation
import com.fingertipfuture.hub.data.ReminderRepeat
import com.fingertipfuture.hub.data.localReminderDateKey
import com.fingertipfuture.hub.data.nextReminderTriggerAtMillis
import com.fingertipfuture.hub.data.reminderWeekdayLabel
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.ui.provider.FloatingCircleAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ReminderEditorDialog(
    initial: AssistantReminder?,
    assistantId: String,
    onDismiss: () -> Unit,
    onSave: (AssistantReminder) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var prompt by remember(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var time by remember(initial?.id) {
        mutableStateOf(initial?.timeLabel ?: "08:00")
    }
    var repeat by remember(initial?.id) {
        mutableStateOf(initial?.repeat ?: ReminderRepeat.DAILY)
    }
    var scheduledDate by remember(initial?.id) {
        mutableStateOf(initial?.scheduledDate?.takeIf { it.isNotBlank() } ?: localReminderDateKey())
    }
    var weekdays by remember(initial?.id) {
        mutableStateOf(
            (initial?.weekdays?.takeIf { it.isNotEmpty() }
                ?: listOf(initial?.weekday ?: 1))
                .filter { it in 1..7 }
                .toSet(),
        )
    }
    var displayLocation by remember(initial?.id) {
        mutableStateOf(
            initial?.displayLocation ?: ReminderDisplayLocation.RECENT_CONVERSATION,
        )
    }
    val parsedTime = parseReminderTime(time)
    val draft = AssistantReminder(
        id = initial?.id ?: "draft",
        assistantId = assistantId,
        title = title,
        prompt = prompt,
        hour = parsedTime?.first ?: 0,
        minute = parsedTime?.second ?: 0,
        repeat = repeat,
        scheduledDate = scheduledDate,
        weekday = weekdays.minOrNull() ?: 1,
        weekdays = weekdays.sorted(),
        displayLocation = displayLocation,
    )
    val triggerValid = repeat != ReminderRepeat.ONCE ||
        nextReminderTriggerAtMillis(draft) != null
    val valid = title.isNotBlank() && prompt.isNotBlank() && parsedTime != null && triggerValid

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
                        text = if (initial == null) "添加自动化任务" else "编辑自动化任务",
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
                    ScheduleChoice("每天", repeat == ReminderRepeat.DAILY) {
                        repeat = ReminderRepeat.DAILY
                    }
                    ScheduleChoice(
                        "今天",
                        repeat == ReminderRepeat.ONCE && scheduledDate == today,
                    ) {
                        repeat = ReminderRepeat.ONCE
                        scheduledDate = today
                    }
                    ScheduleChoice(
                        "明天",
                        repeat == ReminderRepeat.ONCE && scheduledDate == tomorrow,
                    ) {
                        repeat = ReminderRepeat.ONCE
                        scheduledDate = tomorrow
                    }
                    ScheduleChoice(
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
                        showReminderDatePicker(context, scheduledDate) {
                            repeat = ReminderRepeat.ONCE
                            scheduledDate = it
                        }
                    }
                    ScheduleChoice("每周几", repeat == ReminderRepeat.WEEKLY) {
                        repeat = if (weekdays.size == 7) {
                            ReminderRepeat.DAILY
                        } else {
                            ReminderRepeat.WEEKLY
                        }
                    }
                }
                if (repeat == ReminderRepeat.ONCE) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = settingsCardColor(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showReminderDatePicker(context, scheduledDate) {
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
                                text = formatReminderDateLabel(scheduledDate),
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
                            WeekdayChoice(
                                label = reminderWeekdayLabel(day).removePrefix("周"),
                                selected = day in weekdays,
                                modifier = Modifier.weight(1f),
                            ) {
                                val next = if (day in weekdays) {
                                    weekdays - day
                                } else {
                                    weekdays + day
                                }
                                when {
                                    next.size == 7 -> {
                                        weekdays = next
                                        repeat = ReminderRepeat.DAILY
                                    }
                                    next.isEmpty() -> Unit
                                    else -> weekdays = next
                                }
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
                        ReminderLocationOption(
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
                                onSave(
                                    (initial ?: AssistantReminder(
                                        id = AssistantReminderStore.newId(),
                                        assistantId = assistantId,
                                        title = title,
                                        prompt = prompt,
                                        hour = parsed.first,
                                        minute = parsed.second,
                                    )).copy(
                                        title = title.trim(),
                                        prompt = prompt.trim(),
                                        hour = parsed.first,
                                        minute = parsed.second,
                                        repeat = repeat,
                                        scheduledDate = scheduledDate,
                                        weekday = weekdays.minOrNull() ?: 1,
                                        weekdays = weekdays.sorted(),
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
private fun ScheduleChoice(
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
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun WeekdayChoice(
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
private fun ReminderLocationOption(
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
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
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
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                )
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

private fun parseReminderTime(value: String): Pair<Int, Int>? {
    val match = Regex("""^\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*$""")
        .matchEntire(value) ?: return null
    return match.groupValues[1].toInt() to match.groupValues[2].toInt()
}

private fun showReminderDatePicker(
    context: android.content.Context,
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

private fun formatReminderDateLabel(value: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val output = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA)
    return runCatching { input.parse(value)?.let(output::format) }.getOrNull() ?: value
}
