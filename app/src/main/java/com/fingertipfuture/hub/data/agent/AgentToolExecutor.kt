package com.fingertipfuture.hub.data.agent

import android.content.Context
import com.fingertipfuture.hub.data.AssistantReminder
import com.fingertipfuture.hub.data.AssistantReminderIntent
import com.fingertipfuture.hub.data.AssistantReminderStore
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.DeviceContext
import com.fingertipfuture.hub.data.ExtendedDeviceContext
import com.fingertipfuture.hub.data.ExtendedDeviceResult
import com.fingertipfuture.hub.data.FeatureActivation
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.data.ReminderDisplayLocation
import com.fingertipfuture.hub.data.ReminderRepeat
import com.fingertipfuture.hub.data.SearchContext
import com.fingertipfuture.hub.data.UiPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * 唯一允许执行模型工具请求的入口。
 *
 * 模型只产生 [AgentToolCall]；这里的白名单、助手权限、Android 系统权限和用户确认
 * 都是不可绕过的一层。
 */
internal object AgentToolExecutor {

    class Session {
        internal var reminderListed: Boolean = false
        internal val reminderIds: MutableSet<String> = linkedSetOf()
        internal var memoryListed: Boolean = false
        internal val memoryIds: MutableSet<String> = linkedSetOf()
    }

    fun newSession(): Session = Session()

