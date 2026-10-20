package com.fingertipfuture.hub.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.AttachmentPathPolicy
import com.fingertipfuture.hub.data.ArchivedConversation
import com.fingertipfuture.hub.data.ChatDatabase
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.FolderStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.HubModelUsage
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.data.isFromUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 当前对话：内存里留一份给 Compose 渲染，改动写进 [ChatDatabase]（SQLite，手写 SQL）。
 *
 * 为什么要有它：消息原先只活在内存（`rememberSaveable`）—— 转屏扛得住，
 * 但**杀进程 / 装更新 / 重启手机就整段没了**（用户 2026-10-14）。现在打开 App
 * 时从库里读回最近一段，改一条写一条。
 *
 * 写盘节奏：
 * - **结构变化立刻写**（新消息、清空）—— 一次就一条 INSERT/DELETE，几毫秒；
 * - **流式正文攒 500 ms 再写**（一次回答几百片增量，不能一片一写），
 *   流式结束、界面销毁（转屏 / 退出）时立刻写。
 *
 * 多会话：内存里只放**当前这一段**，切走前先 flush；抽屉里的列表来自
 * `conversation` 表。上次打开的是哪一段记在 [UiPrefs]，重启后回到同一段。
 */
internal object ChatStore {

    /** 流式正文的后台检查间隔。 */
    private const val SAVE_DELAY_MS = 500L
    /** 不落库的本机提示使用这个序号；它永远不参与数据库排序。 */
    internal const val LOCAL_ONLY_SORT_ORDER = -1
    /** 首屏和每次“向上加载更多”的消息条数。 */
    private const val PAGE_SIZE = 80

    // 自动落盘跑 IO，别让 SQLite 写磁盘卡住主线程；最终 flush 仍在主线程同步写，
    // 保证退出/切会话时内容一定先落盘。
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    @Volatile private var pendingSave: PendingSave? = null
    private var saveLoopRunning = false
    @Volatile private var saveEpoch = 0L
    private val saveLock = Any()

    private data class PendingSave(
        val conversationId: String,
        val nodeId: String,
        val versionIndex: Int,
        val message: HubMessage,
        val epoch: Long,
    )

    /** 内存里的那条消息，外加它在库里的位置（节点 / 版本）。 */
    private class Entry(
        val nodeId: String,
        val versionIndex: Int,
        val sortOrder: Int,
        val message: HubMessage,
    )

    private var entries: List<Entry> = emptyList()
    private var conversationId: String? = null
    private var appContext: Context? = null

    /**
     * 只在本进程第一次 initialize 时读库。
     *
     * 转屏 / 切深色都会重走 Activity.onCreate（→ initialize），每次都读的话，
     * 内存里比库里新的那几条会被旧数据盖掉（正文本来是攒半秒才写的）。
     */
    private var loaded = false

    /** 当前对话。界面只**读**它；要改必须走 [append] / [appendAll] / [updateLast] / [clear]。 */
    var messages: List<HubMessage> by mutableStateOf(emptyList())
        private set

    /** 抽屉里的会话列表（置顶优先，其余按最近更新时间倒序）。 */
    var conversations: List<HubConversation> by mutableStateOf(emptyList())
        private set

    /** 当前打开的是哪一段；null = 还没建会话（启动时选了「新建对话」）。 */
    var currentConversationId: String? by mutableStateOf<String?>(null)
        private set

