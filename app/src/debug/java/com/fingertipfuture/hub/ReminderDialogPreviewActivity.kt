package com.fingertipfuture.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.ReminderRepeat
import com.fingertipfuture.hub.ui.chat.AssistantReminderIntentDialog
import com.fingertipfuture.hub.ui.theme.HubTheme

/** Debug 专用：无需调用模型即可预览聊天里的自动化任务创建弹窗。 */
class ReminderDialogPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            HubTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AssistantReminderIntentDialog(
                        intent = AssistantReminderIntent.Save(
                            existing = null,
                            title = "科技新闻摘要",
                            prompt = "查询今天与 AI 相关的重要新闻，列出三条并给出要点",
                            hour = 8,
                            minute = 0,
                            repeat = ReminderRepeat.DAILY,
                        ),
                        onDismiss = {},
                        onSave = {},
                        onClose = {},
                        onDelete = {},
                    )
                }
            }
        }
    }
}
