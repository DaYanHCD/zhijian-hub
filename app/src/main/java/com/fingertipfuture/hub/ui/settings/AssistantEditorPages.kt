package com.fingertipfuture.hub.ui.settings

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.MemoryEntry
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.ui.ModelPickerDialog
import com.fingertipfuture.hub.ui.chat.THINKING_DEPTHS
import com.fingertipfuture.hub.ui.chat.ThinkingDepthDialog
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.ui.provider.FLOATING_BAR_SPACE
import com.fingertipfuture.hub.ui.provider.FLOATING_BAR_TOP_MARGIN
import com.fingertipfuture.hub.ui.provider.FloatingBackBar
import com.fingertipfuture.hub.ui.provider.FloatingCircleAction
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import com.fingertipfuture.hub.ui.provider.statusBarTopPadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun AssistantBasicScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var showModel by remember { mutableStateOf(false) }
    var showDepth by remember { mutableStateOf(false) }
    var nameDraft by remember(assistant.id) { mutableStateOf(assistant.name) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveAssistantAvatar(context, assistant, uri)
    }

    val modelLabel = if (assistant.model.isBlank()) "跟全局" else assistant.model
    val depth = assistant.thinkingDepth.coerceIn(0, THINKING_DEPTHS.lastIndex)

    LaunchedEffect(assistant.id, nameDraft) {
        delay(350)
        val next = nameDraft.trim()
        if (next.isNotBlank() && next != assistant.name) {
            AssistantStore.upsert(context, assistant.copy(name = next))
        }
    }

    AssistantStandalonePage(title = "基础设定", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box {
                AssistantAvatar(
                    assistant = assistant,
                    size = 120.dp,
                    modifier = Modifier.clickable { pickAvatar.launch("image/*") },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .clickable { pickAvatar.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_edit,
                        contentDescription = "更换头像",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        }

        AssistantFormCard(padding = 0.dp) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    "助手名称",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (assistant.avatarPath.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "恢复默认头像",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching { File(assistant.avatarPath).delete() }
                            AssistantStore.upsert(context, assistant.copy(avatarPath = ""))
                        },
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            AssistantModelSettingRow(
                modelLabel = modelLabel,
                onClick = { showModel = true },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            AssistantTemperatureRow(
                value = (assistant.temperature ?: 1.0).toFloat().coerceIn(0f, 2f),
                onValueChangeFinished = { value ->
                    AssistantStore.upsert(
                        context,
                        assistant.copy(temperature = (value * 10).toInt() / 10.0),
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsItem(
                title = "思考强度",
                subtitle = "当前模型的默认思考强度",
                icon = R.drawable.ic_bulb,
                value = THINKING_DEPTHS[depth].name,
                chevron = true,
                topDivider = false,
                onClick = { showDepth = true },
            )
        }
    }

    if (showModel) {
        ModelPickerDialog(
            onDismiss = { showModel = false },
            onPick = { providerId, model ->
                AssistantStore.upsert(context, assistant.copy(providerId = providerId, model = model))
            },
            highlight = assistant.providerId to assistant.model,
        )
    }
    if (showDepth) {
        ThinkingDepthDialog(
            current = depth,
            onPick = { value -> AssistantStore.upsert(context, assistant.copy(thinkingDepth = value)) },
            onDismiss = { showDepth = false },
        )
    }
}

@Composable
private fun AssistantModelSettingRow(
    modelLabel: String,
    onClick: () -> Unit,
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = "聊天模型",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "设置助手的默认聊天模型，如果不设置，则使用全局默认聊天模型",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = capsuleShape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(capsuleShape)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (modelLabel == "跟全局") "选择模型" else modelLabel,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AssistantTemperatureRow(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
) {
    var localValue by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 2f)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_psychology,
                contentDescription = null,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "温度",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "数值越低越稳定，越高越有创造性",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", localValue),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it.coerceIn(0f, 2f) },
            onValueChangeFinished = {
                val rounded = (localValue * 10f).toInt().coerceIn(0, 20) / 10f
                localValue = rounded
                onValueChangeFinished(rounded)
            },
            valueRange = 0f..2f,
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        ) {
            Text(
                text = "稳定",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "创造",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantFormCard(
    padding: Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = settingsCardColor(),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .hubCardBorder(shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            content = content,
        )
    }
}

/** 提示词：这个助手的系统提示词。 */
@Composable
internal fun AssistantPromptScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var text by remember(assistant.id) { mutableStateOf(assistant.systemPrompt) }

    fun persistPrompt() {
        if (assistant.systemPrompt != text) {
            AssistantStore.upsert(context, assistant.copy(systemPrompt = text))
        }
    }

    LaunchedEffect(assistant.id, text) {
        delay(350)
        persistPrompt()
    }

    val cardShape = RoundedCornerShape(22.dp)
    SettingsPage(
        title = "提示词",
        scrollKey = "assistant-prompt|$assistantId",
        onBack = {
            persistPrompt()
            onBack()
        },
    ) {
        Surface(
            color = settingsCardColor(),
            shape = cardShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .hubDropShadow(cardShape)
                .hubCardBorder(cardShape),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "系统提示词",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    PromptIconAction(
                        icon = R.drawable.ic_copy,
                        contentDescription = "复制提示词",
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("系统提示词", text),
                            )
                            context.showLocalizedToast("提示词已复制")
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("告诉这个助手该怎么说话…", fontSize = 15.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                )
            }
        }
    }
}

@Composable
private fun PromptIconAction(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            resId = icon,
            contentDescription = contentDescription,
            size = 23.dp,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 技能管理：从这个助手能用的总技能里勾选。 */
@Composable
internal fun AssistantSkillScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    SettingsPage(
        title = "技能管理",
        scrollKey = "assistant-skill|$assistantId",
        onBack = onBack,
    ) {
        AssistantFormCard(padding = 0.dp) {
            SkillStore.all.forEachIndexed { index, skill ->
                SettingsSwitchRow(
                    title = skill.name,
                    subtitle = skill.description,
                    checked = skill.id in assistant.skillIds,
                    topDivider = index != 0,
                    onCheckedChange = { on ->
                        val ids = if (on) {
                            assistant.skillIds + skill.id
                        } else {
                            assistant.skillIds - skill.id
                        }
                        AssistantStore.upsert(context, assistant.copy(skillIds = ids))
                    },
                )
            }
        }
    }
}

/** 记忆设置。 */
@Composable
internal fun AssistantMemoryScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var editorEntry by remember { mutableStateOf<MemoryEntry?>(null) }
    var editorOwner by remember { mutableStateOf<String?>(null) }
    val ownMemories = MemoryStore.forAssistant(assistant.id)

    SettingsPage(
        title = "记忆设置",
        scrollKey = "assistant-memory|$assistantId",
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "添加记忆",
                onClick = { editorOwner = assistant.id },
            )
        },
    ) {
        SettingsGroup(title = "记忆") {
            SettingsSwitchRow(
                title = "记忆",
                subtitle = "助手会记住你告诉它的内容，并在后续自动调用。此功能需要模型支持工具调用才能正常工作",
                checked = assistant.memory,
                topDivider = false,
                onCheckedChange = {
                    AssistantStore.upsert(
                        context,
                        assistant.copy(memory = it, autoMemory = it),
                    )
                },
            )
            SettingsSwitchRow(
                title = "全局记忆",
                subtitle = "所有助手共享彼此的记忆",
                checked = assistant.globalMemory,
                onCheckedChange = { AssistantStore.upsert(context, assistant.copy(globalMemory = it)) },
            )
            SettingsSwitchRow(
                title = "记忆弹窗",
                subtitle = "记忆前弹窗同意",
                checked = assistant.confirmAutoMemory,
                enabled = assistant.memory,
                onCheckedChange = {
                    AssistantStore.upsert(context, assistant.copy(confirmAutoMemory = it))
                },
            )
            SettingsSwitchRow(
                title = "参考历史聊天记录",
                subtitle = "允许助手在过去的对话中搜索相关信息",
                checked = assistant.useHistory,
                onCheckedChange = { AssistantStore.upsert(context, assistant.copy(useHistory = it)) },
            )
        }
        SettingsGroup(title = "助手记忆") {
            ownMemories.forEachIndexed { index, entry ->
                SettingsItem(
                    title = entry.text,
                    subtitle = formatMemoryDate(entry.createdAt),
                    icon = R.drawable.ic_history,
                    topDivider = index != 0,
                    onClick = { editorEntry = entry },
                    trailing = {
                        TextButton(onClick = { MemoryStore.delete(context, entry.id) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            }
            if (ownMemories.isEmpty()) {
                Text(
                    text = "还没有记忆，点击右上角添加",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }
        }
    }

    val editing = editorEntry
    val addingOwner = editorOwner
    if (editing != null || addingOwner != null) {
        var text by remember(editing?.id, addingOwner) {
            mutableStateOf(editing?.text.orEmpty())
        }
        val shownDate = formatMemoryDate(editing?.createdAt ?: System.currentTimeMillis())
        HubBottomSheet(
            onDismiss = {
                editorEntry = null
                editorOwner = null
            },
            modifier = Modifier.imePadding(),
            minHeight = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
            ) {
                Text(
                    text = if (editing != null) "编辑记忆" else "添加记忆",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = shownDate,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    placeholder = { Text("例如：我喜欢简洁的回答", fontSize = 14.sp) },
                    minLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            editorEntry = null
                            editorOwner = null
                        },
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            if (editing != null) {
                                MemoryStore.update(context, editing.id, text)
                            } else if (addingOwner != null) {
                                MemoryStore.add(context, addingOwner, text)
                            }
                            editorEntry = null
                            editorOwner = null
                        },
                    ) {
                        Text("保存")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatMemoryDate(timestamp: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

