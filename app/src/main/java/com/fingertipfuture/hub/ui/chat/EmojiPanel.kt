package com.fingertipfuture.hub.ui.chat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDropShadow

/**
 * 输入胶囊上「表情」弹出来的表情栏。
 *
 * 和添加栏占**同一块地方、同一个高度**（= 键盘高度），所以三块（键盘 / 添加栏 / 表情栏）
 * 互相切换时输入胶囊一动不动，看着像换了一层（见《技术说明》9.3）。
 *
 * 图形使用 Google Noto Emoji（SIL OFL 1.1）：列表始终显示随基础包提供的静态正脸帧；
 * 用户在「设置 › 数据 › 存储与扩展」下载并启用动态扩展后，发送单个表情才在 Android 9+ 原生播放 WebP。
 * 标签栏那 6 个分类图标仍使用 Lucide（ISC，可商用）。
 * 以后要加/换分类，改下面的枚举 + 生成脚本的 MAP，各一行。
 */
internal enum class EmojiCategory(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val emoji: List<String>,
) {
    Smile(R.drawable.ic_tab_smile, R.string.emoji_category_smile, EMOJI_SMILE),
    Hand(R.drawable.ic_tab_hand, R.string.emoji_category_hand, EMOJI_HAND),
    Animal(R.drawable.ic_tab_cat, R.string.emoji_category_animal, EMOJI_ANIMAL),
    Food(R.drawable.ic_tab_apple, R.string.emoji_category_food, EMOJI_FOOD),
    Activity(R.drawable.ic_tab_trophy, R.string.emoji_category_activity, EMOJI_ACTIVITY),
    Symbol(R.drawable.ic_tab_heart, R.string.emoji_category_symbol, EMOJI_SYMBOL),
}

/** 一行放几个表情。 */
private const val EMOJI_COLUMNS = 8

private val EMOJI_SMILE = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
    "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
    "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
    "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
    "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
    "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
    "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥",
    "😓", "🤗", "🤔", "🤭", "🤫", "😶", "😐", "😑",
    "😬", "🙄", "😯", "😴", "🤤", "😪", "😵", "🤐",
    "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🥳",
)

private val EMOJI_HAND = listOf(
    "👍", "👎", "👌", "👏", "🙌", "🤝", "🙏", "💪",
    "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
    "🖖", "👋", "🤞", "🤟", "🤘", "🤙", "✊", "👊",
    "👐", "🤲", "✍️", "💅", "🤳", "🦾", "🦵", "🦶",
    "👀", "👁️", "👂", "👃", "👄", "👅", "🦷", "🦴",
)

private val EMOJI_ANIMAL = listOf(
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
    "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
    "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐗",
    "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
    "🐢", "🐍", "🐙", "🦑", "🦐", "🦀", "🐡", "🐠",
    "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐘", "🦒",
)

private val EMOJI_FOOD = listOf(
    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
    "🍓", "🍈", "🍒", "🍑", "🍍", "🥝", "🍅", "🥑",
    "🍆", "🥔", "🥕", "🌽", "🌶️", "🥒", "🥬", "🍄",
    "🥜", "🍞", "🥐", "🥖", "🧀", "🥚", "🍳", "🥞",
    "🥓", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪",
    "🌮", "🌯", "🥗", "🍝", "🍜", "🍲", "🍣", "🍱",
    "🍚", "🍙", "🍡", "🍧", "🍨", "🍦", "🍰", "🎂",
    "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "☕",
)

private val EMOJI_ACTIVITY = listOf(
    "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎱", "🏓",
    "🏸", "🏒", "🥅", "⛳", "🏹", "🎣", "🥊", "🥋",
    "🎽", "⛸️", "🎿", "⛷️", "🏂", "🏋️", "🏌️", "🏇",
    "🧘", "🏄", "🏊", "🚣", "🚴", "🏆", "🥇", "🥈",
    "🥉", "🏅", "🎖️", "🎫", "🎪", "🎭", "🎨", "🎬",
    "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
    "🎻", "🎲", "🎯", "🎳", "🎮", "🎰", "🧩", "🎁",
)

