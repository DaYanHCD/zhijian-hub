package com.fingertipfuture.hub.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.AppConfig
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.settings.SettingsPage
import com.fingertipfuture.hub.update.ChangelogState
import com.fingertipfuture.hub.update.LocalChangelog
import com.fingertipfuture.hub.update.UpdateController
import com.fingertipfuture.hub.update.UpdateManifest
import com.fingertipfuture.hub.update.VersionInfo

/** 「当前版」标签的黄色。回退版已彻底移除，更新日志只展示正式发布历史。 */
private val BadgeYellow = Color(0xFFF9A825)
private val OnBadgeYellow = Color(0xFF1F1F1F)

/**
 * 更新日志整页（2026-10-17 按「指尖」的样式重做）。
 *
 * 每个版本是一张独立卡片：版本号 + 红绿灯标签 + 右侧日期，下面是更新条目；
 * 「更新：」「修复：」这类分类前缀加粗。服务器失败时显示内置兜底记录。
 */
@Composable
internal fun ChangelogScreen(onBack: () -> Unit) {
    val close = {
        UpdateController.dismissChangelog()
        onBack()
    }

    LaunchedEffect(Unit) { UpdateController.showChangelog() }
    DisposableEffect(Unit) {
        onDispose { UpdateController.dismissChangelog() }
    }

    val localEntries = remember { LocalChangelog.entries() }

    SettingsPage(title = stringResource(R.string.changelog_title), onBack = close) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val state = UpdateController.changelog) {
                ChangelogState.Hidden,
                ChangelogState.Loading,
                -> Box(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ChangelogState.Failed -> {
                    Text(
                        text = stringResource(R.string.changelog_local_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    localEntries.forEach { entry ->
                        ChangelogCard(
                            entry = entry,
                            isCurrent = entry.versionCode == AppConfig.CURRENT_VERSION_CODE,
                        )
                    }
                }

                is ChangelogState.Loaded -> ChangelogList(state.manifest)
            }
        }
    }
}

@Composable
private fun ChangelogList(manifest: UpdateManifest) {
    val entries = remember(manifest) {
        buildList {
            add(manifest.latest)
            addAll(manifest.history)
        }
            .distinctBy { it.versionCode }
            .sortedByDescending { it.versionCode }
    }
    entries.forEach { entry ->
        ChangelogCard(
            entry = entry,
            isCurrent = entry.versionCode == AppConfig.CURRENT_VERSION_CODE,
        )
    }
}

@Composable
private fun ChangelogCard(
    entry: VersionInfo,
    isCurrent: Boolean,
) {
    val shape = RoundedCornerShape(20.dp)
    val cardColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.5f,
    )

    Surface(
        color = cardColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.changelog_version_label,
                            entry.versionName.ifBlank { "#" + entry.versionCode },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        VersionBadge(
                            text = stringResource(R.string.changelog_current),
                            container = BadgeYellow,
                            content = OnBadgeYellow,
                        )
                    }
                }

                if (entry.releasedAt.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.releasedAt,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }

            if (entry.changelog.isEmpty()) {
                Text(
                    text = stringResource(R.string.changelog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.changelog.forEach { line ->
                        Text(
                            text = formatChangelogLine(line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val CHANGELOG_KIND_PREFIXES = listOf(
    "更新", "修复", "优化", "新增", "调整", "说明",
    "修復", "優化", "調整", "說明",
)

private val CHANGELOG_PREFIX_REGEX = Regex(
    "^(更新|修復|修复|優化|优化|新增|調整|调整|說明|说明)\\s*([：:])\\s*",
)

/** 把「修复：xxx」里的分类前缀加粗，其余保持普通正文。 */
private fun formatChangelogLine(line: String): AnnotatedString {
    val match = CHANGELOG_PREFIX_REGEX.find(line) ?: return AnnotatedString(line)
    val prefix = match.groupValues[1]
    if (prefix !in CHANGELOG_KIND_PREFIXES) return AnnotatedString(line)

    val prefixText = prefix + match.groupValues[2]
    val body = line.substring(match.range.last + 1)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(prefixText)
        }
        append(body)
    }
}
