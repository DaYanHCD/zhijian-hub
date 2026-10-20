package com.fingertipfuture.hub.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.MemoryEntry
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.ui.ModelPickerDialog
import com.fingertipfuture.hub.ui.chat.THINKING_DEPTHS
import com.fingertipfuture.hub.ui.chat.ThinkingDepthDialog
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDialogCard
import java.io.File

/** 助手的头像：设过图就用图，没设就用「名字首字 + 圆底」。 */
@Composable
internal fun AssistantAvatar(
    assistant: HubAssistant,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(assistant.avatarPath) {
        assistant.avatarPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = assistant.name.take(1).ifBlank { "AI" },
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 一行文字输入的弹框（名称 / 温度 / 最大 token 都用它）。 */
@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    hint: String = "",
    numeric: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 把选中的图片存成这个助手的头像（方图 + 压到 256）。 */
private fun saveAssistantAvatar(context: Context, assistant: HubAssistant, uri: android.net.Uri) {
    val bitmap: Bitmap = AvatarStore.decodeSquare(context, uri) ?: return
    val file: File = AssistantStore.avatarFile(context, assistant.id)
    runCatching {
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    }.onSuccess {
        AssistantStore.upsert(context, assistant.copy(avatarPath = file.absolutePath))
    }
}

/** 助手列表：头像 + 名称 + 更多（复制 / 删除）。 */
@Composable
internal fun AssistantListScreen(
    onBack: () -> Unit,
    onOpenAssistant: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HubAssistant?>(null) }
    val items = AssistantStore.items

    SettingsPage(title = "助手管理", onBack = onBack) {
        SettingsGroup(title = "助手") {
            items.forEachIndexed { index, assistant ->
                AssistantRow(
                    assistant = assistant,
                    isCurrent = assistant.id == AssistantStore.current.id,
                    topDivider = index != 0,
                    onOpen = { onOpenAssistant(assistant.id) },
                    onDuplicate = { AssistantStore.duplicate(context, assistant.id) },
                    onDelete = { pendingDelete = assistant },
                )
            }
        }
        SettingsGroup(title = "新建") {
            SettingsItem(
                title = "新建助手",
                subtitle = "从零建一个自己的助手",
                icon = R.drawable.ic_add,
                topDivider = false,
                onClick = {
                    val assistant = AssistantStore.createAssistant(context, "新助手")
                    onOpenAssistant(assistant.id)
                },
            )
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.hubDialogCard(),
            title = { Text("删除这个助手？") },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(onClick = {
                    AssistantStore.remove(context, target.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AssistantRow(
    assistant: HubAssistant,
    isCurrent: Boolean,
    topDivider: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
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
                .clickable(enabled = !assistant.underDevelopment, onClick = onOpen)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantAvatar(assistant = assistant, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = assistant.name,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (assistant.underDevelopment) {
                    Spacer(Modifier.width(8.dp))
                    SettingsTag("正在开发")
                }
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    SettingsTag("当前")
                }
            }
            // 开发中的内置助手不参与复制 / 删除，免得删掉后下次启动又自动补回来。
            if (!assistant.underDevelopment) {
                Box {
                    MaterialSymbol(
                        resId = R.drawable.ic_more_vertical,
                        contentDescription = "更多",
                        modifier = Modifier
                            .clickable { menu = true }
                            .padding(6.dp),
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("复制", fontSize = 15.sp) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", fontSize = 15.sp) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** 助手界面：头像 + 名称 + 五个入口。 */
@Composable
internal fun AssistantDetailScreen(
    assistantId: String,
    onBack: () -> Unit,
    onOpenBasic: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenSkill: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId) ?: return
    val isCurrent = assistant.id == AssistantStore.current.id
    val modelLabel = if (assistant.model.isBlank()) "跟全局" else assistant.model

    SettingsPage(title = assistant.name, onBack = onBack) {
        SettingsGroup(title = "助手") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistantAvatar(assistant = assistant, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "默认聊天模型：" + modelLabel,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (assistant.underDevelopment) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "这个助手还在开发中，暂不可设为当前",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (assistant.underDevelopment) {
                    SettingsTag("正在开发")
                } else if (!isCurrent) {
                    Text(
                        text = "设为当前",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { AssistantStore.setCurrent(context, assistant.id) },
                    )
                } else {
                    SettingsTag("当前")
                }
            }
        }
        SettingsGroup(title = "设定") {
            SettingsItem(
                title = "基础设定",
                subtitle = "头像、名称与模型参数",
                icon = R.drawable.smart_toy,
                chevron = true,
                topDivider = false,
                onClick = onOpenBasic,
            )
            SettingsItem(
                title = "提示词",
                subtitle = "这个助手的系统提示词",
                icon = R.drawable.ic_edit,
                chevron = true,
                onClick = onOpenPrompt,
            )
            SettingsItem(
                title = "技能管理",
                subtitle = assistant.skillIds.size.toString() + " 个技能",
                icon = R.drawable.ic_bulb,
                chevron = true,
                onClick = onOpenSkill,
            )
            SettingsItem(
                title = "记忆设置",
                subtitle = "记忆与历史记录",
                icon = R.drawable.ic_history,
                chevron = true,
                onClick = onOpenMemory,
            )
        }
    }
}

/** 基础设定：头像 / 名称 / 默认聊天模型 / 温度 / 思考强度 / 最大 token。 */
@Composable
internal fun AssistantBasicScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId) ?: return
    var showName by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showTemp by remember { mutableStateOf(false) }
    var showDepth by remember { mutableStateOf(false) }
    var showTokens by remember { mutableStateOf(false) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveAssistantAvatar(context, assistant, uri)
    }

    // 右侧只显示模型名（2026-10-17 用户要求）
    val modelLabel = if (assistant.model.isBlank()) "跟全局" else assistant.model
    val depth = assistant.thinkingDepth.coerceIn(0, THINKING_DEPTHS.lastIndex)

    SettingsPage(title = "基础设定", onBack = onBack) {
        SettingsGroup(title = "身份") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickAvatar.launch("image/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistantAvatar(assistant = assistant, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("助手头像", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "点一下从相册选一张",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (assistant.avatarPath.isNotBlank()) {
                SettingsItem(
                    title = "恢复默认头像",
                    icon = R.drawable.ic_regenerate,
                    onClick = {
                        runCatching { File(assistant.avatarPath).delete() }
                        AssistantStore.upsert(context, assistant.copy(avatarPath = ""))
                    },
                )
            }
            SettingsItem(
                title = "名称",
                icon = R.drawable.ic_edit,
                value = assistant.name,
                chevron = true,
                topDivider = assistant.avatarPath.isBlank(),
                onClick = { showName = true },
            )
        }
        SettingsGroup(title = "模型与参数") {
            SettingsItem(
                title = "默认聊天模型",
                subtitle = "留空时跟随全局设置",
                icon = R.drawable.ic_provider,
                value = modelLabel,
                chevron = true,
                topDivider = false,
                onClick = { showModel = true },
            )
            SettingsItem(
                title = "温度",
                subtitle = "0 ~ 2；留空 = 不指定",
                icon = R.drawable.ic_psychology,
                value = assistant.temperature?.toString() ?: "未设置",
                chevron = true,
                onClick = { showTemp = true },
            )
            SettingsItem(
                title = "思考强度",
                subtitle = "当前模型的默认思考强度",
                icon = R.drawable.ic_bulb,
                value = THINKING_DEPTHS[depth].name,
                chevron = true,
                onClick = { showDepth = true },
            )
            SettingsItem(
                title = "最大 token 数",
                subtitle = "这个助手单次生成的上限；留空 = 无限制",
                icon = R.drawable.ic_list,
                value = assistant.maxTokens?.toString() ?: "无限制",
                chevron = true,
                onClick = { showTokens = true },
            )
        }
    }

    if (showName) {
        TextInputDialog(
            title = "助手名称",
            initial = assistant.name,
            onConfirm = { value ->
                if (value.isNotBlank()) AssistantStore.upsert(context, assistant.copy(name = value.trim()))
            },
            onDismiss = { showName = false },
        )
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
    if (showTemp) {
        TextInputDialog(
            title = "温度（0 ~ 2，留空 = 不指定）",
            initial = assistant.temperature?.toString().orEmpty(),
            numeric = true,
            onConfirm = { value ->
                val parsed = value.trim().toDoubleOrNull()
                AssistantStore.upsert(
                    context,
                    assistant.copy(temperature = parsed?.coerceIn(0.0, 2.0)),
                )
            },
            onDismiss = { showTemp = false },
        )
    }
    if (showDepth) {
        ThinkingDepthDialog(
            current = depth,
            onPick = { value -> AssistantStore.upsert(context, assistant.copy(thinkingDepth = value)) },
            onDismiss = { showDepth = false },
        )
    }
    if (showTokens) {
        TextInputDialog(
            title = "最大 token（留空 = 无限制）",
            initial = assistant.maxTokens?.toString().orEmpty(),
            numeric = true,
            onConfirm = { value ->
                AssistantStore.upsert(context, assistant.copy(maxTokens = value.trim().toIntOrNull()))
            },
            onDismiss = { showTokens = false },
        )
    }
}

/** 提示词：这个助手的系统提示词。 */
@Composable
internal fun AssistantPromptScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId) ?: return
    var text by remember(assistant.id) { mutableStateOf(assistant.systemPrompt) }

    SettingsPage(title = "提示词", onBack = onBack) {
        SettingsGroup(title = "系统提示词") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("告诉这个助手该怎么说话…", fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(12.dp),
            )
        }
        SettingsGroup(title = "保存") {
            SettingsItem(
                title = "保存提示词",
                icon = R.drawable.ic_edit,
                topDivider = false,
                onClick = {
                    AssistantStore.upsert(context, assistant.copy(systemPrompt = text))
                    onBack()
                },
            )
        }
    }
}

/** 技能管理：从这个助手能用的总技能里勾选。 */
@Composable
internal fun AssistantSkillScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId) ?: return

    SettingsPage(title = "技能管理", onBack = onBack) {
        SettingsGroup(title = "可用技能") {
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
        SettingsGroup(title = "调用方式") {
            SettingsItem(
                title = "按需启用",
                subtitle = "开关只表示这个技能可用；只有相关消息或点名时才会真正注入",
                icon = R.drawable.ic_bulb,
                topDivider = false,
            )
        }
    }
}

/** 记忆设置。 */
@Composable
internal fun AssistantMemoryScreen(assistantId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val assistant = AssistantStore.byId(assistantId) ?: return
    var editorEntry by remember { mutableStateOf<MemoryEntry?>(null) }
    var editorOwner by remember { mutableStateOf<String?>(null) }
    val ownMemories = MemoryStore.forAssistant(assistant.id)
    val globalMemories = MemoryStore.global()

    SettingsPage(title = "记忆设置", onBack = onBack) {
        SettingsGroup(title = "记忆") {
            SettingsSwitchRow(
                title = "记忆",
                subtitle = "只在问题相关或明确问「还记得」时注入；关闭后不注入",
                checked = assistant.memory,
                topDivider = false,
                onCheckedChange = { AssistantStore.upsert(context, assistant.copy(memory = it)) },
            )
            SettingsSwitchRow(
                title = "全局记忆",
                subtitle = "所有助手共用，同样只在相关时注入",
                checked = assistant.globalMemory,
                onCheckedChange = { AssistantStore.upsert(context, assistant.copy(globalMemory = it)) },
            )
            SettingsSwitchRow(
                title = "参考历史聊天记录",
                subtitle = "关闭后只发送当前这条",
                checked = assistant.useHistory,
                onCheckedChange = { AssistantStore.upsert(context, assistant.copy(useHistory = it)) },
            )
        }
        SettingsGroup(title = "这个助手的记忆") {
            ownMemories.forEachIndexed { index, entry ->
                SettingsItem(
                    title = entry.text,
                    subtitle = "点击编辑 · 仅对本机这个助手生效",
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
            SettingsItem(
                title = "添加记忆",
                subtitle = "例如：我喜欢简洁的回答",
                icon = R.drawable.ic_add,
                topDivider = ownMemories.isNotEmpty(),
                onClick = { editorOwner = assistant.id },
            )
        }
        SettingsGroup(title = "全局记忆（所有助手共用）") {
            globalMemories.forEachIndexed { index, entry ->
                SettingsItem(
                    title = entry.text,
                    subtitle = "点击编辑 · 所有助手共用",
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
            SettingsItem(
                title = "添加全局记忆",
                subtitle = "例如：用户希望默认用中文回答",
                icon = R.drawable.ic_add,
                topDivider = globalMemories.isNotEmpty(),
                onClick = { editorOwner = MemoryStore.GLOBAL_OWNER },
            )
        }
    }

    val editing = editorEntry
    val addingOwner = editorOwner
    if (editing != null || addingOwner != null) {
        var text by remember(editing?.id, addingOwner) {
            mutableStateOf(editing?.text.orEmpty())
        }
        AlertDialog(
            onDismissRequest = {
                editorEntry = null
                editorOwner = null
            },
            modifier = Modifier.hubDialogCard(),
            title = { Text(if (editing != null) "编辑记忆" else "添加记忆") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    minLines = 3,
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editorEntry = null
                        editorOwner = null
                    },
                ) { Text("取消") }
            },
        )
    }
}

