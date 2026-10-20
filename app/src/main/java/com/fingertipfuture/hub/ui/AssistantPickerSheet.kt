package com.fingertipfuture.hub.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.settings.AssistantAvatar
import com.fingertipfuture.hub.ui.settings.SettingsTag

/**
 * 「选助手」弹层（2026-10-16 用户要求）：点副屏底部那颗**助手**图标就是选助手。
 *
 * 选项就是「助手管理」里建好、存下来的那些助手；选中之后 App 把它记成**当前助手**，
 * 之后所有对话都用它（模型、温度、最大 token、提示词、技能、权限 —— 见 AssistantStore）。\n */
@Composable
internal fun AssistantPickerSheet(
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    // 「正在开发」的内置助手只放在助手管理里，不在副屏的选择弹窗出现。
    val items = AssistantStore.items.filterNot { it.underDevelopment }
    val currentId = AssistantStore.current.id

    HubBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
        ) {
            Text(text = "选择助手", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { assistant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AssistantStore.setCurrent(context, assistant.id)
                                onPicked(assistant.name)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistantAvatar(assistant = assistant, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = assistant.name,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (assistant.systemPrompt.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = assistant.systemPrompt.replace('\n', ' '),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (assistant.id == currentId) {
                            Spacer(Modifier.width(8.dp))
                            SettingsTag("当前")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
