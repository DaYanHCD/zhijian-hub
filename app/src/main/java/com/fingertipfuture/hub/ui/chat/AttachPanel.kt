package com.fingertipfuture.hub.ui.chat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
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
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.BrandIcon

/**
 * 输入胶囊上「＋」弹出来的添加栏（照微信那个：从下面滑上来、白方块图标 + 名字、左滑翻页）。
 *
 * 2026-10-17：用户要求**三行四列、全部放在一页**，随后又重排为：
 * 添加图片 / 相机拍摄 / 添加文件 / 权限隐私、
 * 切换模型 / 思考深度 / 搜索设置 / 插件选择、
 * 发送位置。
 * 图标 / 文字 / 顺序都是用户 2026-10-15 照着设计稿给的（图标是 Lucide，见生成脚本）。
 *
 * 高度 = **键盘高度**（调用方把量到的键盘高度传进来）：键盘和面板占的是同一块地方，
 * 所以点「＋」时键盘下滑、面板原位顶上；点输入框时反过来，看起来像换了一层。
 *
 * 现在真正能用的只有「切换模型」和「导出对话」，其余点了会提示还没做 —— 先把入口摆出来，
 * 免得以后加功能还要重新排版。
 */
internal enum class AttachAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    // 第 1 页（8 个）
    // ⚠️ **形状 = 用户定稿的"最初版本"（Material Symbols Outlined）**，但**线宽改用 wght200 细线档**：
    // 用户原话："将图标改为最初版本，不添加填充"（并给了两张参考截图：相册=两张叠图、思考深度=侧脸+齿轮、
    // 发送文件=回形针、发送位置=地图针、导入对话=文档+↑、导出对话=托盘+↓）。
    // 2026-10-15 用户第三次问"为什么图标这么粗"——根因：图标是 24 格画的设计，线宽 ~2 格，
    // 而这一排是按 **50dp** 画的 ⇒ 线宽被放大 50/24 ≈ 2.08 倍 ⇒ ~4.2dp，看着就是粗。
    Gallery(R.drawable.ic_attach_lite_image_v2, R.string.attach_gallery),
    Camera(R.drawable.ic_attach_lite_camera_v2, R.string.attach_camera),
    SendFile(R.drawable.ic_attach_lite_file_v2, R.string.attach_send_file),
    PermissionPrivacy(R.drawable.ic_attach_lite_permission, R.string.attach_permission_privacy),
    // 「切换模型」平时显示的是**当前模型那家的官方标志**（见 ChatAttachPanel 的 modelIcon），
    // 这颗双箭头只在"没有 Logo 的自定义供应商"时兜底。
    SwitchModel(R.drawable.ic_attach_lite_swap, R.string.attach_switch_model),
    ThinkingDepth(R.drawable.ic_attach_lite_depth_v2, R.string.attach_thinking_depth),
    SearchSettings(R.drawable.ic_attach_lite_search, R.string.attach_search_settings),
    Plugins(R.drawable.ic_attach_lite_puzzle, R.string.attach_plugins),
    SendLocation(R.drawable.ic_attach_lite_location, R.string.attach_send_location),
}

/** 每页固定三行、每行四个（不够的位置留空）。 */
private const val ATTACH_COLUMNS = 4
private const val ATTACH_ROWS = 3

/**
 * 「切换模型」里那颗官方标志的尺寸。
 *
 * 比旁边那几颗 Lucide（30）略小：厂商标志是**画满整个 24 格**的（没有自带留白），
 * 一样大反而会显得比邻居胖一圈。
 */
private val MODEL_ICON_SIZE = 27.dp

/** 图标块（白底方块）的尺寸。2026-10-17 用户要求"图标和背景都缩小"：60 → 46。 */
private val ATTACH_TILE_SIZE = 46.dp

/** 图标本身的尺寸：50 → 30（块小了一号，图标再小一点才不显得挤）。 */
private val ATTACH_ICON_SIZE = 30.dp

/** 面板再矮也不能矮过「三行」，否则内容会被切掉（表情栏也共用这个下限）。 */
internal val ATTACH_MIN_HEIGHT = 244.dp

/** 键盘高度拿不到时（还没弹过键盘）用的兜底值。 */
internal val ATTACH_DEFAULT_HEIGHT = 288.dp

internal val ATTACH_PAGES: List<List<AttachAction>> =
    AttachAction.entries.chunked(ATTACH_COLUMNS * ATTACH_ROWS)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatAttachPanel(
    panelHeight: Dp,
    onAction: (AttachAction) -> Unit,
    modifier: Modifier = Modifier,
    // 「切换模型」那颗显示的**当前模型那家的官方标志**（没有标志的自定义供应商才退回箭头图标）
    modelIcon: BrandIcon? = null,
) {
    val pagerState = rememberPagerState(pageCount = { ATTACH_PAGES.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight.coerceAtLeast(ATTACH_MIN_HEIGHT))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        // 图标区撑满「圆点以上」的全部高度：面板（= 键盘）越高，两行就摊得越开，
        // 图标位置跟着面板大小走，不会在底部留一大块空白。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            AttachPage(items = ATTACH_PAGES[page], modelIcon = modelIcon, onAction = onAction)
        }

        // 只有一页时不画页码圆点（一页只有一个点，没意义）
        if (ATTACH_PAGES.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(ATTACH_PAGES.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** 一页：固定三行四列；这一页没有的行用等高的空位补上。 */
@Composable
private fun AttachPage(
    items: List<AttachAction>,
    modelIcon: BrandIcon?,
    onAction: (AttachAction) -> Unit,
) {
    val rows = items.chunked(ATTACH_COLUMNS)
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(ATTACH_ROWS) { rowIndex ->
            val row = rows.getOrNull(rowIndex)
            Row(
                // 三行平分面板高度，内容在各自那一格里居中 —— 面板多高，图标就怎么排
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (row != null) {
                    row.forEach { action ->
                        AttachItem(
                            action = action,
                            modelIcon = modelIcon,
                            modifier = Modifier.weight(1f),
                            onAction = onAction,
                        )
                    }
                    repeat(ATTACH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 一项：白方块 + 图标，下面一行小字（和微信的排布一样）。 */
@Composable
private fun AttachItem(
    action: AttachAction,
    modelIcon: BrandIcon?,
    modifier: Modifier = Modifier,
    onAction: (AttachAction) -> Unit,
) {
    Column(
        modifier = modifier
            .clickable { onAction(action) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 2026-10-17 收紧：背景 46×46、圆角 12，里面的图标 30×30
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(ATTACH_TILE_SIZE)
                .hubCardBorder(RoundedCornerShape(12.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 「切换模型」用**当前选中模型那家的官方标志**（DeepSeek / OpenAI / 通义…的真实 Logo，
                // 来自 LobeHub icons，MIT）；没有标志的自定义供应商才退回那颗箭头图标（用户 2026-10-15）。
                val brand = if (action == AttachAction.SwitchModel) modelIcon else null
                if (brand != null) {
                    MaterialSymbol(
                        resId = brand.res,
                        contentDescription = stringResource(action.label),
                        // 官方彩色标志直接上原色；本来就单色的那几家（OpenAI、Kimi…）才用前景色
                        tint = if (brand.colored) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                        size = MODEL_ICON_SIZE,
                    )
                } else {
                    MaterialSymbol(
                        resId = action.icon,
                        contentDescription = stringResource(action.label),
                        size = ATTACH_ICON_SIZE,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(action.label),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
