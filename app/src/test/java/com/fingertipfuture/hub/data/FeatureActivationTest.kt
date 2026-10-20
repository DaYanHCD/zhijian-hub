package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureActivationTest {

    private val translation = HubSkill(
        id = TRANSLATION_SKILL_ID,
        name = "翻译",
        description = "",
        prompt = "",
    )

    @Test
    fun `normal chat does not search`() {
        assertFalse(FeatureActivation.shouldSearch("你好，今天心情不错"))
        assertFalse(FeatureActivation.shouldSearch("帮我写一个排序算法"))
    }

    @Test
    fun `translation alone does not search`() {
        assertFalse(FeatureActivation.shouldSearch("翻译成英文：I love you"))
    }

    @Test
    fun `fresh information triggers search`() {
        assertTrue(FeatureActivation.shouldSearch("IG战队最新消息"))
        assertTrue(FeatureActivation.shouldSearch("今天上海天气怎么样"))
        assertTrue(FeatureActivation.shouldSearch("search latest OpenAI news"))
    }

    @Test
    fun `weather query is recognized for location search`() {
        assertTrue(FeatureActivation.isWeatherQuery("现在天气怎么样"))
        assertTrue(FeatureActivation.isWeatherQuery("What is the weather now?"))
        assertFalse(FeatureActivation.isWeatherQuery("帮我写一段代码"))
        assertEquals(
            "上海 今天天气 天气预报",
            FeatureActivation.searchQuery("现在天气怎么样", "上海"),
        )
    }

    @Test
    fun `search query strips command and keeps the topic`() {
        val plan = FeatureActivation.planSearch("帮我搜索一下 DeepSeek 最新消息")
        assertTrue(plan.shouldSearch)
        assertEquals("DeepSeek 最新消息", plan.query)
    }

    @Test
    fun `quoted search target wins over surrounding sentence`() {
        val plan = FeatureActivation.planSearch("请联网确认“iPhone 17 发售日期”")
        assertTrue(plan.shouldSearch)
        assertEquals("iPhone 17 发售日期", plan.query)
    }

    @Test
    fun `weather uses an explicitly named place before current location`() {
        val plan = FeatureActivation.planSearch("今天北京天气怎么样", location = "上海")
        assertTrue(plan.shouldSearch)
        assertEquals("北京 今天天气 天气预报", plan.query)
    }

    @Test
    fun `coordinate fallback does not become a bare number search`() {
        val plan = FeatureActivation.planSearch(
            "weather",
            location = "31.2304,121.4737",
        )
        assertTrue(plan.shouldSearch)
        assertEquals("weather forecast near 31.2304N 121.4737E", plan.query)
    }

    @Test
    fun `referential search uses the previous topic`() {
        val plan = FeatureActivation.planSearch(
            "再搜一下这个",
            recentTurns = listOf("DeepSeek 最新价格是多少"),
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("DeepSeek"))
    }

    @Test
    fun `generic follow-up is scoped to the previous topic`() {
        val plan = FeatureActivation.planSearch(
            "再查一下最新消息",
            recentTurns = listOf("帮我看看 iPhone 17 的发售情况"),
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("iPhone 17"))
    }

    @Test
    fun `standalone search does not depend on previous turn for cache key`() {
        assertFalse(FeatureActivation.needsConversationContext("search latest OpenAI news"))
        assertTrue(FeatureActivation.needsConversationContext("再搜一下这个"))
        assertTrue(FeatureActivation.needsConversationContext("这个关键词不对"))
    }

    @Test
    fun `bare insult without context does not become a search query`() {
        assertFalse(FeatureActivation.planSearch("你真傻，连搜索都不会").shouldSearch)
        assertFalse(FeatureActivation.planSearch("你他妈搜的什么垃圾").shouldSearch)
        assertFalse(FeatureActivation.planSearch("搜索一下").shouldSearch)
    }

    @Test
    fun `outdated complaint retries the previous query with freshness`() {
        val plan = FeatureActivation.planSearch(
            text = "你搜的什么垃圾，不是最新的",
            previousSearchQuery = "OpenAI GPT-5 价格",
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("OpenAI GPT-5 价格"))
        assertTrue(plan.query.contains("最新"))
    }

    @Test
    fun `english not the latest complaint also retries`() {
        val plan = FeatureActivation.planSearch(
            text = "This is garbage, not the latest",
            previousSearchQuery = "search latest iPhone 17 news",
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("iPhone 17"))
        assertFalse(plan.query.contains("garbage"))
    }

    @Test
    fun `english abuse alone still reuses previous search context`() {
        val plan = FeatureActivation.planSearch(
            text = "This search is useless",
            previousSearchQuery = "iPhone 17 release date",
        )
        assertTrue(plan.shouldSearch)
        assertEquals("iPhone 17 release date", plan.query)
    }

    @Test
    fun `wrong keyword complaint re-reads the original user question`() {
        val plan = FeatureActivation.planSearch(
            text = "关键词完全不对，不是这个",
            recentTurns = listOf("iPhone 17 什么时候发售"),
            previousSearchQuery = "苹果发布会",
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("iPhone 17"))
    }

    @Test
    fun `missed search complaint immediately retries with context`() {
        val plan = FeatureActivation.planSearch(
            text = "明明可以搜索，为什么刚才不搜索",
            recentTurns = listOf("马斯克今天有什么新闻"),
        )
        assertTrue(plan.shouldSearch)
        assertTrue(plan.query.contains("马斯克"))
    }

    @Test
    fun `explicit correction overrides the previous query`() {
        val plan = FeatureActivation.planSearch(
            text = "你搜的什么垃圾，我要的是“Tesla Cybertruck 2026”",
            recentTurns = listOf("特斯拉新皮卡"),
            previousSearchQuery = "特斯拉 新闻",
        )
        assertTrue(plan.shouldSearch)
        assertEquals("Tesla Cybertruck 2026", plan.query)
    }

    @Test
    fun `translation skill only activates for translation`() {
        assertEquals(
            listOf(translation),
            FeatureActivation.activeSkills(listOf(translation), "把这句话翻译成英文"),
        )
        assertTrue(FeatureActivation.activeSkills(listOf(translation), "你好").isEmpty())
    }

    @Test
    fun `custom skill activates only when named`() {
        val skill = HubSkill(
            id = "s-review",
            name = "代码审查",
            description = "",
            prompt = "",
        )
        assertEquals(
            listOf(skill),
            FeatureActivation.activeSkills(listOf(skill), "帮我做代码审查"),
        )
        assertTrue(FeatureActivation.activeSkills(listOf(skill), "解释一下这段代码").isEmpty())
    }
}
