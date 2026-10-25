package com.fingertipfuture.hub.data

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

internal data class HubNotificationSnapshot(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

internal object NotificationSnapshotStore {
    private const val PREFS = "hub_notification_snapshots"
    private const val KEY_ITEMS = "items"

    @Volatile
    private var cached: List<HubNotificationSnapshot>? = null

    fun load(context: Context): List<HubNotificationSnapshot> {
        cached?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null)
            ?: return emptyList()
        val items = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        HubNotificationSnapshot(
                            key = item.optString("key"),
                            packageName = item.optString("packageName"),
                            title = item.optString("title"),
                            text = item.optString("text"),
                            postedAt = item.optLong("postedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
        cached = items
        return items
    }

    fun replace(context: Context, items: List<HubNotificationSnapshot>) {
        val clean = items.distinctBy { it.key }.sortedByDescending { it.postedAt }.take(200)
        cached = clean
        val array = JSONArray().apply {
            clean.forEach { item ->
                put(JSONObject().apply {
                    put("key", item.key)
                    put("packageName", item.packageName)
                    put("title", item.title)
                    put("text", item.text)
                    put("postedAt", item.postedAt)
                })
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

class HubNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        publishActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishActiveNotifications()
    }

    private fun publishActiveNotifications() {
        val items = runCatching {
            activeNotifications.orEmpty().mapNotNull(::toSnapshot)
        }.getOrDefault(emptyList())
        NotificationSnapshotStore.replace(applicationContext, items)
    }

    private fun toSnapshot(sbn: StatusBarNotification): HubNotificationSnapshot? {
        if (sbn.packageName == packageName) return null
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return HubNotificationSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title.take(200),
            text = text.replace('\n', ' ').take(500),
            postedAt = sbn.postTime,
        )
    }
}
