package com.fingertipfuture.hub.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.FolderStore
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubFolder
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.chat.ChatSearchOverlay
import com.fingertipfuture.hub.ui.chat.ConversationActionsSheet
import com.fingertipfuture.hub.ui.chat.MoveToFolderDialog
import com.fingertipfuture.hub.ui.chat.RenameConversationDialog
import com.fingertipfuture.hub.ui.chat.RenameFolderDialog
import com.fingertipfuture.hub.ui.chat.SearchQuickSheet
import com.fingertipfuture.hub.ui.chat.SkillsDialog
import com.fingertipfuture.hub.ui.chat.ThinkingDepthDialog

/** 聊天页所有弹层 / 底部 sheet 的状态。拆出 ChatScreen 的 God 文件。 */
internal class ChatOverlayState {
    var showProfile by mutableStateOf(false)
    var showModelPicker by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var showThinkingDepth by mutableStateOf(false)
    var showSkills by mutableStateOf(false)
    var showSearchSheet by mutableStateOf(false)
    var showChatSearch by mutableStateOf(false)
    var searchAllConversations by mutableStateOf(false)
    var conversationMenu by mutableStateOf<HubConversation?>(null)
    var moveToFolderFor by mutableStateOf<HubConversation?>(null)
    var renameConversationFor by mutableStateOf<HubConversation?>(null)
    var renameFolderFor by mutableStateOf<HubFolder?>(null)
    var deleteFolderFor by mutableStateOf<HubFolder?>(null)
    var deleteConversationConfirm by mutableStateOf<HubConversation?>(null)
    var showAssistantPicker by mutableStateOf(false)
}

@Composable
internal fun ChatScreenOverlays(
    context: Context,
    state: ChatOverlayState,
    onOpenSearchHit: (com.fingertipfuture.hub.data.ChatSearchHit) -> Unit,
    onRegenerateTitle: (HubConversation) -> Unit,
    onDeleteConversation: (HubConversation) -> Unit,
) {
    if (state.showProfile) {
        UserProfileDialog(onDismiss = { state.showProfile = false })
    }
    if (state.showModelPicker) {
        ModelPickerDialog(onDismiss = { state.showModelPicker = false })
    }
    if (state.showAbout) {
        AboutDialog(onDismiss = { state.showAbout = false })
    }
    if (state.showThinkingDepth) {
        ThinkingDepthDialog(
            current = ProviderStore.chatSettings.thinkingDepth,
            onPick = { depth ->
                ProviderStore.saveChatSettings(
                    context,
                    ProviderStore.chatSettings.copy(thinkingDepth = depth),
                )
            },
            onDismiss = { state.showThinkingDepth = false },
        )
    }
    if (state.showSkills) {
        SkillsDialog(
            currentId = ProviderStore.chatSettings.skillId,
            onPick = { id ->
                ProviderStore.saveChatSettings(
                    context,
                    ProviderStore.chatSettings.copy(skillId = id),
                )
            },
            onDismiss = { state.showSkills = false },
        )
    }
    if (state.showSearchSheet) {
        SearchQuickSheet(onDismiss = { state.showSearchSheet = false })
    }
    if (state.showChatSearch) {
        ChatSearchOverlay(
            allConversations = state.searchAllConversations,
            conversationId = if (state.searchAllConversations) {
                null
            } else {
                ChatStore.currentConversationId
            },
            onDismiss = {
                state.showChatSearch = false
                state.searchAllConversations = false
            },
            onOpenHit = onOpenSearchHit,
        )
    }
    state.conversationMenu?.let { conversation ->
        ConversationActionsSheet(
            conversation = conversation,
            onDismiss = { state.conversationMenu = null },
            onTogglePin = {
                ChatStore.setPinned(conversation.id, !conversation.pinned)
                state.conversationMenu = null
                Toast.makeText(
                    context,
                    if (conversation.pinned) "已取消置顶" else "已置顶",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onRename = {
                state.conversationMenu = null
                state.renameConversationFor = conversation
            },
            onRegenerateTitle = {
                state.conversationMenu = null
                onRegenerateTitle(conversation)
            },
            onMoveToFolder = {
                state.conversationMenu = null
                state.moveToFolderFor = conversation
            },
            onDelete = {
                state.conversationMenu = null
                state.deleteConversationConfirm = conversation
            },
        )
    }
    state.moveToFolderFor?.let { conversation ->
        MoveToFolderDialog(
            conversation = conversation,
            folders = FolderStore.folders,
            onDismiss = { state.moveToFolderFor = null },
            onPick = { folderId ->
                ChatStore.moveToFolder(conversation.id, folderId)
                state.moveToFolderFor = null
                Toast.makeText(
                    context,
                    if (folderId == null) "已移出文件夹" else "已移动到文件夹",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onCreateAndPick = { name ->
                val folder = FolderStore.create(context, name)
                if (folder == null) {
                    Toast.makeText(context, "文件夹名字不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    ChatStore.moveToFolder(conversation.id, folder.id)
                    state.moveToFolderFor = null
                    Toast.makeText(context, "已移动到「" + folder.name + "」", Toast.LENGTH_SHORT).show()
                }
            },
            onRenameFolder = { folder ->
                state.moveToFolderFor = null
                state.renameFolderFor = folder
            },
            onDeleteFolder = { folder ->
                state.moveToFolderFor = null
                state.deleteFolderFor = folder
            },
        )
    }
    state.renameConversationFor?.let { conversation ->
        RenameConversationDialog(
            conversation = conversation,
            onDismiss = { state.renameConversationFor = null },
            onConfirm = { title ->
                ChatStore.renameConversation(conversation.id, title)
                state.renameConversationFor = null
                Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
            },
        )
    }
    state.renameFolderFor?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { state.renameFolderFor = null },
            onConfirm = { name ->
                FolderStore.rename(context, folder.id, name)
                state.renameFolderFor = null
                Toast.makeText(context, "文件夹已重命名", Toast.LENGTH_SHORT).show()
            },
        )
    }
    state.deleteFolderFor?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            onDismiss = { state.deleteFolderFor = null },
            onConfirm = {
                FolderStore.delete(context, folder.id)
                ChatStore.clearFolder(folder.id)
                state.deleteFolderFor = null
                Toast.makeText(context, "文件夹已删除", Toast.LENGTH_SHORT).show()
            },
        )
    }
    state.deleteConversationConfirm?.let { conversation ->
        DeleteConversationDialog(
            conversation = conversation,
            onDismiss = { state.deleteConversationConfirm = null },
            onConfirm = {
                state.deleteConversationConfirm = null
                onDeleteConversation(conversation)
            },
        )
    }
    if (state.showAssistantPicker) {
        AssistantPickerSheet(
            onDismiss = { state.showAssistantPicker = false },
            onPicked = { name ->
                Toast.makeText(context, "已切换到助手「" + name + "」", Toast.LENGTH_SHORT).show()
            },
        )
    }
}
