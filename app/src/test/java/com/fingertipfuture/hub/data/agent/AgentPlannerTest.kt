package com.fingertipfuture.hub.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.fingertipfuture.hub.data.HubAssistant
import org.json.JSONObject

class AgentPlannerTest {

    @Test
    fun `image generation tool is not registered`() {
        assertTrue(AgentToolRegistry.byId("image.generate") == null)
    }

    @Test
    fun `normal chat stays on answer route`() {
        val plan = AgentPlanner.parseRoute(
            raw = """{"route":"answer","skills":[],"reason":"普通聊天","question":""}""",
            allowedIds = setOf("cap-web-search", "cap-reminder"),
        )

        assertEquals(AgentRoute.ANSWER, plan.route)
        assertTrue(plan.skillIds.isEmpty())
    }

    @Test
    fun `router can select multiple allowed skills and drops unknown ids`() {
        val plan = AgentPlanner.parseRoute(
            raw = """
                {"route":"skills","skills":["cap-web-search","missing","cap-reminder"],
                 "reason":"天气和提醒都需要","question":""}
            """.trimIndent(),
            allowedIds = setOf("cap-web-search", "cap-reminder"),
        )

        assertEquals(AgentRoute.SKILLS, plan.route)
        assertEquals(listOf("cap-web-search", "cap-reminder"), plan.skillIds)
    }

    @Test
    fun `tool planner accepts parallel and dependent calls`() {
        val calls = AgentPlanner.parseToolCalls(
            raw = """
                {"calls":[
                  {"tool":"reminder.list","arguments":{},"purpose":"查找目标"},
                  {"tool":"reminder.close","arguments":{"reminderId":"r-1"},"purpose":"关闭目标"},
                  {"tool":"not.allowed","arguments":{},"purpose":"越权工具"}
                ],"reason":"先查再改"}
            """.trimIndent(),
            allowedIds = setOf("reminder.list", "reminder.close"),
        )

        assertEquals(listOf("reminder.list", "reminder.close"), calls.map { it.toolId })
        assertEquals("r-1", calls[1].arguments.getString("reminderId"))
    }

    @Test
    fun `tool planner accepts arguments encoded as a json string`() {
        val calls = AgentPlanner.parseToolCalls(
            raw = """
                {"calls":[{"tool":"web.search","arguments":"{\"query\":\"北京天气\"}"}]}
            """.trimIndent(),
            allowedIds = setOf("web.search"),
        )

        assertEquals("北京天气", calls.single().arguments.getString("query"))
    }

    @Test
    fun `enabled reminder permission is present in local capability catalog`() {
        val skills = AgentSkillCatalog.forAssistant(
            HubAssistant(
                id = "test",
                name = "测试",
                permReminder = true,
            ),
        )

        assertTrue(skills.any { it.id == "cap-reminder" })
    }

    @Test
    fun `memory enabled always allows saving memories`() {
        val writable = AgentSkillCatalog.forAssistant(
            HubAssistant(id = "test", name = "测试", memory = true),
        ).first { it.id == "cap-memory" }

        assertEquals(
            listOf(
                AgentToolRegistry.MEMORY_LIST,
                AgentToolRegistry.MEMORY_SAVE,
                AgentToolRegistry.MEMORY_UPDATE,
                AgentToolRegistry.MEMORY_DELETE,
            ),
            writable.toolIds,
        )
    }

    @Test
    fun `memory and reminder tools expose modification and deletion`() {
        assertTrue(AgentToolRegistry.byId(AgentToolRegistry.MEMORY_UPDATE) != null)
        assertTrue(AgentToolRegistry.byId(AgentToolRegistry.MEMORY_DELETE) != null)
        assertTrue(AgentToolRegistry.byId(AgentToolRegistry.REMINDER_DELETE) != null)
    }

    @Test
    fun `expanded privacy permissions expose real tools`() {
        val assistant = HubAssistant(
            id = "privacy-test",
            name = "测试",
            permCalendarWrite = true,
            permClipboard = true,
            permBluetooth = true,
            permNotificationsRead = true,
            permContactsWrite = true,
            permSmsSend = true,
            permWifi = true,
            permOverlayScreen = true,
            permMedia = true,
        )
        val tools = AgentToolRegistry.forSkills(AgentSkillCatalog.forAssistant(assistant))
            .map { it.id }
            .toSet()

        assertTrue(AgentToolRegistry.CALENDAR_WRITE in tools)
        assertTrue(AgentToolRegistry.CLIPBOARD_READ in tools)
        assertTrue(AgentToolRegistry.BLUETOOTH_DEVICES in tools)
        assertTrue(AgentToolRegistry.NOTIFICATIONS_READ in tools)
        assertTrue(AgentToolRegistry.CONTACTS_WRITE in tools)
        assertTrue(AgentToolRegistry.SMS_COMPOSE in tools)
        assertTrue(AgentToolRegistry.WIFI_DETAILS in tools)
        assertTrue(AgentToolRegistry.OVERLAY_SCREEN in tools)
        assertTrue(AgentToolRegistry.MEDIA_LIST in tools)
    }

