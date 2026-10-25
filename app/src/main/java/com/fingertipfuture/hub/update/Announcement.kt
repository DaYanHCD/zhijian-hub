package com.fingertipfuture.hub.update

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 服务器公告数据。 */
internal data class HubAnnouncement(
    val id: String,
    val active: Boolean,
    val title: String,
    val message: String,
    val level: String = "info",
    val url: String = "",
    val publishedAt: String = "",
    val expiresAt: String = "",
) {
    fun isCurrentlyVisible(now: Long = System.currentTimeMillis()): Boolean {
        if (!active || id.isBlank() || title.isBlank() || message.isBlank()) return false
        if (expiresAt.isBlank()) return true
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        return today <= expiresAt.trim().take(10)
    }
}

/** 从服务器读取公告。网络失败时静默跳过，不影响启动和聊天。 */
internal object AnnouncementApi {
    suspend fun fetch(url: String): HubAnnouncement? =
        parseAnnouncement(HttpClient.getString(url))
}

internal fun parseAnnouncement(raw: String): HubAnnouncement? {
    val json = JSONObject(raw)
    val id = json.optString("id").trim()
    if (id.isBlank()) return null
    return HubAnnouncement(
        id = id,
        active = json.optBoolean("active", false),
        title = json.optString("title").trim(),
        message = json.optString("message").trim(),
        level = json.optString("level", "info").trim().ifBlank { "info" },
        url = json.optString("url").trim(),
        publishedAt = json.optString("publishedAt").trim(),
        expiresAt = json.optString("expiresAt").trim(),
    )
}

internal object AnnouncementStore {
    private const val PREFS = "hub_announcement"
    private const val KEY_LAST_SEEN = "last_seen_id"

    fun lastSeen(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SEEN, "")
            .orEmpty()

    fun markSeen(context: Context, id: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN, id)
            .apply()
    }
}
