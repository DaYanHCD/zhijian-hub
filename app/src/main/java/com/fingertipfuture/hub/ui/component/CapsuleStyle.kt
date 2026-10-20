package com.fingertipfuture.hub.ui.component

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fingertipfuture.hub.data.UiPrefs

/**
 * 聊天页那套"外观定值"的一整套色（胶囊、墨色、输入框、发送键、阴影）。
 *
 * 为什么是**两套定值**而不从 MaterialTheme 推：2026-10-15 用户给的是一组绝对值
 * （胶囊 #E0E8FF、发送键 #6236FF…），页面底色也是指定的白。深色模式不能拿浅色那套
 * （深底 + 深墨色 = 看不见），所以 2026-10-16 补了深色一套，由 [LocalHubChrome] 在组合期选。
 */
@Immutable
data class HubChrome(
    /** 胶囊底色（标题栏、功能栏、输入胶囊、思考框、语音气泡…共用）。 */
    val capsule: Color,
    /** 主墨色：胶囊上的文字/图标、正文文字。 */
    val ink: Color,
    /** 次要墨色：模型名、占位文字、昵称、日期。 */
    val inkSoft: Color,
    /** 输入框底色（和胶囊错开一档，让它一眼是"能打字的地方"）。 */
    val field: Color,
    /** 发送键。 */
    val send: Color,
    /** 外阴影。 */
    val shadow: Color,
)

/** 浅色：用户 2026-10-15 给的定值，一个字没改。 */
internal val LightHubChrome = HubChrome(
    capsule = Color(0xFFE0E8FF),
    ink = Color(0xFF16232B),
    inkSoft = Color(0x9916232B),
    field = Color(0xFFC9D2E3),
    send = Color(0xFF6236FF),
    shadow = Color(0x40000000),
)

/**
 * 深色（2026-10-16 新加）：
 *   - 胶囊比深底亮一档、墨色换浅（和官网深色那套 #2A2C35 / #E3E1E9 同源）；
 *   - 输入框比胶囊**暗**一档 —— 浅色下它是"比胶囊暗"，深色下得反过来才看得出是输入区；
 *   - 发送键提亮到 #8B6BFF：原来那支 #6236FF 压在深底上只有 2.2:1 对比度，太小了。
 */
internal val DarkHubChrome = HubChrome(
    capsule = Color(0xFF2A2C35),
    ink = Color(0xFFE6E6EF),
    inkSoft = Color(0x99E6E6EF),
    field = Color(0xFF1B1D24),
    send = Color(0xFF8B6BFF),
    shadow = Color(0x66000000),
)

/** 当前用哪套：由 `HubTheme` 按主题塞进来。 */
internal val LocalHubChrome = staticCompositionLocalOf { LightHubChrome }

/**
 * 胶囊底色。
 *
 * 这几个 @Composable 取值器是为了**少改调用点**：以前是常量，现在要按主题给深浅两套，
 * 名字保持不变，聊天页那 40 多处 `color = HubCapsuleColor` 一行都不用动。
 * ⚠️ 只能在组合期读 —— 默认参数里可以（见 [hubDropShadow]），`remember` 的 lambda 里不行。
 */
val HubCapsuleColor: Color @Composable get() = LocalHubChrome.current.capsule

/** 主墨色。 */
val HubInk: Color @Composable get() = LocalHubChrome.current.ink

/** 次要墨色（主墨色降透明度）。 */
val HubInkSoft: Color @Composable get() = LocalHubChrome.current.inkSoft

/** 输入框底色。 */
val HubFieldColor: Color @Composable get() = LocalHubChrome.current.field

/** 发送键颜色。 */
val HubSendColor: Color @Composable get() = LocalHubChrome.current.send

/** 阴影默认色。 */
internal val HubShadowColor: Color @Composable get() = LocalHubChrome.current.shadow

/**
 * 设计稿口径的「模糊」是 CSS / Figma 那套（那个数是**直径**，Skia 的高斯 sigma 是它的一半）；
 * Android `Paint.setShadowLayer` 的 radius 与 sigma 的换算：sigma = radius * 0.57735 + 0.5。
 * 光看数字会失真，所以在这里写成一步换算，别在调用处手调。
 */
private const val SIGMA_PER_BLUR = 0.5f
private const val SIGMA_TO_RADIUS = 1f / 0.57735f

/**
 * 按**设计稿的参数**画外阴影：偏移 (x, y)、模糊 [blur]、颜色。
 *
 * 为什么不用 `Modifier.shadow(elevation)`：Material 那套的模糊/偏移是 elevation 推出来的，
 * 塞不进"x0 / y4 / 模糊 4"这种精确值；这里直接落到 `Paint.setShadowLayer`。
 *
 * ⚠️ 阴影是"先在原地画一遍形状、由 shadowLayer 投出来"，随后被 [androidx.compose.material3.Surface]
 * 自己的底色盖住，所以只看得见外圈；因此调用方的 `shadowElevation` 要给 0，别叠两层。
 * ⚠️ 硬件画布对非文字图形支持 shadowLayer 是 API 28 起的事，更低的机器上会退化成"没有阴影"（不影响布局）。
 *
 * 用法：`Modifier.hubDropShadow(shape)` 要放在**尺寸修饰符之前**（drawBehind 取的是最终尺寸）。
 */