private val EMOJI_SYMBOL = listOf(
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘",
    "✨", "⭐", "🌟", "🔥", "💥", "💫", "💦", "☁️",
    "🎉", "🎊", "🎈", "🎀", "🔔", "🎵", "🎶", "💡",
    "🔍", "📌", "📎", "✂️", "📝", "✏️", "📚", "📖",
    "📅", "📊", "📈", "🔒", "🔑", "💎", "🛒", "🚗",
    "✈️", "🚀", "🏠", "⛰️", "🌈", "☀️", "🌤️", "🌧️",
    "❄️", "⛄", "🎃", "🎄", "👑", "💯", "✅", "❗",
)

/**
 * 表情栏本体：上面是可滚动的表情网格，下面是标签栏（分类）。
 *
 * 高度 = [panelHeight]（调用方传进来的键盘高度，最小不低于添加栏那个下限），
 * 所以它和键盘、添加栏完全等高等宽 —— 三块之间切换时像换了一层。
 */
@Composable
internal fun ChatEmojiPanel(
    panelHeight: Dp,
    onPick: (String) -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(EmojiCategory.Smile) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight.coerceAtLeast(ATTACH_MIN_HEIGHT))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(EMOJI_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 9.dp,
                top = 7.dp,
                end = 9.dp,
                bottom = 78.dp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight.coerceAtLeast(ATTACH_MIN_HEIGHT)),
        ) {
            itemsIndexed(
                items = category.emoji,
                key = { index, _ -> "${category.name}-$index" },
            ) { _, symbol ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                        .clickable { onPick(symbol) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NotoEmojiGlyph(
                        symbol = symbol,
                        size = 30.dp,
                    )
                }
            }
        }

        // 底部工具栏：删除 / 分类 / 发送。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(3f)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiToolButton(
                icon = R.drawable.ic_emoji_backspace,
                contentDescription = "删除",
                onClick = onDelete,
            )
            Spacer(Modifier.size(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiCategory.entries.forEach { item ->
                    val selected = item == category
                    val shape = RoundedCornerShape(11.dp)
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            HubCapsuleColor
                        },
                        shape = shape,
                        modifier = Modifier
                            .size(32.dp)
                            .hubDropShadow(shape)
                            .hubCardBorder(shape)
                            // clickable 的 indication 画在这层裁剪里；否则水波会按 32dp 正方形溢出圆角。
                            .clip(shape)
                            .clickable { category = item },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbol(
                                resId = item.icon,
                                contentDescription = stringResource(item.label),
                                size = 18.dp,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))

            Surface(
                color = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(40.dp)
                    .hubDropShadow(RoundedCornerShape(10.dp))
                    .hubCardBorder(RoundedCornerShape(10.dp))
                    .clickable(enabled = canSend, onClick = onSend),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(
                        resId = R.drawable.ic_send,
                        contentDescription = "发送",
                        size = 20.dp,
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiToolButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        color = HubCapsuleColor,
        shape = shape,
        modifier = Modifier
            .size(40.dp)
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(
                resId = icon,
                contentDescription = contentDescription,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 删除最后一个完整字符；不会把 emoji 的代理对或组合序列拆坏。 */
internal fun dropLastGrapheme(text: String): String {
    if (text.isEmpty()) return text

    fun isExtending(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            codePoint == 0xFE0E ||
            codePoint == 0xFE0F ||
            codePoint == 0x20E3 ||
            codePoint in 0x1F3FB..0x1F3FF
    }

    fun clusterStartBefore(boundary: Int): Int {
        var end = boundary
        while (end > 0) {
            val codePoint = Character.codePointBefore(text, end)
            if (!isExtending(codePoint)) break
            end -= Character.charCount(codePoint)
        }
        if (end <= 0) return 0
        return end - Character.charCount(Character.codePointBefore(text, end))
    }

    fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    var start = clusterStartBefore(text.length)
    while (start > 0) {
        val previous = Character.codePointBefore(text, start)
        when {
            previous == 0x200D -> {
                start = clusterStartBefore(start - Character.charCount(previous))
            }
            isRegionalIndicator(Character.codePointAt(text, start)) &&
                isRegionalIndicator(previous) -> {
                start -= Character.charCount(previous)
                break
            }
            else -> break
        }
    }
    return text.substring(0, start.coerceAtLeast(0))
}
