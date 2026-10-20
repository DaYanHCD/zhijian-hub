package com.fingertipfuture.hub.ui

import androidx.compose.runtime.Composable
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.settings.SettingsGroup
import com.fingertipfuture.hub.ui.settings.SettingsItem
import com.fingertipfuture.hub.ui.settings.SettingsPage

/**
 * 设置首页 —— 照用户给的参考图（2026-10-16）：
 * 大标题「设置」+ 蓝色分组小标题 + 一组的卡片（行之间细分割线），分组是：
 *
 * ```
 * 通用设置    偏好设置 · 助手管理 · 技能管理
 * 模型与服务  默认模型设置 · 提供商 · 搜索设置 · 语音服务 · 视频扩展 · 权限与隐私
 * 数据        数据备份 · 数据管理
 * 关于        关于 · 致谢 · 说明书 · 赞赏 · 联系作者
 * ```
 *
 * 页面上不放任何"说明性"文字（用户明确要求删掉），只有标题 / 副标题 / 当前值。
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPreference: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSkill: () -> Unit,
    onOpenModelDefaults: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenThanks: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenContact: () -> Unit,
) {
    SettingsPage(title = "设置", onBack = onBack) {
        SettingsGroup(title = "通用设置") {
            SettingsItem(
                title = "偏好设置",
                subtitle = "主题、通知与界面选项",
                icon = R.drawable.ic_brightness_6,
                chevron = true,
                topDivider = false,
                onClick = onOpenPreference,
            )
            SettingsItem(
                title = "助手管理",
                subtitle = "默认助手、虚拟网友与管家",
                icon = R.drawable.smart_toy,
                chevron = true,
                onClick = onOpenAssistant,
            )
            SettingsItem(
                title = "技能管理",
                subtitle = "选择、导入或创建技能",
                icon = R.drawable.ic_bulb,
                chevron = true,
                onClick = onOpenSkill,
            )
        }

        SettingsGroup(title = "模型与服务") {
            SettingsItem(
                title = "默认模型设置",
                subtitle = "聊天模型、思考强度等",
                icon = R.drawable.ic_psychology,
                chevron = true,
                topDivider = false,
                onClick = onOpenModelDefaults,
            )
            SettingsItem(
                title = "提供商",
                subtitle = "配置 AI 服务提供商",
                icon = R.drawable.ic_provider,
                chevron = true,
                onClick = onOpenProviders,
            )
            SettingsItem(
                title = "搜索设置",
                subtitle = "接入第三方搜索 API",
                icon = R.drawable.ic_search,
                chevron = true,
                onClick = onOpenSearch,
            )
            SettingsItem(
                title = "权限与隐私",
                subtitle = "助手可用能力与系统授权",
                icon = R.drawable.ic_settings_location,
                chevron = true,
                onClick = onOpenPrivacy,
            )
        }

        SettingsGroup(title = "数据") {
            SettingsItem(
                title = "数据备份",
                subtitle = "导出、导入与 WebDAV 备份",
                icon = R.drawable.ic_settings_download,
                chevron = true,
                topDivider = false,
                onClick = onOpenBackup,
            )
            SettingsItem(
                title = "数据管理",
                subtitle = "对话、附件与本地占用",
                icon = R.drawable.ic_settings_file,
                chevron = true,
                onClick = onOpenStorage,
            )
        }

        SettingsGroup(title = "关于") {
            SettingsItem(
                title = "关于",
                subtitle = "版本与安装包信息",
                icon = R.drawable.ic_info,
                chevron = true,
                topDivider = false,
                onClick = onOpenAbout,
            )
            SettingsItem(
                title = "致谢",
                subtitle = "灵感来源与开源资源",
                icon = R.drawable.ic_tab_heart,
                chevron = true,
                onClick = onOpenThanks,
            )
            SettingsItem(
                title = "说明书",
                subtitle = "功能说明与上手步骤",
                icon = R.drawable.ic_list,
                chevron = true,
                onClick = onOpenManual,
            )
            SettingsItem(
                title = "赞赏作者",
                subtitle = "请开发者喝杯咖啡",
                icon = R.drawable.ic_tab_heart,
                tag = "暂未开发",
            )
            SettingsItem(
                title = "联系作者",
                subtitle = "反馈问题与建议",
                icon = R.drawable.ic_tab_smile,
                chevron = true,
                onClick = onOpenContact,
            )
        }
    }
}