    @Test
    fun `system writes are declared as confirm-required tools`() {
        assertEquals(
            "write_private_confirm",
            AgentToolRegistry.byId(AgentToolRegistry.CALENDAR_WRITE)?.risk,
        )
        assertEquals(
            "write_private_confirm",
            AgentToolRegistry.byId(AgentToolRegistry.CONTACTS_WRITE)?.risk,
        )
    }

    @Test
    fun `weather without a place always adds location then web search`() {
        val calls = AgentWeatherPolicy.enrichCalls(
            calls = listOf(
                AgentToolCall(
                    id = "planned-location",
                    toolId = AgentToolRegistry.LOCATION_CURRENT,
                    arguments = JSONObject(),
                ),
            ),
            userText = "现在天气怎么样",
            assistant = HubAssistant(
                id = "test",
                name = "测试",
                permLocation = true,
            ),
        )

        assertEquals(
            listOf(AgentToolRegistry.LOCATION_CURRENT, AgentToolRegistry.WEB_SEARCH),
            calls.map { it.toolId },
        )
    }

    @Test
    fun `weather with explicit place skips location and forces web search`() {
        val calls = AgentWeatherPolicy.enrichCalls(
            calls = emptyList(),
            userText = "北京今天天气怎么样",
            assistant = HubAssistant(
                id = "test",
                name = "测试",
                permLocation = true,
            ),
        )

        assertEquals(listOf(AgentToolRegistry.WEB_SEARCH), calls.map { it.toolId })
        assertEquals("北京 今天天气 天气预报", calls.single().arguments.getString("query"))
    }

    @Test
    fun `resolved weather place replaces a generic search query`() {
        val rewritten = AgentWeatherPolicy.applyResolvedPlace(
            call = AgentToolCall(
                id = "weather-search",
                toolId = AgentToolRegistry.WEB_SEARCH,
                arguments = JSONObject().put("query", "今天天气 天气预报"),
            ),
            userText = "现在天气怎么样",
            resolvedPlace = "上海市",
        )

        assertEquals("上海市 今天天气 天气预报", rewritten.arguments.getString("query"))
    }

    @Test
    fun `weather uses remembered place when location permission is off`() {
        val calls = AgentWeatherPolicy.enrichCalls(
            calls = emptyList(),
            userText = "我这里的天气怎么样",
            assistant = HubAssistant(
                id = "test",
                name = "测试",
                permLocation = false,
            ),
            memoryPlace = "上海",
        )

        assertEquals(listOf(AgentToolRegistry.WEB_SEARCH), calls.map { it.toolId })
        assertEquals("上海 今天天气 天气预报", calls.single().arguments.getString("query"))
    }

    @Test
    fun `weather does not search when neither place nor location is available`() {
        val calls = AgentWeatherPolicy.enrichCalls(
            calls = listOf(
                AgentToolCall(
                    id = "planned-search",
                    toolId = AgentToolRegistry.WEB_SEARCH,
                    arguments = JSONObject().put("query", "今天天气 天气预报"),
                ),
            ),
            userText = "现在天气怎么样",
            assistant = HubAssistant(
                id = "test",
                name = "测试",
                permLocation = false,
            ),
        )

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `single-tool skill is executed even when planner returns no calls`() {
        val calls = AgentExecutionPolicy.ensureRequiredTools(
            calls = emptyList(),
            selectedSkills = listOf(
                AgentSkillDefinition(
                    id = "cap-installed-apps",
                    name = "已安装应用",
                    description = "读取应用列表",
                    whenToUse = "检查可疑应用",
                    prompt = "调用已安装应用工具",
                    toolIds = listOf(AgentToolRegistry.INSTALLED_APPS),
                ),
            ),
            userText = "手机里有没有病毒软件",
        )

        assertEquals(listOf(AgentToolRegistry.INSTALLED_APPS), calls.map { it.toolId })
    }

    @Test
    fun `memory resolver extracts free-form place`() {
        assertEquals(
            "杭州",
            AgentMemoryLocationResolver.parsePlace("""{"place":"杭州"}"""),
        )
        assertEquals(
            null,
            AgentMemoryLocationResolver.parsePlace("""{"place":""}"""),
        )
    }

    @Test
    fun `memory is executed before web search`() {
        val calls = AgentExecutionPolicy.ensureRequiredTools(
            calls = listOf(
                AgentToolCall(
                    id = "search",
                    toolId = AgentToolRegistry.WEB_SEARCH,
                    arguments = JSONObject().put("query", "天气"),
                ),
            ),
            selectedSkills = listOf(
                AgentSkillDefinition(
                    id = "cap-memory",
                    name = "长期记忆",
                    description = "读取记忆",
                    whenToUse = "缺少地点",
                    prompt = "读取全部记忆",
                    toolIds = listOf(AgentToolRegistry.MEMORY_LIST),
                ),
            ),
            userText = "现在天气怎么样",
        )

        assertEquals(
            listOf(AgentToolRegistry.MEMORY_LIST, AgentToolRegistry.WEB_SEARCH),
            calls.map { it.toolId },
        )
    }
}
