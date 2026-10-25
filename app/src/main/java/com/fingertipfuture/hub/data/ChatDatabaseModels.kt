package com.fingertipfuture.hub.data

/** 一行会话（左边抽屉列表里的一行）。 */
internal data class HubConversation(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
)

/** 一次「搜索聊天」命中：会话标题命中，或某条消息命中。 */
internal data class ChatSearchHit(
    val conversationId: String,
    val conversationTitle: String,
    val role: String,
    val text: String,
    val at: Long,
    val messageIndex: Int,
)

/** 一条消息在库里的一行（= 某个节点的一个版本）。 */
internal data class HubStoredMessage(
    val nodeId: String,
    val versionIndex: Int,
    val sortOrder: Int,
    val role: String,
    val text: String,
    val model: String,
    val reasoning: String,
    val attachments: List<HubAttachment>,
    val at: Long,
    val thoughtMs: Long,
    val skillsUsed: List<HubSkillTrace>,
    val searchTraces: List<HubSearchTrace>,
    val thinkingSteps: List<HubThinkingStep>,
) {
    fun toHubMessage(): HubMessage {
        val localNotice = model == HubMessage.LOCAL_NOTICE_MODEL
        return HubMessage(
            id = "$nodeId-$versionIndex",
            role = role,
            text = text,
            at = at,
            model = if (localNotice) "" else model,
            reasoning = reasoning,
            attachments = attachments,
            thoughtMs = thoughtMs,
            skillsUsed = skillsUsed,
            searchTraces = searchTraces,
            thinkingSteps = thinkingSteps,
            localOnly = localNotice,
        )
    }
}

internal data class HubModelUsage(
    val model: String,
    val messages: Int,
    val usage: HubTokenUsage,
)

internal data class HubFloatingUsage(
    val requests: Int,
    val usage: HubTokenUsage,
)
