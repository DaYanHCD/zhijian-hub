package com.fingertipfuture.hub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.util.UUID

/** 一行会话（左边抽屉列表里的一行；列表 UI 下一波做，这里先把表和数据准备好）。 */
internal data class HubConversation(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
)

/** 一次「搜索聊天」命中：会话标题命中，或某条消息命中。 */
internal data class ChatSearchHit(
    val conversationId: String,
    val conversationTitle: String,
    /** user / assistant；标题命中时为空。 */
    val role: String,
    /** 原始正文；界面按关键词截一段展示。 */
    val text: String,
    val at: Long,
    /** 消息在会话里的顺序（message_node.sort_order）；-1 = 只命中了会话标题。 */
    val messageIndex: Int,
)

/** 一条消息在库里的一行（= 某个节点的一个版本）。 */
internal data class HubStoredMessage(
    val nodeId: String,
    val versionIndex: Int,
    val sortOrder: Int,
    val role: String,
    val text: String,
    val model: String,
    val reasoning: String,
    val attachments: List<HubAttachment>,
    val at: Long,
    val thoughtMs: Long,
    val skillsUsed: List<HubSkillTrace>,
    val searchTraces: List<HubSearchTrace>,
    val thinkingSteps: List<HubThinkingStep>,
) {
    fun toHubMessage() = HubMessage(
        id = "$nodeId-$versionIndex",
        role = role,
        text = text,
        at = at,
        model = model,
        reasoning = reasoning,
        attachments = attachments,
        thoughtMs = thoughtMs,
        skillsUsed = skillsUsed,
        searchTraces = searchTraces,
        thinkingSteps = thinkingSteps,
    )
}

internal data class HubModelUsage(
    val model: String,
    val messages: Int,
    val usage: HubTokenUsage,
)

/**
 * 对话库：**手写 SQL 的 SQLite**（不用 Room —— 铁律一：不引第三方库；SQLite 是安卓自带的）。
 *
 * 为什么从「一个 JSON 文件」换成数据库：文件型存一段对话没问题，但
 * 「重新生成 = 同一个位置追加一个版本、气泡上 2/3 切换」这种结构表达不了，
 * 会话一多，搜索/统计也只能把所有文件读一遍。表结构见下面三条 DDL。
 *
 * 线程：所有方法都是**同步**的，调用方一般在主线程（Android 的 SQLite 有缓存，
 * 我们一次只写一行，几毫秒级；流式那种高频写入由 `ChatStore` 攒 500 ms 再写）。
 * 单进程自用，不开 WAL —— 保持磁盘上只有一个 `hub.db` 文件，好备份、好排查。
 */
internal object ChatDatabase {

    private const val TAG = "HubDb"
    const val FILE_NAME = "hub.db"
    private const val SCHEMA_VERSION = 4
    /** 启动清理只处理这个时间之前的空助手消息，避免误删刚插入的流式占位。 */
    private const val INCOMPLETE_MESSAGE_GRACE_MS = 30 * 60_000L

    private var db: SQLiteDatabase? = null
    /** 读连接：WAL 下和写连接分开，长查询不会把主线程的发送写入堵住。 */
    private var readDb: SQLiteDatabase? = null

    // ---- 表结构（v1）----
    //
    // 会话：抽屉列表里的一行
    private const val SQL_CONVERSATION = """
        CREATE TABLE conversation (
            id         TEXT PRIMARY KEY,
            title      TEXT NOT NULL DEFAULT '',
            pinned     INTEGER NOT NULL DEFAULT 0,
            folder_id  TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """
    private const val SQL_CONVERSATION_INDEX =
        "CREATE INDEX idx_conversation_updated ON conversation(pinned DESC, updated_at DESC)"

    // 节点 = 对话里的「一个位置」（一轮提问、一轮回答各占一个）。
    // 单独一张表是为了「重新生成 / 编辑重发」：那是给同一个位置**追加一个版本**，
    // 不是往列表尾部再加一条。位置与版本拆开，将来才不用改数据模型。
    private const val SQL_NODE = """
        CREATE TABLE message_node (
            id              TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
            parent_node_id  TEXT,
            select_index    INTEGER NOT NULL DEFAULT 0,
            sort_order      INTEGER NOT NULL
        )
    """
    private const val SQL_NODE_INDEX =
        "CREATE INDEX idx_node_conversation ON message_node(conversation_id, sort_order)"

