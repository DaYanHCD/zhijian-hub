package com.fingertipfuture.hub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream

/** 头像种类：我 / AI 助手。 */
enum class AvatarKind(val fileName: String) {
    SELF("avatar_me.jpg"),
    ASSISTANT("avatar_assistant.jpg"),
}

/**
 * 本机头像：从相册选一张图，居中裁成正方形、缩到 320×320，存进 App 私有目录。
 *
 * 和「指尖」的思路一致（[com.fingertipfuture.hub.update] 那套没有的服务端部分去掉了）：
 * 只存本机、不上传，换机重新选一次。界面按 [version] 刷新，不用每次重新解码原图。
 *
 * 没有自定义图片时**返回 null**，由界面画默认样式（我是「我」字，助手是机器人图标）。
 */
object AvatarStore {

    private const val TAG = "AvatarStore"

    /** 解码前先把长边压到这个尺寸以内，避免 4000×3000 的原图整个读进内存。 */
    private const val MAX_DECODE_SIDE = 1024

    private const val AVATAR_SIZE = 320
    private const val JPEG_QUALITY = 88

    private var dir: File? = null

    /** 换头像 / 清除头像时 +1，界面据此重新读图。 */
    var version by mutableIntStateOf(0)
        private set

    /**
     * 解码缓存：按 [version] 缓存"我"和"助手"两张图。
     *
     * ⚠️ 不能按"这次请求的是哪一种"做 key：聊天气泡是一行我自己、一行助手交替取图的，
     * 那样每次都会判定未命中，于是**每一行都在主线程重新解码两张 320×320 的图**，
     * 消息一多滚动就会明显卡顿。
     */
    private var cacheVersion = -1
    private var cacheSelf: Bitmap? = null
    private var cacheAssistant: Bitmap? = null

    fun initialize(context: Context) {
        dir = File(context.applicationContext.filesDir, "avatars").apply { mkdirs() }
        version = if (anyExists()) 1 else 0
    }

    private fun anyExists(): Boolean =
        AvatarKind.entries.any { file(it)?.exists() == true }

    /** 头像文件（没设置过时为 null）。 */
    fun file(kind: AvatarKind): File? = dir?.let { File(it, kind.fileName) }

    fun has(kind: AvatarKind): Boolean =
        file(kind)?.let { it.exists() && it.length() > 0L } == true

    /**
     * 读出头像位图；没有设置过或解码失败返回 null。
     * 320×320 的 JPEG 很小，主线程解码没问题。
     */
    fun load(kind: AvatarKind): Bitmap? {
        file(kind)?.takeIf { has(kind) } ?: return null
        if (cacheVersion != version) {
            cacheSelf = decode(file(AvatarKind.SELF))
            cacheAssistant = decode(file(AvatarKind.ASSISTANT))
            cacheVersion = version
        }
        return if (kind == AvatarKind.SELF) cacheSelf else cacheAssistant
    }

    private fun decode(file: File?): Bitmap? {
        val target = file?.takeIf { it.exists() && it.length() > 0L } ?: return null
        return runCatching { BitmapFactory.decodeFile(target.absolutePath) }
            .onFailure { Log.w(TAG, "头像解码失败: ${target.name}", it) }
            .getOrNull()
    }

    /**
     * 保存从相册选的头像。返回是否成功（选到不认识的图片格式时可能是 false）。
     */
    fun save(context: Context, kind: AvatarKind, uri: Uri): Boolean {
        val bitmap = decodeSquare(context, uri) ?: return false
        val target = file(kind) ?: return false
        target.parentFile?.mkdirs()
        return try {
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            bitmap.recycle()
            version += 1
            true
        } catch (t: Throwable) {
            Log.w(TAG, "保存头像失败", t)
            false
        }
    }

    /** 恢复默认头像（删掉本机文件）。 */
    fun clear(kind: AvatarKind) {
        runCatching { file(kind)?.delete() }
        version += 1
    }

    /**
     * 解码 + 居中裁成正方形 + 缩到头像尺寸。我的头像和助手头像共用这一套。
     *
     * 相册里的图片格式五花八门，这里按三条路依次尝试：
     * 1. ImageDecoder + setTargetSize（Android 9+，会自动按 EXIF 把竖拍照片转正）；
     * 2. ImageDecoder 不带尺寸提示（有些相册图片加了尺寸提示会报 Input was incomplete）；
     * 3. BitmapFactory 采样解码兜底（不纠正旋转，但胜在什么都能读）。
     */
    fun decodeSquare(context: Context, uri: Uri): Bitmap? {
        val decoded = decodeWithImageDecoder(context, uri, withTargetSize = true)
            ?: decodeWithImageDecoder(context, uri, withTargetSize = false)
            ?: decodeWithSampling(context, uri)
            ?: return null

        val side = minOf(decoded.width, decoded.height)
        if (side <= 0) {
            decoded.recycle()
            return null
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
        )
        val scaled = Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        if (scaled !== square) square.recycle()
        if (decoded !== square) decoded.recycle()
        return scaled
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri, withTargetSize: Boolean): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (withTargetSize && longest > MAX_DECODE_SIDE) {
                    val scale = MAX_DECODE_SIDE.toDouble() / longest
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "ImageDecoder 解码失败（withTargetSize=$withTargetSize）", it)
        }.getOrNull()
    }

    private fun decodeWithSampling(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}