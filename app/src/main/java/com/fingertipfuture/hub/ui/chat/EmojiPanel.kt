package com.fingertipfuture.hub.ui.chat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.MaterialSymbol

/**
 * 输入胶囊上「表情」弹出来的表情栏。
 *
 * 和添加栏占**同一块地方、同一个高度**（= 键盘高度），所以三块（键盘 / 添加栏 / 表情栏）
 * 互相切换时输入胶囊一动不动，看着像换了一层（见《技术说明》9.3）。
 *
 * 版权：表情本身就是**系统字体里的字符**（Android 自带的那套），不在仓库里带任何图片素材；
 * 标签栏那 6 个分类图标用的是 Lucide（ISC，可商用）—— 生成脚本 `生成Lucide图标.js` 的 `ic_tab_*`。
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

/** 表情字号（够大到一眼看清，又不至于一格塞不下）。 */
private val EMOJI_FONT_SIZE = 26.sp

/** 标签栏里「返回」那颗按钮的边长（右边留同宽的空位，保证分类居中）。 */
private val TAB_BUTTON_SIZE = 32.dp

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
    /** 点左下角那颗「返回」：关掉表情栏（面板会下滑退场，见《技术说明》9.3）。 */
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(EmojiCategory.Smile) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight.coerceAtLeast(ATTACH_MIN_HEIGHT))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 6.dp, bottom = 4.dp),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(EMOJI_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            items(category.emoji) { symbol ->
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(symbol) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = symbol, fontSize = EMOJI_FONT_SIZE)
                }
            }
        }

        // 标签栏：分类图标（Lucide，选中的用主色 + 淡主色底）；
        // 左端一颗「返回」= 关闭表情栏（用户 2026-10-15 要求），右端放一颗等宽空位保持分类居中
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(TAB_BUTTON_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_arrow_left,
                    contentDescription = stringResource(R.string.emoji_panel_back),
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiCategory.entries.forEach { item ->
                val selected = item == category
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { category = item }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        resId = item.icon,
                        contentDescription = stringResource(item.label),
                        size = 20.dp,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    }
                }
            }

            // 和左边那颗「返回」等宽的空位：这样 6 个分类是真正居中的
            Spacer(Modifier.size(TAB_BUTTON_SIZE))
        }
    }
}