    // 消息 = 某个节点的一个版本（现在每个节点只有一个版本，version_index 都是 0）
    private const val SQL_MESSAGE = """
        CREATE TABLE message (
            id               TEXT PRIMARY KEY,
            node_id          TEXT NOT NULL REFERENCES message_node(id) ON DELETE CASCADE,
            version_index    INTEGER NOT NULL DEFAULT 0,
            role             TEXT NOT NULL,
            content          TEXT NOT NULL DEFAULT '',
            reasoning        TEXT NOT NULL DEFAULT '',
            attachments_json TEXT NOT NULL DEFAULT '[]',
            model            TEXT,
            at               INTEGER NOT NULL,
            thought_ms       INTEGER NOT NULL DEFAULT 0,
            skills_json      TEXT NOT NULL DEFAULT '[]',
            search_traces_json TEXT NOT NULL DEFAULT '[]',
            thinking_steps_json TEXT NOT NULL DEFAULT '[]'
        )
    """
    private const val SQL_MESSAGE_INDEX =
        "CREATE UNIQUE INDEX idx_message_version ON message(node_id, version_index)"

    fun initialize(context: Context) {
        if (db != null) return
        val app = context.applicationContext
        val opened = openOrRecover(app)
        db = opened
        runCatching { ChatSearchIndex.prepare(opened) }
            .onFailure { Log.e(TAG, "搜索索引初始化失败", it) }
        runCatching { ensureUsageTable(opened) }
            .onFailure { Log.e(TAG, "创建用量表失败", it) }
        runCatching { migrateLegacyJsonIfNeeded(app) }
            .onFailure { Log.e(TAG, "旧对话迁移失败", it) }
        readDb = runCatching {
            SQLiteDatabase.openDatabase(
                app.getDatabasePath(FILE_NAME).absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        }.onFailure { Log.w(TAG, "只读连接打开失败，读操作回退到主连接", it) }
            .getOrNull()
    }

    /**
     * 数据库文件损坏时不要让 App 每次启动都直接崩。
     *
     * 把损坏文件改名保留（`hub.db.corrupt-<时间戳>`），再建一个空库；
     * 用户至少还能继续用，损坏文件也留给人工抢救。
     */
    private fun openOrRecover(app: Context): SQLiteDatabase = try {
        DbHelper(app).writableDatabase
    } catch (t: Throwable) {
        val message = (t.message ?: "").lowercase()
        val recoverable = message.contains("malformed") ||
            message.contains("corrupt") ||
            message.contains("not a database") ||
            message.contains("file is encrypted")
        if (!recoverable) throw t
        Log.e(TAG, "hub.db 损坏，改名后重建空库", t)
        val databaseFile = app.getDatabasePath(FILE_NAME)
        val backup = File(
            databaseFile.parentFile,
            FILE_NAME + ".corrupt-" + System.currentTimeMillis(),
        )
        runCatching { databaseFile.renameTo(backup) }
        runCatching { File(databaseFile.path + "-wal").delete() }
        runCatching { File(databaseFile.path + "-shm").delete() }
        DbHelper(app).writableDatabase
    }

    private fun requireDb(): SQLiteDatabase = db ?: error("ChatDatabase.initialize() 还没调用")
    private fun requireReadDb(): SQLiteDatabase = readDb ?: requireDb()

    /**
     * 把一组数据库操作放进同一个事务。
     *
     * SQLiteDatabase 支持嵌套事务（内部按 savepoint 处理），所以导入时内部
     * appendMessage/newConversation 自己的事务不会破坏外层回滚。
     */
    fun <T> transaction(block: () -> T): T {
        val database = requireDb()
        database.beginTransaction()
        return try {
            val result = block()
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }

    /** 二字母索引重建可能很慢，放到后台做；未完成前搜索自动回退 LIKE。 */
    suspend fun warmSearchIndex() {
        val database = requireDb()
        // 清理空助手消息 + 重排 sort_order 也放到 IO：这一步会碰每一段会话，
        // 放在启动主线程会让历史多的用户开 App 明显卡住。
        runCatching { cleanupIncompleteAssistantMessages(database) }
            .onFailure { Log.e(TAG, "清理未完成回复失败", it) }
        ChatSearchIndex.warm(database)
    }

    /** 进程在首个正文片段前被杀时会留下空助手消息，启动时直接清掉。 */
    private fun cleanupIncompleteAssistantMessages(database: SQLiteDatabase) {
        database.beginTransaction()
        try {
            database.execSQL(
                """
                DELETE FROM message_node
                WHERE id IN (
                    SELECT node_id FROM message
                    WHERE role = 'assistant'
                      AND content = ''
                      AND attachments_json = '[]'
                      AND at < ?
                )
                """.trimIndent(),
                arrayOf(System.currentTimeMillis() - INCOMPLETE_MESSAGE_GRACE_MS),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        database.rawQuery("SELECT id FROM conversation", null).use { cursor ->
            val ids = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            ids.forEach(::compactSortOrder)
        }
    }

    private fun ensureUsageTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_usage (
                message_id   TEXT PRIMARY KEY REFERENCES message(id) ON DELETE CASCADE,
                input_tokens INTEGER NOT NULL DEFAULT 0,
                output_tokens INTEGER NOT NULL DEFAULT 0,
                cached_tokens INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    private fun newId(prefix: String) = prefix + UUID.randomUUID().toString()

    // ---------------- 会话 ----------------

    /** 抽屉里的全部会话：置顶优先，其余按最近更新时间倒序。 */
    fun conversations(): List<HubConversation> =
        requireDb().rawQuery(
            "SELECT id, title, pinned, created_at, updated_at, folder_id FROM conversation " +
                "ORDER BY pinned DESC, updated_at DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HubConversation(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            pinned = cursor.getInt(2) != 0,
                            createdAt = cursor.getLong(3),
                            updatedAt = cursor.getLong(4),
                            folderId = cursor.getString(5),
                        ),
                    )
                }
            }
        }

    /** 新建一段空会话，返回它的 id。 */
    fun newConversation(title: String, at: Long = System.currentTimeMillis()): String {
        val id = newId("c-")
        val row = requireDb().insert("conversation", null, ContentValues().apply {
            put("id", id)
            put("title", title.take(60))
            put("pinned", 0)
            put("created_at", at)
            put("updated_at", at)
        })
        if (row == -1L) error("无法创建会话")
        return id
    }

    /** 会话还是空标题时，用第一条用户消息补上（列表 UI 要显示它）。 */
    fun setTitleIfEmpty(conversationId: String, title: String) {
        if (title.isBlank()) return
        requireDb().execSQL(
            "UPDATE conversation SET title = ? WHERE id = ? AND (title IS NULL OR title = '')",
            arrayOf(title.take(60), conversationId),
        )
    }

    /** 改名（「重新生成标题」/ 以后的手动改名都走这里）。 */
    fun renameConversation(conversationId: String, title: String) {
        requireDb().update(
            "conversation",
            ContentValues().apply { put("title", title.trim().take(60)) },
            "id = ?",
            arrayOf(conversationId),
        )
    }

    fun setPinned(conversationId: String, pinned: Boolean) {
        requireDb().update(
            "conversation",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ?",
            arrayOf(conversationId),
        )
    }

    /** 移动到文件夹；传 null = 移出文件夹。 */
    fun setFolder(conversationId: String, folderId: String?) {
        requireDb().update(
            "conversation",
            ContentValues().apply {
                if (folderId == null) putNull("folder_id") else put("folder_id", folderId)
            },
            "id = ?",
            arrayOf(conversationId),
        )
    }

    /** 文件夹被删掉时，把所有指向它的会话移回未分类。 */
    fun clearFolder(folderId: String) {
        requireDb().execSQL(
            "UPDATE conversation SET folder_id = NULL WHERE folder_id = ?",
            arrayOf(folderId),
        )
    }

    /** 搜索全部会话：标题命中 + 正文命中。 */
    fun searchConversations(
        query: String,
        limit: Int = 200,
        conversationId: String? = null,
    ): List<ChatSearchHit> =
        ChatSearchIndex.search(requireDb(), query, limit, conversationId)

    /** 删掉一段会话（它的节点与消息由外键级联一起删）。 */
    fun deleteConversation(conversationId: String) {
        requireDb().delete("conversation", "id = ?", arrayOf(conversationId))
    }

    /**
     * 删除全部会话相关的所有行。
     *
     * 不单靠 ON DELETE CASCADE：历史版本删会话时曾经留下过孤儿节点/消息，
     * 这里按依赖顺序显式清一遍，顺便把旧垃圾一起收掉。
     */
    fun deleteAllConversations() {
        val db = requireDb()
        db.beginTransaction()
        try {
            db.delete("message_usage", null, null)
            db.delete("message_gram", null, null)
            db.delete("message", null, null)
            db.delete("message_node", null, null)
            db.delete("conversation", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 所有版本消息引用到的附件路径（删全部对话时用来同步清理文件）。 */
    fun allAttachmentPaths(): Set<String> =
        requireDb().rawQuery("SELECT attachments_json FROM message", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    HubAttachment.listFromJson(
                        runCatching { org.json.JSONArray(cursor.getString(0)) }.getOrNull(),
                    ).forEach { attachment ->
                        if (attachment.path.isNotBlank()) add(attachment.path)
                    }
                }
            }
        }

    /** 除某个会话外，其它会话引用到的附件路径（删会话时避免误删共用文件）。 */
    fun attachmentPathsExcept(conversationId: String): Set<String> =
        requireDb().rawQuery(
            """
            SELECT m.attachments_json
            FROM message m JOIN message_node n ON n.id = m.node_id
            WHERE n.conversation_id <> ?
            """.trimIndent(),
            arrayOf(conversationId),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    HubAttachment.listFromJson(
                        runCatching { org.json.JSONArray(cursor.getString(0)) }.getOrNull(),
                    ).forEach { attachment ->
                        if (attachment.path.isNotBlank()) add(attachment.path)
                    }
                }
            }
        }

