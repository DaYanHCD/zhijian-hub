package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 会话文件夹（只有名字；会话表里的 `folder_id` 指向它的 id）。 */
internal data class HubFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

/**
 * 文件夹列表存本机 SharedPreferences（`hub_ui`，JSON）。
 *
 * 会话本身仍在 SQLite 里，只有 `folder_id` 这一点关联；文件夹的数量很小，
 * 不值得再开一张表，也避免为它改数据库版本。
 */
internal object FolderStore {

    private const val PREFS = "hub_ui"
    private const val KEY_FOLDERS = "chat_folders"

    var folders: List<HubFolder> by mutableStateOf(emptyList())
        private set

    fun initialize(context: Context) {
        val raw = prefs(context).getString(KEY_FOLDERS, null) ?: return
        folders = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    add(HubFolder(id, name, item.optLong("createdAt")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun byId(id: String?): HubFolder? =
        id?.let { folderId -> folders.firstOrNull { it.id == folderId } }

    /** 新建一个文件夹；名字重复时直接返回已有的那个。 */
    fun create(context: Context, name: String): HubFolder? {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return null
        folders.firstOrNull { it.name == clean }?.let { return it }
        val folder = HubFolder(
            id = "f-" + UUID.randomUUID(),
            name = clean,
            createdAt = System.currentTimeMillis(),
        )
        folders = folders + folder
        persist(context)
        return folder
    }

    fun rename(context: Context, id: String, name: String): Boolean {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return false
        if (folders.none { it.id == id }) return false
        folders = folders.map { if (it.id == id) it.copy(name = clean) else it }
        persist(context)
        return true
    }

    fun delete(context: Context, id: String) {
        if (folders.none { it.id == id }) return
        folders = folders.filterNot { it.id == id }
        persist(context)
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("createdAt", folder.createdAt)
            })
        }
        prefs(context).edit().putString(KEY_FOLDERS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
