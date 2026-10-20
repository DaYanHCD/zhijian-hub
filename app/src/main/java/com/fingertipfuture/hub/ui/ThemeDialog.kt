package com.fingertipfuture.hub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.theme.HubThemeMode

/**
 * 「主题」选择：浅色模式 / 深色模式 / 跟随系统。
 *
 * 形和「技能插件」那种底部弹层一致（单选项 + 一行说明），选完立刻生效并关掉。
 */
@Composable
internal fun ThemeDialog(
    current: HubThemeMode,
    onPick: (HubThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
        ) {
            Text(text = "主题", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "「跟随系统」跟着手机的深色设置走；选了浅色或深色就一直用它。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            HubThemeMode.values().forEach { mode ->
                ThemeRow(
                    title = mode.label,
                    description = when (mode) {
                        HubThemeMode.SYSTEM -> "跟着手机的深色设置走（默认）"
                        HubThemeMode.LIGHT -> "一直用亮色，不管系统怎么设"
                        HubThemeMode.DARK -> "一直用暗色，不管系统怎么设"
                    },
                    selected = mode == current,
                    onClick = { onPick(mode); onDismiss() },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
