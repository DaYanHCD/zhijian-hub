package com.fingertipfuture.hub.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.SearchApi
import com.fingertipfuture.hub.data.SearchProvider
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.ui.theme.enforceHubTypography
import com.fingertipfuture.hub.ui.provider.FloatingCircleAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** 搜索设置：结果条数 + 供应商列表 + 并行搜索 + 右上角添加按钮。 */
@Composable
internal fun SearchSettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var maxResultsText by remember { mutableStateOf(SearchStore.maxResults.toString()) }
    val items = SearchStore.items

    SettingsPage(
        title = "搜索设置",
        onBack = onBack,
        topAction = {
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "添加搜索供应商",
                onClick = { showAdd = true },
            )
        },
    ) {
        SettingsGroup(title = null) {
            SettingsItem(
                title = "结果条数",
                subtitle = "每次搜索返回的条数",
                icon = R.drawable.ic_list,
                topDivider = false,
                trailing = {
                    OutlinedTextField(
                        value = maxResultsText,
                        onValueChange = { raw ->
                            val digits = raw.filter(Char::isDigit).take(2)
                            if (digits.isEmpty()) {
                                maxResultsText = ""
                            } else {
                                val value = digits.toInt().coerceIn(1, 50)
                                maxResultsText = value.toString()
                                if (value != SearchStore.maxResults) {
                                    SearchStore.setMaxResults(context, value)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        ).enforceHubTypography(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(82.dp),
                    )
                },
            )
        }
        SettingsGroup(title = "搜索供应商（" + items.size + "）") {
            items.forEachIndexed { index, provider ->
                SettingsItem(
                    title = provider.name,
                    subtitle = if (provider.deep) "全面模式" else "普通模式",
                    icon = kindIcon(provider.kind),
                    iconTint = if (kindColored(provider.kind)) Color.Unspecified else null,
                    topDivider = index != 0,
                    onClick = { onOpenProvider(provider.id) },
                    trailing = {
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { enabled ->
                                SearchStore.upsert(context, provider.copy(enabled = enabled))
                            },
                        )
                    },
                )
            }
        }
        SettingsGroup(title = "并行搜索") {
            items.forEachIndexed { index, provider ->
                SettingsSwitchRow(
                    title = provider.name,
                    checked = provider.inParallel,
                    enabled = provider.enabled,
                    topDivider = index != 0,
                    onCheckedChange = { SearchStore.upsert(context, provider.copy(inParallel = it)) },
                )
            }
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
}

/** 一个搜索供应商的配置页：API Key / 模式 / 测试搜索 / 保存。 */
@Composable
internal fun SearchProviderConfigScreen(
    providerId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = SearchStore.byId(providerId)
    if (original == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var draft by remember(providerId) { mutableStateOf(original) }
    var testing by remember { mutableStateOf(false) }
    var apiKeyVisible by remember(providerId) { mutableStateOf(false) }
    var testQuery by remember(providerId) { mutableStateOf("") }
    var testResults by remember(providerId) { mutableStateOf<List<SearchApi.Hit>>(emptyList()) }
    val modeLabels = listOf("普通模式", "全面模式")

    LaunchedEffect(draft) {
        if (draft != original) {
            delay(350)
            SearchStore.upsert(context, draft)
        }
    }

    fun runTest() {
        val query = testQuery.trim()
        if (query.isEmpty()) {
            context.showLocalizedToast("先输入测试关键词")
            return
        }
        if (testing) return
        testing = true
        testResults = emptyList()
        scope.launch {
            val result = SearchApi.search(draft, query, SearchStore.maxResults)
            testing = false
            result.onSuccess { hits ->
                testResults = hits
            }.onFailure { error ->
                context.showLocalizedToast(
                    "搜索失败：" + (error.message ?: "未知错误"),
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    SettingsPage(
        title = original.name,
        scrollKey = "search-provider|$providerId",
        onBack = {
            SearchStore.upsert(context, draft)
            onBack()
        },
        topAction = if (draft.kind == SearchProvider.KIND_BING) {
            null
        } else {
            {
                FloatingCircleAction(
                    icon = R.drawable.ic_delete,
                    contentDescription = "删除搜索供应商",
                    onClick = {
                        SearchStore.remove(context, draft.id)
                        onBack()
                    },
                )
            }
        },
    ) {
        if (draft.kind != SearchProvider.KIND_BING) {
            SettingsGroup(title = "API 密钥") {
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("API 密钥") },
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        SettingsGroup(title = "搜索模式") {
            SettingsSlidingChoiceRow(
                title = "模式",
                value = modeLabels[if (draft.deep) 1 else 0],
                options = modeLabels,
                topDivider = false,
                onPick = { index -> draft = draft.copy(deep = index == 1) },
            )
        }

        SearchTestCard(
            query = testQuery,
            testing = testing,
            onQueryChange = { testQuery = it },
            onRun = ::runTest,
        )
        if (testResults.isNotEmpty()) {
            SearchTestResultLinks(results = testResults)
        }
    }
}

/** 按参考图重做的测试搜索卡片：标题、可编辑关键词、右侧播放按钮。 */
@Composable
private fun SearchTestCard(
    query: String,
    testing: Boolean,
    onQueryChange: (String) -> Unit,
    onRun: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val canRun = query.isNotBlank() && !testing

    androidx.compose.material3.Surface(
        color = settingsCardColor(),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .hubCardBorder(shape),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "测试搜索",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("输入测试查询…") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canRun, onClick = onRun),
                    contentAlignment = Alignment.Center,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        MaterialSymbol(
                            resId = R.drawable.ic_play,
                            contentDescription = "开始测试搜索",
                            size = 28.dp,
                            tint = if (canRun) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 测试搜索完成后，在卡片下方列出结果标题与可点击链接。 */
@Composable
private fun SearchTestResultLinks(results: List<SearchApi.Hit>) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(20.dp)

    androidx.compose.material3.Surface(
        color = settingsCardColor(),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .hubCardBorder(shape),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "测试结果",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            results.forEachIndexed { index, hit ->
                if (index != 0) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = hit.title.ifBlank { hit.url },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hit.url,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val scheme = runCatching {
                            android.net.Uri.parse(hit.url).scheme?.lowercase()
                        }.getOrNull()
                        if (scheme != "http" && scheme != "https") {
                            context.showLocalizedToast("搜索结果链接不受支持")
                        } else {
                            runCatching { uriHandler.openUri(hit.url) }
                                .onFailure {
                                    context.showLocalizedToast("打不开这个搜索结果链接")
                                }
                        }
                    },
                )
            }
        }
    }
}
