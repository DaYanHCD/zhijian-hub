package com.fingertipfuture.hub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fingertipfuture.hub.data.AssistantReminderStore
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.chat.ChatRecommendationDestination
import com.fingertipfuture.hub.ui.chat.ChatSearchOverlay
import com.fingertipfuture.hub.ui.provider.ProviderConfigScreen
import com.fingertipfuture.hub.ui.provider.ProvidersScreen
import com.fingertipfuture.hub.ui.settings.AboutScreen
import com.fingertipfuture.hub.ui.settings.AssistantBasicScreen
import com.fingertipfuture.hub.ui.settings.AssistantDetailScreen
import com.fingertipfuture.hub.ui.settings.AssistantListScreen
import com.fingertipfuture.hub.ui.settings.AssistantMemoryScreen
import com.fingertipfuture.hub.ui.settings.AssistantPromptScreen
import com.fingertipfuture.hub.ui.settings.AssistantRemindersScreen
import com.fingertipfuture.hub.ui.settings.AllAssistantRemindersScreen
import com.fingertipfuture.hub.ui.settings.AutomationTasksScreen
import com.fingertipfuture.hub.ui.settings.AssistantSkillScreen
import com.fingertipfuture.hub.ui.settings.BackupScreen
import com.fingertipfuture.hub.ui.settings.ContactScreen
import com.fingertipfuture.hub.ui.settings.InfoSectionsScreen
import com.fingertipfuture.hub.ui.settings.ModelDefaultsScreen
import com.fingertipfuture.hub.ui.settings.PreferenceScreen
import com.fingertipfuture.hub.ui.settings.PrivacyScreen
import com.fingertipfuture.hub.ui.settings.SearchSettingsScreen
import com.fingertipfuture.hub.ui.settings.SearchProviderConfigScreen
import com.fingertipfuture.hub.ui.settings.SkillEditScreen
import com.fingertipfuture.hub.ui.settings.SkillManageScreen
import com.fingertipfuture.hub.ui.settings.DataManagementScreen
import com.fingertipfuture.hub.ui.settings.FloatingSearchScreen
import com.fingertipfuture.hub.ui.settings.StorageAttachmentCategory
import com.fingertipfuture.hub.ui.settings.StorageAttachmentDetailScreen
import com.fingertipfuture.hub.ui.update.ChangelogScreen
import com.fingertipfuture.hub.ui.update.AnnouncementDialogHost
import com.fingertipfuture.hub.ui.update.UpdateDialogHost

/**
 * 本 App 的页面。
 *
 * 设置首页把四组（通用设置 / 模型与服务 / 数据管理 / 关于）**平铺在一页**里（照用户 2026-10-16 给的参考图），
 * 每一条点进去是各自的功能页；返回都是一步回设置首页。
 *
 * 仍然是**一个状态 + BackHandler**，没有引 navigation 库。
 */
private sealed interface HubRoute {
    data object Chat : HubRoute

    data object Settings : HubRoute
    data object Stats : HubRoute
    data object FloatingSearch : HubRoute

    // ---- 通用设置 ----
    data object Preference : HubRoute
    data object Assistants : HubRoute
    data object Reminders : HubRoute
    data object ReminderTasks : HubRoute
    data class AssistantDetail(val id: String) : HubRoute
    data class AssistantBasic(val id: String) : HubRoute
    data class AssistantPrompt(val id: String) : HubRoute
    data class AssistantSkill(val id: String) : HubRoute
    data class AssistantMemory(val id: String) : HubRoute
    data class AssistantReminders(
        val id: String,
        val highlightId: String? = null,
        val fromOverview: Boolean = false,
    ) : HubRoute
    data object Skills : HubRoute
    data class SkillEdit(val id: String) : HubRoute

    // ---- 模型与服务 ----
    data object ModelDefaults : HubRoute
    data object Providers : HubRoute
    data class ProviderConfig(val id: String) : HubRoute
    data object SearchSettings : HubRoute
    data class SearchProviderConfig(val id: String) : HubRoute
    data object Privacy : HubRoute

    // ---- 数据管理 ----
    data object Backup : HubRoute
    data object Storage : HubRoute
    data object ChatSearch : HubRoute
    data class StorageAttachments(val category: StorageAttachmentCategory) : HubRoute

    // ---- 关于 ----
    data object About : HubRoute
    data object Changelog : HubRoute
    data object Thanks : HubRoute
    data object Manual : HubRoute
    data object Contact : HubRoute
}

