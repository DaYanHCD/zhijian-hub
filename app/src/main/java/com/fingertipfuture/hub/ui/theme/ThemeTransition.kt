package com.fingertipfuture.hub.ui.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.PathInterpolator
import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot
import kotlin.math.max

/**
 * 主题切换时从指定位置向外揭开新主题。
 *
 * 先截取旧主题画面并盖在新界面之上，再从主题胶囊中心逐渐挖出圆形孔洞。
 * 这样不需要额外的跨平台组件，也不会重建 Activity。
 */
internal fun switchThemeWithReveal(
    context: Context,
    target: HubThemeMode,
    capsuleBounds: Rect,
) {
    if (ThemeStore.mode == target) return
    val activity = context.findActivity()
    val decor = activity?.window?.decorView
    if (activity == null || decor == null || decor.width <= 0 || decor.height <= 0) {
        ThemeStore.set(context, target)
        return
    }
    val center = if (capsuleBounds == Rect.Zero) {
        PointF(decor.width / 2f, decor.height / 2f)
    } else {
        PointF(capsuleBounds.center.x, capsuleBounds.center.y)
    }
    // 从胶囊正中心的一个点开始扩散。
    val startRadius = 0f
    captureWindow(activity.window, decor) { bitmap ->
        if (bitmap == null) {
            ThemeStore.set(activity, target)
        } else {
            startCircularReveal(activity, bitmap, target, center, startRadius)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var cursor: Context = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        cursor = cursor.baseContext
    }
    return cursor as? Activity
}

private fun captureWindow(window: Window, decor: View, onCaptured: (Bitmap?) -> Unit) {
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) {
        onCaptured(null)
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onCaptured(captureView(decor, width, height))
        return
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        window,
        bitmap,
        { result ->
            if (result == PixelCopy.SUCCESS) {
                onCaptured(bitmap)
            } else {
                bitmap.recycle()
                onCaptured(captureView(decor, width, height))
            }
        },
        Handler(Looper.getMainLooper()),
    )
}

private fun captureView(view: View, width: Int, height: Int): Bitmap? = runCatching {
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        view.draw(Canvas(bitmap))
    }
}.getOrNull()

private fun startCircularReveal(
    activity: Activity,
    snapshot: Bitmap,
    target: HubThemeMode,
    center: PointF,
    startRadius: Float,
) {
    val parent = activity.window.decorView as? ViewGroup
    if (parent == null) {
        snapshot.recycle()
        ThemeStore.set(activity, target)
        return
    }
    val mask = ThemeRevealMaskView(activity, snapshot, center, startRadius).apply {
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    parent.addView(
        mask,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 800L
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { animation ->
            mask.progress = animation.animatedValue as Float
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                parent.removeView(mask)
                mask.recycle()
            }
        })
    }
    // 先单独绘制一帧旧主题遮罩，再切换主题并启动扩散，避免按钮区域露出单帧闪烁。
    mask.postDelayed({
        ThemeStore.set(activity, target)
        mask.postDelayed({ animator.start() }, 16L)
    }, 16L)
}

@SuppressLint("ViewConstructor")
private class ThemeRevealMaskView(
    context: Context,
    private val snapshot: Bitmap,
    private val center: PointF,
    private val startRadius: Float,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destRect = RectF()
    private val maskPath = Path()
    private var maxRadius = 1f
    private var revealRadius = 0f

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            revealRadius = maxRadius * field
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        destRect.set(0f, 0f, w.toFloat(), h.toFloat())
        maxRadius = max(
            hypot(center.x, center.y),
            max(
                hypot(w - center.x, center.y),
                max(
                    hypot(center.x, h - center.y),
                    hypot(w - center.x, h - center.y),
                ),
            ),
        )
        revealRadius = startRadius + (maxRadius - startRadius) * progress
    }

    override fun onDraw(canvas: Canvas) {
        if (snapshot.isRecycled) return
        if (revealRadius <= 0f) {
            canvas.drawBitmap(snapshot, null, destRect, paint)
            return
        }
        if (revealRadius >= maxRadius) return

        maskPath.reset()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addCircle(center.x, center.y, revealRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(maskPath)
        canvas.drawBitmap(snapshot, null, destRect, paint)
        canvas.restore()
    }

    fun recycle() {
        if (!snapshot.isRecycled) snapshot.recycle()
    }
}
