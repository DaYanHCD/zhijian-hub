package com.fingertipfuture.hub.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubCapsuleColor
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubShadowColor
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import kotlinx.coroutines.flow.collect

/** 顶部返回胶囊的高度（和聊天页标题栏同高）。 */
private val SETTINGS_BACK_BAR_HEIGHT = 50.dp

/** 顶部返回胶囊的圆角（半高，两端是半圆）。 */
private val SETTINGS_BACK_BAR_RADIUS = 25.dp

/** 内容顶部要让开的高度：8dp 上边距 + 50dp 胶囊 + 18dp 间距。 */
internal val SETTINGS_BACK_BAR_SPACE = 76.dp

/**
 * 每一页的滚动位置（按页面标题存，进程内有效）。
 *
 * 设置页是"整页重建"式路由：从「设置」进子页时，设置页会被销毁；
 * 以前回到设置页会重新从顶部开始（用户 2026-10-17 反馈）。
 * 这里记住每页最后的 scroll 值，回来时按它初始化。
 */
private val settingsScrollPositions = mutableMapOf<String, Int>()

/**
 * 聊天页那套文字外阴影：偏移 (0, 4dp)、模糊 4dp。
 * 和小标题共用一份，别在两处各写一遍数字。
 */
@Composable
private fun settingsDropTextShadow(): Shadow {
    val density = LocalDensity.current
    val shadowColor = HubShadowColor
    return remember(density, shadowColor) {
        with(density) {
            Shadow(color = shadowColor, offset = Offset(0f, 4.dp.toPx()), blurRadius = 4.dp.toPx())
        }
    }
}

/**
 * 设置类页面的外壳（2026-10-16 照用户给的参考图）。
 *
 * 形是：顶部**一颗胶囊**（返回箭头 + 标题合成一个）→ 往下是「分组小标题 + 卡片」。
 * 页面上不放任何"说明性"文字，只有标题 / 副标题 / 当前值。
 *
 * 2026-10-17 用户要求：标题和返回按钮并为一个，胶囊与卡片、小标题都带聊天页那条外阴影
 * （x0 / y4 / 模糊 4，见 [hubDropShadow]）。
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    // 记住/恢复这一页的滚动位置：进子页再返回，停在原来的位置
    val scrollState = remember(title) { ScrollState(settingsScrollPositions[title] ?: 0) }
    LaunchedEffect(scrollState, title) {
        snapshotFlow { scrollState.value }.collect { settingsScrollPositions[title] = it }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                // 顶部空出悬浮胶囊那一块，内容从它下面穿过去
                .padding(top = SETTINGS_BACK_BAR_SPACE)
                .padding(bottom = 40.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = PAGE_HORIZONTAL_MARGIN),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                content = content,
            )
        }

        // 状态栏渐隐：内容从胶囊下面滑过时慢慢变淡
        TopFadeLayer(modifier = Modifier.align(Alignment.TopCenter))

        SettingsTitleBar(
            title = title,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * 悬浮「返回 + 标题」胶囊。设置页和统计页共用，保证标题合并、阴影和深色配色完全一致。
 */
@Composable
internal fun SettingsTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(SETTINGS_BACK_BAR_RADIUS)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = PAGE_HORIZONTAL_MARGIN,
                end = PAGE_HORIZONTAL_MARGIN,
                top = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = HubCapsuleColor,
            shape = barShape,
            tonalElevation = 0.dp,
            // 阴影自己画（hubDropShadow）：Material 的 elevation 推不出"x0 y4 模糊4"
            shadowElevation = 0.dp,
            modifier = Modifier
                .hubDropShadow(barShape)
                .hubCardBorder(barShape)
                .weight(1f, fill = false)
                .heightIn(min = SETTINGS_BACK_BAR_HEIGHT)
                .clickable(onClick = onBack),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = "返回",
                    modifier = Modifier.graphicsLayer(rotationZ = 90f),
                    size = 22.dp,
                    tint = HubInk,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = HubInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 一组：分组小标题 + 一张卡片（行之间是细分割线，见 [SettingsItem]）。 */
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(20.dp)
    // ⚠️ 卡片必须是不透明色：hubDropShadow 会在同一形状里先画一块实心黑再让 Surface 盖住，
    //    底色带透明度（原来这里是 surfaceVariant.copy(alpha = 0.5f)）时黑会透上来，整张卡变灰。
    //    这里把"背景 + 50% surfaceVariant"提前混成不透明色，观感不变。
    val cardColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.5f,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            // 与聊天页昵称同一条文字阴影：x0 / y4 / 模糊 4
            style = TextStyle(shadow = settingsDropTextShadow()),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = cardColor,
            shape = cardShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .hubDropShadow(cardShape)
                .hubCardBorder(cardShape)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** 组内一行的分割线（组里第一行不画）。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

/**
 * 一行：图标 + 标题（+ 可选的小标记）+ 副标题，右边可以是「当前值」胶囊或一个箭头。
 *
 * [topDivider] 一组里的**第一行**传 false。传了 [onClick] 才可点。
 */
@Composable
internal fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: Int? = null,
    /** 图标着色；给 [androidx.compose.ui.graphics.Color.Unspecified] 表示保留原色（品牌标志）。 */
    iconTint: Color? = null,
    value: String? = null,
    tag: String? = null,
    chevron: Boolean = false,
    topDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** 行尾的自定义操作（例如「复制」按钮）；给了就画在最右边。 */
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 24.dp,
                    tint = iconTint ?: MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        // 标题永远只占一行：右侧值再长也不许把标题折成两行
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tag != null) {
                        Spacer(Modifier.width(8.dp))
                        TagPill(tag)
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 给标题留出空间：模型名之类的长值最多占 160dp，超出打省略号
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = -90f),
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/**
 * 带下拉选择的一行（参考图里的「颜色模式」那一行）：
 * 右边是一颗**胶囊**，点它（或点整行）在胶囊**下面弹出**一个菜单，选完即生效。
 */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    value: String,
    options: List<String>,
    subtitle: String? = null,
    icon: Int? = null,
    topDivider: Boolean = true,
    onPick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = value,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        MaterialSymbol(
                            resId = R.drawable.ic_expand,
                            contentDescription = null,
                            // 展开时箭头朝上（照参考图）
                            modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 180f else 0f),
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(text = label, fontSize = 15.sp) },
                            onClick = {
                                onPick(index)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 带开关的一行。 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    topDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.graphicsLayer(scaleX = 0.92f, scaleY = 0.92f),
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/** 行标题后面的小标记（比如「暂未开发」）。 */
@Composable
private fun TagPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 小标记（比如助手列表里的「当前」）。 */
@Composable
internal fun SettingsTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 内容页（致谢 / 说明书）里的段落。 */
@Composable
internal fun SettingsParagraph(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/** 内容页里的小标题。 */
@Composable
internal fun SettingsHeader(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        style = TextStyle(shadow = settingsDropTextShadow()),
        modifier = Modifier.padding(start = 4.dp),
    )
}
