package com.fingertipfuture.hub.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.localizedUiString
import com.fingertipfuture.hub.core.localizedUiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object AssistantReminderScheduler {

    private const val WORK_PREFIX = "assistant-reminder-"
    private const val RUN_PREFIX = "assistant-reminder-run-"
    private const val ALARM_ACTION = "com.fingertipfuture.hub.ASSISTANT_REMINDER"
    internal const val KEY_REMINDER_ID = "reminder_id"

    fun schedule(context: Context, reminder: AssistantReminder) {
        cancelFallback(context, reminder.id)
        scheduleAlarm(context, reminder)
    }

    internal fun scheduleNext(context: Context, reminder: AssistantReminder) {
        // 这里可能正运行在 fallback Worker 自己内部，不能取消当前 unique work；
        // 下一次由 rescheduleAll 或后续编辑统一清理残留。
        scheduleAlarm(context, reminder)
    }

    fun cancel(context: Context, reminderId: String) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(alarmPendingIntent(app, reminderId))
        WorkManager.getInstance(app).apply {
            cancelUniqueWork(WORK_PREFIX + reminderId)
            cancelUniqueWork(RUN_PREFIX + reminderId)
        }
    }

    fun rescheduleAll(context: Context) {
        AssistantReminderStore.initialize(context)
        AssistantReminderStore.items.forEach { reminder ->
            if (reminder.enabled) {
                // 精确闹钟权限可能在两次启动之间被用户打开；先清掉旧 WorkManager
                // fallback，避免它和新闹钟同时执行。
                cancelFallback(context, reminder.id)
                scheduleAlarm(context, reminder)
            } else {
                cancel(context, reminder.id)
            }
        }
    }

    private fun cancelFallback(context: Context, reminderId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_PREFIX + reminderId)
    }

    internal fun executeFromAlarm(context: Context, reminderId: String) {
        val app = context.applicationContext
        if (!AssistantReminderStore.byId(reminderId)?.enabled.let { it == true }) return
        val request = OneTimeWorkRequestBuilder<AssistantReminderWorker>()
            .setInputData(
                Data.Builder().putString(KEY_REMINDER_ID, reminderId).build(),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("assistant-reminders")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            RUN_PREFIX + reminderId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleAlarm(context: Context, reminder: AssistantReminder) {
        if (!reminder.enabled) {
            cancel(context, reminder.id)
            return
        }
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val triggerAt = nextReminderTriggerAtMillis(reminder, now)
        if (triggerAt == null) {
            AssistantReminderStore.upsert(context, reminder.copy(enabled = false))
            cancel(context, reminder.id)
            return
        }
        val manager = app.getSystemService(AlarmManager::class.java)
        if (manager != null && PermissionAccess.canScheduleExactAlarms(app)) {
            val operation = alarmPendingIntent(app, reminder.id)
            val showIntent = PendingIntent.getActivity(
                app,
                reminder.id.hashCode(),
                AssistantReminderNavigation.activityIntent(app, reminder.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                operation,
            )
        } else {
            // 未授予精确闹钟时仍然保留接近时间的兜底调度。权限页会提示用户开启。
            enqueueFallback(app, reminder, (triggerAt - now).coerceAtLeast(1_000L))
        }
    }

    private fun enqueueFallback(
        context: Context,
        reminder: AssistantReminder,
        delayMillis: Long,
    ) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueueUniqueWork(
            WORK_PREFIX + reminder.id,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AssistantReminderWorker>()
                .setInitialDelay(
                    delayMillis,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                )
                .setInputData(
                    Data.Builder().putString(KEY_REMINDER_ID, reminder.id).build(),
                )
                .addTag("assistant-reminders")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    private fun alarmPendingIntent(context: Context, reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, AssistantReminderAlarmReceiver::class.java).apply {
                action = ALARM_ACTION
                putExtra(KEY_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class AssistantReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(AssistantReminderScheduler.KEY_REMINDER_ID)
            ?: return
        AssistantReminderStore.initialize(context)
        AssistantReminderScheduler.executeFromAlarm(context, reminderId)
    }
}

class AssistantReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AssistantReminderScheduler.rescheduleAll(context)
    }
}

private data class ReminderExecution(
    val text: String,
    val thoughtMs: Long,
)

internal fun reminderSearchThinkingSteps(
    reminderId: String,
    startedAt: Long,
    trace: HubSearchTrace?,
    evidence: List<SearchEvidence>,
): List<HubThinkingStep> = trace?.let {
    listOf(
        HubThinkingStep(
            id = "reminder-search-$reminderId-$startedAt",
            kind = "search",
            title = "联网搜索",
            detail = "查询“${it.query}”，返回 ${it.resultCount} 条结果",
            items = evidence.map { item -> HubSearchResult(item.title, item.url) },
            cached = it.cached,
            providerKinds = it.providerKinds,
        ),
    )
}.orEmpty()

class AssistantReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(AssistantReminderScheduler.KEY_REMINDER_ID)
            ?: return Result.success()
        initializeStores(applicationContext)
        val reminder = AssistantReminderStore.byId(reminderId) ?: return Result.success()
        if (!reminder.enabled) return Result.success()
        val assistant = AssistantStore.byId(reminder.assistantId) ?: return Result.success()

        val executionStartedAt = SystemClock.elapsedRealtime()
        var searchTrace: HubSearchTrace? = null
        var searchEvidence: List<SearchEvidence> = emptyList()
        val result = try {
            kotlin.Result.success(run {
                require(reminder.prompt.isNotBlank()) { "提醒事项内容为空" }
                val provider = resolveProvider(assistant)
                    ?: error("还没有配置可用的聊天模型")
                val model = resolveModel(assistant, provider)
                    ?: error("当前提供商没有可用模型")
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA)
                    .format(Date())
                val needsLiveInformation = FeatureActivation.shouldSearch(reminder.prompt)
                val searchOutcome = if (needsLiveInformation) {
                    if (SearchStore.enabled.isEmpty()) {
                        error("这个事项需要查询实时信息。请先在「搜索设置」中添加并启用搜索服务。")
                    }
                    SearchContext.build(reminder.prompt).getOrElse { failure ->
                        error(
                            "未能获取实时信息，请检查网络、搜索服务密钥或额度。" +
                                failure.message.orEmpty().let { reason ->
                                    if (reason.isBlank()) "" else "（$reason）"
                                },
                        )
                    }.also { outcome ->
                        searchTrace = outcome.trace
                        searchEvidence = outcome.evidence
                    }
                } else {
                    null
                }
                val searchContext = searchOutcome?.context.orEmpty()
                val prompt = buildString {
                    append("这是一次由用户预设的自动化任务，请直接执行并返回结果，不要询问用户。\n")
                    append("当前时间：").append(now).append('\n')
                    append("提醒标题：").append(reminder.title).append('\n')
                    append("任务：").append(reminder.prompt.trim()).append('\n')
                    append("要求：先给结论或摘要，再给必要的要点；控制在容易在通知和聊天中阅读的长度。")
                    if (searchContext.isNotBlank()) {
                        append("\n\n").append(searchContext)
                    }
                }
                val settings = AssistantStore.effectiveChatSettings(
                    assistant = assistant,
                    activeSkillIds = assistant.skillIds,
                    userText = reminder.prompt,
                )
                val answer = withTimeoutOrNull(120_000) {
                    AiClient.stream(
                        provider = provider,
                        model = model,
                        turns = listOf(HubTurn(role = "user", content = prompt)),
                        settings = settings,
                        streaming = false,
                        formatResponse = true,
                        onDelta = {},
                    ).getOrThrow()
                } ?: error("模型响应超时")
                ReminderExecution(
                    text = answer.trim().ifBlank { "模型没有返回内容" },
                    thoughtMs = SystemClock.elapsedRealtime() - executionStartedAt,
                )
            })
        } catch (cancelled: CancellationException) {
            // WorkManager 因系统资源或网络约束暂停本次执行时交给它自动重试。
            // 不要写“执行失败”，也不要发失败通知。
            throw cancelled
        } catch (failure: Throwable) {
            kotlin.Result.failure(failure)
        }

        val execution = result.getOrElse {
            ReminderExecution(
                text = "事项执行失败：" + it.message.orEmpty().ifBlank { "请稍后重试" },
                thoughtMs = SystemClock.elapsedRealtime() - executionStartedAt,
            )
        }
        val text = execution.text
        val thinkingSteps = reminderSearchThinkingSteps(
            reminderId = reminder.id,
            startedAt = executionStartedAt,
            trace = searchTrace,
            evidence = searchEvidence,
        )
        val runAt = System.currentTimeMillis()
        val conversationId = runCatching {
            saveResultToConversation(
                reminder = reminder,
                text = text,
                at = runAt,
                thoughtMs = execution.thoughtMs,
                searchTrace = searchTrace,
                thinkingSteps = thinkingSteps,
            )
        }.onFailure {
            Log.e(TAG, "提醒结果写入会话失败 reminder=${reminder.id}", it)
        }.getOrDefault("")
        AssistantReminderStore.markRun(
            applicationContext,
            reminder.id,
            text,
            runAt,
            conversationId,
        )
        notify(reminder.copy(conversationId = conversationId.ifBlank { reminder.conversationId }), text, result.isSuccess)

        val latest = AssistantReminderStore.byId(reminder.id)
        if (latest?.enabled == true) {
            if (latest.repeat == ReminderRepeat.ONCE) {
                AssistantReminderStore.upsert(applicationContext, latest.copy(enabled = false))
                AssistantReminderScheduler.cancel(applicationContext, latest.id)
            } else {
                AssistantReminderScheduler.scheduleNext(applicationContext, latest)
            }
        }
        return Result.success()
    }

    private suspend fun saveResultToConversation(
        reminder: AssistantReminder,
        text: String,
        at: Long,
        thoughtMs: Long,
        searchTrace: HubSearchTrace?,
        thinkingSteps: List<HubThinkingStep>,
    ): String {
        ChatDatabase.initialize(applicationContext)
        val conversationId = when (reminder.displayLocation) {
            ReminderDisplayLocation.NEW_CONVERSATION -> ChatDatabase.newConversation(
                reminder.title.ifBlank { "自动化任务" },
                at,
            )
            ReminderDisplayLocation.RECENT_CONVERSATION ->
                reminder.conversationId.takeIf(ChatDatabase::conversationExists)
                    ?: ChatDatabase.mostRecentConversationId()
                    ?: ChatDatabase.newConversation(
                        reminder.title.ifBlank { "自动化任务" },
                        at,
                    )
        }
        val title = reminder.title.ifBlank { "自动化任务" }
        ChatDatabase.appendMessageAtEnd(
            conversationId = conversationId,
            message = HubMessage(
                role = "assistant",
                text = "**自动化任务 · $title**\n\n${text.trim()}",
                at = at,
                thoughtMs = thoughtMs,
                searchTraces = listOfNotNull(searchTrace),
                thinkingSteps = thinkingSteps,
            ),
        )
        withContext(Dispatchers.Main.immediate) {
            ChatStore.reloadIfCurrent(conversationId)
        }
        return conversationId
    }

    private fun resolveProvider(assistant: HubAssistant): HubProvider? {
        val explicit = assistant.providerId.takeIf { it.isNotBlank() }?.let { id ->
            ProviderStore.providers.firstOrNull { it.id == id && it.enabled }
        }
        return explicit ?: ProviderStore.currentProvider
    }

    private fun resolveModel(assistant: HubAssistant, provider: HubProvider): String? {
        if (assistant.providerId == provider.id && assistant.model in provider.models) {
            return assistant.model
        }
        if (assistant.providerId.isBlank() && ProviderStore.currentModel in provider.models) {
            return ProviderStore.currentModel
        }
        return provider.models.firstOrNull()
    }

    private fun notify(reminder: AssistantReminder, body: String, success: Boolean) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val localizedBody = applicationContext.localizedUiText(body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.localizedUiString(R.string.automation_tasks),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            applicationContext,
            reminder.id.hashCode(),
            AssistantReminderNavigation.activityIntent(applicationContext, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (success) R.drawable.ic_bulb else R.drawable.ic_info)
            .setContentTitle(reminder.title)
            .setContentText(localizedBody.lineSequence().firstOrNull().orEmpty().take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedBody.take(2_000)))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext)
                .notify(reminder.id.hashCode() and 0x7fffffff, notification)
        }
    }

    private fun initializeStores(context: Context) {
        ProviderStore.initialize(context)
        LanguageStore.initialize(context)
        SkillStore.initialize(context)
        ModelRoleStore.initialize(context)
        MemoryStore.initialize(context)
        SearchStore.initialize(context)
        AssistantStore.initialize(context)
        AssistantReminderStore.initialize(context)
        ChatDatabase.initialize(context)
    }

    companion object {
        private const val TAG = "AssistantReminderWorker"
        private const val CHANNEL_ID = "assistant_reminders"
    }
}
