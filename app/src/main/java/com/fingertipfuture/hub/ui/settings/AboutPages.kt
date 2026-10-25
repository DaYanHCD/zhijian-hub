package com.fingertipfuture.hub.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.core.AppConfig
import com.fingertipfuture.hub.core.AppInfo
import com.fingertipfuture.hub.util.formatBytes
import com.fingertipfuture.hub.update.UpdateController
import com.fingertipfuture.hub.ui.component.MaterialSymbol

/** 打开一个链接；打不开就提示。 */
private fun openUrl(context: Context, url: String) {
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    if (!ok) context.showLocalizedToast("没有能打开它的应用")
}

/** 复制到剪贴板。 */
private fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager?.setPrimaryClip(ClipData.newPlainText(label, text))
    context.showLocalizedToast("已复制：" + text)
}

/**
 * 关于：版本 / 大小 / 更新 / 官网 / GitHub。
 *
 * 2026-10-16 用户要求：**删掉包名**（那是给开发看的，用户界面上没用）；
 * 2026-10-17 用户要求：**删掉「系统」那一行**，并把「检查更新」「更新日志」从设置首页
 * 的「关于」分区**收回到这一页**来。
 */
@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val context = LocalContext.current
    val size = remember { AppInfo.installedSizeBytes(context) }

    SettingsPage(title = "关于", onBack = onBack) {
        SettingsGroup(title = "本应用") {
            SettingsItem(
                title = stringResource(R.string.app_name),
                // 2026-10-17 用户指定：这一行的提示词就叫「软件版本号」
                subtitle = "软件版本号",
                icon = R.drawable.ic_info,
                value = AppConfig.CURRENT_VERSION_NAME,
                topDivider = false,
            )
            SettingsItem(
                title = "安装包大小",
                subtitle = "本机已装的 APK",
                icon = R.drawable.ic_settings_file,
                value = formatBytes(size, context.getString(R.string.unknown_size)),
            )
        }
        SettingsGroup(title = "更新") {
            SettingsItem(
                title = "检查更新",
                subtitle = "当前版本 " + AppConfig.CURRENT_VERSION_NAME,
                icon = R.drawable.ic_refresh,
                topDivider = false,
                onClick = { UpdateController.checkForUpdate() },
            )
            SettingsItem(
                title = "更新日志",
                subtitle = "每个版本的改动",
                icon = R.drawable.ic_history,
                chevron = true,
                onClick = onOpenChangelog,
            )
        }
        SettingsGroup(title = "链接") {
            SettingsItem(
                title = "官网",
                subtitle = "fingertipfuture.top",
                icon = R.drawable.ic_search,
                chevron = true,
                topDivider = false,
                onClick = { openUrl(context, "https://fingertipfuture.top/") },
            )
            SettingsItem(
                title = "GitHub 仓库",
                subtitle = "DaYanHCD/zhijian-hub",
                icon = R.drawable.ic_edit,
                chevron = true,
                onClick = { openUrl(context, "https://github.com/DaYanHCD/zhijian-hub") },
            )
        }
    }
}

/**
 * 长内容信息页（致谢 / 说明书共用）。
 *
 * 2026-10-17 用户要求：**整页直接展示**每一节的正文，列表不再可点、也不再有下级页面。
 * 每节仍是「小标题 + 卡片」，卡片里是副标题 + 正文。
 */
@Composable
internal fun InfoSectionsScreen(
    title: String,
    entries: List<InfoEntry>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPage(title = title, onBack = onBack) {
        entries.forEach { entry ->
            SettingsGroup(title = entry.title) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                ) {
                    if (entry.subtitle.isNotBlank()) {
                        Text(
                            text = entry.subtitle,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = entry.body,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (entry.links.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        entry.links.forEach { link ->
                            Text(
                                text = link.label + "  " + link.url,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openUrl(context, link.url) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 联系作者：**只复制，不跳转**（2026-10-17 用户要求）。
 *
 * 每一行点一下就是把那一串联系方式复制走（Toast 提示「已复制」）；
 * 图标都用各自的真实标志：邮箱 / QQ / Telegram / X。
 */
@Composable
internal fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = "联系作者", onBack = onBack) {
        // 列表和下方提示语贴得近一些（5dp），两者放进同一个 Column 避免
        // SettingsPage 默认的 22dp 组间距把它们拉开。
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsGroup(title = "联系方式") {
                SettingsItem(
                    title = "邮箱",
                    subtitle = "715133825@qq.com",
                    topDivider = false,
                    trailing = { CopyAction { copyToClipboard(context, "邮箱", "715133825@qq.com") } },
                )
                SettingsItem(
                    title = "QQ",
                    subtitle = "715133825",
                    trailing = { CopyAction { copyToClipboard(context, "QQ", "715133825") } },
                )
                SettingsItem(
                    title = "Telegram",
                    subtitle = "t.me/DaYan_HCD",
                    trailing = { CopyAction { copyToClipboard(context, "Telegram", "t.me/DaYan_HCD") } },
                )
                SettingsItem(
                    title = "X",
                    subtitle = "@DaYanHCD",
                    trailing = { CopyAction { copyToClipboard(context, "X", "@DaYanHCD") } },
                )
            }
            Text(
                text = "遇到问题或有任何建议，请随时与开发者联系，开发者会尽量在24小时内回复您",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 联系方式行尾的「复制」按钮。 */
@Composable
private fun CopyAction(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        MaterialSymbol(
            resId = R.drawable.ic_copy,
            contentDescription = "复制",
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
