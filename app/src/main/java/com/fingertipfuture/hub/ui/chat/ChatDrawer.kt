package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubOuterShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubSideBorder

/**
 * 左侧抽屉（副屏）—— 2026-10-16 用户给的**精确规格**（数值都是 dp，全部照抄，别自行加减）：
 *
 * ```
 * [头像 60×60] ─10─ 用户昵称（24sp，和头像横向居中对齐）           ← 用户区
 * ──────────────────────────────────────────────────  ← 头像下方 10，长度 = 副屏整宽
 * 搜索图标 ─10─ 搜索聊天                                ← 分界线下方 10；左 10
 * 聊天记录图标 ─10─ 聊天历史                             ← 上一行图标下方 10
 * 「今天」                                             ← 上面那行下方 10
 * 会话标题                                            ← 「今天」下方 5
 * 会话标题                                            ← 同一日期里，标题之间 5
 * （换日期时）下一个日期文字                            ← 上一个日期最后一条标题下方 10
 * ...
 * 助手图标 ─10─ 统计图标            ...             设置图标   ← 悬浮在内容之上，离底边 10
 * ```
 *
 * 另外两条：副屏**右边两个圆角 = 25**（[drawerShape]）；左边两角贴着屏幕边，不圆。
 *
 * 说明（照实说，不摆假界面）：
 * - 历史区固定按「今天 / 昨天 / 日期」分组，列出**全部会话**；当前会话**不做特殊样式**，
 *   和其它标题完全一致（2026-10-17 用户明确要求），点另一条就切过去。间距规则照 2026-10-16 的规格：
 *   "日期 → 5 → 标题 → 5 → 标题 … 跨日期 10"。
 * - 用户 2026-10-16 的规格里，历史区**只有日期 + 会话标题**，没有预览行。
 * - 会话行**长按**弹操作框（置顶 / 重新生成标题 / 移到文件夹 / 删除）；
 *   文件夹在「移动到文件夹」里新建，副屏不再单列「创建文件夹」。
 * - 「助手」「统计」点了仍会说明还没做；「聊天历史」是下面那段的标题，不可点。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChatDrawerContent(
    conversations: List<HubConversation>,
    /** 当前还是一条没落库的空对话时，给副屏补一行「新聊天」，样式和其它会话一样。 */
    currentDraftTitle: String?,
    onSearchClick: () -> Unit,
    onChatClick: (String) -> Unit,
    onChatLongClick: (HubConversation) -> Unit,
    onProfileClick: () -> Unit,
    onAssistantsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModalDrawerSheet(
        // 2026-10-18 用户要求：副屏长度再收 20dp（320 → 300）。
        modifier = Modifier
            .width(300.dp)
            // 2026-10-20 用户要求：左边那条贴着屏幕的描边在深色下太扎眼，去掉，只留上/右/下
            .hubSideBorder(RoundedCornerShape(topEnd = 25.dp, bottomEnd = 25.dp)),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        // 副屏右边两个圆角 = 25（用户 2026-10-16 定）；左边两个角贴着屏幕边，不用圆
        drawerShape = RoundedCornerShape(topEnd = 25.dp, bottomEnd = 25.dp),
        // 抽屉自己默认会避让系统栏；清零后整个副屏一直铺到手势栏下面。
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // ---------- 用户区：头像 60×60，右 10 是昵称（24sp，和头像横向居中对齐） ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProfileClick)
                    // 底部的 10 就是"头像框下方 10 是分界线"那 10
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MessageAvatar(isSelf = true, size = 60.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    // 昵称可以在「我的资料」里改（副屏这一行点一下就是）
                    text = UiPrefs.userName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                // 可修改的标识：一颗小铅笔（提醒这里点头像/昵称能改）
                MaterialSymbol(
                    resId = R.drawable.ic_edit,
                    contentDescription = "修改头像和昵称",
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 分界线：长度 = 副屏整宽（HorizontalDivider 默认就铺满）
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ---------- 选择区：每条都是"左 10 图标 + 图标右 10 文字"，行之间 10 ----------
            Spacer(Modifier.height(10.dp))
            DrawerRow(R.drawable.ic_search, "搜索聊天", onSearchClick)
            Spacer(Modifier.height(10.dp))
            // 「聊天历史」是这一区的最后一行，也是下面那段历史记录的小标题，所以它不可点
            DrawerRow(R.drawable.ic_history, "聊天历史", onClick = null)

            // 上面那行图标下方 10 = 日期文字
            Spacer(Modifier.height(10.dp))

            // ---------- 历史记录 ----------
            // 按最近更新时间分组；conversations 本身已经按 pinned / updated_at 排好，
            // groupBy 会保留这个顺序。
            val allConversations = if (currentDraftTitle != null) {
                listOf(
                    HubConversation(
                        id = DRAWER_DRAFT_ID,
                        title = currentDraftTitle,
                        pinned = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ) + conversations
            } else {
                conversations
            }
            val days = allConversations
                .groupBy { dayLabel(it.updatedAt) }
                .map { (label, chats) -> DrawerDay(label = label, chats = chats) }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 真悬浮：列表一直铺到副屏底边，内容能从三个圆钮下面穿过去。
                contentPadding = PaddingValues(bottom = 72.dp),
            ) {
                if (days.isEmpty()) {
                    item {
                        Text(
                            text = "还没有对话，发一条试试",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                        )
                    }
                }
                days.forEachIndexed { dayIndex, day ->
                    item(key = "day-" + dayIndex) {
                        Text(
                            text = day.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            // 跨日期 10（第一组不用）
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = 10.dp,
                                top = if (dayIndex == 0) 0.dp else 10.dp,
                            ),
                        )
                    }
                    items(day.chats, key = { it.id }) { chat ->
                        val draft = chat.id == DRAWER_DRAFT_ID
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onChatClick(chat.id) },
                                    onLongClick = if (draft) {
                                        null
                                    } else {
                                        { onChatLongClick(chat) }
                                    },
                                )
                                // 日期下 5 = 第一条标题；同一日期里标题之间也是 5
                                .padding(start = 10.dp, end = 10.dp, top = 5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = chat.title.ifBlank { "新聊天" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (chat.pinned) {
                                    Spacer(Modifier.width(4.dp))
                                    MaterialSymbol(
                                        resId = R.drawable.ic_pin,
                                        contentDescription = "已置顶",
                                        size = 14.dp,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            }

            // ---------- 悬浮功能区：不占列表高度，内容从下面穿过 ----------
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FunctionItem(R.drawable.ic_mood, "助手", onAssistantsClick)
                Spacer(Modifier.width(10.dp))
                FunctionItem(R.drawable.ic_history, "统计", onStatsClick)
                Spacer(Modifier.weight(1f))
                FunctionItem(R.drawable.ic_settings, "设置", onSettingsClick)
            }
        }
    }
}

/**
 * 选择区里一行：左 10 是图标，图标右 10 是文字（用户 2026-10-16 的规格）。
 *
 * [onClick] 传 null 表示这一行是下面列表的小标题，不可点（例如「聊天历史」）。
 */
@Composable
private fun DrawerRow(
    icon: Int,
    label: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 行本身不加竖直 padding —— "上一行图标下方 10"靠外面的 Spacer(10.dp) 给，别叠两层
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(
            resId = icon,
            contentDescription = null,
            size = 22.dp,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (onClick == null) FontWeight.Bold else FontWeight.Normal,
            color = if (onClick == null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** 历史区里的一组：「今天」+ 这一天的会话。 */
private data class DrawerDay(val label: String, val chats: List<HubConversation>)

/** 副屏里给“还没落库的新对话”用的临时 id；不会写进数据库。 */
internal const val DRAWER_DRAFT_ID = "__current_draft__"

/** 会话列表的日期分组名（按最后更新时间）。 */
private fun dayLabel(at: Long): String {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay(day, today) -> "今天"
        sameDay(day, yesterday) -> "昨天"
        else -> (day.get(java.util.Calendar.MONTH) + 1).toString() + "月" +
            day.get(java.util.Calendar.DAY_OF_MONTH) + "日"
    }
}

/**
 * 功能区里的一个入口：**圆形底 + 图标**。
 *
 * 2026-10-16 用户两次要求：先是"只要图标"（去掉了圆底），又说"加个圆形背景、再放大些"——
 * 现在是 52dp 圆底 + 26dp 图标（改之前是 24dp 光图标）。
 * 2026-10-17：圆底改成**输入胶囊那颗的背景色**（[HubCapsuleColor]）并加外阴影，图标用 [HubInk]。
 */
@Composable
private fun FunctionItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .hubOuterShadow(CircleShape)
            .size(52.dp)
            .clip(CircleShape)
            .background(HubCapsuleColor)
            .hubCardBorder(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            resId = icon,
            contentDescription = label,
            size = 26.dp,
            tint = HubInk,
        )
    }
}
