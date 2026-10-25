package com.fingertipfuture.hub.data

/** v1 基础表结构；字段升级由 ChatDatabase.onUpgrade 增量完成。 */
internal object ChatDatabaseSchema {
    const val CONVERSATION = """
        CREATE TABLE conversation (
            id         TEXT PRIMARY KEY,
            title      TEXT NOT NULL DEFAULT '',
            pinned     INTEGER NOT NULL DEFAULT 0,
            folder_id  TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """

    const val CONVERSATION_INDEX =
        "CREATE INDEX idx_conversation_updated ON conversation(pinned DESC, updated_at DESC)"

    const val NODE = """
        CREATE TABLE message_node (
            id              TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
            parent_node_id  TEXT,
            select_index    INTEGER NOT NULL DEFAULT 0,
            sort_order      INTEGER NOT NULL
        )
    """

    const val NODE_INDEX =
        "CREATE INDEX idx_node_conversation ON message_node(conversation_id, sort_order)"

    const val MESSAGE = """
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

    const val MESSAGE_INDEX =
        "CREATE UNIQUE INDEX idx_message_version ON message(node_id, version_index)"
}
