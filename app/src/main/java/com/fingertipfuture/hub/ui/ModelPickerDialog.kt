package com.fingertipfuture.hub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.brandIcon

/**
 * 「当前对话用哪个模型」——底部弹层（用户 2026-10-14 给的参考图定的形）。
 *
 * 拖动条 → 搜索框 → 按供应商分组 → 每个模型一张卡片（Logo + 名字 + 能力标签，当前那张高亮）。
 *
 * 说明：参考图上还有「上下文长度 / 原生工具 / 余额」这些标签，我们**没有模型元数据来源**
 * （不联自有后端、不扒接口），编几个数字上去就是骗人，所以只放能确定的：
 * 「聊天」永远有，「思考」按模型名判断（reasoner / r1 / thinking / qwq 这类）。
 *
 * 弹层是自己画的（HubBottomSheet），不是 M3 那个另开窗口的 ModalBottomSheet ——
 * 后者在边到边的时候底下会露出一条缝，能看见聊天界面（用户反馈过）。
 */
@Composable
internal fun ModelPickerDialog(
    onDismiss: () -> Unit,
    /**
     * 选完交给谁：传了就回调给调用方（比如"给这个助手设默认模型"），**不改全局**选中项；
     * 不传就是老行为 —— 直接切全局的当前模型。
     */
    onPick: ((providerId: String, model: String) -> Unit)? = null,
    /** 高亮用：不传就看全局当前选中项。 */
    highlight: Pair<String, String>? = null,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val providers = ProviderStore.providers.filter { it.enabled }

    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("输入模型名称搜索", fontSize = 15.sp) },
                leadingIcon = {
                    MaterialSymbol(
                        resId = R.drawable.ic_search,
                        contentDescription = null,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                var shown = 0
                providers.forEach { provider ->
                    val models = provider.models.filter {
                        query.isBlank() || it.contains(query, ignoreCase = true)
                    }
                    if (models.isEmpty()) return@forEach
                    shown += models.size
                    Text(
                        text = provider.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                    )
                    models.forEach { model ->
                        val hlProvider = highlight?.first ?: ProviderStore.currentProviderId
                        val selected = provider.id == hlProvider && model == (highlight?.second ?: ProviderStore.currentModel)
                        ModelCard(
                            provider = provider,
                            model = model,
                            selected = selected,
                            onClick = {
                                if (onPick != null) onPick(provider.id, model) else ProviderStore.select(context, provider.id, model)
                                onDismiss()
                            },
                        )
                    }
                }
                if (shown == 0) {
                    Text(
                        text = if (providers.isEmpty()) {
                            "还没有启用的供应商 —— 先去「提供商」里开一家"
                        } else {
                            "没有匹配的模型"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 一个模型一张卡片：Logo + 名字 + 能确定的标签（当前用的那张底色高亮）。 */
@Composable
private fun ModelCard(
    provider: HubProvider,
    model: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .hubCardBorder(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelLogo(provider)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapabilityBadge(text = "聊天", color = Color(0xFF2F6BE4))
                    if (looksLikeThinkingModel(model)) {
                        CapabilityBadge(text = "思考", color = Color(0xFF13806B), icon = R.drawable.ic_psychology)
                    }
                }
            }
            if (selected) {
                Text(
                    text = "当前",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelLogo(provider: HubProvider) {
    val icon = brandIcon(provider.logo)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                MaterialSymbol(
                    resId = icon.res,
                    contentDescription = null,
                    size = 26.dp,
                    tint = if (icon.colored) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = provider.name.trim().take(1).ifBlank { "?" },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(text: String, color: Color, icon: Int? = null) {
    Surface(color = color, shape = RoundedCornerShape(7.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                MaterialSymbol(resId = icon, contentDescription = null, size = 12.dp, tint = Color.White)
                Spacer(Modifier.width(3.dp))
            }
            Text(text = text, color = Color.White, fontSize = 11.sp)
        }
    }
}

/** 名字里带这些词的多半是"会思考"的模型（只是我们自己的经验判断，不是官方元数据）。 */
private fun looksLikeThinkingModel(model: String): Boolean {
    val name = model.lowercase()
    return listOf("reasoner", "reasoning", "thinking", "qwq", "-r1", "r1-", "o1-", "o3-", "o4-")
        .any { name.contains(it) }
}
