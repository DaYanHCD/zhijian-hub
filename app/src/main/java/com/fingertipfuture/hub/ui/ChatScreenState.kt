package com.fingertipfuture.hub.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubChatSettings
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.VoiceRecorder
import com.fingertipfuture.hub.data.isFromUser
import com.fingertipfuture.hub.ui.chat.ChatRuntime
import com.fingertipfuture.hub.ui.chat.VoiceAction
import com.fingertipfuture.hub.ui.chat.runChatRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

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
    var titleStreamHandle by mutableStateOf<AiClient.StreamHandle?>(null)
    var titleBusyId by mutableStateOf<String?>(null)
    var moreSheetIndex by mutableStateOf<Int?>(null)
    var voiceMode by mutableStateOf(false)
    var voiceTimedOut by mutableStateOf(false)
    var voiceTimerJob by mutableStateOf<Job?>(null)
    var editingIndex by mutableStateOf<Int?>(null)
    var editingAttachments by mutableStateOf<List<HubAttachment>>(emptyList())

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
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun notice(text: String) {
        scrollToBottomRequest++
        ChatStore.append(
            HubMessage(
                role = "assistant",
                text = text,
                at = System.currentTimeMillis(),
                localOnly = true,
            ),
            refreshList = false,
        )
    }

    fun stopStreaming() {
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
        if (titleBusyId != null) {
            toast("正在生成另一个标题…")
            return
        }
        val provider = activeProvider
        val model = activeModel
        if (provider == null || model.isBlank()) {
            toast("先在「提供商」里启用一家并选好模型")
            return
        }
        val fastRoute = ModelRoleStore.route(ModelRoleStore.fast)
        val titleProvider = fastRoute?.first ?: provider
        val titleModel = fastRoute?.second ?: model
        titleBusyId = conversation.id
        toast("正在生成标题…")
        scope.launch {
            val history = runCatching {
                withContext(Dispatchers.IO) {
                    ChatStore.messagesOf(conversation.id)
                        .filterNot { it.localOnly }
                        .filter { it.text.isNotBlank() }
                }
            }.getOrDefault(emptyList())
            if (history.isEmpty()) {
                titleBusyId = null
                toast("空会话不能生成标题")
                return@launch
            }
            val handle = AiClient.newStreamHandle()
            titleStreamHandle = handle
            val transcript = history.take(6).joinToString("\n\n") { message ->
                val role = if (message.isFromUser) "我" else "AI"
                role + "：" + message.text.replace('\n', ' ').take(300)
            }
            try {
                val result = withTimeoutOrNull(45_000) {
                    AiClient.stream(
                        provider = titleProvider,
                        model = titleModel,
                        turns = listOf(
                            HubTurn(
                                role = "user",
                                content = "请根据下面这段对话生成一个会话标题。\n" +
                                    "要求：不超过 12 个汉字；只输出标题本身；不要引号、句号或解释。\n\n" +
                                    transcript,
                            ),
                        ),
                        settings = HubChatSettings(
                            systemPrompt = "你是会话标题生成器，只输出一个简洁、具体的中文标题。",
                            temperature = 0.2,
                            maxTokens = 512,
                            thinkingDepth = 2,
                        ),
                        streaming = true,
                        onDelta = {},
                        handle = handle,
                    )
                }
                titleBusyId = null
                if (result == null) {
                    handle.cancel()
                    toast("生成标题超时，请稍后重试")
                    return@launch
                }
                result.onSuccess { raw ->
                    val title = raw.trim()
                        .replace('\n', ' ')
                        .trim('"', '\'', '“', '”', '‘', '’', '。', '.', ' ')
                        .take(24)
                    if (title.isBlank()) {
                        val fallback = history.firstOrNull { it.isFromUser }
                            ?.text?.replace('\n', ' ')?.trim()?.take(24).orEmpty()
                        if (fallback.isNotBlank()) {
                            ChatStore.renameConversation(conversation.id, fallback)
                            toast("模型没返回标题，已用首条消息代替")
                        } else {
                            toast("模型没有返回可用的标题")
                        }
                    } else {
                        ChatStore.renameConversation(conversation.id, title)
                        toast("标题已更新：" + title)
                    }
                }.onFailure { error ->
                    toast("生成标题失败：" + (error.message ?: "未知错误"), long = true)
                }
            } finally {
                if (titleStreamHandle === handle) titleStreamHandle = null
            }
        }
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
        ChatStore.deleteConversation(conversation.id)
        if (wasCurrent) scrollToBottomRequest++
        toast("已删除会话")
    }

    fun openSearchHit(hit: ChatSearchHit) {
        overlay.showChatSearch = false
        overlay.searchAllConversations = false
        val sameConversation = hit.conversationId == ChatStore.currentConversationId
        if (!sameConversation) {
            stopStreaming()
            ChatStore.switchTo(hit.conversationId)
            input = ""
            editingIndex = null
            editingAttachments = emptyList()
        }
        if (hit.messageIndex >= 0) ChatStore.loadAllForCurrent()
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

    fun deleteMoreSheetMessage() {
        val index = moreSheetIndex ?: return
        moreSheetIndex = null
        val message = ChatStore.messages.getOrNull(index) ?: return
        if (message.streaming) stopStreaming()
        editingIndex?.let { editing ->
            when {
                editing == index -> {
                    editingIndex = null
                    editingAttachments = emptyList()
                }
                editing > index -> editingIndex = editing - 1
            }
        }
        ChatStore.removeAt(index)
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
        send(
            attachments = listOf(
                HubAttachment(
                    kind = HubAttachment.KIND_VOICE,
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    mime = "audio/mp4",
                    durationMs = ms,
                ),
            ),
        )
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

    fun disposeVoice() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        VoiceRecorder.cancel()
    }

    fun stop() {
        ChatRuntime.cancelRequestedState.value = true
        ChatRuntime.activeStreamHandleState.value?.cancel()
    }

    fun discardUnsentAttachments(items: List<HubAttachment>) {
        items.asSequence()
            .map { it.path }
            .filter { it.isNotBlank() && !ChatStore.isAttachmentReferenced(it) }
            .distinct()
            .forEach { AttachmentStore.deleteOwnedAttachment(context, it) }
    }

    fun send(
        text: String = input.trim(),
        attachments: List<HubAttachment> = emptyList(),
    ) {
        if (text.isEmpty() && attachments.isEmpty()) return
        if (titleBusyId != null) {
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
        editingIndex?.let { index ->
            val preserve = editingAttachments
                .map { it.path }
                .filter { it.isNotBlank() }
                .toSet()
            if (!ChatStore.truncateFrom(index, preserve)) {
                toast("编辑失败：存储空间或数据库异常", long = true)
                return
            }
            editingIndex = null
            editingAttachments = emptyList()
        }
        input = ""
        val at = System.currentTimeMillis()
        scrollToBottomRequest++
        val saved = ChatStore.append(
            HubMessage(role = "user", text = text, at = at, attachments = attachments),
        )
        if (!saved) {
            if (input.isBlank()) input = text
            toast("保存消息失败：存储空间或数据库异常", long = true)
            return
        }
        if (!ready) {
            if (attachments.isEmpty()) {
                notice(context.getString(R.string.chat_need_provider))
            } else {
                val names = attachments.joinToString("、") { it.name.ifBlank { it.kind } }
                notice("附件（$names）已经发出来了，但还没有可用的供应商 —— 先去「提供商」里开一家并填好 Key，再点重发。")
            }
            return
        }
        startStreaming(requireNotNull(provider), model)
    }

    fun resend(message: HubMessage) {
        if (titleBusyId != null) {
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
        if (titleBusyId != null) {
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
            notice(context.getString(R.string.chat_need_provider))
            return
        }
        scrollToBottomRequest++
        startStreaming(provider, model)
    }

    fun startStreaming(provider: HubProvider, model: String) {
        val token = ChatRuntime.streamTokenState.intValue + 1
        ChatRuntime.streamTokenState.intValue = token
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
            ChatRuntime.streamingState.value = false
            toast("保存回复失败：存储空间或数据库异常", long = true)
            return
        }
        val requestConversationId = ChatStore.currentConversationId
            ?: run {
                ChatRuntime.streamingState.value = false
                return
            }
        scrollToBottomRequest++
        ChatRuntime.streamingState.value = true
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
                )
                if (token == ChatRuntime.streamTokenState.intValue) {
                    ChatRuntime.streamingState.value = false
                    ChatStore.recordUsage(usage)
                }
            } finally {
                if (ChatRuntime.activeStreamHandleState.value === handle) {
                    ChatRuntime.activeStreamHandleState.value = null
                }
            }
        }
    }
}
