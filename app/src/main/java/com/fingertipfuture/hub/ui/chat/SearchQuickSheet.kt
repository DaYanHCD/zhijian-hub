package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.SearchProvider
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN

/** 与设置页的搜索供应商图标一致（官方彩色标志）。 */
private fun searchIcon(kind: String): Int = when (kind) {
    SearchProvider.KIND_BING -> R.drawable.ic_search_bing
    SearchProvider.KIND_DEEPSEEK -> R.drawable.ic_brand_deepseek
    SearchProvider.KIND_DOUBAO -> R.drawable.ic_search_doubao
    SearchProvider.KIND_TAVILY -> R.drawable.ic_search_tavily
    SearchProvider.KIND_FIRECRAWL -> R.drawable.ic_search_firecrawl
    else -> R.drawable.ic_search
}

private fun searchColored(kind: String): Boolean = kind in setOf(
    SearchProvider.KIND_BING,
    SearchProvider.KIND_DEEPSEEK,
    SearchProvider.KIND_DOUBAO,
    SearchProvider.KIND_TAVILY,
    SearchProvider.KIND_FIRECRAWL,
)

/**
 * 添加栏「搜索设置」弹层（2026-10-17 用户要求）：
 * 和「切换模型 / 思考深度 / 插件选择」一样从下面滑出来，
 * 自动联网搜索固定开启（2026-10-19 用户要求删掉总开关），这里只逐家开关供应商，
 * 最后提供一个进入完整搜索设置页的入口。
 */
@Composable
internal fun SearchQuickSheet(
    onDismiss: () -> Unit,
    onOpenSearchSettings: () -> Unit,
) {
    val context = LocalContext.current
    val items = SearchStore.items

    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
        ) {
            Text(
                text = "搜索设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "自动搜索只在需要实时信息时触发；普通闲聊和翻译不会搜索",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(4.dp))

            if (items.isEmpty()) {
                Text(
                    text = "还没有搜索供应商。\n先到「设置 › 模型与服务 › 搜索设置」添加一个。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items.forEach { provider ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol(
                        resId = searchIcon(provider.kind),
                        contentDescription = null,
                        size = 26.dp,
                        tint = if (searchColored(provider.kind)) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (provider.deep) "全面模式" else "普通模式",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = { SearchStore.upsert(context, provider.copy(enabled = it)) },
                        modifier = Modifier.graphicsLayer(scaleX = 0.92f, scaleY = 0.92f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenSearchSettings()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "前往搜索设置",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "管理供应商、并行搜索与搜索模式",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = -90f),
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