/** 路由的轻量存档格式，保证配置变化后仍停留在当前页面。 */
private fun HubRoute.key(): String = when (this) {
    HubRoute.Chat -> "chat"
    HubRoute.Settings -> "settings"
    HubRoute.Stats -> "stats"
    HubRoute.FloatingSearch -> "floating-search"
    HubRoute.Preference -> "preference"
    HubRoute.Assistants -> "assistants"
    HubRoute.Reminders -> "reminders"
    HubRoute.ReminderTasks -> "reminder-tasks"
    is HubRoute.AssistantDetail -> "assistant-detail|$id"
    is HubRoute.AssistantBasic -> "assistant-basic|$id"
    is HubRoute.AssistantPrompt -> "assistant-prompt|$id"
    is HubRoute.AssistantSkill -> "assistant-skill|$id"
    is HubRoute.AssistantMemory -> "assistant-memory|$id"
    is HubRoute.AssistantReminders -> "assistant-reminders|$id|" +
        "${highlightId.orEmpty()}|${if (fromOverview) 1 else 0}"
    HubRoute.Skills -> "skills"
    is HubRoute.SkillEdit -> "skill-edit|$id"
    HubRoute.ModelDefaults -> "model-defaults"
    HubRoute.Providers -> "providers"
    is HubRoute.ProviderConfig -> "provider|$id"
    HubRoute.SearchSettings -> "search"
    is HubRoute.SearchProviderConfig -> "search-provider|$id"
    HubRoute.Privacy -> "privacy"
    HubRoute.Backup -> "backup"
    HubRoute.Storage -> "storage"
    HubRoute.ChatSearch -> "chat-search"
    is HubRoute.StorageAttachments -> "storage-attachments|${category.name}"
    HubRoute.About -> "about"
    HubRoute.Changelog -> "changelog"
    HubRoute.Thanks -> "thanks"
    HubRoute.Manual -> "manual"
    HubRoute.Contact -> "contact"
}

private fun routeFromKey(key: String): HubRoute {
    val name = key.substringBefore('|')
    val id = key.substringAfter('|', "")
    return when (name) {
        "chat" -> HubRoute.Chat
        "settings" -> HubRoute.Settings
        "stats" -> HubRoute.Stats
        "floating-search" -> HubRoute.FloatingSearch
        "preference" -> HubRoute.Preference
        "assistants" -> HubRoute.Assistants
        "reminders" -> HubRoute.Reminders
        "reminder-tasks" -> HubRoute.ReminderTasks
        "assistant-detail" -> HubRoute.AssistantDetail(id)
        "assistant-basic" -> HubRoute.AssistantBasic(id)
        "assistant-prompt" -> HubRoute.AssistantPrompt(id)
        "assistant-skill" -> HubRoute.AssistantSkill(id)
        "assistant-memory" -> HubRoute.AssistantMemory(id)
        "assistant-reminders" -> {
            val parts = key.split('|')
            HubRoute.AssistantReminders(
                id = parts.getOrNull(1).orEmpty(),
                highlightId = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                fromOverview = parts.getOrNull(3) == "1",
            )
        }
        "skills" -> HubRoute.Skills
        "skill-edit" -> HubRoute.SkillEdit(id)
        "model-defaults" -> HubRoute.ModelDefaults
        "providers" -> HubRoute.Providers
        "provider" -> HubRoute.ProviderConfig(id)
        "search" -> HubRoute.SearchSettings
        "search-provider" -> HubRoute.SearchProviderConfig(id)
        "privacy" -> HubRoute.Privacy
        "backup" -> HubRoute.Backup
        "storage" -> HubRoute.Storage
        "chat-search" -> HubRoute.ChatSearch
        "storage-attachments" -> HubRoute.StorageAttachments(
            StorageAttachmentCategory.entries.firstOrNull { it.name == id }
                ?: StorageAttachmentCategory.IMAGES,
        )
        "attachments" -> HubRoute.StorageAttachments(StorageAttachmentCategory.IMAGES)
        "extensions" -> HubRoute.Storage
        "webdav" -> HubRoute.Backup
        "about" -> HubRoute.About
        "changelog" -> HubRoute.Changelog
        "thanks" -> HubRoute.Thanks
        "manual" -> HubRoute.Manual
        "contact" -> HubRoute.Contact
        else -> HubRoute.Chat
    }
}