    /** 当前窗口之外是否还有更早的消息（聊天页滚到顶时拉上一页）。 */
    var hasOlder: Boolean by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ChatDatabase.initialize(context)
        if (loaded) return
        loaded = true
        saveScope.launch {
            runCatching { ChatDatabase.warmSearchIndex() }
                .onFailure { Log.e("HubChatStore", "搜索索引预热失败", it) }
        }
        refreshConversations()
        // 设置 › 常规 ›「启动时新建对话」开着：**不读回上一段**，直接开一段空的。
        // ⚠️ 这里不能调 clear() —— 那是"删掉当前这段对话"，会把上一段从库里删掉。
        if (UiPrefs.newChatOnStart) {
            conversationId = null
            currentConversationId = null
            persistActive(null)
            publish()
            return
        }
        // 优先回到上次打开的那一段；它被删了就退回最近更新的那一段。
        val savedId = UiPrefs.activeConversationId(context)
        val conversation = conversations.firstOrNull { it.id == savedId }
            ?: conversations.firstOrNull()
            ?: return
        conversationId = conversation.id
        currentConversationId = conversation.id
        entries = loadRecentEntries(conversation.id)
        persistActive(conversation.id)
        publish()
    }

    /**
     * 新建一段空会话并切过去；**旧会话原样留在库里**（顶栏「新对话」用）。
     */
    fun newConversation(title: String = ""): String {
        flush()
        val id = runCatching { ChatDatabase.newConversation(title) }
            .onFailure { Log.e("HubChatStore", "新建会话失败", it) }
            .getOrDefault("")
        if (id.isBlank()) return ""
        conversationId = id
        currentConversationId = id
        entries = emptyList()
        hasOlder = false
        publish()
        persistActive(id)
        refreshConversations()
        return id
    }

    /** 切到另一段会话；切走前把当前这段没落盘的流式正文先写掉。 */
    fun switchTo(id: String) {
        if (id == conversationId) return
        if (conversations.none { it.id == id }) return
        flush()
        conversationId = id
        currentConversationId = id
        runCatching { ChatDatabase.compactSortOrder(id) }
            .onFailure { Log.e("HubChatStore", "整理会话顺序失败", it) }
        entries = loadRecentEntries(id)
        persistActive(id)
        publish()
    }

    /** 抽屉搜索：标题 + 全部会话的正文。 */
    fun search(query: String, conversationId: String? = null): List<ChatSearchHit> =
        ChatDatabase.searchConversations(query, conversationId = conversationId)

    /** 读某一段的消息（重新生成标题时要看内容；不改变当前打开的那段）。 */
    fun messagesOf(id: String): List<HubMessage> =
        ChatDatabase.messages(id).filterNot { it.isIncompletePlaceholder() }.map { it.toHubMessage() }

    /** 统计用：全部会话的当前版本消息（不改变当前打开的那段）。 */
    fun allMessages(): List<HubMessage> =
        ChatDatabase.allMessages().map { it.toHubMessage() }

    fun messageCount(): Int = ChatDatabase.messageCount()

    /**
     * 请求历史：从库里取最近一段，不依赖界面当前加载到哪一页。
     * 由调用方放到 IO 线程。
     */
    fun requestHistory(conversationId: String, limit: Int = 60): List<HubMessage> =
        ChatDatabase.recentMessages(conversationId, limit)
            .filterNot { it.isIncompletePlaceholder() }
            .map { it.toHubMessage() }

    /** 向上滚动到顶时加载更早的一页；不改变当前滚动位置。 */
    fun loadOlder() {
        if (!hasOlder) return
        val id = conversationId ?: return
        val before = entries.firstOrNull { !it.message.localOnly }?.sortOrder ?: return
        val loaded = runCatching {
            ChatDatabase.messagesBefore(id, before, PAGE_SIZE + 1)
        }.onFailure { Log.e("HubChatStore", "加载更早消息失败", it) }
            .getOrDefault(emptyList())
        if (loaded.isEmpty()) {
            hasOlder = false
            return
        }
        hasOlder = loaded.size > PAGE_SIZE
        val page = loaded.takeLast(PAGE_SIZE)
        entries = page.map {
            Entry(it.nodeId, it.versionIndex, it.sortOrder, it.toHubMessage())
        } + entries
        publish()
    }

    /** 搜索跳转需要跨到更早的消息时，直接读回整段（极少发生，优先保证定位正确）。 */
    fun loadAllForCurrent() {
        val id = conversationId ?: return
        entries = runCatching {
            ChatDatabase.messages(id).filterNot { it.isIncompletePlaceholder() }.map {
                Entry(it.nodeId, it.versionIndex, it.sortOrder, it.toHubMessage())
            }
        }.onFailure { Log.e("HubChatStore", "读取完整会话失败", it) }
            .getOrDefault(emptyList())
        hasOlder = false
        publish()
    }

    private fun loadRecentEntries(id: String): List<Entry> {
        val page = runCatching { ChatDatabase.recentMessages(id, PAGE_SIZE + 1) }
            .onFailure { Log.e("HubChatStore", "读取最近消息失败", it) }
            .getOrDefault(emptyList())
        hasOlder = page.size > PAGE_SIZE
        return page.takeLast(PAGE_SIZE)
            .filterNot { it.isIncompletePlaceholder() }
            .map { Entry(it.nodeId, it.versionIndex, it.sortOrder, it.toHubMessage()) }
    }

    /** 这个路径是否已经被当前内存里的某条消息引用（发送被拦截时用来决定能不能删新导入的文件）。 */
    fun isAttachmentReferenced(path: String): Boolean =
        path.isNotBlank() && entries.any { entry ->
            entry.message.attachments.any { it.path == path }
        }

    fun usageTotals(): HubTokenUsage = ChatDatabase.usageTotals()
    fun modelUsage(): List<HubModelUsage> = ChatDatabase.modelUsage()

    fun recordUsage(usage: HubTokenUsage) {
        val last = entries.lastOrNull { !it.message.localOnly } ?: return
        if (last.message.role != "assistant") return
        runCatching { ChatDatabase.setUsage("${last.nodeId}-${last.versionIndex}", usage) }
            .onFailure { Log.e("HubChatStore", "记录 token 用量失败", it) }
    }

    /** 导出全部会话（当前流式内容先落盘）。 */
    fun exportArchive(flushFirst: Boolean = true): List<ArchivedConversation> {
        if (flushFirst) flush()
        return conversations.mapNotNull { conversation ->
            val messages = ChatDatabase.messages(conversation.id)
                .filterNot { it.isIncompletePlaceholder() }
                .map { it.toHubMessage() }
                .filterNot { it.localOnly }
            if (messages.isEmpty()) return@mapNotNull null
            ArchivedConversation(
                title = conversation.title,
                pinned = conversation.pinned,
                folderId = conversation.folderId,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
                messages = messages,
            )
        }
    }

    /**
     * 导入存档：每段都新建一段会话，绝不并进当前会话。
     * 返回真正导入的会话数。
     */
    internal data class ArchiveImportResult(
        val count: Int,
        val conversationIds: List<String>,
    )

    fun importArchive(items: List<ArchivedConversation>): Int =
        importArchiveDetailed(items).count

    internal fun importArchiveDetailed(items: List<ArchivedConversation>): ArchiveImportResult {
        var imported = 0
        val conversationIds = mutableListOf<String>()
        // 导入要么全部成功，要么全部回滚；中途磁盘满/数据库异常不能留下半套会话。
        ChatDatabase.transaction {
            items.forEach { archived ->
                val messages = archived.messages
                    .filterNot { it.localOnly }
                    .map { message ->
                        message.copy(attachments = remapAttachmentPaths(message.attachments))
                    }
                if (messages.isEmpty()) return@forEach
                val title = archived.title.ifBlank {
                    messages.firstOrNull { it.isFromUser && it.text.isNotBlank() }
                        ?.text?.replace('\n', ' ')?.take(24).orEmpty()
                }
                val conversation = ChatDatabase.newConversation(
                    title = title,
                    at = archived.createdAt,
                )
                conversationIds += conversation
                var parentNodeId: String? = null
                messages.forEachIndexed { index, message ->
                    val nodeId = "n-" + UUID.randomUUID()
                    ChatDatabase.appendMessage(
                        conversationId = conversation,
                        nodeId = nodeId,
                        parentNodeId = parentNodeId,
                        sortOrder = index,
                        message = message,
                    )
                    parentNodeId = nodeId
                }
                ChatDatabase.setTitleIfEmpty(conversation, title)
                ChatDatabase.setPinned(conversation, archived.pinned)
                // 文件夹 id 是另一台设备上的本地 id；本机没有这个名字就落回未分类
                val folderId = archived.folderId?.takeIf { FolderStore.byId(it) != null }
                ChatDatabase.setFolder(conversation, folderId)
                imported++
            }
        }
        refreshConversations()
        return ArchiveImportResult(imported, conversationIds)
    }

    /** 文件恢复失败时，把刚才导入的会话一起撤掉，避免留下指向半套附件的记录。 */
    internal fun deleteImportedConversations(ids: List<String>) {
        if (ids.isEmpty()) return
        runCatching {
            ChatDatabase.transaction {
                ids.forEach { ChatDatabase.deleteConversation(it) }
            }
        }.onFailure { Log.e("HubChatStore", "回滚导入会话失败", it) }
        refreshConversations()
    }

    /** 跨设备恢复时，存档里是旧设备的绝对路径；按文件名找回本机新路径。 */
    private fun remapAttachmentPaths(items: List<HubAttachment>): List<HubAttachment> {
        val context = appContext ?: return items
        return items.map { attachment ->
            if (attachment.path.isBlank()) return@map attachment
            val remapped = AttachmentPathPolicy.remapImported(context.filesDir, attachment.path)
            attachment.copy(path = remapped.orEmpty())
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        runCatching { ChatDatabase.setPinned(id, pinned) }
            .onFailure { Log.e("HubChatStore", "置顶会话失败", it); return }
        refreshConversations()
    }

    fun renameConversation(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        runCatching { ChatDatabase.renameConversation(id, clean) }
            .onFailure { Log.e("HubChatStore", "重命名会话失败", it); return }
        refreshConversations()
    }

    fun moveToFolder(id: String, folderId: String?) {
        runCatching { ChatDatabase.setFolder(id, folderId) }
            .onFailure { Log.e("HubChatStore", "移动会话失败", it); return }
        refreshConversations()
    }

    fun clearFolder(folderId: String) {
        runCatching { ChatDatabase.clearFolder(folderId) }
            .onFailure { Log.e("HubChatStore", "清空文件夹失败", it); return }
        refreshConversations()
    }

    /**
     * 删除一段会话。删的是当前这段时，自动切到剩下的最近一段；一段都不剩就回到空态。
     * 调用方在流式生成中要先 [ChatScreen.stopStreaming]（界面层才知道在不在生成）。
     */
    fun deleteConversation(id: String) {
        try {
            val deletingCurrent = id == conversationId
            val droppedMessages = if (deletingCurrent) {
                cancelPendingSave()
                // 首屏 entries 最多只有 80 条；从库里补齐全量附件，
                // 否则删除长会话时，更早页的图片/文件/语音会变成孤儿文件。
                val stored = runCatching {
                    ChatDatabase.messages(id)
                        .filterNot { it.isIncompletePlaceholder() }
                        .map { it.toHubMessage() }
                }.onFailure { Log.e("HubChatStore", "读取待删会话附件失败", it) }
                    .getOrDefault(emptyList())
                stored + entries.map { it.message }
            } else {
                ChatDatabase.messages(id).filterNot { it.isIncompletePlaceholder() }.map { it.toHubMessage() }
            }
            ChatDatabase.deleteConversation(id)
            dropAttachments(droppedMessages, allAttachmentPathsExcept(id))
            if (!deletingCurrent) {
                refreshConversations()
                return
            }
            entries = emptyList()
            hasOlder = false
            conversationId = null
            currentConversationId = null
            persistActive(null)
            publish()
            refreshConversations()
            conversations.firstOrNull()?.let { switchTo(it.id) }
        } catch (t: Throwable) {
            Log.e("HubChatStore", "删除会话失败", t)
        }
    }

    /**
     * 删除所有对话及其附件，并回到空态。
     * 调用方如果正在生成回答，应先 [ChatRuntime.stopCurrent]。
     */
    fun deleteAllConversations() {
        try {
            cancelPendingSave()
            val paths = ChatDatabase.allAttachmentPaths()
            ChatDatabase.deleteAllConversations()
            appContext?.let { context ->
                // 删除上百个附件文件不该占着主线程
                saveScope.launch {
                    paths.forEach { path -> AttachmentStore.deleteOwnedAttachment(context, path) }
                }
            }
            entries = emptyList()
            hasOlder = false
            conversationId = null
            currentConversationId = null
            persistActive(null)
            publish()
            refreshConversations()
        } catch (t: Throwable) {
            Log.e("HubChatStore", "删除全部会话失败", t)
        }
    }

    /**
     * 清空附件目录，同时清掉消息里的引用，避免留下打不开的死链接。
     * 返回实际删掉的文件数（文件枚举/删除都在调用方所在线程，通常由设置页调用）。
     */
    fun clearAllAttachments(context: Context): Int {
        val files = AttachmentStore.listAttachments(context)
        val paths = files.map { it.path }.toSet()
        var deleted = 0
        files.forEach { file ->
            if (AttachmentStore.deleteAttachment(file.path)) deleted++
        }
        if (paths.isNotEmpty()) {
            runCatching { ChatDatabase.stripAttachmentReferences(paths) }
                .onFailure { Log.e("HubChatStore", "清理消息里的附件引用失败", it) }
        }
        return deleted
    }

    /** 外部修改了消息表（例如清理附件引用）后，把当前会话重新读进内存。 */
    fun reloadCurrent() {
        val id = conversationId ?: return
        entries = loadRecentEntries(id)
        publish()
    }

    fun append(message: HubMessage, refreshList: Boolean = true): Boolean =
        appendAll(listOf(message), refreshList)

    /** 本机提示不占数据库序号；普通消息使用数据库给出的下一个序号。 */
    internal fun sortOrderForNewMessage(
        message: HubMessage,
        nextPersistedSortOrder: Int,
    ): Int = if (message.localOnly) LOCAL_ONLY_SORT_ORDER else nextPersistedSortOrder

    /**
     * 一次插一批（导入对话用）。返回是否全部写成功。
     *
     * 磁盘满 / 数据库损坏时不能把异常直接抛到点击事件里：先把内存回滚到调用前，
     * 再让界面提示“保存失败”。否则 App 会从“发一条消息”直接崩掉。
     */
    fun appendAll(extra: List<HubMessage>, refreshList: Boolean = true): Boolean {
        if (extra.isEmpty()) return true
        val previousEntries = entries
        val previousConversation = conversationId
        try {
            extra.forEach { message ->
                val nodeId = "n-" + UUID.randomUUID()
                // 第一条用户消息顺便当会话标题（抽屉列表要显示它）
                val title = if (message.role == "user" && message.text.isNotBlank()) {
                    message.text.replace('\n', ' ').take(24)
                } else {
                    ""
                }
                val conversation = if (message.localOnly) {
                    conversationId
                } else {
                    ensureConversation(title)
                }
                val sortOrder = sortOrderForNewMessage(
                    message = message,
                    nextPersistedSortOrder = if (message.localOnly) {
                        0
                    } else {
                        ChatDatabase.nextSortOrder(checkNotNull(conversation))
                    },
                )
                entries = entries + Entry(
                    nodeId = nodeId,
                    versionIndex = 0,
                    sortOrder = sortOrder,
                    message = message.copy(id = "$nodeId-0"),
                )
                // 本机提示（「已导出…」这类）不落库：重开不必看见，也更不该发给模型
                if (!message.localOnly) {
                    checkNotNull(conversation)
                    ChatDatabase.setTitleIfEmpty(conversation, title)
                    ChatDatabase.appendMessage(
                        conversationId = conversation,
                        nodeId = nodeId,
                        // 上一个位置 = 这条消息的父节点（Fork / 分支将来要用）
                        parentNodeId = entries.dropLast(1)
                            .lastOrNull { !it.message.localOnly }
                            ?.nodeId,
                        sortOrder = sortOrder,
                        message = message,
                    )
                }
            }
            publish()
            if (refreshList) refreshConversations()
            return true
        } catch (t: Throwable) {
            Log.e("HubChatStore", "消息写入失败", t)
            entries = previousEntries
            if (previousConversation == null && conversationId != null) {
                // 这次调用刚建出来的会话也一起收掉，避免抽屉里留下空壳
                runCatching { ChatDatabase.deleteConversation(conversationId!!) }
                conversationId = null
                currentConversationId = null
                persistActive(null)
            }
            publish()
            if (refreshList) refreshConversations()
            return false
        }
    }

    /** 丢掉一批消息时，把它们的附件也删掉（只删 App 自己目录里的文件）。 */
    private fun dropAttachments(dropped: List<HubMessage>, keepPaths: Set<String>) {
        val context = appContext ?: return
        attachmentPathsToDelete(dropped, keepPaths)
            .forEach { path -> AttachmentStore.deleteOwnedAttachment(context, path) }
    }

    /** 纯函数：哪些附件路径可以删（保留仍在其它消息/即将复用的路径）。 */
    internal fun attachmentPathsToDelete(
        dropped: List<HubMessage>,
        keepPaths: Set<String>,
    ): Set<String> = dropped.asSequence()
        .flatMap { it.attachments.asSequence() }
        .map { it.path }
        .filter { it.isNotBlank() && it !in keepPaths }
        .toSet()

    private fun attachmentPaths(entries: List<Entry>): Set<String> =
        entries.asSequence()
            .flatMap { it.message.attachments.asSequence() }
            .map { it.path }
            .filter { it.isNotBlank() }
            .toSet()

    /** 其它会话还引用着的附件路径，删当前会话时不能误删。 */
    private fun allAttachmentPathsExcept(conversationId: String?): Set<String> =
        if (conversationId == null) {
            ChatDatabase.allAttachmentPaths()
        } else {
            ChatDatabase.attachmentPathsExcept(conversationId)
        }

    /** 就地改写最后一条（流式增量、收尾都要用）。空列表时什么都不做。 */
    fun updateLast(transform: (HubMessage) -> HubMessage) {
        updateLastInternal(transform)
    }

    /**
     * 只在指定会话仍然是当前会话时改写最后一条。
     *
     * 请求协程切走会话后可能还会继续跑几帧；所有请求内更新都必须过这一层，
     * 否则旧请求会把思考步骤写进新会话。
     */
    fun updateLastIf(conversationId: String, transform: (HubMessage) -> HubMessage): Boolean {
        if (this.conversationId != conversationId) return false
        return updateLastInternal(transform)
    }

    private fun updateLastInternal(transform: (HubMessage) -> HubMessage): Boolean {
        val last = entries.lastOrNull() ?: return false
        val updated = transform(last.message)
        entries = entries.dropLast(1) + Entry(
            last.nodeId,
            last.versionIndex,
            last.sortOrder,
            updated,
        )
        publish()
        if (updated.localOnly) return true
        // 流式刚结束（streaming true → false）立刻写；打字过程中攒着写
        if (last.message.streaming && !updated.streaming) {
            saveNow()
            refreshConversations()
        } else {
            scheduleSave()
        }
        return true
    }

    /**
     * 从第 [index] 条（含）开始截断：内存里丢掉，库里把对应节点连消息一起删。
     *
     * 「编辑重发」用：编辑一条旧消息并发送 = 那条及其之后的所有内容作废，
     * 新的内容再作为新消息追加（用户 2026-10-15 定的行为）。
     */
    fun truncateFrom(
        index: Int,
        /** 编辑/重发马上要复用的附件，不能跟旧消息一起删。 */
        preserveAttachmentPaths: Set<String> = emptySet(),
    ): Boolean {
        if (index < 0 || index >= entries.size) return false
        val target = entries[index]
        if (target.message.localOnly) {
            entries = entries.take(index)
            publish()
            return true
        }
        // 先保存当前尾部，避免截断前面的消息时丢掉刚流式写入的最新内容。
        saveNow()
        val remaining = entries.take(index)
        val dbOk = conversationId?.let { id ->
            runCatching {
                ChatDatabase.deleteNodesFrom(
                    id,
                    target.sortOrder,
                )
            }.isSuccess
        } ?: true
        if (!dbOk) {
            Log.e("HubChatStore", "截断会话失败，内存保持不变")
            return false
        }
        dropAttachments(
            dropped = entries.drop(index).map { it.message },
            keepPaths = attachmentPaths(remaining) +
                allAttachmentPathsExcept(conversationId) +
                preserveAttachmentPaths,
        )
        entries = remaining
        publish()
        return true
    }

    /**
     * 删掉第 [index] 条 —— **只删这一条**，前后的输入/输出都不动（「更多」栏里的「删除」）。
     *
     * 库里删完还要 [ChatDatabase.renumber] 重新编号：内存按下标、`truncateFrom` / `append` 也按下标写
     * `sort_order`，中间挖空一个号，下一次 append 就会和已有的号撞上。
     */
    fun removeAt(index: Int) {
        if (index < 0 || index >= entries.size) return
        val target = entries[index]
        if (target.message.localOnly) {
            entries = entries.filterIndexed { i, _ -> i != index }
            publish()
            return
        }
        saveNow()
        val nodeId = target.nodeId
        val removedOrder = target.sortOrder
        val remaining = entries.toMutableList().also { it.removeAt(index) }
        val dbOk = conversationId?.let { id ->
            runCatching {
                ChatDatabase.deleteNode(id, nodeId)
                ChatDatabase.renumberAll(id)
            }.isSuccess
        } ?: true
        if (!dbOk) {
            Log.e("HubChatStore", "删除消息失败，内存保持不变")
            return
        }
        dropAttachments(
            dropped = listOf(target.message),
            keepPaths = attachmentPaths(remaining) + allAttachmentPathsExcept(conversationId),
        )
        // 库里 renumberAll 会把后面的 sort_order 全部减一，内存窗口也要同步，
        // 否则下一次向上翻页会拿旧的边界去查，造成重复/漏消息。
        entries = remaining.map { entry ->
            if (!entry.message.localOnly && entry.sortOrder > removedOrder) {
                Entry(entry.nodeId, entry.versionIndex, entry.sortOrder - 1, entry.message)
            } else {
                entry
            }
        }
        publish()
    }

    /** 立刻写，不等那半秒（界面销毁 / 流式结束时用）。 */
    fun flush() {
        saveNow()
    }

    private fun ensureConversation(title: String): String {
        conversationId?.let { return it }
        return ChatDatabase.newConversation(title).also {
            conversationId = it
            currentConversationId = it
            persistActive(it)
        }
    }

    private fun scheduleSave() {
        val conversation = conversationId ?: return
        val last = entries.lastOrNull { !it.message.localOnly } ?: return
        val epoch = ++saveEpoch
        pendingSave = PendingSave(conversation, last.nodeId, last.versionIndex, last.message, epoch)
        // 不取消旧任务：每 500ms 取一次“当前最新快照”写盘。
        // 这样连续流式输出（间隔小于 500ms）也会周期性落盘，而不是等到流结束。
        synchronized(saveLock) {
            if (saveLoopRunning) return
            saveLoopRunning = true
        }
        val job = saveScope.launch {
            while (true) {
                delay(SAVE_DELAY_MS)
                val snapshot = synchronized(saveLock) {
                    val value = pendingSave
                    if (value == null) {
                        // 和 scheduleSave 用同一把锁：如果它刚好在 break 前塞入新快照，
                        // 它能看到 loop 已经退出并启动下一轮，不会把最后一帧丢掉。
                        saveLoopRunning = false
                    } else {
                        pendingSave = null
                    }
                    value
                } ?: break
                if (snapshot.epoch != saveEpoch) continue
                runCatching {
                    synchronized(saveLock) {
                        // epoch 在拿到锁后再检查：等待锁期间主线程可能已经写了更新版本。
                        if (snapshot.epoch == saveEpoch) {
                            ChatDatabase.updateMessage(
                                conversationId = snapshot.conversationId,
                                nodeId = snapshot.nodeId,
                                versionIndex = snapshot.versionIndex,
                                message = snapshot.message,
                                indexSearch = false,
                            )
                        }
                    }
                }.onFailure { Log.e("HubChatStore", "自动保存失败", it) }
            }
        }
        saveJob = job
        job.invokeOnCompletion {
            if (saveJob === job) saveJob = null
        }
    }

    private fun saveNow() {
        // 让已经排队/正在跑的自动落盘作废，避免旧快照最后覆盖新内容。
        saveEpoch++
        synchronized(saveLock) {
            pendingSave = null
            saveLoopRunning = false
        }
        saveJob?.cancel()
        saveJob = null
        val conversation = conversationId ?: return
        val last = entries.lastOrNull { !it.message.localOnly } ?: return
        val finalMessage = !last.message.streaming
        try {
            synchronized(saveLock) {
                ChatDatabase.updateMessage(
                    conversationId = conversation,
                    nodeId = last.nodeId,
                    versionIndex = last.versionIndex,
                    message = last.message,
                    // 收尾的全文索引放到后台，主线程只写正文这一行
                    indexSearch = !finalMessage,
                )
            }
        } catch (t: Throwable) {
            Log.e("HubChatStore", "立即保存失败", t)
        }
        if (finalMessage) {
            val nodeId = last.nodeId
            val versionIndex = last.versionIndex
            val text = last.message.text
            saveScope.launch {
                runCatching { ChatDatabase.indexMessage(nodeId, versionIndex, text) }
                    .onFailure { Log.e("HubChatStore", "后台搜索索引失败", it) }
            }
        }
    }

    /** 丢弃后台保存任务；随后通常要删除会话或重建内存窗口。 */
    private fun cancelPendingSave() {
        saveEpoch++
        synchronized(saveLock) {
            pendingSave = null
            saveLoopRunning = false
        }
        saveJob?.cancel()
        saveJob = null
    }

    private fun refreshConversations() {
        conversations = runCatching { ChatDatabase.conversations() }
            .onFailure { Log.e("HubChatStore", "读取会话列表失败", it) }
            .getOrDefault(emptyList())
    }

    private fun persistActive(id: String?) {
        appContext?.let { UiPrefs.setActiveConversationId(it, id) }
    }

    /** 内存 → Compose 状态。 */
    private fun publish() {
        messages = entries.map { it.message }
    }
}

/** 启动清理还没跑完时，先把“空助手占位”挡在 UI 之外。 */
private fun HubStoredMessage.isIncompletePlaceholder(): Boolean =
    role == "assistant" && text.isBlank() && attachments.isEmpty()
