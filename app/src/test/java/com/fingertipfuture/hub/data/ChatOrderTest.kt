package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOrderTest {

    @Test
    fun localOnlyMessageDoesNotConsumePersistedSortOrder() {
        val notice = HubMessage(role = "assistant", text = "提示", at = 1L, localOnly = true)
        val persisted = HubMessage(role = "user", text = "消息", at = 2L)

        assertEquals(ChatStore.LOCAL_ONLY_SORT_ORDER, ChatStore.sortOrderForNewMessage(notice, 0))
        assertEquals(7, ChatStore.sortOrderForNewMessage(persisted, 7))
    }

    @Test
    fun persistentLocalNoticeUsesTheNextDatabaseSortOrder() {
        val notice = HubMessage(
            role = "assistant",
            text = "还没配置供应商",
            at = 1L,
            model = HubMessage.LOCAL_NOTICE_MODEL,
            localOnly = true,
        )

        assertEquals(8, ChatStore.sortOrderForNewMessage(notice, 8))
    }

    @Test
    fun storedLocalNoticeRestoresAsNonModelMessage() {
        val message = HubStoredMessage(
            nodeId = "n-1",
            versionIndex = 0,
            sortOrder = 8,
            role = "assistant",
            text = "还没配置供应商",
            model = HubMessage.LOCAL_NOTICE_MODEL,
            reasoning = "",
            attachments = emptyList(),
            at = 1L,
            thoughtMs = 0L,
            skillsUsed = emptyList(),
            searchTraces = emptyList(),
            thinkingSteps = emptyList(),
        ).toHubMessage()

        assertEquals(true, message.localOnly)
        assertEquals("", message.model)
    }

    @Test
    fun attachmentReusedByEditIsNotScheduledForDeletion() {
        val path = "/data/user/0/com.fingertipfuture.hub.beta/files/attachments/a.jpg"
        val message = HubMessage(
            role = "user",
            text = "看图",
            at = 1L,
            attachments = listOf(HubAttachment(kind = HubAttachment.KIND_IMAGE, path = path)),
        )

        assertEquals(
            emptySet<String>(),
            ChatStore.attachmentPathsToDelete(listOf(message), setOf(path)),
        )
        assertEquals(
            setOf(path),
            ChatStore.attachmentPathsToDelete(listOf(message), emptySet()),
        )
    }
}
