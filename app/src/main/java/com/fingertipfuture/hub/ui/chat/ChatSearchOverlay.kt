package com.fingertipfuture.hub.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import com.fingertipfuture.hub.ui.theme.enforceHubTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 搜索聊天：覆盖整个聊天页的一层（不是弹窗），键盘弹起时结果区自动让位。
 *
 * - [conversationId] 非空：只搜索当前会话的正文；
 * - [conversationId] 为空：搜索全部会话的标题和正文（数据管理入口）。
 */
@Composable
internal fun ChatSearchOverlay(
    conversationId: String? = null,
    allConversations: Boolean = false,
    onDismiss: () -> Unit,
    onOpenHit: (ChatSearchHit) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        // 先让搜索页画出第一帧，再叫键盘；否则入口那一帧要同时做整页布局和 IME 动画。
        withFrameNanos { }
        delay(120)
        focusRequester.requestFocus()
    }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        // 连续输入时只查最后一次，避免每个字都去扫一遍全库
        delay(160)
        results = if (!allConversations && conversationId == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                ChatStore.search(
                    q,
                    conversationId = conversationId.takeUnless { allConversations },
                )
            }
        }
        searching = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PAGE_HORIZONTAL_MARGIN,
                        end = PAGE_HORIZONTAL_MARGIN,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .hubDropShadow(CircleShape)
                        .size(44.dp)
                        .clip(CircleShape)
                        .hubCardBorder(CircleShape)
                        .background(HubCapsuleColor)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = R.drawable.ic_arrow_back,
                        contentDescription = "关闭搜索",
                        size = 24.dp,
                        tint = HubInk,
                    )
                }
                Spacer(Modifier.width(6.dp))
                val searchShape = RoundedCornerShape(24.dp)
                val searchColor = lerp(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surfaceVariant,
                    0.65f,
                )
                Surface(
                    color = searchColor,
                    shape = searchShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .hubDropShadow(searchShape)
                        .hubCardBorder(searchShape)
                        .weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialSymbol(
                            resId = R.drawable.ic_search,
                            contentDescription = null,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ).enforceHubTypography(),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = if (allConversations) {
                                                "搜索会话和消息"
                                            } else {
                                                "搜索当前会话"
                                            },
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            if (query.isBlank()) {
                if (allConversations) {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        items(ChatStore.conversations, key = { it.id }) { conversation ->
                            SearchRow(
                                title = conversation.title.ifBlank { "新聊天" },
                                snippet = "打开这段会话",
                                at = conversation.updatedAt,
                                onClick = {
                                    onOpenHit(
                                        ChatSearchHit(
                                            conversationId = conversation.id,
                                            conversationTitle = conversation.title,
                                            role = "",
                                            text = "",
                                            at = conversation.updatedAt,
                                            messageIndex = -1,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = "输入关键词，搜索当前会话的聊天记录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = PAGE_HORIZONTAL_MARGIN,
                            top = 14.dp,
                        ),
                    )
                }
            } else {
                when {
                    searching -> Text(
                        text = "搜索中…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = PAGE_HORIZONTAL_MARGIN, top = 14.dp),
                    )
                    results.isEmpty() -> Text(
                        text = "没有找到「" + query.trim() + "」",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = PAGE_HORIZONTAL_MARGIN, top = 14.dp),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        items(
                            results,
                            key = { hit ->
                                hit.conversationId + "|" + hit.messageIndex + "|" + hit.at
                            },
                        ) { hit ->
                            SearchRow(
                                title = if (!allConversations) {
                                    when (hit.role) {
                                        "user" -> "我"
                                        "assistant" -> "AI"
                                        else -> "当前会话"
                                    }
                                } else {
                                    hit.conversationTitle.ifBlank { "新聊天" }
                                },
                                snippet = when {
                                    hit.messageIndex < 0 -> "会话标题匹配"
                                    !allConversations -> snippet(hit.text, query)
                                    hit.role == "user" -> "我：" + snippet(hit.text, query)
                                    else -> "AI：" + snippet(hit.text, query)
                                },
                                at = hit.at,
                                onClick = { onOpenHit(hit) },
                            )
                        }
                    }
                }
            }
        }

        // 和其它整页一致：内容从状态栏下面经过时渐隐，状态栏本身保持透明。
        TopFadeLayer(
            modifier = Modifier.align(Alignment.TopCenter),
            extraTop = 56.dp,
            tail = 20.dp,
        )
    }
}

@Composable
private fun SearchRow(
    title: String,
    snippet: String,
    at: Long,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val cardColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.5f,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
    ) {
        Surface(
            onClick = onClick,
            color = cardColor,
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .hubDropShadow(shape)
                .hubCardBorder(shape)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snippet,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = searchTimeLabel(at),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 命中词前后各留一点上下文；换行先压成空格。 */
private fun snippet(text: String, query: String): String {
    val clean = text.replace('\n', ' ').trim()
    if (clean.isEmpty()) return ""
    val at = clean.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
    val start = (at - 28).coerceAtLeast(0)
    val end = (start + 72).coerceAtMost(clean.length)
    return buildString {
        if (start > 0) append('…')
        append(clean.substring(start, end))
        if (end < clean.length) append('…')
    }
}

private fun searchTimeLabel(at: Long): String {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val today = java.util.Calendar.getInstance()
    val sameDay = day.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        day.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(at))
    } else {
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date(at))
    }
}
