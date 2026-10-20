package com.fingertipfuture.hub.ui.settings

import android.widget.Toast
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.SearchApi
import com.fingertipfuture.hub.data.SearchProvider
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDialogCard
import kotlinx.coroutines.launch

/** 「测试搜索」用的那条查询。 */
private const val TEST_QUERY = "指尖Hub"

/** 搜索供应商的官方标志（彩色，来自 LobeHub icons）。 */
private fun kindIcon(kind: String): Int = when (kind) {
    SearchProvider.KIND_BING -> R.drawable.ic_search_bing
    SearchProvider.KIND_DEEPSEEK -> R.drawable.ic_brand_deepseek
    SearchProvider.KIND_DOUBAO -> R.drawable.ic_search_doubao
    SearchProvider.KIND_TAVILY -> R.drawable.ic_search_tavily
    SearchProvider.KIND_FIRECRAWL -> R.drawable.ic_search_firecrawl
    else -> R.drawable.ic_search
}

/** 上面那三个是彩色 Logo，不要跟着主题 tint。 */
private fun kindColored(kind: String): Boolean = kind in setOf(
    SearchProvider.KIND_BING,
    SearchProvider.KIND_DEEPSEEK,
    SearchProvider.KIND_DOUBAO,
    SearchProvider.KIND_TAVILY,
    SearchProvider.KIND_FIRECRAWL,
)

