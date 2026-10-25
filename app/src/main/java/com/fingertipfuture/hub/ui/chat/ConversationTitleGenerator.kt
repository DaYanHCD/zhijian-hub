package com.fingertipfuture.hub.ui.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.HubChatSettings
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.isFromUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 会话标题生成属于进程级任务，不能跟随 ChatScreen 的组合生命周期。
 * 用户离开聊天页、转屏或临时进入设置时，请求仍会完成并把标题写回数据库。
 */
internal object ConversationTitleGenerator {
    var busyConversationId by mutableStateOf<String?>(null)
        private set
    private var automatic by mutableStateOf(false)

    val hasManualRequest: Boolean
        get() = busyConversationId != null && !automatic

    fun generate(
        context: Context,
        conversationId: String,
        provider: HubProvider,
        model: String,
        announce: Boolean,
    ): Boolean {
        if (busyConversationId != null) {
            if (announce) context.showLocalizedToast("正在生成另一个标题…")
            return false
        }
        busyConversationId = conversationId
        automatic = !announce
        if (announce) context.showLocalizedToast("正在生成标题…")

        val appContext = context.applicationContext
        ChatRuntime.scope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    ChatStore.messagesOf(conversationId)
                        .filterNot { it.localOnly }
                        .filter { it.text.isNotBlank() }
                }
                if (history.isEmpty()) {
                    if (announce) appContext.showLocalizedToast("空会话不能生成标题")
                    return@launch
                }
                val transcript = history.take(6).joinToString("\n\n") { message ->
                    val role = if (message.isFromUser) "我" else "AI"
                    role + "：" + message.text.replace('\n', ' ').take(300)
                }
                val handle = AiClient.newStreamHandle()
                try {
                    val result = withTimeoutOrNull(45_000) {
                        AiClient.stream(
                            provider = provider,
                            model = model,
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
                    if (result == null) {
                        handle.cancel()
                        if (announce) appContext.showLocalizedToast("生成标题超时，请稍后重试")
                        return@launch
                    }
                    result.onSuccess { raw ->
                        val title = raw.trim()
                            .replace('\n', ' ')
                            .trim('"', '\'', '“', '”', '‘', '’', '。', '.', ' ')
                            .take(24)
                        if (title.isBlank()) {
                            if (announce) {
                                val fallback = history.firstOrNull { it.isFromUser }
                                    ?.text?.replace('\n', ' ')?.trim()?.take(24).orEmpty()
                                if (fallback.isNotBlank()) {
                                    ChatStore.renameConversation(conversationId, fallback)
                                    appContext.showLocalizedToast("模型没返回标题，已用首条消息代替")
                                } else {
                                    appContext.showLocalizedToast("模型没有返回可用的标题")
                                }
                            }
                        } else {
                            ChatStore.renameConversation(conversationId, title)
                            if (announce) appContext.showLocalizedToast("标题已更新：$title")
                        }
                    }.onFailure { error ->
                        if (announce) {
                            appContext.showLocalizedToast(
                                "生成标题失败：" + (error.message ?: "未知错误"),
                                Toast.LENGTH_LONG,
                            )
                        }
                    }
                } finally {
                    handle.cancel()
                }
            } finally {
                busyConversationId = null
                automatic = false
            }
        }
        return true
    }
}
