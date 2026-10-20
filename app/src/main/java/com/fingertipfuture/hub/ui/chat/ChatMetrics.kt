package com.fingertipfuture.hub.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 聊天页的几何规格。
 *
 * 全部数值都是**用户给的精确规格**（单位 dp），照的是设计稿：聊天页分三块 ——
 * 上「标题栏 + 功能栏」、中「正文」、下「输入胶囊」。
 *
 * - 标题栏 / 输入胶囊：2026-10-15 第一版规格（见技术说明 5.18）
 * - 功能栏 / 正文：2026-10-15 第二版规格（见技术说明 5.19）——
 *   正文拆成「日期 + 输入区（我）+ 输出区（AI）」，一次完整对话 = 这三段。
 *
 * 改之前先确认规格是否变化 —— 这些数字不是"看着调"出来的。
 */
object ChatMetrics {

    // ---- 顶栏：左边标题栏（一颗胶囊）+ 右边功能栏（一颗胶囊）----

    /** 胶囊距屏幕左右边缘、距状态栏下沿的距离。 */
    val TOP_BAR_SIDE_MARGIN = 10.dp
    val TOP_BAR_TOP_MARGIN = 5.dp

    /** 两颗胶囊的圆角都是 25（胶囊高度的一半）。 */
    val TOP_BAR_RADIUS = 25.dp

    /** 顶栏图标统一 24×24。 */
    val TOP_BAR_ICON_SIZE = 24.dp

    // ---- 顶栏左侧：标题栏 ----

    /**
     * 标题栏的**上限**宽 180dp（长度是**自适应**的 —— 会话名 / 模型名短则胶囊跟着收窄，
     * 最长不超过这个值，超出的文字打省略号）；高度 50。
     */
    val TITLE_BAR_WIDTH_MAX = 180.dp
    val TITLE_BAR_HEIGHT = 50.dp

    /** 「展开」按钮：24×24，距标题栏左边 7，垂直居中。 */
    val TITLE_BAR_ICON_INSET = 7.dp

    /** 会话名 / 模型名距「展开」按钮右侧 10。 */
    val TITLE_BAR_TEXT_GAP = 10.dp

    /**
     * 文字右边到胶囊右沿的留白（用户没给这一项，我加了 10dp）。
     *
     * 为什么必须要有：胶囊宽度是**自适应内容**的，没有这段留白的话，最长的那行文字就会
     * 正好顶到胶囊内边，而胶囊是按圆角**裁切内容**的 —— 圆角那一圈会把最后一两个字母切掉
     * （2026-10-15 实测：`mock-model` 的最后一个 `l` 被切了一半，见 5.18）。
     * 代价：文字可用宽度从 95dp 变成 136-7-24-10-10 = 85dp。
     */
    val TITLE_BAR_TEXT_END_PADDING = 10.dp

    /** 会话名字号 14、模型名字号 12，两行**之间不加间距**（行高取 1.15 倍左右）。 */
    val TITLE_BAR_NAME_SIZE: TextUnit = 14.sp
    val TITLE_BAR_MODEL_SIZE: TextUnit = 12.sp
    val TITLE_BAR_NAME_LINE = 16.sp
    val TITLE_BAR_MODEL_LINE = 14.sp

    // ---- 顶栏右侧：功能栏 ----

    /** 功能栏 78×50（用户规格），与标题栏同高。 */
    val FUNCTION_BAR_WIDTH = 78.dp
    val FUNCTION_BAR_HEIGHT = TITLE_BAR_HEIGHT

    /** 功能栏两侧的内边距、两个图标之间的间距（各 10）。 */
    val FUNCTION_BAR_SIDE_PADDING = 10.dp
    val FUNCTION_BAR_ICON_GAP = 10.dp

    // ---- 正文：一次完整对话 = 日期 + 输入区（我）+ 输出区（AI）----

    /** 头像框距自己那一侧界面边缘的距离（输出区在左、输入区在右）。 */
    val BODY_SIDE_MARGIN = 10.dp

    /** 头像框 40×40 的圆。 */
    val BODY_AVATAR_SIZE = 40.dp

    /** 昵称与头像之间的间距（输入区昵称在头像左边、输出区在右边）。 */
    val BODY_NICKNAME_GAP = 5.dp

    /** 昵称字号 14。 */
    val BODY_NICKNAME_SIZE: TextUnit = 14.sp

    /** 日期（时间分隔）：字号 11，水平居中，位于输入区头像**上方 5**。 */
    val TIME_SIZE: TextUnit = 11.sp

    /** 相邻两行之间的间距：日期↔输入区、输入区↔输出区、两次完整对话之间，都是 5。 */
    val MESSAGE_GAP = 5.dp

