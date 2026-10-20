package com.fingertipfuture.hub.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * 录音 —— 纯安卓原生 `MediaRecorder`（不引任何第三方库，铁律）。
 *
 * - 落盘：App 私有目录 `files/voice/voice-<时间戳>.m4a`（MPEG_4 容器 + AAC，等于微信那种语音消息）；
 * - 时长自己量（[SystemClock.elapsedRealtime]）：MediaRecorder 只在 `stop()` 之后才给时长，太晚，
 *   而气泡上那个「5″」要在录的时候就能拿到；
 * - 一次只录一条：新的 [start] 会先把上一条掐掉（[cancel]）；
 * - 太短（< 0.5 秒）或根本没录上：`stop()` 返回 null，界面提示一句就行。
 */
object VoiceRecorder {

    /** 单条语音的时长上限（超了界面自己会停，这里留个常量给将来用）。 */
    const val MAX_DURATION_MS = 60_000L

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** 已经开始录了多少毫秒（没在录就是 0）。 */
    fun elapsedMs(): Long =
        if (recorder == null) 0L else (SystemClock.elapsedRealtime() - startedAt)

    /** 开始录。麦克风被占 / 没权限 / 设备不支持 → 返回 false（界面提示，不崩）。 */
    fun start(context: Context): Boolean {
        cancel()
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val out = File(dir, "voice-" + System.currentTimeMillis() + ".m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44_100)
            mr.setAudioEncodingBitRate(96_000)
            mr.setOutputFile(out.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            file = out
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (t: Throwable) {
            runCatching { mr.release() }
            out.delete()
            false
        }
    }

    /** 正常结束：返回（文件, 时长ms）；没在录 / 太短 / 空文件 → null（文件删掉）。 */
    fun stop(): Pair<File, Long>? {
        val mr = recorder ?: return null
        val out = file
        val ms = elapsedMs()
        recorder = null
        file = null
        startedAt = 0L
        // 录得太短时 MediaRecorder.stop() 会抛 RuntimeException —— 当成"没录上"处理
        val ok = try {
            mr.stop()
            true
        } catch (t: Throwable) {
            false
        }
        runCatching { mr.release() }
        if (!ok || out == null || ms < 500L || !out.isFile || out.length() <= 0L) {
            out?.delete()
            return null
        }
        return out to ms.coerceAtMost(MAX_DURATION_MS)
    }

    /** 取消：停掉并把文件删了（没发出去就不该留东西在盘上）。 */
    fun cancel() {
        val mr = recorder
        val out = file
        recorder = null
        file = null
        startedAt = 0L
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        out?.delete()
    }
}