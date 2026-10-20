package com.fingertipfuture.hub.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * WebDAV 备份的设置与记录。
 *
 * 2026-10-19 用户要求「重构备份界面的服务器列表」后，这里从“只存一台服务器”
 * 改成“服务器列表 + 当前服务器”：可以保存多台 WebDAV，切到哪台就备份到哪台。
 * 备份内容默认是「聊天记录 + 文件」。
 */
object BackupStore {

    private const val PREFS = "hub_backup"
    private const val KEY_SERVERS = "servers_v2"
    private const val KEY_SETTINGS_LEGACY = "webdav"
    private const val KEY_CURRENT_SERVER = "current_server_id"
    private const val KEY_SCOPE = "scope"
    private const val KEY_SCOPE_DEFAULTED = "scope_defaulted_both_v1"
    private const val KEY_LAST_AT = "last_backup_at"
    private const val KEY_LAST_NAME = "last_backup_name"

    const val SCOPE_CHAT = "chat"
    const val SCOPE_FILES = "files"
    const val SCOPE_BOTH = "both"

    data class WebDav(
        val id: String = "",
        val name: String = "",
        val url: String = "",
        val user: String = "",
        val password: String = "",
        val path: String = "",
    ) {
        val configured: Boolean get() = url.isNotBlank() && path.isNotBlank()
        val title: String get() = name.ifBlank { url.ifBlank { "未命名服务器" } }
    }

    var servers: List<WebDav> by mutableStateOf(emptyList())
        private set
    var currentServerId: String by mutableStateOf("")
        private set
    var scope: String by mutableStateOf(SCOPE_BOTH)
        private set
    var lastBackupAt: Long by mutableLongStateOf(0L)
        private set
    var lastBackupName: String by mutableStateOf("")
        private set

    /** 同 ProviderStore：有密码解密失败时，本次进程不再覆盖服务器列表。 */
    private var secretLoadFailed = false

    /** 当前要用的服务器；列表为空时返回空配置。 */
    val webdav: WebDav
        get() = servers.firstOrNull { it.id == currentServerId }
            ?: servers.firstOrNull()
            ?: WebDav()

    fun initialize(context: Context) {
        val prefs = prefs(context)
        SecretStore.resetDecryptFailure()
        secretLoadFailed = false
        val rawServers = prefs.getString(KEY_SERVERS, null)
        val loaded = rawServers?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    fromJson(array.optJSONObject(index) ?: return@mapNotNull null)
                }
            }.getOrNull()
        }
        val configCorrupt = rawServers != null && loaded == null
        val loadedServers = loaded.orEmpty()
        servers = loadedServers.ifEmpty {
            // 从旧版单服务器配置迁移；没有旧配置就是空列表。
            prefs.getString(KEY_SETTINGS_LEGACY, null)?.let { raw ->
                runCatching { fromJson(JSONObject(raw)) }.getOrNull()?.let(::listOf)
            }.orEmpty()
        }
        currentServerId = prefs.getString(KEY_CURRENT_SERVER, null)
            ?.takeIf { id -> servers.any { it.id == id } }
            ?: servers.firstOrNull()?.id.orEmpty()
        if (!prefs.getBoolean(KEY_SCOPE_DEFAULTED, false)) {
            scope = SCOPE_BOTH
            prefs.edit()
                .putString(KEY_SCOPE, SCOPE_BOTH)
                .putBoolean(KEY_SCOPE_DEFAULTED, true)
                .apply()
        } else {
            scope = prefs.getString(KEY_SCOPE, SCOPE_BOTH)
                ?.takeIf { it in setOf(SCOPE_CHAT, SCOPE_FILES, SCOPE_BOTH) }
                ?: SCOPE_BOTH
        }
        lastBackupAt = prefs.getLong(KEY_LAST_AT, 0L)
        lastBackupName = prefs.getString(KEY_LAST_NAME, "").orEmpty()
        secretLoadFailed = SecretStore.lastDecryptFailed
        if (!secretLoadFailed && !configCorrupt) {
            runCatching { persistServers(context) }
        }
    }

    /** 新增一台服务器并立刻设为当前服务器。 */
    fun addServer(context: Context, name: String = "新服务器"): WebDav {
        val server = WebDav(
            id = "dav-" + UUID.randomUUID().toString().take(8),
            name = name,
        )
        servers = servers + server
        currentServerId = server.id
        persistServers(context)
        return server
    }

    /** 保存当前编辑的服务器；传进来的 id 为空时视为新增。 */
    fun save(context: Context, value: WebDav) {
        val normalized = value.copy(
            id = value.id.ifBlank { "dav-" + UUID.randomUUID().toString().take(8) },
            name = value.name.trim().ifBlank { friendlyName(value.url) },
            url = value.url.trim(),
            user = value.user.trim(),
            password = value.password,
            path = value.path.trim().trim('/'),
        )
        val index = servers.indexOfFirst { it.id == normalized.id }
        servers = if (index >= 0) {
            servers.toMutableList().also { it[index] = normalized }
        } else {
            servers + normalized
        }
        currentServerId = normalized.id
        persistServers(context)
    }

    fun removeServer(context: Context, id: String) {
        servers = servers.filterNot { it.id == id }
        if (currentServerId == id) {
            currentServerId = servers.firstOrNull()?.id.orEmpty()
        }
        persistServers(context)
    }

    fun setCurrentServer(context: Context, id: String) {
        if (servers.none { it.id == id }) return
        currentServerId = id
        prefs(context).edit().putString(KEY_CURRENT_SERVER, id).apply()
    }

    fun setScope(context: Context, value: String) {
        scope = value.takeIf { it in setOf(SCOPE_CHAT, SCOPE_FILES, SCOPE_BOTH) } ?: SCOPE_BOTH
        prefs(context).edit().putString(KEY_SCOPE, scope).apply()
    }

    fun markBackedUp(context: Context, at: Long, name: String) {
        lastBackupAt = at
        lastBackupName = name
        prefs(context).edit().putLong(KEY_LAST_AT, at).putString(KEY_LAST_NAME, name).apply()
    }

    private fun persistServers(context: Context) {
        if (secretLoadFailed) {
            Log.e("HubBackupStore", "有 WebDAV 密码解密失败，本次不写回服务器列表，避免空密码覆盖密文")
            return
        }
        runCatching {
            val encoded = JSONArray().apply {
                servers.forEach { put(toJson(it)) }
            }.toString()
            prefs(context).edit()
                .putString(KEY_SERVERS, encoded)
                .putString(KEY_CURRENT_SERVER, currentServerId)
                .apply()
        }.onFailure { Log.e("HubBackupStore", "保存 WebDAV 配置失败", it) }
    }

    private fun toJson(value: WebDav): JSONObject = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("url", value.url)
        put("user", value.user)
        put("password", SecretStore.encrypt(value.password))
        put("path", value.path)
    }

    private fun fromJson(json: JSONObject): WebDav? {
        val url = json.optString("url")
        val path = json.optString("path")
        if (url.isBlank() && path.isBlank()) return null
        return WebDav(
            id = json.optString("id").ifBlank { "dav-" + UUID.randomUUID().toString().take(8) },
            name = json.optString("name").ifBlank { friendlyName(url) },
            url = url,
            user = json.optString("user"),
            password = SecretStore.decrypt(json.optString("password")),
            path = path,
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun friendlyName(url: String): String {
        val host = url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
        return host.ifBlank { "WebDAV" }
    }
}