    /** 头像下沿到思考框 / 内容框的距离。 */
    val BODY_BLOCK_GAP = 5.dp

    /** 顶栏下沿到正文顶部、正文底部到输入胶囊的距离。 */
    val BODY_TOP_GAP = 5.dp
    val BODY_BOTTOM_GAP = 10.dp

    /** 正文文字（回复内容 / 内容框里的字）字号 16，行高按 1.5 倍给。 */
    val BODY_TEXT_SIZE: TextUnit = 16.sp
    val BODY_TEXT_LINE = 24.sp

    /** 内容框（输入区）：圆角 10、宽高自适应内容；内边距规格没给，取了 12/10。 */
    val BODY_BOX_RADIUS = 10.dp
    val BODY_BOX_MAX_WIDTH = 280.dp
    val BODY_BOX_PADDING_H = 12.dp
    val BODY_BOX_PADDING_V = 10.dp

    // ---- 输出区的思考框 ----

    /** 思考框 125×35、圆角 10（挂在头像下方 5、左边 10）。 */
    // 2026-10-17：125 → 140。12sp 的「思考了 xx.x 秒」+ 灯泡 + 展开箭头在 125 里差几像素，
    // 秒数一过 10 秒就会被 Ellipsis 截掉最后一位；加宽一点留足余量。
    val THINK_BOX_WIDTH = 140.dp
    val THINK_BOX_HEIGHT = 35.dp
    val THINK_BOX_RADIUS = 10.dp

    /** 内部：灯泡 / 展开 20×20，盒子两端各留 5 → 规格算下来文本分到 65dp。 */
    val THINK_BOX_PADDING = 5.dp

    /**
     * 灯泡→文字、文字→展开 这两段间距（规格给的也是 5）。
     *
     * 2026-10-15 实测：65dp 装不下 12 号的「思考了 2.2 秒」—— 量出来 66dp，
     * 差 1dp 就会被截掉最后一个「秒」字。**三个规格数（125 / 20 / 12 号）都没动**，
     * 只把这两段内部间距从 5 收到 4，文本就有 67dp 了。
     */
    val THINK_TEXT_GAP = 4.dp
    val THINK_ICON_SIZE = 20.dp
    val THINK_TEXT_SIZE: TextUnit = 12.sp

    // ---- 消息下方那一排小按钮 ----

    /**
     * 小按钮图标 18×18（用户 2026-10-15 从 22 改成 18），距内容下方 5。
     *
     * 排版全部由这个常量推出来（`ActionRow` 只用它 + 下面两个间距 + Row 的首尾对齐），
     * 所以改大小不用动别的地方：输出区总宽 = 5×18 + 4×15 = 150dp，
     * 输入区 = 4×18 + 3×10 = 102dp，第一颗距屏幕边 10、最后一颗也距边 10。
     * ⚠️ 点击热区不受影响：Compose 的 `clickable` 会把触摸区自动撑到 48×48 的最小尺寸。
     */
    val ACTION_ICON_SIZE = 18.dp
    val ACTION_TOP_GAP = 5.dp

    /** 输出区五个按钮间距 15；输入区四个按钮间距 10（两个规格给的值不一样）。 */
    val ACTION_GAP_OUTPUT = 15.dp
    val ACTION_GAP_INPUT = 10.dp

    // ---- 底部：输入胶囊 ----

    /** 胶囊：高 50、宽 = 屏宽 - 20、圆角 25，距屏幕底部 10。 */
    val INPUT_CAPSULE_SIDE_MARGIN = 10.dp
    val INPUT_CAPSULE_BOTTOM_MARGIN = 10.dp
    val INPUT_CAPSULE_HEIGHT = 50.dp
    val INPUT_CAPSULE_RADIUS = 25.dp

    /**
     * 胶囊**上下**的内边距（5dp）：单行时 5 + 输入框 40 + 5 = 胶囊 50（规格不变）；
     * 多行时胶囊跟着输入框一起长高，图标贴着下底走（照「指尖」的聊天输入框）。
     */
    val INPUT_CAPSULE_VERTICAL_PADDING = 5.dp

    /** 最左 / 最右两个部件到胶囊左右两边的距离。 */
    val INPUT_CAPSULE_CONTENT_PADDING = 10.dp

    /** 四个部件（麦克风 / 输入框 / 表情 / 添加）两两之间的间距。 */
    val INPUT_PART_GAP = 10.dp

    /** 麦克风 / 表情 / 添加都是 24×24，四个部件全部垂直居中。 */
    val INPUT_ICON_SIZE = 24.dp

