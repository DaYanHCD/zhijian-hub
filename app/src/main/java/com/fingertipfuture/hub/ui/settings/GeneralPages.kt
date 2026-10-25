package com.fingertipfuture.hub.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AppLanguage
import com.fingertipfuture.hub.data.LanguageStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.theme.HubThemeMode
import com.fingertipfuture.hub.ui.theme.ThemeStore
import com.fingertipfuture.hub.ui.theme.switchThemeWithReveal
import com.fingertipfuture.hub.ui.component.LocalizedText as Text

/** 偏好设置：外观、常规和界面的全部选项集中在这一页。 */
@Composable
internal fun PreferenceScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val modes = HubThemeMode.values()
    var themeCapsuleBounds by remember { mutableStateOf(Rect.Zero) }
    SettingsPage(title = "偏好设置", onBack = onBack) {
        SettingsGroup(title = "应用外观") {
            SettingsSlidingChoiceRow(
                title = "主题",
                value = ThemeStore.mode.label,
                options = modes.map { it.label },
                icon = R.drawable.ic_brightness_6,
                topDivider = false,
                onCapsuleBoundsChanged = { themeCapsuleBounds = it },
                onPick = { index ->
                    switchThemeWithReveal(context, modes[index], themeCapsuleBounds)
                },
            )
            SettingsSlidingChoiceRow(
                title = "语言",
                value = LanguageStore.current.label,
                options = AppLanguage.entries.map { it.label },
                icon = R.drawable.ic_translate,
                onPick = { index -> LanguageStore.select(context, AppLanguage.entries[index]) },
            )
            SettingsSwitchRow(
                title = "描边效果",
                subtitle = "为所有界面添加描边效果",
                checked = UiPrefs.cardBorders,
                onCheckedChange = { UiPrefs.setCardBorders(context, it) },
            )
        }
        SettingsGroup(title = "常规逻辑") {
            SettingsSwitchRow(
                title = "启动时新建对话",
                subtitle = "每次打开 App 都从新对话开始",
                checked = UiPrefs.newChatOnStart,
                topDivider = false,
                onCheckedChange = { UiPrefs.setNewChatOnStart(context, it) },
            )
            SettingsSwitchRow(
                title = "自动滚动",
                subtitle = "收到回复时自动滚动到底部",
                checked = UiPrefs.autoScroll,
                onCheckedChange = { UiPrefs.setAutoScroll(context, it) },
            )
            SettingsSwitchRow(
                title = "收起键盘",
                subtitle = "键盘弹起时，点消息区自动收起键盘",
                checked = UiPrefs.tapMessageClosesKeyboard,
                onCheckedChange = { UiPrefs.setTapMessageClosesKeyboard(context, it) },
            )
            SettingsSwitchRow(
                title = "隐藏思考",
                subtitle = "不显示思考框、思考时长和思考内容",
                checked = UiPrefs.hideThinking,
                onCheckedChange = { UiPrefs.setHideThinking(context, it) },
            )
            SettingsSwitchRow(
                title = "展示思考内容",
                subtitle = "默认展开助手的思考过程",
                checked = UiPrefs.showThinking,
                enabled = !UiPrefs.hideThinking,
                onCheckedChange = { UiPrefs.setShowThinking(context, it) },
            )
            SettingsSwitchRow(
                title = "自动折叠思考",
                subtitle = "思考结束后自动收起思考框",
                checked = UiPrefs.autoCollapseThinking,
                enabled = !UiPrefs.hideThinking,
                onCheckedChange = { UiPrefs.setAutoCollapseThinking(context, it) },
            )
            SettingsSwitchRow(
                title = "流式输出",
                subtitle = "回答逐字出现，而不是一次性显示",
                checked = UiPrefs.streamingOutput,
                onCheckedChange = { UiPrefs.setStreamingOutput(context, it) },
            )
        }
        SettingsGroup(title = "聊天界面") {
            SettingsSwitchRow(
                title = "显示用户头像",
                subtitle = "聊天时显示我的头像",
                checked = UiPrefs.showUserAvatar,
                topDivider = false,
                onCheckedChange = { UiPrefs.setShowUserAvatar(context, it) },
            )
            SettingsSwitchRow(
                title = "使用助手头像",
                subtitle = "聊天时使用助手头像（未设置时使用模型标志）",
                checked = UiPrefs.useAssistantAvatar,
                onCheckedChange = { UiPrefs.setUseAssistantAvatar(context, it) },
            )
            SettingsSwitchRow(
                title = "隐藏表情按钮",
                subtitle = "隐藏输入胶囊的表情图标，输入框会自动变宽",
                checked = UiPrefs.hideEmojiButton,
                onCheckedChange = { UiPrefs.setHideEmojiButton(context, it) },
            )
        }
    }
}
