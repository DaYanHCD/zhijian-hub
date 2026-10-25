package com.fingertipfuture.hub.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fingertipfuture.hub.data.EmojiExtensionStore
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import kotlinx.coroutines.delay
import java.io.File

private val staticEmojiCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val claimedPlaybackKeys = linkedSetOf<String>()

@Composable
internal fun NotoEmojiGlyph(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    normalSize: Dp = size,
    animateOnce: Boolean = false,
    playbackKey: String? = null,
) {
    val context = LocalContext.current
    val assetName = notoEmojiAssetName(symbol)
    // 这两个状态在组合期间被读取，下载 / 启用 / 删除扩展后会立即触发重绘。
    val extensionReady = EmojiExtensionStore.isInstalled && EmojiExtensionStore.isEnabled
    val hasAnimated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        extensionReady &&
        EmojiExtensionStore.hasAnimatedAsset(context, assetName)
    val claimed = animateOnce &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        hasAnimated &&
        remember(playbackKey) { claimPlayback(playbackKey) }
    var playing by remember(playbackKey, claimed) { mutableStateOf(claimed) }
    var settlingStatic by remember(playbackKey) { mutableStateOf(false) }
    val renderedSize by animateDpAsState(
        // 切换静态图后先保持原尺寸，随后再缩回普通气泡高度。
        targetValue = if (playing || settlingStatic) size else normalSize,
        animationSpec = tween(durationMillis = 180),
        label = "single-emoji-bubble-size",
    )
    LaunchedEffect(settlingStatic) {
        if (settlingStatic) {
            delay(100L)
            settlingStatic = false
        }
    }

    val bitmap = remember(context, assetName) {
        loadStaticBitmap(context, assetName)
    }

    if (hasAnimated && animateOnce) {
        Box(
            modifier = modifier.size(renderedSize),
            contentAlignment = Alignment.Center,
        ) {
            // 同一时刻只画一层：播放时只有动画，播完后只有静态图。
            if (!playing) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = symbol,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                    )
                } else {
                    Text(
                        text = symbol,
                        fontSize = (normalSize.value * 0.82f).sp,
                        lineHeight = (normalSize.value * 0.82f).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (playing) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = symbol
                            val drawable = loadAnimatedDrawable(
                                context = viewContext,
                                assetName = assetName,
                                loop = false,
                                autoPlay = playing,
                                onFinished = {
                                    // 等最后一帧稳定，再硬切到与表情栏一致的静态正脸。
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        playing = false
                                        settlingStatic = true
                                    }, 34L)
                                },
                            )
                            if (drawable == null) {
                                Handler(Looper.getMainLooper()).post { playing = false }
                            } else {
                                setImageDrawable(drawable)
                            }
                        }
                    },
                    update = { view ->
                        view.contentDescription = symbol
                    },
                    onRelease = { view ->
                        (view.drawable as? AnimatedImageDrawable)?.stop()
                        view.setImageDrawable(null)
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                )
            }
        }
        return
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = symbol,
            modifier = modifier.size(normalSize),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    } else {
        Text(
            text = symbol,
            fontSize = (normalSize.value * 0.82f).sp,
            lineHeight = (normalSize.value * 0.82f).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = modifier.size(normalSize),
        )
    }
}

internal fun notoEmojiAssetName(symbol: String): String {
    val points = buildList {
        var index = 0
        while (index < symbol.length) {
            val codePoint = symbol.codePointAt(index)
            if (codePoint != 0xFE0F) add(codePoint.toString(16))
            index += Character.charCount(codePoint)
        }
    }
    return points.joinToString("-")
}

@RequiresApi(Build.VERSION_CODES.P)
private fun loadAnimatedDrawable(
    context: Context,
    assetName: String,
    loop: Boolean,
    autoPlay: Boolean,
    onFinished: () -> Unit,
): Drawable? =
    runCatching {
        val extensionDir = File(
            context.filesDir,
            "extensions/noto-emoji-animated/v1",
        )
        val source = android.graphics.ImageDecoder.createSource(
            File(extensionDir, "$assetName.webp"),
        )
        android.graphics.ImageDecoder.decodeDrawable(source).also { drawable ->
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = if (loop) {
                    AnimatedImageDrawable.REPEAT_INFINITE
                } else {
                    0
                }
                if (autoPlay) {
                    drawable.registerAnimationCallback(
                        object : Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable?) {
                                onFinished()
                            }
                        },
                    )
                    drawable.start()
                }
            }
        }
    }.getOrNull()

@Synchronized
private fun claimPlayback(key: String?): Boolean {
    if (key.isNullOrBlank()) return true
    if (!claimedPlaybackKeys.add(key)) return false
    if (claimedPlaybackKeys.size > 500) {
        claimedPlaybackKeys.remove(claimedPlaybackKeys.first())
    }
    return true
}

private fun loadStaticBitmap(context: Context, assetName: String): Bitmap? {
    staticEmojiCache.get(assetName)?.let { return it }
    return runCatching {
        context.assets.open("noto_emoji_static/$assetName.png").use(BitmapFactory::decodeStream)
    }.getOrNull()?.also { staticEmojiCache.put(assetName, it) }
}

/** 只有去空白后恰好一个完整 emoji 时才返回它。 */
internal fun singleEmojiOrNull(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || dropLastGrapheme(text).isNotEmpty()) return null
    var index = 0
    var hasEmoji = false
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (isEmojiCodePoint(codePoint)) {
            hasEmoji = true
            break
        }
        index += Character.charCount(codePoint)
    }
    return text.takeIf { hasEmoji }
}