/** 搜索设置：供应商列表 + 并行搜索 + 结果条数 + 右下角添加按钮。 */
@Composable
internal fun SearchSettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showMax by remember { mutableStateOf(false) }
    val items = SearchStore.items

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsPage(title = "搜索设置", onBack = onBack) {
            SettingsGroup(title = "搜索供应商（" + items.size + "）") {
                SettingsItem(
                    title = "自动联网搜索已开启",
                    subtitle = "只有需要实时信息时才搜索；普通闲聊、写作和翻译不会搜索",
                    icon = R.drawable.ic_search,
                    topDivider = false,
                )
                if (items.isEmpty()) {
                    SettingsItem(title = "还没有搜索供应商", icon = R.drawable.ic_search)
                }
                items.forEach { provider ->
                    SettingsItem(
                        title = provider.name,
                        subtitle = buildString {
                            append(if (provider.deep) "全面模式" else "普通模式")
                            if (!provider.inParallel) append(" · 未参与并行搜索")
                        },
                        icon = kindIcon(provider.kind),
                        iconTint = if (kindColored(provider.kind)) Color.Unspecified else null,
                        chevron = true,
                        topDivider = true,
                        onClick = { onOpenProvider(provider.id) },
                    )
                }
            }
            SettingsGroup(title = "并行搜索") {
                if (items.isEmpty()) {
                    SettingsItem(title = "先添加供应商", icon = R.drawable.ic_list, topDivider = false)
                }
                items.forEachIndexed { index, provider ->
                    SettingsSwitchRow(
                        title = provider.name,
                subtitle = "与其他供应商并行调用",
                        checked = provider.inParallel,
                        topDivider = index != 0,
                        onCheckedChange = { SearchStore.upsert(context, provider.copy(inParallel = it)) },
                    )
                }
            }
            SettingsGroup(title = "搜索结果") {
                SettingsItem(
                    title = "结果条数",
                subtitle = "每次搜索返回的条数",
                    icon = R.drawable.ic_list,
                    value = SearchStore.maxResults.toString(),
                    chevron = true,
                    topDivider = false,
                    onClick = { showMax = true },
                )
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PAGE_HORIZONTAL_MARGIN, bottom = 20.dp),
        ) {
            MaterialSymbol(
                resId = R.drawable.ic_add,
                contentDescription = "添加搜索供应商",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    if (showAdd) {
        HubBottomSheet(onDismiss = { showAdd = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
            ) {
                Text(text = "选择搜索供应商", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                SearchStore.kinds.forEach { option ->
                    val kind = option.first
                    val name = option.second
                    val hint = option.third
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val created = SearchStore.add(context, kind, name)
                                showAdd = false
                                onOpenProvider(created.id)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialSymbol(
                            resId = kindIcon(kind),
                            contentDescription = null,
                            size = 26.dp,
                            tint = Color.Unspecified,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(text = name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = hint,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showMax) {
        var text by remember { mutableStateOf(SearchStore.maxResults.toString()) }
        AlertDialog(
            onDismissRequest = { showMax = false },
            modifier = Modifier.hubDialogCard(),
            title = { Text("每次搜几条？") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.trim().toIntOrNull()?.let { SearchStore.setMaxResults(context, it) }
                    showMax = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showMax = false }) { Text("取消") } },
        )
    }
}

/** 一个搜索供应商的配置页：API Key / 模式 / 测试搜索 / 保存。 */
@Composable
internal fun SearchProviderConfigScreen(
    providerId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = SearchStore.byId(providerId) ?: return
    var draft by remember(providerId) { mutableStateOf(original) }
    var testing by remember { mutableStateOf(false) }
    var apiKeyVisible by remember(providerId) { mutableStateOf(false) }
    val modeLabels = listOf("普通模式", "全面模式")

    SettingsPage(title = original.name, onBack = onBack) {
        SettingsGroup(title = "状态") {
            SettingsSwitchRow(
                title = "启用这个供应商",
                subtitle = "关闭后不参与聊天联网搜索，也不参与并行搜索",
                checked = draft.enabled,
                topDivider = false,
                onCheckedChange = { draft = draft.copy(enabled = it) },
            )
            SettingsSwitchRow(
                title = "参与并行搜索",
                subtitle = if (SearchStore.parallel.any { it.id != draft.id }) {
                    "打开后会和其他已开启的供应商一起并行调用"
                } else {
                    "当前没有其他并行供应商，打开后会作为主搜索"
                },
                checked = draft.inParallel,
                onCheckedChange = { draft = draft.copy(inParallel = it) },
            )
        }

        SettingsGroup(title = "接入") {
            // Bing 是免费的抓取方案，不需要 Key
            if (draft.kind != SearchProvider.KIND_BING) {
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        MaterialSymbol(
                            resId = if (apiKeyVisible) {
                                R.drawable.ic_visibility_slash_simple
                            } else {
                                R.drawable.ic_visibility_simple
                            },
                            contentDescription = if (apiKeyVisible) "隐藏 Key" else "显示 Key",
                            size = 22.dp,
                            modifier = Modifier.clickable { apiKeyVisible = !apiKeyVisible },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }
            if (draft.kind == SearchProvider.KIND_SEARXNG) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    label = { Text("SearXNG 地址") },
                    placeholder = { Text("http://192.168.1.10:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
            if (draft.kind == SearchProvider.KIND_GOOGLE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    label = { Text("cx（搜索引擎 id）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("显示名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
            if (draft.kind == SearchProvider.KIND_DEEPSEEK) {
                SettingsItem(
                    title = "沿用聊天里的 DeepSeek Key",
                    subtitle = "直接使用已经配置好的 DeepSeek 提供商 API Key",
                    icon = R.drawable.ic_brand_deepseek,
                    iconTint = Color.Unspecified,
                    topDivider = false,
                    onClick = {
                        val key = ProviderStore.providers
                            .firstOrNull {
                                (it.logo == "deepseek" || it.baseUrl.contains("api.deepseek.com")) &&
                                    it.apiKey.isNotBlank()
                            }
                            ?.apiKey
                        if (key.isNullOrBlank()) {
                            Toast.makeText(context, "还没有可沿用的 DeepSeek Key", Toast.LENGTH_SHORT).show()
                        } else {
                            draft = draft.copy(apiKey = key)
                            Toast.makeText(context, "已沿用 DeepSeek Key", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingsItem(
                    title = "官方计费",
                    subtitle = "每 1000 次成功 Search 请求 5 元，失败不计费；全面模式会取正文，仍只算一次 Search",
                    icon = R.drawable.ic_info,
                )
            }
        }

        SettingsGroup(title = "模式") {
            SettingsChoiceRow(
                title = "搜索模式",
                subtitle = if (draft.kind == SearchProvider.KIND_DEEPSEEK) {
                    "普通：摘要；全面：摘要 + 网页正文（仍只算一次 Search）"
                } else {
                    "普通：标题 / 链接 / 摘要；全面：更完整，更费额度"
                },
                value = modeLabels[if (draft.deep) 1 else 0],
                options = modeLabels,
                topDivider = false,
                onPick = { index -> draft = draft.copy(deep = index == 1) },
            )
        }

        SettingsGroup(title = "测试") {
            SettingsItem(
                title = if (testing) "正在测试…" else "测试搜索",
                subtitle = "用「" + TEST_QUERY + "」发一次测试查询",
                icon = R.drawable.ic_search,
                topDivider = false,
                onClick = {
                    if (!testing) {
                        testing = true
                        scope.launch {
                            val result = SearchApi.search(draft, TEST_QUERY, SearchStore.maxResults)
                            testing = false
                            result.onSuccess { hits ->
                                val head = hits.take(3).joinToString("\n") { "· " + it.title }
                                Toast.makeText(
                                    context,
                                    "通了，" + hits.size + " 条结果：\n" + head,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "没通：" + (error.message ?: "未知错误"),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                },
            )
        }

        SettingsGroup(title = "保存") {
            SettingsItem(
                title = "保存",
                icon = R.drawable.ic_edit,
                topDivider = false,
                onClick = {
                    SearchStore.upsert(context, draft)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
            )
            SettingsItem(
                title = "删除这个供应商",
                icon = R.drawable.ic_delete,
                onClick = {
                    SearchStore.remove(context, draft.id)
                    onBack()
                },
            )
        }
    }
}