    /**
     * 输入框高 40、圆角 25，宽度 = 胶囊宽 - 122
     * （10 + 24 + 10 + 10 + 24 + 10 + 24 + 10，即两侧内边距 + 三个图标 + 三段间距）。
     *
     * 代码里用 `weight(1f)` 实现：其它部件都是定值，剩下的正好就是这 122 之外的部分，
     * 任何屏宽下都等于规格里的公式，也不会被挤成负数。
     */
    val INPUT_FIELD_HEIGHT = 40.dp
    val INPUT_FIELD_RADIUS = 25.dp

    /** 输入框内文字距框左沿的距离（规格没给，沿用 0.2.x 的 14）。 */
    val INPUT_FIELD_TEXT_PADDING = 14.dp

    /** 占位文字与正文都是 14 号，行高 20（单行时框高正好 40 = 20 + 上下各 10）。 */
    val INPUT_TEXT_SIZE: TextUnit = 14.sp
    val INPUT_TEXT_LINE = 20.sp

    /** 输入框最多显示 6 行，超出后框内滚动（胶囊跟着变高）。 */
    const val INPUT_FIELD_MAX_LINES = 6

    /** 输入框实际排到 3 行时，显示「全屏输入」按钮。 */
    const val INPUT_FULLSCREEN_MIN_LINES = 3

    // ---- 语音输入（2026-10-15 用户给的图：**只做 UI，不做功能**）----

    /** 「长按说话」那颗的高度（= 输入框高度，胶囊内部几何一个字不用改）。 */
    val VOICE_HOLD_HEIGHT = 40.dp

    /**
     * 语音选择框：**高 100**（用户 2026-10-15 给的数），和输入胶囊同宽、同左沿、**底边对齐** ——
     * 于是它的下半截（50dp）正好被输入胶囊挡住，露在上面的只有 50dp，三颗按钮就放在那半截里。
     */
    val VOICE_CARD_HEIGHT = 100.dp

    /**
     * 卡片**露出来的那部分**的高度 = 100 - 胶囊高 50 = 50dp。
     *
     * 卡片是在一个「高 50、圆角 25」的**可视窗口**里往上滑的（窗口正好位于胶囊顶边以上）：
     * 于是它只从窗口里露出来，永远不会从胶囊两端半圆旁边露出直边（等宽矩形卡片 + 半圆胶囊，
     * 直边必然从圆角旁露出来 —— 2026-10-15 用户一眼看出来的就是这个）。
     */
    val VOICE_CARD_VISIBLE_HEIGHT = 50.dp
    val VOICE_CARD_RADIUS = 25.dp
    val VOICE_CARD_PADDING = 10.dp

    /** 三颗按钮距卡片顶边的距离 = (50 - 36)/2 + 7 ≈ 让它们在"露出来的那半截"里居中。 */
    val VOICE_ACTION_TOP_PADDING = 7.dp

    /** 三颗按钮的高度与圆角（圆角取一半，是标准胶囊形）。 */
    val VOICE_ACTION_HEIGHT = 36.dp
    val VOICE_ACTION_RADIUS = 18.dp

    /** 相邻两颗按钮之间的间距。 */
    val VOICE_ACTION_GAP = 10.dp

    /**
     * 语音选择框和输入胶囊的**重叠**量。
     *
     * 2026-10-15 定稿：卡片 100 高、底边和胶囊底边对齐 → 重叠正好是半个胶囊（25dp），
     * 「下半部分和输入胶囊重叠」就是这么来的。留着这个常量只是给手势换算用。
     */
    val VOICE_CARD_OVERLAP = 25.dp

    /** 「已经回到最近一条」的容差：手指滑回来时很难刚好停在像素级底部。 */
    val NEAR_BOTTOM_THRESHOLD = 24.dp

    // ---- 「更多」栏：消息那一排的「更多」弹出来的面板（2026-10-15 用户给的图）----

    /** 面板**上方两个角** 25 的圆角（用户明确给的数）。 */
    val MORE_SHEET_RADIUS = 25.dp

    /**
     * 面板高度 = **屏高的 55%**。
     *
     * 照设计稿量的：画框高 568px，面板从 y≈290 一直盖到底边 601 → 311.5 / 568 ≈ 0.55。
     * 起来之后把输入胶囊也盖住（设计稿里胶囊确实是看不见的）。
     */
    const val MORE_SHEET_HEIGHT_FRACTION = 0.55f

    /** 中间那颗**黑色小条**：宽 100、高 6（设计稿量的 62×4 px，按 1.6dp/px 换算）。 */
    val MORE_SHEET_HANDLE_WIDTH = 100.dp
    val MORE_SHEET_HANDLE_HEIGHT = 6.dp

    /** 小条距面板上边的距离（设计稿量的 4px ≈ 6dp）。 */
    val MORE_SHEET_HANDLE_TOP = 6.dp
}
