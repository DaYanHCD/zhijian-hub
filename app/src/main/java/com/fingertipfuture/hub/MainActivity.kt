package com.fingertipfuture.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AssistantReminderNavigation
import com.fingertipfuture.hub.data.AssistantReminderScheduler
import com.fingertipfuture.hub.data.AssistantReminderStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.data.BackupStore
import com.fingertipfuture.hub.data.FolderStore
import com.fingertipfuture.hub.data.LanguageStore
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.ui.HubApp
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.EmojiExtensionStore
import com.fingertipfuture.hub.data.PersistentChatBackup
import com.fingertipfuture.hub.ui.theme.HubTheme
import com.fingertipfuture.hub.ui.theme.ThemeStore
import com.fingertipfuture.hub.update.UpdateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 指尖Hub 唯一的入口。
 *
 * 打开即对话：多会话历史、自带 Key、流式输出。
 * 这里只做两件"开机必做"的事：读本机设置、恢复头像。
 */
class MainActivity : ComponentActivity() {
    private var reminderToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialReminderId = AssistantReminderNavigation.consumeOpenReminderId(intent)
        // 边到边：状态栏让内容自己铺过去（主题里状态栏是透明的）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ProviderStore.initialize(this)
        AvatarStore.initialize(this)
        // 主题（浅色 / 深色 / 跟随系统，设置页里选）：要在 setContent 之前读回来
        ThemeStore.initialize(this)
        // 习惯类开关（常规 / 界面）要在读对话之前读回来 —— 「启动时新建对话」会影响读不读上一段
        UiPrefs.initialize(this)
        // 第一次启动按系统语言自动选择；之后沿用用户手动选择。
        LanguageStore.initialize(this)
        // 助手（设置 › 通用设置 › 助手管理）：聊天用哪个助手、它那套参数，都要先读回来
        AssistantStore.initialize(this)
        AssistantReminderStore.initialize(this)
        AssistantReminderScheduler.rescheduleAll(this)
        MemoryStore.initialize(this)
        // 技能（内置 + 用户自己加的）：助手和技能管理都要用
        SkillStore.initialize(this)
        // 角色模型（快速 / 图像 / 压缩）+ 搜索供应商
        ModelRoleStore.initialize(this)
        SearchStore.initialize(this)
        BackupStore.initialize(this)
        FolderStore.initialize(this)
        EmojiExtensionStore.initialize(this)
        // 读回上次的对话（SQLite：databases/hub.db，见 data/ChatDatabase.kt）
        ChatStore.initialize(this)
        // 快照解压和附件恢复可能很大，放到 IO；界面先正常启动，恢复完成后再发布会话状态。
        lifecycleScope.launch {
            ChatStore.restorePersistentSnapshotIfNeeded(this@MainActivity)
        }
        reminderToOpen = openReminderTarget(initialReminderId)
        // 读本地更新日志，并清掉上一次升级留下的安装包。
        UpdateController.initialize(this)
        // 启动时静默查询服务器；只有真的存在新版本才弹窗。
        UpdateController.checkForUpdateOnStartup()

        setContent {
            HubTheme(darkTheme = ThemeStore.isDark) {
                HubApp(
                    initialReminderId = reminderToOpen,
                    onReminderOpenHandled = { reminderToOpen = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AssistantReminderNavigation.consumeOpenReminderId(intent)
            ?.let(::openReminderTarget)
            ?.let { reminderToOpen = it }
    }

    override fun onStop() {
        ChatStore.flush()
        PersistentChatBackup.flush(this)
        super.onStop()
    }

    private fun openReminderTarget(reminderId: String?): String? {
        if (reminderId.isNullOrBlank()) return null
        val reminder = AssistantReminderStore.byId(reminderId) ?: return reminderId
        if (AssistantStore.byId(reminder.assistantId) != null) {
            AssistantStore.setCurrent(this, reminder.assistantId)
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ChatStore.openConversation(reminder.conversationId)
            }
        }
        return reminder.id
    }

}
