package com.fingertipfuture.hub.data

/**
 * 搜索用的相邻二字索引。
 *
 * 独立成纯 Kotlin 对象：不碰 SQLite / Android，方便单元测试。
 * 中文和英文都按连续两个字符切（英文先转小写），所以“聊天”能命中“今天聊天”，
 * “chat”能命中“multichat”。
 */
internal object SearchTokens {

    fun grams(text: String): Set<String> {
        if (text.length < 2) return emptySet()
        val normalized = text.lowercase()
        val grams = LinkedHashSet<String>()
        for (i in 0 until normalized.length - 1) {
            val gram = normalized.substring(i, i + 2)
            if (gram.isNotBlank()) grams += gram
        }
        return grams
    }
}
