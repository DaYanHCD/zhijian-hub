package com.fingertipfuture.hub.ui.provider

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.AnimatedSegmentedTabs
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubOuterShadow
import com.fingertipfuture.hub.ui.component.settingsCardColor
import kotlinx.coroutines.launch

private enum class ProviderEditSection { CONFIG, MODELS }

/**
 * 「供应商配置」整页（不是弹窗）。
 *
 * 2026-10-13 用户要求合并 + 精简之后只剩这些：
 * 配置分区：名称 / API Key / 接口地址 / 接口路径 / 启用开关；
 * 模型分区：自动检测、已添加模型、手动添加、未添加的检测结果；底部统一「保存」。
 *
 * 删掉的：三个 Tab、Response API、回传历史思考过程、获取账户余额、清除 Key、删除、
 * 底部那排的"预设 / 刷新"（删除挪到列表卡片的 ⋮ 里，启用放在配置分区）。
 * **这里不能再改"当前对话用哪个模型"** —— 那个入口在聊天页顶栏「更多 → 供应商与模型」。
 *
 * 保存是手动的（照参考图），编辑期间改的是草稿 `draft`。
 */
@Composable
internal fun ProviderConfigScreen(
    providerId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val stored = ProviderStore.providers.firstOrNull { it.id == providerId }
    if (stored == null) {
        // 被删掉了（比如在列表里删的）：直接退回去，别停在空白页
        onBack()
        return
    }

    var draft by remember(providerId) { mutableStateOf(stored) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var balanceBusy by remember { mutableStateOf(false) }
    var balanceText by remember { mutableStateOf("") }
    var section by remember(providerId) { mutableStateOf(ProviderEditSection.CONFIG) }
    var modelInput by remember(providerId) { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(section) {
        scrollState.scrollTo(0)
    }

    fun save(showToast: Boolean = true) {
        val models = draft.models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        draft = draft.copy(models = models)
        val saved = draft.copy(
            name = draft.name.trim().ifBlank { "未命名" },
            baseUrl = draft.baseUrl.trim().trimEnd('/'),
            apiKey = draft.apiKey.trim(),
            apiPath = draft.apiPath.trim(),
        )
        if (!ProviderStore.upsert(context, saved)) {
            context.showLocalizedToast(
                "保存失败：系统安全存储不可用或本机存储异常",
                Toast.LENGTH_LONG,
            )
            return
        }
        if (!saved.enabled && ProviderStore.currentProviderId == providerId) {
            ProviderStore.setEnabled(context, providerId, false)
        }
        if (saved.enabled && saved.apiKey.isBlank() && !isLocalUrl(saved.baseUrl)) {
            context.showLocalizedToast(R.string.provider_enable_need_key)
        }
        // 刚接入完却还没有可用的当前模型时，顺手选上这家；新手引导卡才能真实退场。
        if (models.isNotEmpty() && ProviderStore.currentModel.isBlank()) {
            ProviderStore.select(context, providerId, models.first())
        }
        if (showToast) context.showLocalizedToast(R.string.provider_saved)
    }

    /** 测试连接：拿一次模型清单，能拿到就算通（2026-10-16）。 */
    fun testConnection() {
        if (draft.baseUrl.isBlank()) {
            context.showLocalizedToast(R.string.provider_detect_need_url)
            return
        }
        testing = true
        scope.launch {
            val result = AiClient.listModels(draft)
            testing = false
            result.onSuccess { found ->
                context.showLocalizedToast(
                    "连接成功：拿到 " + found.size + " 个模型",
                    Toast.LENGTH_LONG,
                )
            }.onFailure { error ->
                context.showLocalizedToast(
                    "连接失败：" + (error.message ?: "未知错误"),
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    /** 查询余额：只对有几家有公开接口的生效，别家如实说没有（2026-10-16）。 */
    fun queryBalance() {
        balanceBusy = true
        balanceText = ""
        scope.launch {
            val result = AiClient.fetchBalance(draft)
            balanceBusy = false
            balanceText = result.getOrElse { it.message ?: "查询失败" }
        }
    }

    /** 拉一次 `{baseUrl}/models`，把这家现在能用的模型全拿回来。 */
    fun detectModels() {
        if (draft.baseUrl.isBlank()) {
            context.showLocalizedToast(R.string.provider_detect_need_url)
            return
        }
        detecting = true
        scope.launch {
            val result = AiClient.listModels(draft)
            detecting = false
            result.onSuccess { found ->
                if (found.isEmpty()) {
                    context.showLocalizedToast(
                        R.string.provider_detect_empty,
                        Toast.LENGTH_LONG,
                    )
                } else {
                    draft = draft.copy(
                        availableModels = (draft.availableModels + found).distinct(),
                        models = (draft.models + found).distinct(),
                    )
                    save(showToast = false)
                    context.showLocalizedToast(
                        context.getString(R.string.provider_detect_ok, found.size),
                        Toast.LENGTH_LONG,
                    )
                }
            }.onFailure {
                context.showLocalizedToast(
                    R.string.provider_detect_failed,
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    // 新建后没保存就退出：把那条空壳删掉，别在列表里留下「未命名」
    // （2026-10-17：之前点「＋」会立刻写一条空供应商，退出来就攒成好几个「未命名」）
    fun leave() {
        val live = ProviderStore.providers.firstOrNull { it.id == providerId }
        val untouched = live != null &&
            live.name.isBlank() &&
            live.baseUrl.isBlank() &&
            live.apiKey.isBlank() &&
            live.models.isEmpty() &&
            live.availableModels.isEmpty()
        if (untouched) ProviderStore.remove(context, providerId)
        onBack()
    }

    BackHandler(onBack = ::leave)

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 边到边之后窗口不再被键盘顶上去，这里得自己让开软键盘
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    // 和统计页 / 设置页一样：状态栏 padding 放进滚动内容里，
                    // 滚动时输入框可以从透明状态栏下面穿过，再由 TopFadeLayer 渐隐。
                    .statusBarsPadding()
                    .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
            ) {
                // 让开上面那颗悬浮的「返回 + 名字」胶囊
                Spacer(Modifier.height(FLOATING_BAR_SPACE))
            ProviderEditSectionTabs(
                selected = section,
                onSelect = { section = it },
            )
            Spacer(Modifier.height(20.dp))
            if (section == ProviderEditSection.CONFIG) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.provider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { draft = draft.copy(apiKey = it) },
                label = { Text(stringResource(R.string.provider_api_key)) },
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
                        contentDescription = stringResource(R.string.provider_api_key),
                        size = 22.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { apiKeyVisible = !apiKeyVisible }
                            .padding(6.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it) },
                label = { Text(stringResource(R.string.provider_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.apiPath,
                onValueChange = { draft = draft.copy(apiPath = it) },
                label = { Text(stringResource(R.string.provider_api_path)) },
                placeholder = { Text(stringResource(R.string.provider_api_path_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            Surface(
                color = settingsCardColor(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .hubCardBorder(RoundedCornerShape(18.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用这个提供商", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "关闭后不会出现在聊天模型选择中",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (!testing) testConnection() },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (testing) "正在测试…" else "测试连接") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { if (!balanceBusy) queryBalance() },
                    enabled = !balanceBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (balanceBusy) "正在查询…" else "查询余额") }
            }
            if (balanceText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            } else {
            val unaddedModels = draft.availableModels.filterNot { it in draft.models }
            OutlinedButton(
                onClick = { if (!detecting) detectModels() },
                enabled = !detecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaterialSymbol(resId = R.drawable.ic_refresh, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (detecting) R.string.provider_detect_busy else R.string.provider_detect_models,
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "已添加的模型",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            if (draft.models.isEmpty()) {
                Text(
                    text = stringResource(R.string.provider_model_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            draft.models.forEach { model ->
                val itemShape = RoundedCornerShape(12.dp)
                Surface(
                    color = settingsCardColor(),
                    shape = itemShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .hubCardBorder(itemShape),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MaterialSymbol(
                            resId = R.drawable.ic_delete,
                            contentDescription = stringResource(R.string.provider_delete),
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { draft = draft.copy(models = draft.models - model) }
                                .padding(6.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("手动添加模型") },
                    placeholder = { Text("输入模型 ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        val model = modelInput.trim()
                        if (model.isNotEmpty()) {
                            draft = draft.copy(
                                models = (draft.models + model).distinct(),
                                availableModels = (draft.availableModels + model).distinct(),
                            )
                            modelInput = ""
                        }
                    },
                    enabled = modelInput.isNotBlank(),
                ) {
                    Text("添加")
                }
            }

            if (unaddedModels.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "检测到但尚未添加",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                unaddedModels.forEach { model ->
                    val itemShape = RoundedCornerShape(12.dp)
                    Surface(
                        color = settingsCardColor(),
                        shape = itemShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .hubCardBorder(itemShape),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            MaterialSymbol(
                                resId = R.drawable.ic_add,
                                contentDescription = "加入已添加模型",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        draft = draft.copy(models = (draft.models + model).distinct())
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
            }

            // 底部留出悬浮「保存」的高度，别让最后一行被盖住
            Spacer(Modifier.height(96.dp))
        }
    }

        // 保存键：真悬浮（内容从它下面滚过去），阴影与设置页卡片同一条
        val saveShape = RoundedCornerShape(26.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN, vertical = 12.dp),
        ) {
            Button(
                onClick = { save() },
                shape = saveShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .hubOuterShadow(saveShape),
            ) {
                Text(stringResource(R.string.provider_save), fontWeight = FontWeight.SemiBold)
            }
        }

        // 状态栏渐隐 + 悬浮的「返回 + 名字」胶囊（颜色 / 阴影跟聊天页那两颗胶囊一致）
        TopFadeLayer(modifier = Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = PAGE_HORIZONTAL_MARGIN,
                    top = statusBarTopPadding() + FLOATING_BAR_TOP_MARGIN,
                ),
        ) {
            FloatingBackBar(
                title = draft.name.ifBlank { stringResource(R.string.provider_untitled) },
                onBack = ::leave,
            )
        }
    }
    }
}

@Composable
private fun ProviderEditSectionTabs(
    selected: ProviderEditSection,
    onSelect: (ProviderEditSection) -> Unit,
) {
    AnimatedSegmentedTabs(
        labels = listOf("配置", "模型"),
        selectedIndex = ProviderEditSection.entries.indexOf(selected),
        onSelect = { index -> onSelect(ProviderEditSection.entries[index]) },
    )
}

