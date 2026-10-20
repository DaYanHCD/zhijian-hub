package com.fingertipfuture.hub.data

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 语音播放 —— 纯安卓原生 `MediaPlayer`。
 *
 * 全 App 只留**一个**播放器：点第二条的时候第一条自动停（微信也是这个行为）。
 * [playingPath] 是 Compose 状态：哪条语音正在响，那个气泡就换成"正在播放"的样子（再点一下 = 停）。
 */
object VoicePlayer {

    /** 正在播放的文件路径；null = 没在放。 */
    var playingPath: String? by mutableStateOf(null)
        private set

    private var player: MediaPlayer? = null

    /** 点同一条 = 停；点另一条 = 换成它从头放。 */
    fun toggle(path: String) {
        if (playingPath == path) {
            stop()
            return
        }
        stop()
        val file = File(path)
        if (!file.isFile) return
        player = try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            null
        }
        playingPath = if (player != null) path else null
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playingPath = null
    }
}