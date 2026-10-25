package com.fingertipfuture.hub.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.AssistantReminderScheduler
import com.fingertipfuture.hub.data.AssistantReminderStore
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.VoiceRecorder
import com.fingertipfuture.hub.data.plus
import com.fingertipfuture.hub.ui.chat.ChatRuntime
import com.fingertipfuture.hub.ui.chat.ConversationTitleGenerator
import com.fingertipfuture.hub.ui.chat.VoiceAction
import com.fingertipfuture.hub.ui.chat.runChatRequest
import com.fingertipfuture.hub.ui.chat.singleEmojiOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天页的状态与主链路动作。
 *
 * ChatScreen 只负责布局和事件转发；发送、流式请求、停止、重发、
 * 语音、标题生成、会话删除和搜索定位都在这里。状态使用 Compose
 * `mutableStateOf`，因此不引入 ViewModel 依赖，也不改变原有单进程模型。
 */
internal class ChatScreenState(
    private val context: Context,
    private val scope: CoroutineScope,
    initialInput: String = "",
) {

    val overlay = ChatOverlayState()

    var input by mutableStateOf(initialInput)
    var scrollToBottomRequest by mutableIntStateOf(0)
    var pendingJumpMessageIndex by mutableStateOf<Int?>(null)
    var fullScreen by mutableStateOf(false)
    var fullScreenKeyboardVisible by mutableStateOf(false)
    var moreSheetIndex by mutableStateOf<Int?>(null)
    var voiceMode by mutableStateOf(false)
    var voiceTimedOut by mutableStateOf(false)
    var voiceTimerJob by mutableStateOf<Job?>(null)
    /** 已提交发送，但请求状态尚未接管的这一小段也继续显示停止键，避免闪回添加键。 */
    var submissionPending by mutableStateOf(false)
        private set
    var editingIndex by mutableStateOf<Int?>(null)
    var editingAttachments by mutableStateOf<List<HubAttachment>>(emptyList())
    val pendingReminderIntent: AssistantReminderIntent?
        get() = ChatRuntime.pendingReminderIntentState.value
    val pendingAutoMemoryText: String?
        get() = ChatRuntime.pendingAutoMemoryTextState.value
    val pendingDeviceAction
        get() = ChatRuntime.pendingDeviceActionState.value

    val messages: List<HubMessage> get() = ChatStore.messages
    val assistant get() = AssistantStore.current

    val activeProvider: HubProvider?
        get() {
            val assistantProvider = assistant.providerId
                .takeIf { it.isNotBlank() && assistant.model.isNotBlank() }
                ?.let { id -> ProviderStore.providers.firstOrNull { it.id == id && it.enabled } }
            return assistantProvider ?: ProviderStore.currentProvider
        }

    val activeModel: String
        get() {
            val assistantProvider = assistant.providerId
                .takeIf { it.isNotBlank() && assistant.model.isNotBlank() }
                ?.let { id -> ProviderStore.providers.firstOrNull { it.id == id && it.enabled } }
            return if (assistantProvider != null) assistant.model else ProviderStore.currentModel
        }

    private val stoppedLabel = context.getString(R.string.chat_stopped)
    private val emptyReplyLabel = context.getString(R.string.chat_empty_reply)
    private val alreadyNewLabel = context.getString(R.string.chat_already_new)
    private val busyLabel = context.getString(R.string.chat_busy_hint)

    private fun toast(text: String, long: Boolean = false) {
        context.showLocalizedToast(text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
    }

    fun notice(text: String, persist: Boolean = false) {
        scrollToBottomRequest++
        ChatStore.append(
            HubMessage(
                role = "assistant",
                text = text,
                at = System.currentTimeMillis(),
                model = if (persist) HubMessage.LOCAL_NOTICE_MODEL else "",
                // 本地提醒默认只在当前页面显示；未配置供应商的助手回复需要持久化。
                localOnly = true,
            ),
            refreshList = false,
        )
    }

    fun stopStreaming() {
        submissionPending = false
        if (ChatRuntime.streamingState.value) {
            ChatRuntime.cancelRequestedState.value = true
            ChatRuntime.activeStreamHandleState.value?.cancel()
            ChatStore.updateLast { message ->
                if (!message.streaming) {
                    message
                } else {
                    message.copy(
                        text = message.text.ifBlank { stoppedLabel },
                        streaming = false,
                    )
                }
            }
        }
        ChatRuntime.streamTokenState.intValue += 1
        ChatRuntime.streamingState.value = false
        ChatRuntime.cancelPendingApprovals()
    }

    fun startNewChat() {
        if (messages.isEmpty()) {
            toast(alreadyNewLabel)
            return
        }
        stopStreaming()
        if (ChatStore.newConversation().isBlank()) {
            toast("新建对话失败：存储空间或数据库异常", long = true)
            return
        }
        input = ""
        editingIndex = null
        editingAttachments = emptyList()
        scrollToBottomRequest++
    }

    fun regenerateConversationTitle(conversation: HubConversation) {
        if (ChatRuntime.streamingState.value) {
            toast("正在生成回复，稍后再生成标题")
            return
        }
        val fastRoute = ModelRoleStore.route(ModelRoleStore.fast)
        val provider = fastRoute?.first ?: activeProvider
        val model = fastRoute?.second ?: activeModel
        if (provider == null || model.isBlank()) {
            toast("先在「提供商」里启用一家并选好模型")
            return
        }
        ConversationTitleGenerator.generate(
            context = context,
            conversationId = conversation.id,
            provider = provider,
            model = model,
            announce = true,
        )
    }

    fun deleteConversation(conversation: HubConversation) {
        val wasCurrent = conversation.id == ChatStore.currentConversationId
        if (wasCurrent) {
            stopStreaming()
            input = ""
            editingIndex = null
            editingAttachments = emptyList()
        }
        pendingJumpMessageIndex = null
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                ChatStore.deleteConversation(conversation.id)
            }
            if (!deleted) {
                toast("删除会话失败：存储空间或数据库异常", long = true)
                return@launch
            }
            if (wasCurrent) scrollToBottomRequest++
            toast("已删除会话")
        }
    }

    fun openSearchHit(hit: ChatSearchHit) {
        overlay.showChatSearch = false
        overlay.searchAllConversations = false
        val sameConversation = hit.conversationId == ChatStore.currentConversationId
        if (!sameConversation) {
            stopStreaming()
            input = ""
            editingIndex = null
            editingAttachments = emptyList()
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (!sameConversation) ChatStore.switchTo(hit.conversationId)
                if (hit.messageIndex >= 0) ChatStore.loadAllForCurrent()
            }
            pendingJumpMessageIndex = if (hit.messageIndex < 0) {
                null
            } else {
                var persistedIndex = -1
                ChatStore.messages.indexOfFirst { message ->
                    if (message.localOnly) return@indexOfFirst false
                    persistedIndex++
                    persistedIndex == hit.messageIndex
                }.takeIf { it >= 0 }
            }
            if (pendingJumpMessageIndex == null) scrollToBottomRequest++
        }
    }

    fun deleteMoreSheetMessage() {
        val index = moreSheetIndex ?: return
        moreSheetIndex = null
        val message = ChatStore.messages.getOrNull(index) ?: return
        if (message.streaming) stopStreaming()
        scope.launch {
            val removed = withContext(Dispatchers.IO) { ChatStore.removeAt(index) }
            if (!removed) {
                toast("删除消息失败：存储空间或数据库异常", long = true)
                return@launch
            }
            editingIndex?.let { editing ->
                when {
                    editing == index -> {
                        editingIndex = null
                        editingAttachments = emptyList()
                    }
                    editing > index -> editingIndex = editing - 1
                }
            }
        }
    }

    fun sendVoiceMessage() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        val recorded = VoiceRecorder.stop()
        if (recorded == null) {
            toast("太短了，没录上")
            return
        }
        val (file, ms) = recorded
        val draft = input
        val attachment = HubAttachment(
            kind = HubAttachment.KIND_VOICE,
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            mime = "audio/mp4",
            durationMs = ms,
        )
        if (ConversationTitleGenerator.hasManualRequest) {
            AttachmentStore.deleteOwnedAttachment(context, file.absolutePath)
            toast("正在生成标题，稍后再发")
            return
        }
        if (ChatRuntime.streamingState.value) {
            AttachmentStore.deleteOwnedAttachment(context, file.absolutePath)
            toast(busyLabel)
            return
        }
        if (!commitEditingBeforeNewMessage()) {
            AttachmentStore.deleteOwnedAttachment(context, file.absolutePath)
            return
        }
        input = ""
        val at = System.currentTimeMillis()
        scrollToBottomRequest++
        val saved = ChatStore.append(
            HubMessage(role = "user", text = "", at = at, attachments = listOf(attachment)),
        )
        if (!saved) {
            input = draft
            toast("保存消息失败：存储空间或数据库异常", long = true)
            return
        }
        val replied = ChatStore.append(
            HubMessage(
                role = "assistant",
                text = "抱歉，助手的语音识别功能暂时没有开发，请等待开发者更新！",
                at = System.currentTimeMillis(),
                model = activeModel,
            ),
        )
        if (!replied) {
            toast("保存回复失败：存储空间或数据库异常", long = true)
        }
        input = draft
    }

    fun startVoice() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        if (!VoiceRecorder.start(context)) {
            toast("麦克风打不开（被别的应用占着？），这次没录上")
            return
        }
        voiceTimedOut = false
        voiceTimerJob = scope.launch {
            delay(VoiceRecorder.MAX_DURATION_MS)
            if (VoiceRecorder.isRecording) {
                voiceTimedOut = true
                voiceTimerJob = null
                sendVoiceMessage()
            }
        }
    }

    fun endVoice(action: VoiceAction) {
        if (voiceTimedOut) {
            voiceTimedOut = false
            return
        }
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        when (action) {
            VoiceAction.Cancel -> VoiceRecorder.cancel()
            VoiceAction.Send -> sendVoiceMessage()
            VoiceAction.ToText -> {
                val recorded = VoiceRecorder.stop()
                if (recorded == null) {
                    toast("太短了，没录上")
                    return
                }
                recorded.first.delete()
                toast("当前暂不支持转文字，请等待开发者更新", long = true)
            }
        }
    }

    fun discardUnsentAttachments(items: List<HubAttachment>) {
        items.asSequence()
            .map { it.path }
            .filter { it.isNotBlank() && !ChatStore.isAttachmentReferenced(it) }
            .distinct()
            .forEach { AttachmentStore.deleteOwnedAttachment(context, it) }
    }

    fun confirmReminderSave(intent: AssistantReminderIntent.Save): AssistantReminder {
        val existing = intent.existing
        val reminder = AssistantReminder(
            id = existing?.id ?: AssistantReminderStore.newId(),
            assistantId = assistant.id,
            title = intent.title.trim(),
            prompt = intent.prompt.trim(),
            hour = intent.hour.coerceIn(0, 23),
            minute = intent.minute.coerceIn(0, 59),
            repeat = intent.repeat,
            enabled = true,
            lastRunAt = existing?.lastRunAt ?: 0,
            lastResult = existing?.lastResult.orEmpty(),
            conversationId = existing?.conversationId.orEmpty()
                .ifBlank { ChatStore.currentConversationId.orEmpty() },
            scheduledDate = intent.scheduledDate.ifBlank { existing?.scheduledDate.orEmpty() },
            weekday = intent.weekday,
            weekdays = listOf(intent.weekday),
            displayLocation = intent.displayLocation,
        )
        AssistantReminderStore.upsert(context, reminder)
        AssistantReminderScheduler.schedule(context, reminder)
        ChatRuntime.completeReminderApproval(reminder)
        return reminder
    }

    fun confirmReminderClose(intent: AssistantReminderIntent.Close): AssistantReminder {
        val reminder = intent.reminder.copy(enabled = false)
        AssistantReminderStore.upsert(context, reminder)
        AssistantReminderScheduler.cancel(context, reminder.id)
        ChatRuntime.completeReminderApproval(reminder)
        return reminder
    }

    fun confirmReminderDelete(intent: AssistantReminderIntent.Delete): AssistantReminder {
        val reminder = intent.reminder
        AssistantReminderScheduler.cancel(context, reminder.id)
        AssistantReminderStore.remove(context, reminder.id)
        ChatRuntime.completeReminderApproval(reminder)
        return reminder
    }

    fun dismissReminderIntent() {
        if (pendingReminderIntent == null) return
        ChatRuntime.completeReminderApproval(null)
    }

    fun approveAutoMemory() {
        ChatRuntime.completeAutoMemoryApproval(true)
    }

    fun rejectAutoMemory() {
        ChatRuntime.completeAutoMemoryApproval(false)
    }

    fun approveDeviceAction() {
        ChatRuntime.completeDeviceActionApproval(true)
    }

    fun rejectDeviceAction() {
        ChatRuntime.completeDeviceActionApproval(false)
    }

    private fun commitEditingBeforeNewMessage(): Boolean {
        val index = editingIndex ?: return true
        val preserve = editingAttachments
            .map { it.path }
            .filter { it.isNotBlank() }
            .toSet()
        if (!ChatStore.truncateFrom(index, preserve)) {
            toast("编辑失败：存储空间或数据库异常", long = true)
            return false
        }
        editingIndex = null
        editingAttachments = emptyList()
        return true
    }

    fun send(
        text: String = input.trim(),
        attachments: List<HubAttachment> = emptyList(),
    ) {
        if (text.isEmpty() && attachments.isEmpty()) return
        if (ConversationTitleGenerator.hasManualRequest) {
            discardUnsentAttachments(attachments)
            toast("正在生成标题，稍后再发")
            return
        }
        if (ChatRuntime.streamingState.value) {
            discardUnsentAttachments(attachments)
            toast(busyLabel)
            return
        }

        val provider = activeProvider
        val model = activeModel
        val ready = provider != null && provider.baseUrl.isNotBlank() && model.isNotBlank()
        if (!commitEditingBeforeNewMessage()) return
        submissionPending = true
        input = ""
        val at = System.currentTimeMillis()
        scrollToBottomRequest++
        val saved = ChatStore.append(
            HubMessage(
                role = "user",
                text = text,
                at = at,
                attachments = attachments,
                playEmojiOnce = attachments.isEmpty() && singleEmojiOrNull(text) != null,
            ),
        )
        if (!saved) {
            submissionPending = false
            if (input.isBlank()) input = text
            toast("保存消息失败：存储空间或数据库异常", long = true)
            return
        }
        if (!ready) {
            submissionPending = false
            if (attachments.isEmpty()) {
                notice(context.getString(R.string.chat_need_provider), persist = true)
            } else {
                val names = attachments.joinToString("、") { it.name.ifBlank { it.kind } }
                notice(
                    "附件（$names）已经发出来了，但还没有可用的供应商 —— 先去「提供商」里开一家并填好 Key，再点重发。",
                    persist = true,
                )
            }
            return
        }
        startStreaming(requireNotNull(provider), model)
        if (ChatStore.messages.count { !it.localOnly && it.text.isNotBlank() } == 1) {
            ChatStore.currentConversationId?.let { conversationId ->
                val fastRoute = ModelRoleStore.route(ModelRoleStore.fast)
                ConversationTitleGenerator.generate(
                    context = context,
                    conversationId = conversationId,
                    provider = fastRoute?.first ?: requireNotNull(provider),
                    model = fastRoute?.second ?: model,
                    announce = false,
                )
            }
        }
    }

    fun resend(message: HubMessage) {
        if (ConversationTitleGenerator.hasManualRequest) {
            toast("正在生成标题，稍后再试")
            return
        }
        val index = ChatStore.messages.indexOf(message)
        if (index < 0) return
        stopStreaming()
        val preserve = message.attachments.map { it.path }.filter { it.isNotBlank() }.toSet()
        if (!ChatStore.truncateFrom(index, preserve)) {
            toast("重发失败：存储空间或数据库异常", long = true)
            return
        }
        send(text = message.text, attachments = message.attachments)
    }

    fun regenerate(message: HubMessage) {
        if (ConversationTitleGenerator.hasManualRequest) {
            toast("正在生成标题，稍后再试")
            return
        }
        val index = ChatStore.messages.indexOf(message)
        if (index < 0) return
        stopStreaming()
        val provider = activeProvider
        val model = activeModel
        if (!ChatStore.truncateFrom(index)) {
            toast("重新生成失败：存储空间或数据库异常", long = true)
            return
        }
        if (provider == null || provider.baseUrl.isBlank() || model.isBlank()) {
            notice(context.getString(R.string.chat_need_provider), persist = true)
            return
        }
        scrollToBottomRequest++
        startStreaming(provider, model)
    }

    fun startStreaming(
        provider: HubProvider,
        model: String,
        initialUsage: HubTokenUsage = HubTokenUsage(),
    ) {
        val token = ChatRuntime.streamTokenState.intValue + 1
        ChatRuntime.streamTokenState.intValue = token
        ChatRuntime.cancelPendingApprovals()
        val latestUser = ChatStore.messages.lastOrNull { it.role == "user" }
        val imageRoute = if (latestUser?.attachments?.any { it.isImage } == true) {
            ModelRoleStore.route(ModelRoleStore.image)
        } else {
            null
        }
        val answerProvider = imageRoute?.first ?: provider
        val answerModel = imageRoute?.second ?: model
        val placeholderSaved = ChatStore.append(
            HubMessage(
                role = "assistant",
                text = "",
                at = System.currentTimeMillis(),
                model = answerModel,
                streaming = true,
            ),
            refreshList = false,
        )
        if (!placeholderSaved) {
            submissionPending = false
            ChatRuntime.streamingState.value = false
            toast("保存回复失败：存储空间或数据库异常", long = true)
            return
        }
        val requestConversationId = ChatStore.currentConversationId
            ?: run {
                submissionPending = false
                ChatRuntime.streamingState.value = false
                return
            }
        scrollToBottomRequest++
        ChatRuntime.streamingState.value = true
        submissionPending = false
        ChatRuntime.cancelRequestedState.value = false
        val handle = AiClient.newStreamHandle()
        ChatRuntime.activeStreamHandleState.value = handle
        val appContext = context.applicationContext
        ChatRuntime.scope.launch {
            try {
                val usage = runChatRequest(
                    provider = answerProvider,
                    model = answerModel,
                    assistant = assistant,
                    conversationId = requestConversationId,
                    appContext = appContext,
                    handle = handle,
                    stoppedLabel = stoppedLabel,
                    emptyReplyLabel = emptyReplyLabel,
                    isCurrent = { token == ChatRuntime.streamTokenState.intValue },
                    cancelRequested = { ChatRuntime.cancelRequestedState.value },
                    confirmReminder = { intent -> ChatRuntime.awaitReminderApproval(intent) },
                    confirmMemory = { text -> ChatRuntime.awaitAutoMemoryApproval(text) },
                    confirmDeviceAction = { intent -> ChatRuntime.awaitDeviceActionApproval(intent) },
                )
                if (token == ChatRuntime.streamTokenState.intValue) {
                    ChatRuntime.streamingState.value = false
                    ChatStore.recordUsage(usage + initialUsage)
                }
            } finally {
                if (ChatRuntime.activeStreamHandleState.value === handle) {
                    ChatRuntime.activeStreamHandleState.value = null
                }
            }
        }
    }
}
