package com.fingertipfuture.hub.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条消息里带的附件：图片 / 文件 / 位置。
 *
 * 纯安卓原生：图片存本机文件、位置存经纬度，不依赖任何服务端。
 * 发给模型时：图片走多模态（`image_url` 的 base64），文件和位置拼成一段文字说明
 * （模型读不了任意二进制文件，但至少知道用户发了什么、在哪）。
 */
data class HubAttachment(
    /** image / file / location */
    val kind: String,
    /** 图片和文件在本机的路径（导入时已经把内容拷进 App 私有目录，不怕原文件被删） */
    val path: String = "",
    val name: String = "",
    val size: Long = 0L,
    val mime: String = "",
    /** 位置 */
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val label: String = "",
    /** 语音的时长（毫秒）—— 只有 [KIND_VOICE] 用得到，气泡上那个「5″」就是它。 */
    val durationMs: Long = 0L,
) {
    val isImage: Boolean get() = kind == KIND_IMAGE
    val isFile: Boolean get() = kind == KIND_FILE
    val isLocation: Boolean get() = kind == KIND_LOCATION
    val isVoice: Boolean get() = kind == KIND_VOICE

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("path", path)
        put("name", name)
        put("size", size)
        put("mime", mime)
        put("lat", lat)
        put("lon", lon)
        put("label", label)
        put("durationMs", durationMs)
    }

    /** 发给模型的文字（图片不用它，走多模态）。 */
    fun describe(): String = when (kind) {
        KIND_IMAGE -> "【图片】$name"
        KIND_FILE -> "【文件】$name（${humanSize(size)}）"
        KIND_LOCATION -> "【位置】${positionText()}"
        // 语音：模型听不了音频，而且本机还没有转写。必须明确告诉它“听不到具体内容”，
        // 否则模型会拿一句空消息硬答，甚至编出用户没说过的话。
        KIND_VOICE ->
            "【语音消息】用户发送了一段 ${"%.1f".format(durationMs / 1000.0)} 秒的语音，" +
                "本机没有转写、无法得知具体内容。请如实说明你暂时听不到语音，" +
                "并请用户改用文字重新描述。"
        else -> name
    }

    fun positionText(): String =
        (if (label.isBlank()) "" else "$label ") +
            "纬度 $lat，经度 $lon · 地图：https://maps.google.com/?q=$lat,$lon"

    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_FILE = "file"
        const val KIND_LOCATION = "location"
        const val KIND_VOICE = "voice"

        fun fromJson(json: JSONObject): HubAttachment = HubAttachment(
            kind = json.str("kind"),
            path = json.str("path"),
            name = json.str("name"),
            size = json.optLong("size"),
            mime = json.str("mime"),
            lat = json.optDouble("lat", 0.0),
            lon = json.optDouble("lon", 0.0),
            label = json.str("label"),
            durationMs = json.optLong("durationMs"),
        )

        private fun JSONObject.str(key: String): String =
            if (isNull(key)) "" else optString(key)

        fun listToJson(list: List<HubAttachment>): JSONArray =
            JSONArray().apply { list.forEach { put(it.toJson()) } }

        fun listFromJson(array: JSONArray?): List<HubAttachment> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { fromJson(it) }
            }
        }

        fun humanSize(bytes: Long): String = when {
            bytes <= 0 -> "未知大小"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / 1048576.0)
        }
    }
}
