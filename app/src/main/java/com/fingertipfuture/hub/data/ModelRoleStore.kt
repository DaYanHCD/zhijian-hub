package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * 「默认模型设置」里除了默认聊天模型之外的那三个**角色模型**（2026-10-16 用户要求：
 * 快速 / 图像 / 压缩模型也要真的能选）。
 *
 * 每个角色存一对「供应商 id + 模型名」；留空就用全局当前那家。
 * 它们什么时候派上用场：
 * - `fast`：生成会话标题、跑其他快速 / 后台任务；
 * - `image`：当前模型不认图时，把图交给它；
 * - `compact`：对话太长时压缩历史。
 *
 * 现在三个角色都已接入：快速模型用于标题和搜索规划，图像模型用于带图片的回答，
 * 压缩模型用于长对话的较早历史摘要。
 */
object ModelRoleStore {

    private const val PREFS = "hub_model_roles"
    private const val KEY_FAST = "fast"
    private const val KEY_IMAGE = "image"
    private const val KEY_COMPACT = "compact"

    /** 一个角色：供应商 id + 模型名（空 = 没设）。 */
    data class Role(val providerId: String = "", val model: String = "") {
        val isSet: Boolean get() = model.isNotBlank()
    }

    var fast: Role by mutableStateOf(Role())
        private set
    var image: Role by mutableStateOf(Role())
        private set
    var compact: Role by mutableStateOf(Role())
        private set

    fun initialize(context: Context) {
        val prefs = prefs(context)
        fast = read(prefs.getString(KEY_FAST, null))
        image = read(prefs.getString(KEY_IMAGE, null))
        compact = read(prefs.getString(KEY_COMPACT, null))
    }

    fun setFast(context: Context, role: Role) = write(context, KEY_FAST, role) { fast = it }
    fun setImage(context: Context, role: Role) = write(context, KEY_IMAGE, role) { image = it }
    fun setCompact(context: Context, role: Role) = write(context, KEY_COMPACT, role) { compact = it }

    /** 界面上显示的名字：只给模型名（2026-10-17 用户要求，右侧不再挤供应商名），没设就是「跟全局」。 */
    fun label(role: Role): String {
        if (!role.isSet) return "跟全局"
        return role.model
    }

    /**
     * 把角色解析成真正可请求的「供应商 + 模型」。
     * 供应商被删除或停用时返回 null，调用方应退回当前聊天模型。
     */
    fun route(role: Role): Pair<HubProvider, String>? {
        if (!role.isSet || role.providerId.isBlank()) return null
        val provider = ProviderStore.providers.firstOrNull {
            it.id == role.providerId && it.enabled
        } ?: return null
        return provider to role.model
    }

    private fun read(raw: String?): Role = raw?.let {
        runCatching { JSONObject(it) }.getOrNull()?.let { json ->
            Role(json.optString("providerId"), json.optString("model"))
        }
    } ?: Role()

    private fun write(context: Context, key: String, role: Role, apply: (Role) -> Unit) {
        apply(role)
        prefs(context).edit().putString(
            key,
            JSONObject().apply {
                put("providerId", role.providerId)
                put("model", role.model)
            }.toString(),
        ).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
