package com.fingertipfuture.hub.ui.chat

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.agent.AgentDeviceActionIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 跨 Activity 重建保留的聊天运行态。
 *
 * 转屏时 Compose 页面会重建，但这里的状态和请求协程属于进程，不会跟着界面销毁。
 * 这样回答可以继续流式写入 ChatStore，新的页面只负责重新读状态和消息。
 */
internal object ChatRuntime {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val streamingState = mutableStateOf(false)
    val cancelRequestedState = mutableStateOf(false)
    val streamTokenState = mutableIntStateOf(0)
    val activeStreamHandleState = mutableStateOf<AiClient.StreamHandle?>(null)

    /**
     * 工具确认请求属于进程级运行态，而不是某一个 ChatScreen 实例。
     *
     * 转屏或暂时离开聊天页时请求仍会继续；确认状态必须跟着请求走，否则旧页面等待的
     * Deferred 永远没有界面能完成它。
     */
    val pendingReminderIntentState = mutableStateOf<AssistantReminderIntent?>(null)
    val pendingAutoMemoryTextState = mutableStateOf<String?>(null)
    val pendingDeviceActionState = mutableStateOf<AgentDeviceActionIntent?>(null)
    private var reminderApprovalWaiter: CompletableDeferred<AssistantReminder?>? = null
    private var autoMemoryApprovalWaiter: CompletableDeferred<Boolean>? = null
    private var deviceActionApprovalWaiter: CompletableDeferred<Boolean>? = null

    fun stopCurrent() {
        cancelRequestedState.value = true
        activeStreamHandleState.value?.cancel()
        cancelPendingApprovals()
    }

    suspend fun awaitReminderApproval(intent: AssistantReminderIntent): AssistantReminder? =
        withContext(Dispatchers.Main.immediate) {
            reminderApprovalWaiter?.complete(null)
            val waiter = CompletableDeferred<AssistantReminder?>()
            reminderApprovalWaiter = waiter
            pendingReminderIntentState.value = intent
            try {
                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { waiter.await() }
            } finally {
                if (reminderApprovalWaiter === waiter) {
                    reminderApprovalWaiter = null
                    pendingReminderIntentState.value = null
                }
            }
        }

    suspend fun awaitAutoMemoryApproval(text: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            autoMemoryApprovalWaiter?.complete(false)
            val waiter = CompletableDeferred<Boolean>()
            autoMemoryApprovalWaiter = waiter
            pendingAutoMemoryTextState.value = text
            try {
                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { waiter.await() } ?: false
            } finally {
                if (autoMemoryApprovalWaiter === waiter) {
                    autoMemoryApprovalWaiter = null
                    pendingAutoMemoryTextState.value = null
                }
            }
        }

    suspend fun awaitDeviceActionApproval(intent: AgentDeviceActionIntent): Boolean =
        withContext(Dispatchers.Main.immediate) {
            deviceActionApprovalWaiter?.complete(false)
            val waiter = CompletableDeferred<Boolean>()
            deviceActionApprovalWaiter = waiter
            pendingDeviceActionState.value = intent
            try {
                withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { waiter.await() } ?: false
            } finally {
                if (deviceActionApprovalWaiter === waiter) {
                    deviceActionApprovalWaiter = null
                    pendingDeviceActionState.value = null
                }
            }
        }

    fun completeReminderApproval(result: AssistantReminder?) {
        pendingReminderIntentState.value = null
        val waiter = reminderApprovalWaiter
        reminderApprovalWaiter = null
        waiter?.complete(result)
    }

    fun completeAutoMemoryApproval(result: Boolean) {
        pendingAutoMemoryTextState.value = null
        val waiter = autoMemoryApprovalWaiter
        autoMemoryApprovalWaiter = null
        waiter?.complete(result)
    }

    fun completeDeviceActionApproval(result: Boolean) {
        pendingDeviceActionState.value = null
        val waiter = deviceActionApprovalWaiter
        deviceActionApprovalWaiter = null
        waiter?.complete(result)
    }

    fun cancelPendingApprovals() {
        completeReminderApproval(null)
        completeAutoMemoryApproval(false)
        completeDeviceActionApproval(false)
    }

    private const val APPROVAL_TIMEOUT_MS = 5 * 60_000L
}
