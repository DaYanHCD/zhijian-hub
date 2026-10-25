package com.fingertipfuture.hub.data

import android.content.Context
import android.content.Intent
import com.fingertipfuture.hub.MainActivity

/** 通知和系统闹钟点开后，把用户带到真正对应的那条提醒。 */
internal object AssistantReminderNavigation {
    const val EXTRA_OPEN_REMINDER_ID = "open_reminder_id"

    fun activityIntent(context: Context, reminderId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_REMINDER_ID, reminderId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    /**
     * 读取后立刻从 Intent 里移除，避免 Activity 因配置变化重建时重复跳转。
     */
    fun consumeOpenReminderId(intent: Intent?): String? {
        val id = intent?.getStringExtra(EXTRA_OPEN_REMINDER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        intent.removeExtra(EXTRA_OPEN_REMINDER_ID)
        return id
    }
}
