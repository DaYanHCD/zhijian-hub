package com.fingertipfuture.hub.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AppLanguage
import com.fingertipfuture.hub.data.LanguageStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.theme.HubThemeMode
import com.fingertipfuture.hub.ui.theme.ThemeStore

/** 偏好设置：主题（下拉）/ 通知 / 常规 / 界面。 */
@Composable
internal fun PreferenceScreen(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenInterface: () -> Unit,
) {
    val context = LocalContext.current
    val modes = HubThemeMode.values()
    SettingsPage(title = "偏好设置", onBack = onBack) {
        SettingsGroup(title = "偏好设置") {
            SettingsChoiceRow(
                title = "主题",
                value = ThemeStore.mode.label,
                options = modes.map { it.label },
                icon = R.drawable.ic_brightness_6,
                topDivider = false,
                onPick = { index -> ThemeStore.set(context, modes[index]) },
            )
            SettingsChoiceRow(
                title = "语言",
                value = LanguageStore.current.label,
                options = AppLanguage.entries.map { it.label },
                icon = R.drawable.ic_translate,
                onPick = { index -> LanguageStore.select(context, AppLanguage.entries[index]) },
            )
            SettingsItem(
                title = "常规",
                subtitle = "启动行为与自动滚动",
                icon = R.drawable.ic_list,
                chevron = true,
                onClick = onOpenGeneral,
            )
            SettingsItem(
                title = "界面",
                subtitle = "头像、思考过程与输出方式",
                icon = R.drawable.ic_mood,
                chevron = true,
                onClick = onOpenInterface,
            )
        }
    }
}

/** 偏好设置 › 常规：两个开关。 */
@Composable
internal fun GeneralBehaviorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = "常规", onBack = onBack) {
        SettingsGroup(title = "常规") {
            SettingsSwitchRow(
                title = "启动时新建对话",
                subtitle = "每次打开 App 都从新对话开始",
                checked = UiPrefs.newChatOnStart,
                topDivider = false,
                onCheckedChange = { UiPrefs.setNewChatOnStart(context, it) },
            )
            SettingsSwitchRow(
                title = "自动滚动",
                subtitle = "生成时自动滚到底部",
                checked = UiPrefs.autoScroll,
                onCheckedChange = { UiPrefs.setAutoScroll(context, it) },
            )
            SettingsSwitchRow(
                title = "消息区逻辑",
                subtitle = "键盘弹起时，点消息区自动收起键盘",
                checked = UiPrefs.tapMessageClosesKeyboard,
                onCheckedChange = { UiPrefs.setTapMessageClosesKeyboard(context, it) },
            )
            SettingsSwitchRow(
                title = "显示描边",
                subtitle = "为标题栏、输入胶囊、卡片、弹层和对话框添加描边",
                checked = UiPrefs.cardBorders,
                onCheckedChange = { UiPrefs.setCardBorders(context, it) },
            )
        }
    }
}

/** 偏好设置 › 界面：头像、思考框和输出方式。 */
@Composable
internal fun InterfaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = "界面", onBack = onBack) {
        SettingsGroup(title = "聊天界面") {
            SettingsSwitchRow(
                title = "显示用户头像",
                subtitle = "聊天时显示我的头像",
                checked = UiPrefs.showUserAvatar,
                topDivider = false,
                onCheckedChange = { UiPrefs.setShowUserAvatar(context, it) },
            )
            SettingsSwitchRow(
                title = "隐藏思考",
                subtitle = "不显示思考框、思考时长和思考内容",
                checked = UiPrefs.hideThinking,
                onCheckedChange = { UiPrefs.setHideThinking(context, it) },
            )
            if (!UiPrefs.hideThinking) {
                SettingsSwitchRow(
                    title = "展示思考内容",
                    subtitle = "默认展开助手的思考过程",
                    checked = UiPrefs.showThinking,
                    onCheckedChange = { UiPrefs.setShowThinking(context, it) },
                )
                SettingsSwitchRow(
                    title = "自动折叠思考",
                    subtitle = "思考结束后自动收起",
                    checked = UiPrefs.autoCollapseThinking,
                    onCheckedChange = { UiPrefs.setAutoCollapseThinking(context, it) },
                )
            }
            SettingsSwitchRow(
                title = "流式输出",
                subtitle = "回答逐字出现，而不是一次性显示",
                checked = UiPrefs.streamingOutput,
                onCheckedChange = { UiPrefs.setStreamingOutput(context, it) },
            )
            SettingsSwitchRow(
                title = "使用助手头像",
                subtitle = "聊天时用助手头像（未设置时用模型标志）",
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

