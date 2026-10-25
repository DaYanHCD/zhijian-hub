package com.fingertipfuture.hub.data

import com.fingertipfuture.hub.data.HubAttachment
import org.json.JSONArray
import org.json.JSONObject

/** 存档里的一段会话（导入时会在本机新建一段，不会并进当前会话）。 */
internal data class ArchivedConversation(
    val title: String,
    val pinned: Boolean,
    val folderId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<HubMessage>,
)

/**
 * 导出 / 导入对话用的存档格式（JSON）。
 *
 * 导出能**再导回来**，所以附件也一起存（图片存路径 —— 文件在本 App 私有目录里，
 * 导回来时按文件名找得到；找不到就只显示文字）。
 */
internal object ChatArchive {

    const val FORMAT = "zhijianhub-chat"
    private const val VERSION = 4

    /**
     * v2：全部会话一起导。
     *
     * v1 的 [encode] 继续保留，旧调用/旧文件仍能读；新代码一律走这里。
     */
    fun encodeArchive(conversations: List<ArchivedConversation>): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("conversations", JSONArray().apply {
            conversations.forEach { conversation ->
                put(JSONObject().apply {
                    put("title", conversation.title)
                    put("pinned", conversation.pinned)
                    if (conversation.folderId == null) put("folderId", JSONObject.NULL)
                    else put("folderId", conversation.folderId)
                    put("createdAt", conversation.createdAt)
                    put("updatedAt", conversation.updatedAt)
                    put("messages", messagesJson(conversation.messages))
                })
            }
        })
    }.toString(2)

    /** 解析 v2；拿到旧 v1 单会话文件时也包成一段。 */
    fun decodeArchive(text: String): List<ArchivedConversation>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT) return null

        val conversations = root.optJSONArray("conversations")
        if (conversations != null) {
            val parsed = (0 until conversations.length()).mapNotNull { index ->
                val item = conversations.optJSONObject(index) ?: return@mapNotNull null
                val messages = parseMessages(item.optJSONArray("messages"))
                if (messages.isEmpty()) return@mapNotNull null
                val firstAt = messages.first().at
                val lastAt = messages.last().at
                ArchivedConversation(
                    title = item.optString("title"),
                    pinned = item.optBoolean("pinned", false),
                    folderId = item.optString("folderId")
                        .takeIf { !item.isNull("folderId") && it.isNotBlank() },
                    createdAt = item.optLong("createdAt").takeIf { it > 0 } ?: firstAt,
                    updatedAt = item.optLong("updatedAt").takeIf { it > 0 } ?: lastAt,
                    messages = messages,
                )
            }
            return parsed.takeIf { it.isNotEmpty() }
        }

        val messages = parseMessages(root.optJSONArray("messages"))
        if (messages.isEmpty()) return null
        val firstAt = messages.first().at
        val lastAt = messages.last().at
        return listOf(
            ArchivedConversation(
                title = messages.firstOrNull { it.role == "user" && it.text.isNotBlank() }
                    ?.text?.replace('\n', ' ')?.take(24).orEmpty(),
                pinned = false,
                folderId = null,
                createdAt = firstAt,
                updatedAt = lastAt,
                messages = messages,
            ),
        )
    }

    fun encode(messages: List<HubMessage>): String {
        val firstAt = messages.firstOrNull()?.at ?: System.currentTimeMillis()
        val lastAt = messages.lastOrNull()?.at ?: firstAt
        val title = messages.firstOrNull { it.role == "user" && it.text.isNotBlank() }
            ?.text?.replace('\n', ' ')?.take(24).orEmpty()
        return encodeArchive(
            listOf(
                ArchivedConversation(
                    title = title,
                    pinned = false,
                    folderId = null,
                    createdAt = firstAt,
                    updatedAt = lastAt,
                    messages = messages,
                ),
            ),
        )
    }

    /** 解析存档；不是本 App 的格式返回 null。 */
    fun decode(text: String): List<HubMessage>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT) return null
        val array = root.optJSONArray("messages") ?: return null
        return parseMessages(array).takeIf { it.isNotEmpty() }
    }

    private fun messagesJson(messages: List<HubMessage>): JSONArray = JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("role", message.role)
                put("at", message.at)
                put("model", message.model)
                put("text", message.text)
                put("reasoning", message.reasoning)
                put("thoughtMs", message.thoughtMs)
                put("skills", skillTracesToJson(message.skillsUsed))
                put("searchTraces", searchTracesToJson(message.searchTraces))
                put("thinkingSteps", thinkingStepsToJson(message.thinkingSteps))
                put("attachments", HubAttachment.listToJson(message.attachments))
                put("localOnly", message.localOnly)
            })
        }
    }

    private fun parseMessages(array: JSONArray?): List<HubMessage> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val messageText = item.optString("text")
            val attachments = HubAttachment.listFromJson(item.optJSONArray("attachments"))
            if (messageText.isBlank() && attachments.isEmpty()) return@mapNotNull null
            HubMessage(
                role = item.optString("role").ifBlank { "user" },
                text = messageText,
                at = item.optLong("at").takeIf { it > 0 } ?: System.currentTimeMillis(),
                model = item.optString("model"),
                reasoning = item.optString("reasoning"),
                attachments = attachments,
                thoughtMs = item.optLong("thoughtMs"),
                skillsUsed = skillTracesFromJson(item.optJSONArray("skills")),
                searchTraces = searchTracesFromJson(item.optJSONArray("searchTraces")),
                thinkingSteps = thinkingStepsFromJson(item.optJSONArray("thinkingSteps")),
                localOnly = item.optBoolean("localOnly", false),
            )
        }
    }
}
