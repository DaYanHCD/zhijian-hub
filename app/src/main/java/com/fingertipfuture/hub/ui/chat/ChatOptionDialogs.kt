package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN

/** 思考深度七档（与 RikkaHub 的 ReasoningLevel 对齐）。 */
internal data class ThinkingDepth(val value: Int, val name: String)

internal val THINKING_DEPTHS = listOf(
    ThinkingDepth(0, "关闭"),
    ThinkingDepth(1, "自动"),
    ThinkingDepth(2, "低"),
    ThinkingDepth(3, "中等"),
    ThinkingDepth(4, "高"),
    ThinkingDepth(5, "超高"),
    ThinkingDepth(6, "最大"),
)

/**
 * 「调整模型思考深度」——底部弹层 + 滑杆（用户参考图定的形）。
 *
 * 两条重要的行为（用户 2026-10-14 明确要求）：
 *  1. **滑动只是调档位，不要一滑就把弹层关掉、跳回聊天页**；
 *  2. **不要在聊天里插一条「思考深度已切换」的假消息**（以前那样很讨厌）。
 * 所以这里：onValueChange 直接 onPick(value) 存下来，弹层留着；关不掉除非点遮罩/按返回。
 */
@Composable
internal fun ThinkingDepthDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(current.coerceIn(0, THINKING_DEPTHS.lastIndex)) }

    HubBottomSheet(
        onDismiss = onDismiss,
        minHeight = 380.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "调整模型思考深度", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "并非所有模型都支持深度调整。请参考模型和提供商文档。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            MaterialSymbol(
                resId = R.drawable.ic_bulb,
                contentDescription = null,
                size = 34.dp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = THINKING_DEPTHS[index].name, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            Slider(
                value = index.toFloat(),
                onValueChange = { raw ->
                    val picked = raw.toInt().coerceIn(0, THINKING_DEPTHS.lastIndex)
                    if (picked != index) {
                        index = picked
                        onPick(picked)   // 存下就走，不关弹层、不插聊天消息
                    }
                },
                valueRange = 0f..THINKING_DEPTHS.lastIndex.toFloat(),
                steps = (THINKING_DEPTHS.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                THINKING_DEPTHS.forEach { depth ->
                    Text(
                        text = depth.name,
                        fontSize = 9.5.sp,
                        color = if (depth.value == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "完成",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 技能（skills）选择：沿用设置页统一的开关样式。 */
@Composable
internal fun SkillsDialog(
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "技能插件",
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "选中后会把这段提示词加在系统提示词后面发给模型。纯本地，不上传任何东西。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (SkillStore.all.isEmpty()) {
                    Text(
                        text = "还没有可用的技能插件",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
                SkillStore.all.forEach { skill ->
                    SkillRow(
                        title = skill.name,
                        description = skill.description,
                        enabled = skill.id == currentId,
                        onToggle = {
                            onPick(if (skill.id == currentId) "" else skill.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    title: String,
    description: String?,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp)
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.graphicsLayer(scaleX = 0.92f, scaleY = 0.92f),
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