    /**
     * 从消息里移除已经删掉的文件引用。
     *
     * 「清空全部附件」只删文件会把聊天里的图片/文件气泡变成永远打不开的死链接，
     * 这里在同一事务里把对应的 `attachments_json` 清干净。
     */
    fun stripAttachmentReferences(paths: Set<String>): Int {
        if (paths.isEmpty()) return 0
        val database = requireDb()
        val updates = mutableListOf<Pair<String, String>>()
        database.rawQuery("SELECT id, attachments_json FROM message", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val raw = cursor.getString(1)
                val items = HubAttachment.listFromJson(
                    runCatching { org.json.JSONArray(raw) }.getOrNull(),
                )
                val kept = items.filterNot { it.path in paths }
                if (kept.size != items.size) {
                    updates += id to HubAttachment.listToJson(kept).toString()
                }
            }
        }
        if (updates.isEmpty()) return 0
        database.beginTransaction()
        try {
            updates.forEach { (id, json) ->
                database.update(
                    "message",
                    ContentValues().apply { put("attachments_json", json) },
                    "id = ?",
                    arrayOf(id),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return updates.size
    }

    /** 清空对话后回收数据库空闲页，避免文件大小一直停在旧值。 */
    fun vacuum() {
        runCatching { requireDb().execSQL("VACUUM") }
    }

    // ---------------- 消息 ----------------

    private const val MESSAGE_COLUMNS = """
        n.id, m.version_index, n.sort_order, m.role, m.content, m.reasoning,
        m.attachments_json, m.at, m.thought_ms, m.skills_json,
        m.search_traces_json, m.thinking_steps_json, m.model
    """

    private fun readMessages(cursor: android.database.Cursor): List<HubStoredMessage> =
        buildList {
            while (cursor.moveToNext()) {
                add(
                    HubStoredMessage(
                        nodeId = cursor.getString(0),
                        versionIndex = cursor.getInt(1),
                        sortOrder = cursor.getInt(2),
                        role = cursor.getString(3),
                        text = cursor.getString(4),
                        reasoning = cursor.getString(5),
                        attachments = HubAttachment.listFromJson(
                            runCatching { org.json.JSONArray(cursor.getString(6)) }.getOrNull(),
                        ),
                        at = cursor.getLong(7),
                        thoughtMs = cursor.getLong(8),
                        skillsUsed = skillTracesFromJson(
                            runCatching { org.json.JSONArray(cursor.getString(9)) }.getOrNull(),
                        ),
                        searchTraces = searchTracesFromJson(
                            runCatching { org.json.JSONArray(cursor.getString(10)) }.getOrNull(),
                        ),
                        thinkingSteps = thinkingStepsFromJson(
                            runCatching { org.json.JSONArray(cursor.getString(11)) }.getOrNull(),
                        ),
                        model = cursor.getString(12).orEmpty(),
                    ),
                )
            }
        }

    /** 读出一段会话的全部消息，按顺序（节点顺序 → 版本序号）。 */
    fun messages(conversationId: String): List<HubStoredMessage> =
        requireReadDb().rawQuery(
            """
            SELECT $MESSAGE_COLUMNS
            FROM message_node n JOIN message m ON m.node_id = n.id
            WHERE n.conversation_id = ?
              AND m.version_index = (
                  SELECT MAX(v.version_index) FROM message v WHERE v.node_id = n.id
              )
            ORDER BY n.sort_order ASC, m.version_index ASC
            """.trimIndent(),
            arrayOf(conversationId),
        ).use(::readMessages)

    /** 只取最近 [limit] 条（分页首屏）。 */
    fun recentMessages(conversationId: String, limit: Int): List<HubStoredMessage> =
        requireReadDb().rawQuery(
            """
            SELECT $MESSAGE_COLUMNS
            FROM message_node n JOIN message m ON m.node_id = n.id
            WHERE n.conversation_id = ?
              AND m.version_index = (
                  SELECT MAX(v.version_index) FROM message v WHERE v.node_id = n.id
              )
            ORDER BY n.sort_order DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(conversationId, limit.toString()),
        ).use(::readMessages).asReversed()

    /** 取比 [beforeSortOrder] 更早的一页，返回时仍是正序。 */
    fun messagesBefore(
        conversationId: String,
        beforeSortOrder: Int,
        limit: Int,
    ): List<HubStoredMessage> =
        requireReadDb().rawQuery(
            """
            SELECT $MESSAGE_COLUMNS
            FROM message_node n JOIN message m ON m.node_id = n.id
            WHERE n.conversation_id = ?
              AND n.sort_order < ?
              AND m.version_index = (
                  SELECT MAX(v.version_index) FROM message v WHERE v.node_id = n.id
              )
            ORDER BY n.sort_order DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(conversationId, beforeSortOrder.toString(), limit.toString()),
        ).use(::readMessages).asReversed()

    /** 会话下一个可用的 sort_order（分页后不能再用内存条数推算）。 */
    fun nextSortOrder(conversationId: String): Int =
        requireDb().rawQuery(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /** 统计用：全部会话的当前版本消息（跨会话）。 */
    fun allMessages(): List<HubStoredMessage> =
        requireReadDb().rawQuery(
            """
            SELECT $MESSAGE_COLUMNS
            FROM message_node n
            JOIN message m ON m.node_id = n.id
            WHERE m.version_index = (
                SELECT MAX(v.version_index) FROM message v WHERE v.node_id = n.id
            )
            ORDER BY n.conversation_id, n.sort_order ASC
            """.trimIndent(),
            null,
        ).use(::readMessages)

    fun messageCount(): Int =
        requireDb().rawQuery(
            "SELECT COUNT(DISTINCT node_id) FROM message",
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /** 记录一条助手消息的 token 用量（同一消息重复回调时覆盖）。 */
    fun setUsage(messageId: String, usage: HubTokenUsage) {
        requireDb().insertWithOnConflict(
            "message_usage",
            null,
            ContentValues().apply {
                put("message_id", messageId)
                put("input_tokens", usage.input)
                put("output_tokens", usage.output)
                put("cached_tokens", usage.cached)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** 全部会话的 token 合计。 */
    fun usageTotals(): HubTokenUsage =
        requireDb().rawQuery(
            "SELECT COALESCE(SUM(input_tokens),0), COALESCE(SUM(output_tokens),0), " +
                "COALESCE(SUM(cached_tokens),0) FROM message_usage",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                HubTokenUsage(
                    input = cursor.getInt(0),
                    output = cursor.getInt(1),
                    cached = cursor.getInt(2),
                )
            } else {
                HubTokenUsage()
            }
        }

    /** 按模型汇总：回复条数 + 接口返回的 token。老消息没有模型名时不出现在这里。 */
    fun modelUsage(): List<HubModelUsage> =
        requireDb().rawQuery(
            """
            SELECT m.model,
                   COUNT(*),
                   COALESCE(SUM(u.input_tokens), 0),
                   COALESCE(SUM(u.output_tokens), 0),
                   COALESCE(SUM(u.cached_tokens), 0)
            FROM message m
            LEFT JOIN message_usage u ON u.message_id = m.id
            WHERE m.role != 'user' AND m.model IS NOT NULL AND m.model != ''
              AND m.version_index = (
                  SELECT MAX(v.version_index) FROM message v WHERE v.node_id = m.node_id
              )
            GROUP BY m.model
            ORDER BY COUNT(*) DESC, m.model ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HubModelUsage(
                            model = cursor.getString(0),
                            messages = cursor.getInt(1),
                            usage = HubTokenUsage(
                                input = cursor.getInt(2),
                                output = cursor.getInt(3),
                                cached = cursor.getInt(4),
                            ),
                        ),
                    )
                }
            }
        }

    /**
     * 追加一条消息：建一个节点 + 写一条消息 + 顶一下会话的 updated_at，**一个事务里做完**。
     *
     * [parentNodeId] 是上一个节点的 id（Fork / 分支将来要用），[sortOrder] 是它在会话里的顺序。
     */
    fun appendMessage(
        conversationId: String,
        nodeId: String,
        parentNodeId: String?,
        sortOrder: Int,
        message: HubMessage,
    ) {
        val database = requireDb()
        database.beginTransaction()
        try {
            val nodeRow = database.insert("message_node", null, ContentValues().apply {
                put("id", nodeId)
                put("conversation_id", conversationId)
                if (parentNodeId == null) putNull("parent_node_id") else put("parent_node_id", parentNodeId)
                put("select_index", 0)
                put("sort_order", sortOrder)
            })
            if (nodeRow == -1L) error("无法创建消息节点")
            val messageRow = database.insert("message", null, messageValues(nodeId, 0, message))
            if (messageRow == -1L) error("无法写入消息")
            ChatSearchIndex.index(database, "$nodeId-0", message.text)
            touch(database, conversationId, message.at)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /**
     * 从第 [sortOrder] 个位置（含）起，把后面的节点全删掉（消息由外键级联一起走）。
     *
     * 用在「编辑重发」：改一条旧消息 = 那条及其之后的所有内容作废，
     * 新的内容再从头追加（不是"追加一个版本"那种分叉，用户 2026-10-15 要的就是清掉重来）。
     */
    fun deleteNodesFrom(conversationId: String, sortOrder: Int) {
        val database = requireDb()
        database.delete(
            "message_node",
            "conversation_id = ? AND sort_order >= ?",
            arrayOf(conversationId, sortOrder.toString()),
        )
        touch(database, conversationId, System.currentTimeMillis())
    }

    /**
     * 删掉**一个**节点（消息靠外键 ON DELETE CASCADE 一起走）。
     *
     * 「更多 → 删除这条消息」用：**只删这一条**，前后的输入/输出都留着 ——
     * 和 [deleteNodesFrom] 那种"这条及其之后全删"不是一回事。
     */
    fun deleteNode(conversationId: String, nodeId: String) {
        val database = requireDb()
        database.delete(
            "message_node",
            "conversation_id = ? AND id = ?",
            arrayOf(conversationId, nodeId),
        )
        touch(database, conversationId, System.currentTimeMillis())
    }

    /**
     * 重新编号：按 [nodeIds] 的顺序把剩下的节点写成 0..n-1。
     *
     * ⚠️ 为什么必须做：内存里用的是**下标**，而 [deleteNodesFrom] / `appendMessage` 都是按下标写 `sort_order` 的。
     * 删掉中间一条会留一个号洞，下次 append 就会和已有的号撞上（顺序乱、之后 truncate 会删错行）。
     */
    fun renumber(
        conversationId: String,
        nodeIds: List<String>,
        touchConversation: Boolean = true,
    ) {
        val database = requireDb()
        database.beginTransaction()
        try {
            nodeIds.forEachIndexed { index, nodeId ->
                val values = ContentValues().apply {
                    put("sort_order", index)
                    val parent = nodeIds.getOrNull(index - 1)
                    if (parent == null) putNull("parent_node_id") else put("parent_node_id", parent)
                }
                database.update(
                    "message_node",
                    values,
                    "conversation_id = ? AND id = ?",
                    arrayOf(conversationId, nodeId),
                )
            }
            if (touchConversation) {
                database.execSQL(
                    "UPDATE conversation SET updated_at = ? WHERE id = ?",
                    arrayOf(System.currentTimeMillis(), conversationId),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** 按库里的真实顺序重排整段会话（分页后内存里只有一段窗口，不能拿窗口当全集）。 */
    fun renumberAll(conversationId: String) {
        val ids = requireDb().rawQuery(
            "SELECT id FROM message_node WHERE conversation_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(conversationId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        if (ids.isNotEmpty()) renumber(conversationId, ids)
    }

    /** 修复旧版本留下的 sort_order 空洞。 */
    fun compactSortOrder(conversationId: String) {
        val rows = requireDb().rawQuery(
            "SELECT id, sort_order FROM message_node " +
                "WHERE conversation_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(conversationId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
            }
        }
        // 大多数会话本来就是 0..n-1。以前每次启动都无条件 renumber，
        // 等于把全部消息节点重写一遍；先只读检查，没空洞/重号就不写。
        val hasHole = rows.withIndex().any { (index, row) -> row.second != index }
        val hasDuplicate = rows.zipWithNext().any { (left, right) -> left.second == right.second }
        if (rows.isNotEmpty() && (hasHole || hasDuplicate)) {
            renumber(conversationId, rows.map { it.first }, touchConversation = false)
        }
    }

    /**
     * 就地改写一个版本的内容（流式输出最后那一条就是反复改它）。
     *
     * 只 UPDATE 一行 —— 这正是换成数据库最直接的收益：以前每来一片增量，
     * 都要把整段对话重新写成一个 JSON 文件。
     */
    fun updateMessage(
        conversationId: String,
        nodeId: String,
        versionIndex: Int,
        message: HubMessage,
        /**
         * false = 只写正文，搜索索引交给后台线程补。
         * 长回复收尾时在建 bigram 索引上可能要执行上万次 INSERT，不能再堵主线程。
         */
        indexSearch: Boolean = true,
    ) {
        val database = requireDb()
        database.beginTransaction()
        try {
            val rows = database.update(
                "message",
                messageValues(nodeId, versionIndex, message),
                "node_id = ? AND version_index = ?",
                arrayOf(nodeId, versionIndex.toString()),
            )
            if (rows == 0) {
                Log.w(TAG, "updateMessage 没有命中：node=$nodeId version=$versionIndex")
            }
            if (indexSearch) {
                // 流式中间态不建索引（正文每 500ms 变一次）；收尾时再建，
                // 搜索能搜到完整回复，又不会为每个增量反复删/插几百个 gram。
                if (message.streaming) {
                    ChatSearchIndex.remove(database, "$nodeId-$versionIndex")
                } else {
                    ChatSearchIndex.index(database, "$nodeId-$versionIndex", message.text)
                }
            }
            touch(database, conversationId, System.currentTimeMillis())
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** 后台补一条消息的搜索索引（正文已经落库，失败只影响搜索，不影响聊天）。 */
    fun indexMessage(nodeId: String, versionIndex: Int, text: String) {
        val database = requireDb()
        database.beginTransaction()
        try {
            ChatSearchIndex.index(database, "$nodeId-$versionIndex", text)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun messageValues(nodeId: String, versionIndex: Int, message: HubMessage) = ContentValues().apply {
        put("id", "$nodeId-$versionIndex")
        put("node_id", nodeId)
        put("version_index", versionIndex)
        put("role", message.role)
        put("content", message.text)
        put("reasoning", message.reasoning)
        put("attachments_json", HubAttachment.listToJson(message.attachments).toString())
        if (message.model.isBlank()) putNull("model") else put("model", message.model)
        put("at", message.at)
        put("thought_ms", message.thoughtMs)
        put("skills_json", skillTracesToJson(message.skillsUsed).toString())
        put("search_traces_json", searchTracesToJson(message.searchTraces).toString())
        put("thinking_steps_json", thinkingStepsToJson(message.thinkingSteps).toString())
    }

    private fun touch(database: SQLiteDatabase, conversationId: String, at: Long) {
        database.execSQL(
            "UPDATE conversation SET updated_at = ? WHERE id = ? AND updated_at < ?",
            arrayOf(at, conversationId, at),
        )
    }

    // ---------------- 旧数据迁移（0.3 之前是 filesDir/chats/current.json） ----------------

    /**
     * 第一版落盘用的是「一个 JSON 文件」。这里在**建库时**把它导进来，然后改名为
     * `.migrated` 留着（不删：万一迁移写坏了，文件还在，人工能捞回来）。
     */
    private fun migrateLegacyJsonIfNeeded(context: Context) {
        val dir = File(context.filesDir, "chats")
        val legacy = File(dir, "current.json")
        if (!legacy.exists()) return
        val marker = context.getSharedPreferences("hub_db_migration", Context.MODE_PRIVATE)
        if (marker.getBoolean("legacy_current_json_v1", false)) return
        runCatching {
            val messages = ChatArchive.decode(legacy.readText()).orEmpty().filterNot { it.localOnly }
            if (messages.isNotEmpty()) {
                val title = messages.firstOrNull { it.role == "user" && it.text.isNotBlank() }
                    ?.text?.replace('\n', ' ')?.take(24).orEmpty()
                val conversationId = newConversation(title, messages.first().at)
                var parent: String? = null
                messages.forEachIndexed { index, message ->
                    val nodeId = newId("n-")
                    appendMessage(conversationId, nodeId, parent, index, message)
                    parent = nodeId
                }
            }
            marker.edit().putBoolean("legacy_current_json_v1", true).apply()
            val renamed = legacy.renameTo(File(dir, "current.json.migrated"))
            if (renamed) {
                Log.i(TAG, "已把旧对话迁移进 hub.db（${messages.size} 条）")
            } else {
                Log.w(TAG, "旧对话已导入，但 current.json 无法改名；下次启动可能重复导入")
            }
        }.onFailure {
            // 迁移失败就不动那个文件，下次启动还会再试一遍
            Log.w(TAG, "旧对话迁移失败: " + it.javaClass.name + " / " + it.message)
        }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, FILE_NAME, null, SCHEMA_VERSION) {

        init {
            // WAL：读不阻塞写、写不阻塞读；写连接仍只有一个，写之间继续串行。
            setWriteAheadLoggingEnabled(true)
        }

        /** 外键级联（删会话 → 它的节点与消息一起走）要每个连接显式打开。 */
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CONVERSATION)
            db.execSQL(SQL_CONVERSATION_INDEX)
            db.execSQL(SQL_NODE)
            db.execSQL(SQL_NODE_INDEX)
            db.execSQL(SQL_MESSAGE)
            db.execSQL(SQL_MESSAGE_INDEX)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 加字段 / 加表都按版本号往下走，不重建表（对话是用户的东西，迁移不能弄丢）。
            if (oldVersion < 2) {
                // v2：消息多一列「思考了 X 秒」（见 HubMessage.thoughtMs）
                db.execSQL("ALTER TABLE message ADD COLUMN thought_ms INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 3) {
                // v3：思考框里的 Skill / 搜索活动时间线。
                db.execSQL("ALTER TABLE message ADD COLUMN skills_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE message ADD COLUMN search_traces_json TEXT NOT NULL DEFAULT '[]'")
            }
            if (oldVersion < 4) {
                // v4：思考、位置、搜索、再思考按真实执行顺序组成的整条时间线。
                db.execSQL("ALTER TABLE message ADD COLUMN thinking_steps_json TEXT NOT NULL DEFAULT '[]'")
            }
            if (newVersion > 4) {
                throw IllegalStateException("hub.db 还没写过 $oldVersion → $newVersion 的迁移")
            }
        }
    }
}
