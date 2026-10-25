package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    @Test
    fun emptyMemoryAddsNothingToPrompt() {
        assertEquals("", MemoryStore.formatPrompt(emptyList()))
    }

    @Test
    fun memoriesAreListedAsFacts() {
        val prompt = MemoryStore.formatPrompt(
            listOf(
                MemoryEntry("m1", "a1", "用户喜欢简洁回答", 1),
                MemoryEntry("m2", "__global__", "默认使用中文", 2),
            ),
        )
        assertTrue(prompt.startsWith("【长期记忆】"))
        assertTrue(prompt.contains("- 用户喜欢简洁回答"))
        assertTrue(prompt.contains("- 默认使用中文"))
    }

    @Test
    fun unrelatedQuestionDoesNotPullMemory() {
        assertEquals(
            false,
            MemoryStore.isMemoryQueryRelevant("二次方程求根公式", "用户喜欢喝美式咖啡"),
        )
    }

    @Test
    fun relatedPreferencePullsMemory() {
        assertEquals(
            true,
            MemoryStore.isMemoryQueryRelevant("我平时喜欢喝什么", "用户喜欢喝美式咖啡"),
        )
    }

    @Test
    fun explicitMemoryQuestionPullsMemory() {
        assertEquals(
            true,
            MemoryStore.isMemoryQueryRelevant("你还记得我的偏好吗", "用户喜欢简洁回答"),
        )
    }

    @Test
    fun locationMemoryProvidesAWeatherPlace() {
        assertEquals(
            "上海",
            MemoryStore.extractLocationHint("我的位置在上海"),
        )
        assertEquals(
            "北京",
            MemoryStore.extractLocationHint("我住在北京，平时喜欢安静"),
        )
        assertEquals(
            "北京",
            MemoryStore.extractLocationHint("我现在在北京"),
        )
        assertEquals(
            "上海",
            MemoryStore.extractLocationHint("我目前在上海"),
        )
        assertEquals(
            "拉萨",
            MemoryStore.extractLocationHint("我现在住在拉萨"),
        )
        assertEquals(
            "拉萨",
            MemoryStore.extractLocationHint("我目前住在拉萨"),
        )
        assertEquals(
            "成都",
            MemoryStore.extractLocationHint("我定居在成都"),
        )
        assertEquals(
            "杭州",
            MemoryStore.extractLocationHint("我家在杭州"),
        )
        assertEquals(
            "杭州",
            MemoryStore.extractLocationHint("住在杭州"),
        )
        assertEquals(
            null,
            MemoryStore.extractLocationHint("我喜欢喝美式咖啡"),
        )
    }
}
