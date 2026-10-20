package com.fingertipfuture.hub.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 对话全文检索：bigram 索引的建表、重建和查询都集中在这里。
 *
 * `ChatDatabase` 只负责调用，不再把 SQLite CRUD 和搜索算法堆在同一个对象里。
 */
internal object ChatSearchIndex {

    @Volatile
    private var ready = false
    private val lock = Any()

    fun prepare(database: SQLiteDatabase) {
        runCatching { database.execSQL("DROP TRIGGER IF EXISTS message_fts_ai") }
        runCatching { database.execSQL("DROP TRIGGER IF EXISTS message_fts_au") }
        runCatching { database.execSQL("DROP TRIGGER IF EXISTS message_fts_ad") }
        runCatching { database.execSQL("DROP TABLE IF EXISTS message_fts") }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_gram (
                gram       TEXT NOT NULL,
                message_id TEXT NOT NULL REFERENCES message(id) ON DELETE CASCADE,
                PRIMARY KEY(gram, message_id)
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_message_gram ON message_gram(gram)")
        // 按消息反查（补索引 / 删除消息）需要这个索引，否则 NOT EXISTS 会退化成全表扫描。
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_message_gram_message ON message_gram(message_id)",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS search_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        )
        ready = runCatching {
            database.rawQuery(
                "SELECT value FROM search_meta WHERE key = 'gram_v1'",
                null,
            ).use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() == "1"
    }

    suspend fun warm(database: SQLiteDatabase) = withContext(Dispatchers.IO) {
        if (!ready) {
            synchronized(lock) {
                if (!ready) {
                    runCatching {
                        // 先清空旧索引；重建按 100 条一小批提交，避免一个超大事务
                        // 把主线程的聊天写入一直挡在外面。
                        database.beginTransaction()
                        try {
                            database.delete("message_gram", null, null)
                            database.setTransactionSuccessful()
                        } finally {
                            database.endTransaction()
                        }
                        val rows = database.rawQuery("SELECT id, content FROM message", null).use { cursor ->
                            buildList {
                                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                            }
                        }
                        rows.chunked(100).forEach { chunk ->
                            database.beginTransaction()
                            try {
                                chunk.forEach { (id, content) -> index(database, id, content) }
                                database.setTransactionSuccessful()
                            } finally {
                                database.endTransaction()
                            }
                        }
                        database.execSQL(
                            "INSERT OR REPLACE INTO search_meta(key, value) VALUES('gram_v1', '1')",
                        )
                        ready = true
                    }.onFailure {
                        android.util.Log.e("HubSearchIndex", "搜索索引重建失败，继续用 LIKE 兜底", it)
                    }
                }
            }
        }
        if (ready) {
            // 进程在流式收尾前被杀时，正文已落库但 gram 被删掉了；
            // 启动后补一次“有正文却没有索引”的消息，避免搜索永久漏掉半截回复。
            runCatching { repairMissing(database) }
                .onFailure { android.util.Log.w("HubSearchIndex", "补索引失败", it) }
        }
    }

    private fun repairMissing(database: SQLiteDatabase) {
        val rows = database.rawQuery(
            """
            SELECT m.id, m.content FROM message m
            WHERE m.content <> ''
              AND NOT EXISTS (SELECT 1 FROM message_gram g WHERE g.message_id = m.id)
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }
        rows.chunked(100).forEach { chunk ->
            database.beginTransaction()
            try {
                chunk.forEach { (id, content) -> index(database, id, content) }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    fun index(database: SQLiteDatabase, messageId: String, content: String) {
        database.delete("message_gram", "message_id = ?", arrayOf(messageId))
        SearchTokens.grams(content).forEach { gram ->
            database.insertWithOnConflict(
                "message_gram",
                null,
                android.content.ContentValues().apply {
                    put("gram", gram)
                    put("message_id", messageId)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    fun remove(database: SQLiteDatabase, messageId: String) {
        database.delete("message_gram", "message_id = ?", arrayOf(messageId))
    }

    fun search(
        database: SQLiteDatabase,
        query: String,
        limit: Int,
        conversationId: String?,
    ): List<ChatSearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val escaped = q
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val like = "%$escaped%"
        val hits = ArrayList<ChatSearchHit>()

        if (conversationId == null) {
            database.rawQuery(
                "SELECT id, title, updated_at FROM conversation " +
                    "WHERE title LIKE ? ESCAPE '\\' ORDER BY updated_at DESC LIMIT ?",
                arrayOf(like, limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    hits += ChatSearchHit(
                        conversationId = cursor.getString(0),
                        conversationTitle = cursor.getString(1),
                        role = "",
                        text = "",
                        at = cursor.getLong(2),
                        messageIndex = -1,
                    )
                }
            }
        }

        val gramHits = if (ready && q.length >= 2) {
            val grams = SearchTokens.grams(q)
            if (grams.isNotEmpty() && grams.size <= 100) {
                runCatching {
                    val placeholders = grams.joinToString(",") { "?" }
                    val args = grams.toMutableList()
                    val scopeClause = if (conversationId == null) {
                        ""
                    } else {
                        args += conversationId
                        " AND n.conversation_id = ?"
                    }
                    args += grams.size.toString()
                    args += (limit * 4).toString()
                    database.rawQuery(
                        """
                        SELECT n.conversation_id, c.title, m.role, m.content, m.at, n.sort_order
                        FROM message_gram g
                        JOIN message m ON m.id = g.message_id
                        JOIN message_node n ON n.id = m.node_id
                        JOIN conversation c ON c.id = n.conversation_id
                        WHERE g.gram IN ($placeholders)$scopeClause
                          AND m.version_index = (
                              SELECT MAX(v.version_index) FROM message v WHERE v.node_id = m.node_id
                          )
                        GROUP BY m.id
                        HAVING COUNT(DISTINCT g.gram) = ?
                        ORDER BY m.at DESC
                        LIMIT ?
                        """.trimIndent(),
                        args.toTypedArray(),
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                val text = cursor.getString(3)
                                if (!text.contains(q, ignoreCase = true)) continue
                                add(
                                    ChatSearchHit(
                                        conversationId = cursor.getString(0),
                                        conversationTitle = cursor.getString(1),
                                        role = cursor.getString(2),
                                        text = text,
                                        at = cursor.getLong(4),
                                        messageIndex = cursor.getInt(5),
                                    ),
                                )
                            }
                        }
                    }
                }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val messageHits = if (gramHits.isNotEmpty()) {
            gramHits
        } else {
            val fallbackArgs = mutableListOf(like)
            val scopeClause = if (conversationId == null) {
                ""
            } else {
                fallbackArgs += conversationId
                " AND n.conversation_id = ?"
            }
            fallbackArgs += limit.toString()
            database.rawQuery(
                """
                SELECT n.conversation_id, c.title, m.role, m.content, m.at, n.sort_order
                FROM message m
                JOIN message_node n ON n.id = m.node_id
                JOIN conversation c ON c.id = n.conversation_id
                WHERE m.content LIKE ? ESCAPE '\'$scopeClause
                  AND m.version_index = (
                      SELECT MAX(v.version_index) FROM message v WHERE v.node_id = m.node_id
                  )
                ORDER BY m.at DESC
                LIMIT ?
                """.trimIndent(),
                fallbackArgs.toTypedArray(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val text = cursor.getString(3)
                        if (!text.contains(q, ignoreCase = true)) continue
                        add(
                            ChatSearchHit(
                                conversationId = cursor.getString(0),
                                conversationTitle = cursor.getString(1),
                                role = cursor.getString(2),
                                text = text,
                                at = cursor.getLong(4),
                                messageIndex = cursor.getInt(5),
                            ),
                        )
                    }
                }
            }
        }
        hits += messageHits
        return hits.sortedByDescending { it.at }.take(limit)
    }
}