@Composable
fun HubApp(
    initialReminderId: String? = null,
    onReminderOpenHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    var route by rememberSaveable(
        stateSaver = Saver<HubRoute, String>(
            save = { it.key() },
            restore = ::routeFromKey,
        ),
    ) { mutableStateOf<HubRoute>(HubRoute.Chat) }
    var pendingChatSearchHit by remember { mutableStateOf<ChatSearchHit?>(null) }
    var recommendationReturnToChat by remember { mutableStateOf(false) }
    // 数据管理 / 隐私 / 关于可从不同页面进入；返回时回到真正的来源页。
    var subPageParent by rememberSaveable(
        stateSaver = Saver<HubRoute, String>(
            save = { it.key() },
            restore = ::routeFromKey,
        ),
    ) { mutableStateOf<HubRoute>(HubRoute.Settings) }

    fun newId() = "p" + System.currentTimeMillis()
    fun toSettings() { route = HubRoute.Settings }
    fun openSubPage(target: HubRoute, from: HubRoute) {
        subPageParent = from
        route = target
    }
    fun backFromRecommendation(fallback: HubRoute) {
        if (recommendationReturnToChat) {
            recommendationReturnToChat = false
            route = HubRoute.Chat
        } else {
            route = fallback
        }
    }

    LaunchedEffect(initialReminderId) {
        val reminderId = initialReminderId ?: return@LaunchedEffect
        val reminder = AssistantReminderStore.byId(reminderId)
        if (reminder == null) {
            route = HubRoute.ReminderTasks
        } else if (
            reminder.conversationId.isNotBlank() &&
            ChatStore.currentConversationId == reminder.conversationId
        ) {
            route = HubRoute.Chat
        } else {
            route = HubRoute.AssistantReminders(
                id = reminder.assistantId,
                highlightId = reminder.id,
            )
        }
        onReminderOpenHandled()
    }

    LaunchedEffect(route) {
        if (route == HubRoute.Chat) recommendationReturnToChat = false
    }

    when (val r = route) {
        HubRoute.Chat -> ChatScreen(
            onOpenSettings = { route = HubRoute.Settings },
            onOpenStats = { route = HubRoute.Stats },
            onOpenFloatingSearch = { route = HubRoute.FloatingSearch },
            onOpenPrivacy = { openSubPage(HubRoute.Privacy, HubRoute.Chat) },
            onOpenSearchSettings = {
                recommendationReturnToChat = true
                route = HubRoute.SearchSettings
            },
            onOpenRecommendation = { destination ->
                recommendationReturnToChat = true
                route = when (destination) {
                    ChatRecommendationDestination.Providers -> HubRoute.Providers
                    ChatRecommendationDestination.SearchSettings -> HubRoute.SearchSettings
                }
            },
            openSearchHitOnStart = pendingChatSearchHit,
            onSearchHitStartHandled = { pendingChatSearchHit = null },
        )

        HubRoute.Stats -> StatsScreen(onBack = { route = HubRoute.Chat })
        HubRoute.FloatingSearch -> FloatingSearchScreen(onBack = { route = HubRoute.Chat })
        HubRoute.Settings -> SettingsScreen(
            onBack = { route = HubRoute.Chat },
            onOpenPreference = { route = HubRoute.Preference },
            onOpenAssistant = { route = HubRoute.Assistants },
            onOpenReminders = { route = HubRoute.Reminders },
            onOpenSkill = { route = HubRoute.Skills },
            onOpenModelDefaults = { route = HubRoute.ModelDefaults },
            onOpenProviders = { route = HubRoute.Providers },
            onOpenSearch = { route = HubRoute.SearchSettings },
            onOpenPrivacy = { openSubPage(HubRoute.Privacy, HubRoute.Settings) },
            onOpenBackup = { openSubPage(HubRoute.Backup, HubRoute.Settings) },
            onOpenStorage = { openSubPage(HubRoute.Storage, HubRoute.Settings) },
            onOpenAbout = { openSubPage(HubRoute.About, HubRoute.Settings) },
            onOpenThanks = { route = HubRoute.Thanks },
            onOpenManual = { route = HubRoute.Manual },
            onOpenContact = { route = HubRoute.Contact },
        )

        // ---- 通用设置 ----
        HubRoute.Preference -> PreferenceScreen(
            onBack = { toSettings() },
        )
        HubRoute.Assistants -> AssistantListScreen(
            onBack = { toSettings() },
            onOpenAssistant = { id -> route = HubRoute.AssistantDetail(id) },
        )
        HubRoute.Reminders -> AutomationTasksScreen(
            onBack = { toSettings() },
            onOpenReminderTasks = { route = HubRoute.ReminderTasks },
        )
        HubRoute.ReminderTasks -> AllAssistantRemindersScreen(
            onBack = { route = HubRoute.Reminders },
        )
        is HubRoute.AssistantDetail -> AssistantDetailScreen(
            assistantId = r.id,
            onBack = { route = HubRoute.Assistants },
            onOpenBasic = { route = HubRoute.AssistantBasic(r.id) },
            onOpenPrompt = { route = HubRoute.AssistantPrompt(r.id) },
            onOpenSkill = { route = HubRoute.AssistantSkill(r.id) },
            onOpenMemory = { route = HubRoute.AssistantMemory(r.id) },
        )
        is HubRoute.AssistantBasic -> AssistantBasicScreen(assistantId = r.id, onBack = { route = HubRoute.AssistantDetail(r.id) })
        is HubRoute.AssistantPrompt -> AssistantPromptScreen(assistantId = r.id, onBack = { route = HubRoute.AssistantDetail(r.id) })
        is HubRoute.AssistantSkill -> AssistantSkillScreen(assistantId = r.id, onBack = { route = HubRoute.AssistantDetail(r.id) })
        is HubRoute.AssistantMemory -> AssistantMemoryScreen(assistantId = r.id, onBack = { route = HubRoute.AssistantDetail(r.id) })
        is HubRoute.AssistantReminders -> AssistantRemindersScreen(
            assistantId = r.id,
            onBack = {
                route = if (r.fromOverview) HubRoute.ReminderTasks else HubRoute.AssistantDetail(r.id)
            },
        )
        HubRoute.Skills -> SkillManageScreen(
            onBack = { toSettings() },
            onEditSkill = { id -> route = HubRoute.SkillEdit(id) },
        )
        is HubRoute.SkillEdit -> SkillEditScreen(skillId = r.id, onBack = { route = HubRoute.Skills })

        // ---- 模型与服务 ----
        HubRoute.ModelDefaults -> ModelDefaultsScreen(onBack = { toSettings() })
        HubRoute.SearchSettings -> SearchSettingsScreen(
            onBack = { backFromRecommendation(HubRoute.Settings) },
            onOpenProvider = { id -> route = HubRoute.SearchProviderConfig(id) },
        )
        is HubRoute.SearchProviderConfig -> SearchProviderConfigScreen(
            providerId = r.id,
            onBack = { route = HubRoute.SearchSettings },
        )
        HubRoute.Privacy -> PrivacyScreen(onBack = { route = subPageParent })

        HubRoute.Providers -> ProvidersScreen(
            onBack = { backFromRecommendation(HubRoute.Settings) },
            onOpenProvider = { id -> route = HubRoute.ProviderConfig(id) },
            onCreateEmpty = {
                val id = newId()
                ProviderStore.upsert(
                    context,
                    HubProvider(id = id, name = "", baseUrl = "", enabled = true),
                )
                route = HubRoute.ProviderConfig(id)
            },
        )
        is HubRoute.ProviderConfig -> ProviderConfigScreen(
            providerId = r.id,
            onBack = { route = HubRoute.Providers },
        )

        // ---- 数据管理 ----
        HubRoute.Backup -> BackupScreen(
            onBack = { route = subPageParent },
        )
        HubRoute.Storage -> DataManagementScreen(
            onBack = { route = subPageParent },
            onOpenAttachmentCategory = { category ->
                route = HubRoute.StorageAttachments(category)
            },
        )
        HubRoute.ChatSearch -> ChatSearchOverlay(
            allConversations = true,
            conversationId = null,
            onDismiss = { route = HubRoute.Storage },
            onOpenHit = { hit ->
                pendingChatSearchHit = hit
                route = HubRoute.Chat
            },
        )
        is HubRoute.StorageAttachments -> StorageAttachmentDetailScreen(
            category = r.category,
            onBack = { route = HubRoute.Storage },
        )

        // ---- 关于 ----
        HubRoute.About -> AboutScreen(
            onBack = { route = subPageParent },
            onOpenChangelog = { route = HubRoute.Changelog },
        )
        HubRoute.Changelog -> ChangelogScreen(onBack = { route = HubRoute.About })
        HubRoute.Thanks -> InfoSectionsScreen(
            title = "致谢",
            entries = com.fingertipfuture.hub.ui.settings.THANKS_ENTRIES,
            onBack = { toSettings() },
        )
        HubRoute.Manual -> InfoSectionsScreen(
            title = "说明书",
            entries = com.fingertipfuture.hub.ui.settings.MANUAL_ENTRIES,
            onBack = { toSettings() },
        )
        HubRoute.Contact -> ContactScreen(onBack = { toSettings() })
    }

    // 更新 / 更新日志的弹窗挂在**全局**：任何页面点「检查更新」都要能弹出来
    UpdateDialogHost()
    // 公告也挂在全局：只读取 APK 内置公告，不联网。
    AnnouncementDialogHost()
    // 公共存储授权后才能把快照写到卸载后仍保留的 Documents 目录。
    PersistentStorageAccessDialog()
}
