package com.fingertipfuture.hub.ui.chat

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.fingertipfuture.hub.data.AiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    fun stopCurrent() {
        cancelRequestedState.value = true
        activeStreamHandleState.value?.cancel()
    }
}
