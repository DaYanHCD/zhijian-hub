package com.fingertipfuture.hub.floatingocr

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可下载组件里的 OCR 入口。由主 App 通过同签名权限绑定，只处理主 App 分享的截图 URI。
 */
class OcrBridgeService : Service() {

    companion object {
        const val MSG_RECOGNIZE = 1
        const val MSG_RESULT = 2
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_TEXT = "text"
        const val KEY_LINES = "lines"
        const val KEY_ERROR = "error"
    }

    private val thread = HandlerThread("floating-ocr-bridge").apply { start() }
    private val messenger = Messenger(Handler(thread.looper) { message ->
        when (message.what) {
            MSG_RECOGNIZE -> recognize(message)
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    private fun recognize(message: Message) {
        val reply = message.replyTo
        val uri = message.data.getString(KEY_IMAGE_URI)?.let(Uri::parse)
        if (reply == null || uri == null) return
        val bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return reply(reply, error = "无法读取截图")

        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = JSONArray()
                result.textBlocks.flatMap { it.lines }.forEach { line ->
                    val box = line.boundingBox ?: return@forEach
                    lines.put(JSONObject().apply {
                        put("text", line.text.trim())
                        put("left", box.left)
                        put("top", box.top)
                        put("right", box.right)
                        put("bottom", box.bottom)
                    })
                }
                reply(reply, text = result.text.trim(), lines = lines.toString())
                bitmap.recycle()
            }
            .addOnFailureListener {
                reply(reply, error = it.message.orEmpty().ifBlank { "OCR 失败" })
                bitmap.recycle()
            }
            .addOnCompleteListener { recognizer.close() }
    }

    private fun reply(
        target: Messenger,
        text: String = "",
        lines: String = "[]",
        error: String = "",
    ) {
        val data = Bundle().apply {
            putString(KEY_TEXT, text)
            putString(KEY_LINES, lines)
            putString(KEY_ERROR, error)
        }
        runCatching { target.send(Message.obtain(null, MSG_RESULT).apply { this.data = data }) }
    }
}
