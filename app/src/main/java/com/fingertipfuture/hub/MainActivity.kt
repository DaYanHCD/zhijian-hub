package com.fingertipfuture.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.AssistantStore
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
import com.fingertipfuture.hub.ui.theme.HubTheme
import com.fingertipfuture.hub.ui.theme.ThemeStore
import com.fingertipfuture.hub.update.UpdateController

/**
 * 指尖Hub 唯一的入口。
 *
 * 打开即对话：多会话历史、自带 Key、流式输出。
 * 这里只做三件"开机必做"的事：读本机设置、恢复头像、检查更新。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        MemoryStore.initialize(this)
        // 技能（内置 + 用户自己加的）：助手和技能管理都要用
        SkillStore.initialize(this)
        // 角色模型（快速 / 图像 / 压缩）+ 搜索供应商
        ModelRoleStore.initialize(this)
        SearchStore.initialize(this)
        BackupStore.initialize(this)
        FolderStore.initialize(this)
        // 读回上次的对话（SQLite：databases/hub.db，见 data/ChatDatabase.kt）
        ChatStore.initialize(this)
        // 初始化的同时会把上一次升级留下的安装包清掉（见 UpdateController.initialize）
        UpdateController.initialize(this)
        // 启动时静默检查更新：只有真的发现新版本才弹窗，失败不打扰
        UpdateController.checkForUpdateOnStartup()



        setContent {
            HubTheme(darkTheme = ThemeStore.isDark) {
                HubApp()
            }
        }
    }
}
