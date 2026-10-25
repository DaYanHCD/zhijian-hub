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
internal fun AssistantRemindersScreen(
    assistantId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val reminders = AssistantReminderStore.forAssistant(assistantId)
    var editor by remember { mutableStateOf<AssistantReminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            context.showLocalizedToast("没有通知权限，事项到点后无法提醒", Toast.LENGTH_LONG)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsPage(
        title = "提醒任务",
        scrollKey = "assistant-reminders|$assistantId",
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "添加提醒任务",
                onClick = { adding = true },
            )
        },
    ) {
        if (reminders.isNotEmpty()) {
            SettingsGroup {
                reminders.forEachIndexed { index, reminder ->
                    ReminderTaskRow(
                        reminder = reminder,
                        assistantName = assistant.name,
                        topDivider = index != 0,
                        onEdit = { editor = reminder },
                        onToggle = { enabled ->
                            val next = reminder.copy(enabled = enabled)
                            AssistantReminderStore.upsert(context, next)
                            if (enabled) {
                                requestNotificationPermissionIfNeeded()
                                AssistantReminderScheduler.schedule(context, next)
                            } else {
                                AssistantReminderScheduler.cancel(context, reminder.id)
                            }
                        },
                        onDelete = {
                            AssistantReminderScheduler.cancel(context, reminder.id)
                            AssistantReminderStore.remove(context, reminder.id)
                        },
                    )
                }
            }
        }
    }

    if (adding || editor != null) {
        val target = editor
        ReminderEditorDialog(
            initial = target,
            assistantId = assistantId,
            onDismiss = {
                adding = false
                editor = null
            },
            onSave = { reminder ->
                AssistantReminderStore.upsert(context, reminder)
                requestNotificationPermissionIfNeeded()
                if (reminder.enabled) {
                    AssistantReminderScheduler.schedule(context, reminder)
                } else {
                    AssistantReminderScheduler.cancel(context, reminder.id)
                }
                adding = false
                editor = null
            },
        )
    }

}

@Composable
internal fun AutomationTasksScreen(
    onBack: () -> Unit,
    onOpenReminderTasks: () -> Unit,
) {
    val context = LocalContext.current
    val reminders = AssistantReminderStore.items
    val assistants = AssistantStore.items.filterNot { it.underDevelopment }
    var choosingAssistant by remember { mutableStateOf(false) }
    var addingAssistantId by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            context.showLocalizedToast("没有通知权限，事项到点后无法提醒", Toast.LENGTH_LONG)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun beginAdd() {
        if (assistants.size == 1) {
            addingAssistantId = assistants.first().id
        } else {
            choosingAssistant = true
        }
    }

    SettingsPage(
        title = "自动化任务",
        scrollKey = "automation-tasks",
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "添加提醒任务",
                onClick = ::beginAdd,
            )
        },
    ) {
        if (reminders.isNotEmpty()) {
            SettingsGroup {
                SettingsItem(
                    title = "提醒任务",
                    subtitle = "共 ${reminders.size} 个提醒任务",
                    icon = R.drawable.ic_alarm,
                    chevron = true,
                    topDivider = false,
                    onClick = onOpenReminderTasks,
                )
            }
        }
    }

    if (choosingAssistant) {
        ReminderAssistantPickerDialog(
            onDismiss = { choosingAssistant = false },
            onPick = { assistantId ->
                choosingAssistant = false
                addingAssistantId = assistantId
            },
        )
    }

    addingAssistantId?.let { assistantId ->
        ReminderEditorDialog(
            initial = null,
            assistantId = assistantId,
            onDismiss = { addingAssistantId = null },
            onSave = { reminder ->
                AssistantReminderStore.upsert(context, reminder)
                requestNotificationPermissionIfNeeded()
                if (reminder.enabled) {
                    AssistantReminderScheduler.schedule(context, reminder)
                } else {
                    AssistantReminderScheduler.cancel(context, reminder.id)
                }
                addingAssistantId = null
            },
        )
    }
}