@Composable
fun Modifier.hubDropShadow(
    shape: Shape,
    color: Color = HubShadowColor,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    blur: Dp = 4.dp,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toAndroidPath() ?: return@drawBehind
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        // 形状本身也画一遍（随后被 Surface 底色盖住），不画的话阴影是空心的
        this.color = android.graphics.Color.BLACK
        val radius = ((blur.toPx() * SIGMA_PER_BLUR) - 0.5f) * SIGMA_TO_RADIUS
        setShadowLayer(radius.coerceAtLeast(0f), offsetX.toPx(), offsetY.toPx(), color.toArgb())
    }
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path, paint) }
}

/**
 * 只画**形状外面**的那圈阴影（形状内部一律不画）。
 *
 * 和 [hubDropShadow] 的区别：那个是"在原地先画一遍实心形状、再由控件自己的底色盖住"，
 * 控件底色不透明、且形状完全一致时才干净；Material 的 OutlinedTextField（标签缺口）、
 * OutlinedButton（透明底）会把那块实心色露出来。这个版本用 clip 把形状内部剪掉，
 * 只留下外面那圈模糊阴影，所以**不需要底板**，也不会和控件的描边错开半个像素。
 *
 * 用法：`Modifier.hubOuterShadow(shape)`，同样放在尺寸修饰符之前。
 */
@Suppress("DEPRECATION")
@Composable
fun Modifier.hubOuterShadow(
    shape: Shape,
    color: Color = HubShadowColor,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    blur: Dp = 4.dp,
): Modifier = drawWithContent {
    val path = shape.createOutline(size, layoutDirection, this).toAndroidPath()
    if (path == null) {
        drawContent()
        return@drawWithContent
    }
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.BLACK
        val radius = ((blur.toPx() * SIGMA_PER_BLUR) - 0.5f) * SIGMA_TO_RADIUS
        setShadowLayer(radius.coerceAtLeast(0f), offsetX.toPx(), offsetY.toPx(), color.toArgb())
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        // 形状内部剪掉：只留外面那圈阴影
        native.clipPath(path, android.graphics.Region.Op.DIFFERENCE)
        native.drawPath(path, paint)
        native.restoreToCount(checkpoint)
    }
    drawContent()
}

/**
 * 全 App 统一的卡片描边。
 *
 * 设置在「卡片外轮廓」这一层做，深色 / 浅色都取主题的 outline，和卡片底色拉开一档；
 * 调用时把它放在卡片尺寸 / 阴影之后，确保描边画在最终轮廓上。
 */
@Composable
fun Modifier.hubCardBorder(
    shape: Shape,
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
): Modifier = if (!UiPrefs.cardBorders) this else border(width, color, shape)

/**
 * 左侧抽屉（副屏）专用描边：只画上、右、下三边，**不画贴着屏幕左边的竖线**。
 *
 * 2026-10-20 用户要求：深色模式下副屏左边那条描边紧贴屏幕边缘，看起来像多出一条缝，
 * 很影响观感，直接去掉。左边两角本来就是方的，所以除了那根竖线以外轮廓完全一致。
 * 做法和 [hubCardBorder] 一样取主题 outline，只是画之前把左边一条裁掉。
 */
@Composable
fun Modifier.hubSideBorder(
    shape: Shape,
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
): Modifier = if (!UiPrefs.cardBorders) this else drawWithContent {
    drawContent()
    val path = shape.createOutline(size, layoutDirection, this).toAndroidPath()
        ?: return@drawWithContent
    val stroke = width.toPx()
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeWidth = stroke
        this.color = color.toArgb()
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        // 只留 right/top/bottom 的描边：把最左边那一条竖线整个裁掉
        native.clipRect(stroke * 2f, 0f, size.width, size.height)
        native.drawPath(path, paint)
        native.restoreToCount(checkpoint)
    }
}

/** 对话框容器统一描边；和 Material3 AlertDialog 默认的 extraLarge 圆角对齐。 */
@Composable
fun Modifier.hubDialogCard(
    shape: Shape = MaterialTheme.shapes.extraLarge,
): Modifier = hubCardBorder(shape)

/** 把 Compose 的 [Outline] 落成安卓的 [AndroidPath]（只认这几种；别的形状没需求）。 */
private fun Outline.toAndroidPath(): AndroidPath? = when (this) {
    is Outline.Rounded -> AndroidPath().apply {
        val r = roundRect
        addRoundRect(
            android.graphics.RectF(r.left, r.top, r.right, r.bottom),
            floatArrayOf(
                r.topLeftCornerRadius.x, r.topLeftCornerRadius.y,
                r.topRightCornerRadius.x, r.topRightCornerRadius.y,
                r.bottomRightCornerRadius.x, r.bottomRightCornerRadius.y,
                r.bottomLeftCornerRadius.x, r.bottomLeftCornerRadius.y,
            ),
            AndroidPath.Direction.CW,
        )
    }

    // 注意：Compose 里这个子类叫 Outline.**Rectangle**（不是 Outline.Rect，写成 Rect 直接 Unresolved）。
    // 也别写裸的 `Rect(left, top, ...)`：那样同样解析不到。
    is Outline.Rectangle -> AndroidPath().apply {
        val r = this@toAndroidPath.rect
        addRect(
            android.graphics.RectF(r.left, r.top, r.right, r.bottom),
            AndroidPath.Direction.CW,
        )
    }

    is Outline.Generic -> path.asAndroidPath()
    else -> null
}
