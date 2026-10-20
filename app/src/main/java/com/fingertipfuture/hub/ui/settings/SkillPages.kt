package com.fingertipfuture.hub.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.NetworkGuard
import com.fingertipfuture.hub.data.HubSkill
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDialogCard
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 技能管理（2026-10-16 用户定的形）：
 *
 * - 一页**技能列表**；每行右边一个「更多」，里面有**删除**（只对用户自己加的）和**查看并修改**；
 * - 右下角一颗**添加**按钮 → 选添加方式：**手动添加 / 从文件导入 / 从 GitHub 导入**；
 * - 三种方式最后都得到一段 skill.md（`---name/description---` + 提示词），存在 [SkillStore] 里。
 */
@Composable
internal fun SkillManageScreen(
    onBack: () -> Unit,
    onEditSkill: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showGithub by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HubSkill?>(null) }
    val skills = SkillStore.all

    fun addParsed(text: String, fallbackName: String) {
        val parsed = SkillStore.parseSkillMd(text, fallbackName)
        if (parsed == null) {
            Toast.makeText(context, "这段内容读不出技能（要有提示词正文）", Toast.LENGTH_LONG).show()
            return
        }
        SkillStore.add(context, parsed.name, parsed.description, parsed.prompt)
        Toast.makeText(context, "已添加技能「" + parsed.name + "」", Toast.LENGTH_SHORT).show()
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = AttachmentStore.readText(context, uri)
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "这个文件读不出来（要文本类文件）", Toast.LENGTH_LONG).show()
                } else {
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
                    addParsed(text, name)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsPage(title = "技能管理", onBack = onBack) {
            SettingsGroup(title = "技能（" + skills.size + "）") {
                if (skills.isEmpty()) {
                    SettingsItem(
                        title = "暂无技能",
                        subtitle = "点右下角添加自己需要的能力",
                        topDivider = false,
                    )
                } else {
                    skills.forEachIndexed { index, skill ->
                        SkillRow(
                            skill = skill,
                            builtIn = SkillStore.isBuiltIn(skill.id),
                            topDivider = index != 0,
                            onEdit = { onEditSkill(skill.id) },
                            onDelete = { pendingDelete = skill },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PAGE_HORIZONTAL_MARGIN, bottom = 20.dp),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_add,
                contentDescription = "添加技能",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    if (showAddSheet) {
        HubBottomSheet(onDismiss = { showAddSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
            ) {
                Text(text = "添加技能", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                AddMethodRow("手动添加", "像写 skill.md 一样，粘贴或直接写") {
                    showAddSheet = false
                    showManual = true
                }
                AddMethodRow("从文件导入", "选一个 .md / .txt 技能文件") {
                    showAddSheet = false
                    pickFile.launch(arrayOf("*/*"))
                }
                AddMethodRow("从 GitHub 导入", "填一个 raw 链接，直接拉进来") {
                    showAddSheet = false
                    showGithub = true
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showManual) {
        SkillMdDialog(
            title = "手动添加技能",
            initial = "---\nname: 新技能\ndescription: 一句话说明\n---\n\n这里写提示词：告诉模型该怎么做。",
            confirmText = "保存",
            onConfirm = { text -> addParsed(text, "新技能") },
            onDismiss = { showManual = false },
        )
    }

    if (showGithub) {
        GitHubImportDialog(
            onImport = { url ->
                scope.launch {
                    val text = withContext(Dispatchers.IO) { fetchText(url) }
                    if (text.isNullOrBlank()) {
                        Toast.makeText(context, "拉不到内容（检查链接或网络）", Toast.LENGTH_LONG).show()
                    } else {
                        addParsed(text, url.substringAfterLast('/').substringBeforeLast('.'))
                    }
                }
            },
            onDismiss = { showGithub = false },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.hubDialogCard(),
            title = { Text("删除技能？") },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(onClick = {
                    SkillStore.remove(context, target.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 列表里的一行技能：名字 + 说明 + 更多（删除 / 查看并修改）。 */
@Composable
private fun SkillRow(
    skill: HubSkill,
    builtIn: Boolean,
    topDivider: Boolean,
    onEdit: () -> Unit,
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
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skill.name,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (builtIn) {
                        Spacer(Modifier.width(8.dp))
                        SettingsTag("内置")
                    }
                }
                if (skill.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = skill.description,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
                    if (!builtIn) {
                        DropdownMenuItem(
                            text = { Text("删除", fontSize = 15.sp) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (builtIn) "查看技能" else "查看并修改", fontSize = 15.sp) },
                        onClick = { menu = false; onEdit() },
                    )
                }
            }
        }
    }
}

/** 弹层里的一行添加方式。 */
@Composable
private fun AddMethodRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 贴/写 skill.md 的弹框（手动添加、以及编辑时都用它）。 */
@Composable
private fun SkillMdDialog(
    title: String,
    initial: String,
    confirmText: String,
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
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 从 GitHub（raw 链接）导入。 */
@Composable
private fun GitHubImportDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.hubDialogCard(),
        title = { Text("从 GitHub 导入") },
        text = {
            Column {
                Text(
                    text = "填一个指向 skill.md 的 raw 链接，例如：\n" +
                        "https://raw.githubusercontent.com/用户/仓库/main/skills/xxx/SKILL.md",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://raw.githubusercontent.com/…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) onImport(url.trim())
                    onDismiss()
                },
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 拉一段文本（GitHub raw 用；两万字符以内）。 */
private fun fetchText(url: String): String? = runCatching {
    NetworkGuard.requireAllowed(url)
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Accept", "text/plain")
    }
    try {
        if (conn.responseCode !in 200..299) return@runCatching null
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(20_000)
            val read = reader.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read)
        }
    } finally {
        runCatching { conn.disconnect() }
    }
}.getOrNull()

/** 「查看并修改」：把技能还原成 skill.md 看/改；内置的只给看。 */
@Composable
internal fun SkillEditScreen(skillId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val skill = SkillStore.byId(skillId) ?: return
    val builtIn = SkillStore.isBuiltIn(skillId)
    var text by remember(skillId) { mutableStateOf(SkillStore.toMarkdown(skill)) }

    SettingsPage(title = if (builtIn) "查看技能" else "查看并修改", onBack = onBack) {
        SettingsGroup(title = if (builtIn) "技能内容（内置，不能改）" else "技能内容（skill.md）") {
            OutlinedTextField(
                value = text,
                onValueChange = { if (!builtIn) text = it },
                readOnly = builtIn,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(12.dp),
            )
        }
        if (!builtIn) {
            SettingsGroup(title = "保存") {
                SettingsItem(
                    title = "保存修改",
                    icon = R.drawable.ic_edit,
                    topDivider = false,
                    onClick = {
                        val parsed = SkillStore.parseSkillMd(text, skill.name)
                        if (parsed == null) {
                            Toast.makeText(context, "内容不完整（要有提示词正文）", Toast.LENGTH_LONG).show()
                        } else {
                            SkillStore.update(context, parsed.copy(id = skill.id))
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                )
            }
        }
    }
}