    suspend fun execute(
        context: Context,
        assistant: HubAssistant,
        userText: String,
        call: AgentToolCall,
        session: Session,
        confirmReminder: suspend (AssistantReminderIntent) -> AssistantReminder?,
        confirmMemory: suspend (String) -> Boolean,
        confirmDeviceAction: suspend (AgentDeviceActionIntent) -> Boolean,
    ): AgentToolResult {
        val spec = AgentToolRegistry.byId(call.toolId)
            ?: return failure(call, "工具不在白名单")
        return try {
            when (call.toolId) {
                AgentToolRegistry.LOCATION_CURRENT ->
                    readLocation(context, assistant, userText, call)
                AgentToolRegistry.TIME_CURRENT -> readTime(assistant, call)
                AgentToolRegistry.MEMORY_LIST -> listMemories(assistant, call, session)
                AgentToolRegistry.MEMORY_SAVE ->
                    saveMemory(context, assistant, call, confirmMemory)
                AgentToolRegistry.MEMORY_UPDATE ->
                    updateMemory(context, assistant, call, session)
                AgentToolRegistry.MEMORY_DELETE ->
                    deleteMemory(context, assistant, call, session)
                AgentToolRegistry.WEB_SEARCH -> search(call)
                AgentToolRegistry.REMINDER_LIST -> listReminders(assistant, call, session)
                AgentToolRegistry.REMINDER_SAVE ->
                    saveReminder(assistant, call, session, confirmReminder)
                AgentToolRegistry.REMINDER_CLOSE ->
                    closeReminder(assistant, call, session, confirmReminder)
                AgentToolRegistry.REMINDER_DELETE ->
                    deleteReminder(assistant, call, session, confirmReminder)
                AgentToolRegistry.SCREEN_TIME -> readDevice(context, assistant, call, "screen_time")
                AgentToolRegistry.CALENDAR -> readDevice(context, assistant, call, "calendar")
                AgentToolRegistry.INSTALLED_APPS -> readDevice(context, assistant, call, "apps")
                AgentToolRegistry.CONTACTS -> readDevice(context, assistant, call, "contacts")
                AgentToolRegistry.SMS -> readDevice(context, assistant, call, "sms")
                AgentToolRegistry.MMS -> readDevice(context, assistant, call, "mms")
                AgentToolRegistry.STEPS -> readDevice(context, assistant, call, "activity")
                AgentToolRegistry.CALENDAR_WRITE -> {
                    if (!assistant.permCalendarWrite) {
                        unavailable(call, "当前助手未开启写入日历能力")
                    } else {
                        val title = call.arguments.optString("title").trim()
                        val begin = call.arguments.optString("begin").trim()
                        val end = call.arguments.optString("end").trim()
                        val description = call.arguments.optString("description").trim()
                        val intent = AgentDeviceActionIntent(
                            kind = AgentDeviceActionKind.CALENDAR_EVENT,
                            title = "创建日历事项？",
                            details = listOf(
                                "标题：$title",
                                "开始：$begin",
                                "结束：" + end.ifBlank { "默认一小时" },
                                "说明：" + description.ifBlank { "无" },
                            ),
                        )
                        if (!confirmDeviceAction(intent)) {
                            deviceWriteRejected(call, "用户没有同意写入日历")
                        } else {
                            extended(
                                call,
                                ExtendedDeviceContext.writeCalendar(
                                    context = context,
                                    title = title,
                                    beginText = begin,
                                    endText = end,
                                    description = description,
                                ),
                            )
                        }
                    }
                }
                AgentToolRegistry.CLIPBOARD_READ -> if (!assistant.permClipboard) {
                    unavailable(call, "当前助手未开启剪贴板能力")
                } else {
                    extended(call, ExtendedDeviceContext.readClipboard(context, UiPrefs.approximatePermissions))
                }
                AgentToolRegistry.BLUETOOTH_DEVICES -> if (!assistant.permBluetooth) {
                    unavailable(call, "当前助手未开启蓝牙/附近设备能力")
                } else {
                    extended(call, ExtendedDeviceContext.readBluetooth(context, UiPrefs.approximatePermissions))
                }
                AgentToolRegistry.NOTIFICATIONS_READ -> if (!assistant.permNotificationsRead) {
                    unavailable(call, "当前助手未开启通知读取能力")
                } else {
                    extended(call, ExtendedDeviceContext.readNotifications(context, UiPrefs.approximatePermissions))
                }
                AgentToolRegistry.CONTACTS_WRITE -> if (!assistant.permContactsWrite) {
                    unavailable(call, "当前助手未开启写入联系人能力")
                } else {
                    val name = call.arguments.optString("name").trim()
                    val phone = call.arguments.optString("phone").trim()
                    val intent = AgentDeviceActionIntent(
                        kind = AgentDeviceActionKind.CONTACT,
                        title = "创建联系人？",
                        details = listOf(
                            "姓名：$name",
                            "电话：" + phone.ifBlank { "不填写" },
                        ),
                    )
                    if (!confirmDeviceAction(intent)) {
                        deviceWriteRejected(call, "用户没有同意写入联系人")
                    } else {
                        extended(
                            call,
                            ExtendedDeviceContext.writeContact(context, name, phone),
                        )
                    }
                }
                AgentToolRegistry.SMS_COMPOSE -> if (!assistant.permSmsSend) {
                    unavailable(call, "当前助手未开启短信编辑能力")
                } else {
                    extended(
                        call,
                        ExtendedDeviceContext.composeSms(
                            context,
                            call.arguments.optString("number").trim(),
                            call.arguments.optString("text").trim(),
                        ),
                    )
                }
                AgentToolRegistry.WIFI_DETAILS -> if (!assistant.permWifi) {
                    unavailable(call, "当前助手未开启 Wi-Fi/网络详情能力")
                } else {
                    extended(call, ExtendedDeviceContext.readWifi(context, UiPrefs.approximatePermissions))
                }
                AgentToolRegistry.OVERLAY_SCREEN -> if (!assistant.permOverlayScreen) {
                    unavailable(call, "当前助手未开启悬浮窗与屏幕读取能力")
                } else {
                    extended(call, ExtendedDeviceContext.overlayStatus(context))
                }
                AgentToolRegistry.MEDIA_LIST -> if (!assistant.permMedia) {
                    unavailable(call, "当前助手未开启媒体读取能力")
                } else {
                    extended(call, ExtendedDeviceContext.listMedia(context, UiPrefs.approximatePermissions))
                }
                else -> failure(call, "工具 ${spec.name} 还没有执行器")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failure(call, error.message ?: "工具执行失败")
        }
    }

    fun toolTitle(toolId: String): String =
        AgentToolRegistry.byId(toolId)?.name ?: toolId

    fun thinkingKind(toolId: String): String = when (toolId) {
        AgentToolRegistry.LOCATION_CURRENT -> "location"
        AgentToolRegistry.TIME_CURRENT -> "time"
        AgentToolRegistry.MEMORY_LIST,
        AgentToolRegistry.MEMORY_SAVE,
        AgentToolRegistry.MEMORY_UPDATE,
        AgentToolRegistry.MEMORY_DELETE -> "memory"
        AgentToolRegistry.WEB_SEARCH -> "search"
        AgentToolRegistry.REMINDER_LIST,
        AgentToolRegistry.REMINDER_SAVE,
        AgentToolRegistry.REMINDER_CLOSE,
        AgentToolRegistry.REMINDER_DELETE -> "reminder"
        AgentToolRegistry.SCREEN_TIME -> "screen_time"
        AgentToolRegistry.CALENDAR -> "calendar"
        AgentToolRegistry.INSTALLED_APPS -> "apps"
        AgentToolRegistry.CONTACTS -> "contacts"
        AgentToolRegistry.SMS -> "sms"
        AgentToolRegistry.MMS -> "mms"
        AgentToolRegistry.STEPS -> "activity"
        AgentToolRegistry.CALENDAR_WRITE -> "calendar"
        AgentToolRegistry.CLIPBOARD_READ -> "clipboard"
        AgentToolRegistry.BLUETOOTH_DEVICES -> "bluetooth"
        AgentToolRegistry.NOTIFICATIONS_READ -> "notifications"
        AgentToolRegistry.CONTACTS_WRITE -> "contacts"
        AgentToolRegistry.SMS_COMPOSE -> "sms"
        AgentToolRegistry.WIFI_DETAILS -> "wifi"
        AgentToolRegistry.OVERLAY_SCREEN -> "overlay"
        AgentToolRegistry.MEDIA_LIST -> "media"
        else -> "tool"
    }

    private suspend fun readLocation(
        context: Context,
        assistant: HubAssistant,
        userText: String,
        call: AgentToolCall,
    ): AgentToolResult {
        if (!assistant.permLocation) {
            return unavailable(call, "当前助手未开启位置能力")
        }
        val explicitWeatherPlace = FeatureActivation.explicitWeatherPlace(userText)
        if (FeatureActivation.isWeatherQuery(userText) && explicitWeatherPlace.isNotBlank()) {
            return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.SKIPPED,
                title = "获取当前位置",
                error = "用户已明确地点“$explicitWeatherPlace”，按最小权限原则跳过当前位置",
            )
        }
        val location = AttachmentStore.currentLocation(context)
            ?: return failure(call, "没有获取到当前位置，请检查系统定位权限和定位服务")
        val at = System.currentTimeMillis()
        val approximate = UiPrefs.approximatePermissions
        val text = buildString {
            append("地点：").append(location.label.ifBlank { "未知地点" }).append('\n')
            if (!approximate) {
                append("经纬度：").append(location.lat).append(", ").append(location.lon).append('\n')
            }
            append("获取时间：").append(formatTime(at))
        }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = AgentToolStatus.SUCCESS,
            title = "获取当前位置",
            contextText = text,
            data = JSONObject().apply {
                put("label", location.label)
                if (!approximate) {
                    put("latitude", location.lat)
                    put("longitude", location.lon)
                }
                put("fetchedAt", at)
            },
            verified = true,
        )
    }

    private fun readTime(assistant: HubAssistant, call: AgentToolCall): AgentToolResult {
        if (!assistant.permTime) return unavailable(call, "当前助手未开启时间能力")
        val now = System.currentTimeMillis()
        val label = formatTime(now)
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = AgentToolStatus.SUCCESS,
            title = "读取当前时间",
            contextText = "手机当前本地时间：$label",
            data = JSONObject().apply {
                put("formatted", label)
                put("timestamp", now)
            },
            verified = true,
        )
    }

    private fun listMemories(
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
    ): AgentToolResult {
        val memories = MemoryStore.activeFor(assistant)
        session.memoryListed = true
        session.memoryIds.clear()
        session.memoryIds += memories.map { it.id }
        if (memories.isEmpty()) {
            return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.SUCCESS,
                title = "读取长期记忆",
                contextText = "当前助手没有已启用的长期记忆。",
                data = JSONObject().put("entries", JSONArray()),
                verified = true,
            )
        }
        val entries = JSONArray().apply {
            memories.forEach { memory ->
                put(JSONObject().apply {
                    put("id", memory.id)
                    put("text", memory.text)
                    put("createdAt", memory.createdAt)
                    put("ownerId", memory.ownerId)
                })
            }
        }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = AgentToolStatus.SUCCESS,
            title = "读取长期记忆",
            contextText = memories.joinToString("\n") {
                "- id=${it.id}；${it.text}"
            }.take(12_000),
            data = JSONObject().put("entries", entries),
            verified = true,
        )
    }

    private suspend fun saveMemory(
        context: Context,
        assistant: HubAssistant,
        call: AgentToolCall,
        confirmMemory: suspend (String) -> Boolean,
    ): AgentToolResult {
        if (!assistant.memory) {
            return unavailable(call, "当前助手未开启自动记忆")
        }
        val text = call.arguments.optString("text").trim().take(500)
        if (text.isBlank()) return failure(call, "记忆内容为空")
        // 记忆统一记在当前助手下；「全局记忆」只决定是否共享给其它助手读取。
        val scope = if (
            call.arguments.optString("scope") == "global" &&
            assistant.globalMemory
        ) {
            "global"
        } else {
            "assistant"
        }
        val ownerId = if (scope == "global") MemoryStore.GLOBAL_OWNER else assistant.id
        val existing = MemoryStore.forAssistant(ownerId)
            .firstOrNull { it.text.trim().equals(text, ignoreCase = true) }
        if (existing != null) {
            return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.SKIPPED,
                title = "保存长期记忆",
                contextText = "这条记忆已经存在：${existing.text}",
                data = JSONObject().apply {
                    put("id", existing.id)
                    put("text", existing.text)
                    put("scope", scope)
                },
                verified = true,
            )
        }
        if (assistant.confirmAutoMemory && !confirmMemory(text)) {
            return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.REJECTED,
                title = "保存长期记忆",
                error = "用户没有同意保存这条记忆",
            )
        }
        val saved = MemoryStore.add(context, ownerId, text)
            ?: return failure(call, "记忆写入失败")
        val verified = MemoryStore.forAssistant(ownerId).any { it.id == saved.id }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (verified) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = "保存长期记忆",
            contextText = if (verified) "已保存长期记忆：${saved.text}" else "",
            error = if (verified) "" else "记忆写入后验证失败",
            data = JSONObject().apply {
                put("id", saved.id)
                put("text", saved.text)
                put("scope", scope)
            },
            verified = verified,
        )
    }

    private fun updateMemory(
        context: Context,
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
    ): AgentToolResult {
        if (!assistant.memory) return unavailable(call, "当前助手未开启自动记忆")
        val id = call.arguments.optString("id").trim()
        val text = call.arguments.optString("text").trim().take(500)
        if (id.isBlank()) return failure(call, "缺少记忆 id")
        if (text.isBlank()) return failure(call, "修改后的记忆内容为空")
        if (!session.memoryListed || id !in session.memoryIds) {
            return failure(call, "修改记忆前必须先调用 memory.list 并确认目标 id")
        }
        val target = MemoryStore.entries.firstOrNull { it.id == id }
            ?: return failure(call, "没有找到要修改的记忆")
        if (target.ownerId != assistant.id && target.ownerId != MemoryStore.GLOBAL_OWNER) {
            return failure(call, "这条记忆不属于当前助手")
        }
        val updated = MemoryStore.update(context, id, text)
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (updated) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = "修改长期记忆",
            contextText = if (updated) "长期记忆已修改并验证：$text" else "",
            error = if (updated) "" else "记忆修改后验证失败",
            data = JSONObject().apply {
                put("id", id)
                put("text", text)
            },
            verified = updated,
        )
    }

    private fun deleteMemory(
        context: Context,
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
    ): AgentToolResult {
        if (!assistant.memory) return unavailable(call, "当前助手未开启自动记忆")
        val id = call.arguments.optString("id").trim()
        if (id.isBlank()) return failure(call, "缺少记忆 id")
        if (!session.memoryListed || id !in session.memoryIds) {
            return failure(call, "删除记忆前必须先调用 memory.list 并确认目标 id")
        }
        val target = MemoryStore.entries.firstOrNull { it.id == id }
            ?: return failure(call, "没有找到要删除的记忆")
        if (target.ownerId != assistant.id && target.ownerId != MemoryStore.GLOBAL_OWNER) {
            return failure(call, "这条记忆不属于当前助手")
        }
        val deleted = MemoryStore.delete(context, id)
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (deleted) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = "删除长期记忆",
            contextText = if (deleted) "长期记忆已删除并验证：${target.text}" else "",
            error = if (deleted) "" else "记忆删除后验证失败",
            data = JSONObject().put("id", id),
            verified = deleted,
        )
    }

    private suspend fun search(call: AgentToolCall): AgentToolResult {
        val query = call.arguments.optString("query").trim()
        if (query.isBlank()) return failure(call, "搜索关键词为空")
        val outcome = SearchContext.build(
            query = query,
            intentHint = query,
            allowIntentCache = true,
        ).getOrElse { error ->
            return failure(call, error.message ?: "联网搜索失败")
        }
        if (outcome.context.isBlank()) {
            return unavailable(call, "还没有配置可用的搜索供应商")
        }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = AgentToolStatus.SUCCESS,
            title = "联网搜索：$query",
            contextText = outcome.context,
            data = JSONObject().apply {
                put("query", query)
                put("resultCount", outcome.evidence.size)
                put("providerKinds", JSONArray().apply {
                    outcome.trace?.providerKinds.orEmpty().forEach(::put)
                })
                put("cached", outcome.trace?.cached == true)
                put("evidence", JSONArray().apply {
                    outcome.evidence.take(10).forEach { item ->
                        put(JSONObject().apply {
                            put("title", item.title)
                            put("url", item.url)
                            put("snippet", item.snippet.take(1_000))
                        })
                    }
                })
            },
            verified = outcome.evidence.isNotEmpty(),
        )
    }

    private fun listReminders(
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
    ): AgentToolResult {
        if (!assistant.permReminder) return unavailable(call, "当前助手未开启自动化任务能力")
        val reminders = AssistantReminderStore.forAssistant(assistant.id)
        session.reminderListed = true
        session.reminderIds.clear()
        session.reminderIds += reminders.map { it.id }
        val array = JSONArray().apply {
            reminders.forEach { reminder ->
                put(JSONObject().apply {
                    put("id", reminder.id)
                    put("title", reminder.title)
                    put("prompt", reminder.prompt)
                    put("time", reminder.timeLabel)
                    put("repeat", reminder.repeat.key)
                    put("scheduledDate", reminder.scheduledDate)
                    put("weekday", reminder.weekday)
                    put("displayLocation", reminder.displayLocation.key)
                    put("enabled", reminder.enabled)
                })
            }
        }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = AgentToolStatus.SUCCESS,
            title = "查看自动化任务",
            contextText = if (reminders.isEmpty()) {
                "当前助手没有自动化任务。"
            } else {
                "当前助手共有 ${reminders.size} 条自动化任务：\n" +
                    reminders.joinToString("\n") {
                        "- id=${it.id}；${it.title}；${it.scheduleLabel} ${it.timeLabel}；" +
                            "显示位置=${it.displayLocation.label}；任务=${it.prompt}"
                    }
            },
            data = JSONObject().put("reminders", array),
            verified = true,
        )
    }

    private suspend fun saveReminder(
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
        confirmReminder: suspend (AssistantReminderIntent) -> AssistantReminder?,
    ): AgentToolResult {
        if (!assistant.permReminder) return unavailable(call, "当前助手未开启自动化任务能力")
        val id = call.arguments.optString("reminderId").trim()
        val existing = id.takeIf { it.isNotBlank() }?.let(AssistantReminderStore::byId)
        if (id.isNotBlank() && existing == null) return failure(call, "没有找到要修改的提醒 id")
        if (existing != null && (!session.reminderListed || id !in session.reminderIds)) {
            return failure(call, "修改提醒前必须先调用 reminder.list 并确认目标 id")
        }
        if (existing != null && existing.assistantId != assistant.id) {
            return failure(call, "这条提醒不属于当前助手")
        }
        val prompt = call.arguments.optString("prompt").trim().ifBlank { existing?.prompt.orEmpty() }
        if (prompt.isBlank()) return failure(call, "缺少到点后要执行的任务")
        val time = parseTime(call.arguments.optString("time"))
            ?: return failure(call, "缺少或无法解析 24 小时制提醒时间")
        val repeat = when (call.arguments.optString("repeat").lowercase()) {
            "daily", "" -> existing?.repeat ?: ReminderRepeat.DAILY
            "once" -> ReminderRepeat.ONCE
            "weekly" -> ReminderRepeat.WEEKLY
            else -> return failure(call, "不支持的重复方式")
        }
        val scheduledDate = call.arguments.optString("date").trim()
        if (repeat == ReminderRepeat.ONCE && scheduledDate.isBlank() && existing?.scheduledDate.isNullOrBlank()) {
            return failure(call, "一次性任务缺少日期")
        }
        val selectedWeekday = call.arguments.optInt(
            "weekday",
            existing?.weekday ?: java.util.Calendar.MONDAY,
        )
        val intent = AssistantReminderIntent.Save(
            existing = existing,
            title = call.arguments.optString("title").trim()
                .ifBlank { existing?.title ?: prompt.take(20) },
            prompt = prompt,
            hour = time.first,
            minute = time.second,
            repeat = repeat,
            scheduledDate = scheduledDate,
            weekday = selectedWeekday,
            displayLocation = ReminderDisplayLocation.fromKey(
                call.arguments.optString("displayLocation").ifBlank {
                    existing?.displayLocation?.key ?: ReminderDisplayLocation.RECENT_CONVERSATION.key
                },
            ),
        )
        val saved = confirmReminder(intent)
            ?: return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.REJECTED,
                title = "创建或修改自动化任务",
                error = "用户取消了操作",
            )
        val verifiedReminder = AssistantReminderStore.byId(saved.id)
        val verified = verifiedReminder != null &&
            verifiedReminder.enabled &&
            verifiedReminder.prompt == saved.prompt &&
            verifiedReminder.hour == saved.hour &&
            verifiedReminder.minute == saved.minute &&
            verifiedReminder.repeat == saved.repeat
        if (verified) {
            session.reminderIds += saved.id
        }
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (verified) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = if (existing == null) "创建自动化任务" else "修改自动化任务",
            contextText = if (verified) {
                "自动化任务已${if (existing == null) "创建" else "修改"}并验证：${saved.title}；" +
                    "${saved.scheduleLabel} ${saved.timeLabel}；显示位置=${saved.displayLocation.label}；" +
                    "任务=${saved.prompt}；id=${saved.id}"
            } else {
                ""
            },
            error = if (verified) "" else "自动化任务写入后验证失败",
            data = reminderJson(saved),
            verified = verified,
        )
    }

    private suspend fun closeReminder(
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
        confirmReminder: suspend (AssistantReminderIntent) -> AssistantReminder?,
    ): AgentToolResult {
        if (!assistant.permReminder) return unavailable(call, "当前助手未开启自动化任务能力")
        val id = call.arguments.optString("reminderId").trim()
        if (id.isBlank()) return failure(call, "缺少提醒 id")
        if (!session.reminderListed || id !in session.reminderIds) {
            return failure(call, "关闭提醒前必须先调用 reminder.list 并确认目标 id")
        }
        val reminder = AssistantReminderStore.byId(id)
            ?: return failure(call, "没有找到要关闭的提醒")
        if (reminder.assistantId != assistant.id) return failure(call, "这条提醒不属于当前助手")
        val closed = confirmReminder(AssistantReminderIntent.Close(reminder))
            ?: return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.REJECTED,
                title = "关闭自动化任务",
                error = "用户取消了操作",
            )
        val verifiedReminder = AssistantReminderStore.byId(closed.id)
        val verified = verifiedReminder != null && !verifiedReminder.enabled
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (verified) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = "关闭自动化任务",
            contextText = if (verified) "自动化任务已关闭并验证：${closed.title}；id=${closed.id}" else "",
            error = if (verified) "" else "自动化任务关闭后验证失败",
            data = reminderJson(closed),
            verified = verified,
        )
    }

    private suspend fun deleteReminder(
        assistant: HubAssistant,
        call: AgentToolCall,
        session: Session,
        confirmReminder: suspend (AssistantReminderIntent) -> AssistantReminder?,
    ): AgentToolResult {
        if (!assistant.permReminder) return unavailable(call, "当前助手未开启自动化任务能力")
        val id = call.arguments.optString("reminderId").trim()
        if (id.isBlank()) return failure(call, "缺少提醒 id")
        if (!session.reminderListed || id !in session.reminderIds) {
            return failure(call, "删除提醒前必须先调用 reminder.list 并确认目标 id")
        }
        val reminder = AssistantReminderStore.byId(id)
            ?: return failure(call, "没有找到要删除的提醒")
        if (reminder.assistantId != assistant.id) return failure(call, "这条提醒不属于当前助手")
        val confirmed = confirmReminder(AssistantReminderIntent.Delete(reminder))
            ?: return AgentToolResult(
                callId = call.id,
                toolId = call.toolId,
                status = AgentToolStatus.REJECTED,
                title = "删除自动化任务",
                error = "用户取消了操作",
            )
        val deleted = AssistantReminderStore.byId(confirmed.id) == null
        if (deleted) session.reminderIds -= confirmed.id
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (deleted) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
            title = "删除自动化任务",
            contextText = if (deleted) "自动化任务已永久删除并验证：${reminder.title}" else "",
            error = if (deleted) "" else "自动化任务删除后验证失败",
            data = reminderJson(reminder),
            verified = deleted,
        )
    }

    private suspend fun readDevice(
        context: Context,
        assistant: HubAssistant,
        call: AgentToolCall,
        kind: String,
    ): AgentToolResult {
        val result = DeviceContext.readSelected(context, assistant, setOf(kind))
        if (result.text.isBlank()) {
            return unavailable(call, "设备没有返回数据，请检查对应权限")
        }
        val trace = result.accessTraces.firstOrNull()
        return AgentToolResult(
            callId = call.id,
            toolId = call.toolId,
            status = if (trace?.ok == false) AgentToolStatus.ERROR else AgentToolStatus.SUCCESS,
            title = toolTitle(call.toolId),
            contextText = result.text,
            error = if (trace?.ok == false) trace.detail else "",
            verified = trace?.ok == true || result.text.isNotBlank(),
        )
    }

    private fun extended(
        call: AgentToolCall,
        result: ExtendedDeviceResult,
    ): AgentToolResult = AgentToolResult(
        callId = call.id,
        toolId = call.toolId,
        status = if (result.ok) AgentToolStatus.SUCCESS else AgentToolStatus.ERROR,
        title = toolTitle(call.toolId),
        contextText = result.text,
        error = result.error,
        data = result.data,
        verified = result.ok,
    )

    private fun failure(call: AgentToolCall, message: String) = AgentToolResult(
        callId = call.id,
        toolId = call.toolId,
        status = AgentToolStatus.ERROR,
        title = toolTitle(call.toolId),
        error = message,
    )

    private fun unavailable(call: AgentToolCall, message: String) = AgentToolResult(
        callId = call.id,
        toolId = call.toolId,
        status = AgentToolStatus.UNAVAILABLE,
        title = toolTitle(call.toolId),
        error = message,
    )

    private fun deviceWriteRejected(call: AgentToolCall, message: String) = AgentToolResult(
        callId = call.id,
        toolId = call.toolId,
        status = AgentToolStatus.REJECTED,
        title = toolTitle(call.toolId),
        error = message,
    )

    private fun reminderJson(reminder: AssistantReminder) = JSONObject().apply {
        put("id", reminder.id)
        put("title", reminder.title)
        put("prompt", reminder.prompt)
        put("time", reminder.timeLabel)
        put("repeat", reminder.repeat.key)
        put("scheduledDate", reminder.scheduledDate)
        put("weekday", reminder.weekday)
        put("weekdays", org.json.JSONArray().apply { reminder.weekdays.forEach { put(it) } })
        put("displayLocation", reminder.displayLocation.key)
        put("enabled", reminder.enabled)
    }

    private fun parseTime(value: String): Pair<Int, Int>? =
        Regex("""^\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*$""")
            .matchEntire(value)
            ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    private fun formatTime(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA).format(Date(at))
}