@Composable
internal fun AllAssistantRemindersScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var choosingAssistant by remember { mutableStateOf(false) }
    var addingAssistantId by remember { mutableStateOf<String?>(null) }
    var editingReminder by remember { mutableStateOf<AssistantReminder?>(null) }
    val assistantNames = AssistantStore.items.associate { it.id to it.name }
    val assistants = AssistantStore.items.filterNot { it.underDevelopment }
    val reminders = AssistantReminderStore.items.sortedWith(
        compareBy<AssistantReminder>(
            { assistantNames[it.assistantId].orEmpty() },
            { it.hour },
            { it.minute },
            { it.title },
        ),
    )
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            context.showLocalizedToast("没有通知权限，事项到点后无法提醒", Toast.LENGTH_LONG)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun beginAdd() {
        if (assistants.size == 1) {
            addingAssistantId = assistants.first().id
        } else {
            choosingAssistant = true
        }
    }

    SettingsPage(
        title = "提醒任务",
        scrollKey = "all-reminders",
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "添加提醒任务",
                onClick = ::beginAdd,
            )
        },
    ) {
        if (reminders.isNotEmpty()) {
            SettingsGroup {
                reminders.forEachIndexed { index, reminder ->
                    val assistantName = assistantNames[reminder.assistantId] ?: "已删除的助手"
                    ReminderTaskRow(
                        reminder = reminder,
                        assistantName = assistantName,
                        topDivider = index != 0,
                        onEdit = { editingReminder = reminder },
                        onToggle = { enabled ->
                            val next = reminder.copy(enabled = enabled)
                            AssistantReminderStore.upsert(context, next)
                            if (enabled) {
                                requestNotificationPermissionIfNeeded()
                                AssistantReminderScheduler.schedule(context, next)
                            } else {
                                AssistantReminderScheduler.cancel(context, reminder.id)
                            }
                        },
                        onDelete = {
                            AssistantReminderScheduler.cancel(context, reminder.id)
                            AssistantReminderStore.remove(context, reminder.id)
                        },
                    )
                }
            }
        }
    }

    if (choosingAssistant) {
        ReminderAssistantPickerDialog(
            onDismiss = { choosingAssistant = false },
            onPick = { assistantId ->
                choosingAssistant = false
                addingAssistantId = assistantId
            },
        )
    }

    addingAssistantId?.let { assistantId ->
        ReminderEditorDialog(
            initial = null,
            assistantId = assistantId,
            onDismiss = { addingAssistantId = null },
            onSave = { reminder ->
                AssistantReminderStore.upsert(context, reminder)
                requestNotificationPermissionIfNeeded()
                if (reminder.enabled) {
                    AssistantReminderScheduler.schedule(context, reminder)
                } else {
                    AssistantReminderScheduler.cancel(context, reminder.id)
                }
                addingAssistantId = null
            },
        )
    }

    editingReminder?.let { reminder ->
        ReminderEditorDialog(
            initial = reminder,
            assistantId = reminder.assistantId,
            onDismiss = { editingReminder = null },
            onSave = { updated ->
                AssistantReminderStore.upsert(context, updated)
                requestNotificationPermissionIfNeeded()
                if (updated.enabled) {
                    AssistantReminderScheduler.schedule(context, updated)
                } else {
                    AssistantReminderScheduler.cancel(context, updated.id)
                }
                editingReminder = null
            },
        )
    }

}

@Composable
private fun ReminderAssistantPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val assistants = AssistantStore.items.filterNot { it.underDevelopment }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择执行任务的助手",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(8.dp))
                assistants.forEach { assistant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(assistant.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistantAvatar(assistant = assistant, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = assistant.name,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        MaterialSymbol(
                            resId = R.drawable.ic_expand,
                            contentDescription = null,
                            size = 17.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ReminderTaskRow(
    reminder: AssistantReminder,
    assistantName: String,
    topDivider: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_alarm,
                contentDescription = null,
                size = 24.dp,
                tint = if (reminder.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    SettingsTag(
                        when (reminder.displayLocation) {
                            ReminderDisplayLocation.RECENT_CONVERSATION -> "最近对话"
                            ReminderDisplayLocation.NEW_CONVERSATION -> "新建对话"
                        },
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${reminder.scheduleLabel} ${reminder.timeLabel} / " +
                        assistantName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onDelete) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

