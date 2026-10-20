package com.fingertipfuture.hub.ui.chat

import android.content.Context
import android.widget.Toast
import com.fingertipfuture.hub.data.AgentDecision
import com.fingertipfuture.hub.data.AgentPlanningResult
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.data.DeviceContext
import com.fingertipfuture.hub.data.FeatureActivation
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.HubChatSettings
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubSearchTrace
import com.fingertipfuture.hub.data.HubSkillTrace
import com.fingertipfuture.hub.data.HubThinkingStep
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.SearchAgent
import com.fingertipfuture.hub.data.SearchContext
import com.fingertipfuture.hub.data.SearchEvidence
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.data.isFromUser
import com.fingertipfuture.hub.data.plus
import com.fingertipfuture.hub.data.resolvedSkillIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一次完整聊天请求：设备上下文 → 记忆/技能 → 联网规划与搜索 → 最终流式回答。
 *
 * 从 ChatScreen 的 God Composable 中抽出，界面层只负责创建占位气泡、维护
 * request token 和请求句柄；这里不直接持有 Compose 状态，方便独立测试与维护。
 */
internal suspend fun runChatRequest(
    provider: HubProvider,
    model: String,
    assistant: HubAssistant,
    conversationId: String,
    appContext: Context,
    handle: AiClient.StreamHandle,
    stoppedLabel: String,
    emptyReplyLabel: String,
    isCurrent: () -> Boolean,
    cancelRequested: () -> Boolean,
): HubTokenUsage {
    // 历史从库里取最近 60 条：界面为了分页只加载了一小段窗口，
    // 不能拿 UI 的 messages 当完整上下文。库里没有正在生成的那条空气泡。
    val history = withContext(Dispatchers.IO) {
        ChatStore.requestHistory(conversationId)
    }.filterNot { it.localOnly }
    // 助手关掉「参考历史聊天记录」就只把当前这一条发出去（省 token）
    val historyForRequest = if (assistant.useHistory) history else history.takeLast(1)
    // 只把「最后一条用户消息」里的图片重新发给模型。历史图片每轮都 base64 一遍，
    // 聊几轮就会把请求体撑到几 MB，既烧流量也容易触发 413 / 超出上下文。
    val lastUserIndex = historyForRequest.indexOfLast { it.isFromUser }
    val baseTurns = historyForRequest.mapIndexed { index, message ->
        HubTurn(
            role = message.role,
            content = message.text,
            attachments = if (index == lastUserIndex) {
                message.attachments
            } else {
                message.attachments.filterNot { it.isImage }
            },
        )
    }
    // 长对话超过阈值时真正调用「压缩模型」；失败则保留原历史，不让聊天停掉。
    val compactRoute = ModelRoleStore.route(ModelRoleStore.compact)
    var summaryUsage = HubTokenUsage()
    val summary = if (assistant.useHistory && compactRoute != null && baseTurns.size > 24) {
        val target = baseTurns.dropLast(8)
        val routeKey = compactRoute.first.id + "|" + compactRoute.second
        val cached = SummaryCache.get(conversationId, routeKey)
        // 前缀内容变了（编辑/重发）就不能复用旧摘要，但只是新增消息时前缀不变，
        // 可以继续增量压缩。
        val validCached = cached?.takeIf {
            it.covered <= target.size && target.take(it.covered).hashCode() == it.prefixHash
        }
        when {
            validCached != null && validCached.covered == target.size -> validCached.summary
            else -> {
                val covered = validCached?.covered ?: 0
                val chunk = target.drop(covered)
                if (chunk.isEmpty()) {
                    validCached?.summary.orEmpty()
                } else {
                    summarizeHistory(
                        route = compactRoute,
                        turns = chunk,
                        handle = handle,
                        previousSummary = if (covered > 0) validCached?.summary.orEmpty() else "",
                    ) { summaryUsage += it }.takeIf { it.isNotBlank() }?.also { merged ->
                        SummaryCache.put(
                            conversationId = conversationId,
                            routeKey = routeKey,
                            covered = target.size,
                            prefixHash = target.hashCode(),
                            summary = merged,
                        )
                    }.orEmpty()
                }
            }
        }
    } else {
        ""
    }
    val turns = if (summary.isNotBlank()) {
        listOf(HubTurn(role = "user", content = "【更早对话摘要】\n$summary")) +
            baseTurns.takeLast(8)
    } else {
        baseTurns
    }
    val requestAt = System.currentTimeMillis()
    val userText = turns.lastOrNull { it.role == "user" }?.content.orEmpty()
    if (!isCurrent()) return HubTokenUsage()

    fun updateAssistant(transform: (HubMessage) -> HubMessage): Boolean {
        if (!isCurrent()) return false
        return ChatStore.updateLastIf(conversationId, transform)
    }

    fun addThinkingStep(step: HubThinkingStep) {
        updateAssistant { it.copy(thinkingSteps = it.thinkingSteps + step) }
    }

    fun updateThinkingStep(
        id: String,
        transform: (HubThinkingStep) -> HubThinkingStep,
    ) {
        updateAssistant { message ->
            message.copy(
                thinkingSteps = message.thinkingSteps.map {
                    if (it.id == id) transform(it) else it
                },
            )
        }
    }

    fun addPlannerReasoning(id: String, planned: AgentPlanningResult) {
        if (planned.reasoning.isBlank()) return
        addThinkingStep(
            HubThinkingStep(
                id = id,
                kind = "reasoning",
                title = "思考",
                detail = planned.reasoning,
                status = HubThinkingStep.STATUS_DONE,
                durationMs = planned.durationMs,
            ),
        )
    }

    val understandStepId = "understand-$requestAt"
    addThinkingStep(
        HubThinkingStep(
            id = understandStepId,
            kind = "thinking",
            title = "理解问题",
            detail = "正在判断需不需要位置和实时信息",
            status = HubThinkingStep.STATUS_PENDING,
        ),
    )

    val locationIntent = assistant.permLocation &&
        FeatureActivation.needsLocationContext(userText)
    val locationStepId = "location-$requestAt"
    if (locationIntent) {
        addThinkingStep(
            HubThinkingStep(
                id = locationStepId,
                kind = "location",
                title = "获取位置",
                detail = "正在读取当前位置",
                status = HubThinkingStep.STATUS_PENDING,
            ),
        )
    }
    val deviceInfo = DeviceContext.build(appContext, assistant, userText)
    val deviceContext = deviceInfo.text
    deviceInfo.accessTraces.forEach { trace ->
        addThinkingStep(
            HubThinkingStep(
                id = "permission-${trace.kind}-$requestAt",
                kind = trace.kind,
                title = trace.title,
                detail = trace.detail,
                status = if (trace.ok) {
                    HubThinkingStep.STATUS_DONE
                } else {
                    HubThinkingStep.STATUS_ERROR
                },
            ),
        )
    }

    // 只有问题与记忆相关（或用户明确问“还记得…”）时才注入，避免每条消息都塞全量记忆。
    val activeMemories = MemoryStore.relevantFor(assistant, userText)
    if (activeMemories.isNotEmpty()) {
        addThinkingStep(
            HubThinkingStep(
                id = "memory-$requestAt",
                kind = "memory",
                title = "调用记忆",
                detail = activeMemories.size.toString() + " 条：" +
                    activeMemories.joinToString("；") {
                        it.text.replace('\n', ' ').take(80)
                    },
                status = HubThinkingStep.STATUS_DONE,
            ),
        )
    }

    // 技能和搜索都是请求前发生的真实动作；先把它们写进助手占位，
    // 思考框就能按“思考 → Skill → 搜索”的顺序实时长出来。
    val selectedSkillId = ProviderStore.chatSettings.skillId
    val selectedSkill = selectedSkillId.takeIf { it.isNotBlank() }?.let(SkillStore::byId)
    val availableSkills = resolvedSkillIds(assistant.skillIds, selectedSkillId)
        .mapNotNull(SkillStore::byId)
    // 明确在聊天页选中的技能无条件注入；助手自带技能仍按“用户点名才启用”的规则。
    val activeSkills = (FeatureActivation.activeSkills(availableSkills, userText) +
        listOfNotNull(selectedSkill)).distinctBy { it.id }
    val skillTraces = activeSkills.map { skill ->
        HubSkillTrace(id = skill.id, name = skill.name, description = skill.description)
    }
    if (skillTraces.isNotEmpty()) {
        updateAssistant { it.copy(skillsUsed = skillTraces) }
        skillTraces.forEachIndexed { index, skill ->
            addThinkingStep(
                HubThinkingStep(
                    id = "skill-$requestAt-$index",
                    kind = "skill",
                    title = "使用技能",
                    detail = skill.name,
                    status = HubThinkingStep.STATUS_DONE,
                ),
            )
        }
    }

    val searchProviders = SearchStore.parallel.ifEmpty { SearchStore.enabled.take(1) }
    val previousSearchQuery = history.asReversed()
        .firstNotNullOfOrNull { message ->
            message.searchTraces.asReversed()
                .firstOrNull { it.query.isNotBlank() }
                ?.query
        }
        .orEmpty()
    val canUseSearch = SearchStore.chatEnabled && searchProviders.isNotEmpty()
    // 自动联网搜索是默认行为，但“默认开”不等于“每条都先跑一次规划”：
    // 普通闲聊、写作、翻译、代码解释在本地就能判定不需要检索，直接回答。
    val searchIntent = FeatureActivation.shouldSearch(userText) ||
        FeatureActivation.needsConversationContext(userText)
    val providerKinds = searchProviders.map { it.kind }.distinct()
    var totalUsage = HubTokenUsage()
    totalUsage += summaryUsage
    val evidence = mutableListOf<SearchEvidence>()
    val searchContexts = mutableListOf<String>()
    val previousUserText = if (FeatureActivation.needsConversationContext(userText)) {
        turns.dropLast(1).lastOrNull { it.role == "user" }?.content.orEmpty()
    } else {
        ""
    }
    val searchIntentHint = buildString {
        append(userText.replace('\n', ' ').take(600))
        if (previousUserText.isNotBlank()) {
            append("\nprev:").append(previousUserText.replace('\n', ' ').take(240))
        }
        append("\nloc:").append(deviceInfo.searchLocation)
    }

    updateThinkingStep(understandStepId) {
        it.copy(
            status = HubThinkingStep.STATUS_DONE,
            detail = if (canUseSearch && searchIntent) {
                "这条可能需要实时信息，先判断是否要联网核对"
            } else {
                "普通对话，直接回答，不需要联网搜索"
            },
        )
    }
    if (locationIntent) {
        val locationOk = deviceInfo.locationSummary.isNotBlank() &&
            deviceInfo.searchLocation.isNotBlank()
        updateThinkingStep(locationStepId) {
            it.copy(
                status = if (locationOk) {
                    HubThinkingStep.STATUS_DONE
                } else {
                    HubThinkingStep.STATUS_ERROR
                },
                detail = deviceInfo.locationSummary.ifBlank { "没有拿到当前位置" },
            )
        }
    }
    if (assistant.permTime && FeatureActivation.needsTimeContext(userText)) {
        addThinkingStep(
            HubThinkingStep(
                id = "permission-time-$requestAt",
                kind = "time",
                title = "读取时间信息",
                detail = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm EEEE",
                    java.util.Locale.CHINA,
                ).format(java.util.Date()),
                status = HubThinkingStep.STATUS_DONE,
            ),
        )
    }

    // 规划调用和最终回答用同一模型，token 才能准确记到这条回复的模型名下。
    val fastRoute = ModelRoleStore.route(ModelRoleStore.fast)
    val plannerProvider = fastRoute?.first ?: provider
    val plannerModel = fastRoute?.second ?: model
    val planStepId = "agent-plan-$requestAt"
    var decision: AgentDecision = AgentDecision.Answer("不需要联网搜索")

    if (canUseSearch && searchIntent) {
        addThinkingStep(
            HubThinkingStep(
                id = planStepId,
                kind = "thinking",
                title = "模型规划",
                detail = "正在判断是否需要搜索，以及该搜什么",
                status = HubThinkingStep.STATUS_PENDING,
            ),
        )
        val first = SearchAgent.decide(
            provider = plannerProvider,
            model = plannerModel,
            userText = userText,
            recentTurns = turns.dropLast(1),
            location = deviceInfo.searchLocation,
            previousSearchQuery = previousSearchQuery,
            evidence = emptyList(),
            handle = handle,
            includeCurrentTime = assistant.permTime && FeatureActivation.needsTimeContext(userText),
        )
        val planned = first.getOrNull()
        if (planned != null) {
            totalUsage += planned.usage
            decision = planned.decision
            addPlannerReasoning("reasoning-plan-$requestAt", planned)
            updateThinkingStep(planStepId) {
                it.copy(
                    status = HubThinkingStep.STATUS_DONE,
                    detail = when (planned.decision) {
                        is AgentDecision.Search -> planned.decision.reason
                        is AgentDecision.Answer -> planned.decision.reason
                    },
                )
            }
        } else {
            // 规划器失败不能让聊天停掉：退回本地规则，仍可继续搜索。
            val fallback = FeatureActivation.planSearch(
                text = userText,
                recentTurns = turns.dropLast(1)
                    .filter { it.role == "user" }
                    .map { it.content },
                location = deviceInfo.searchLocation,
                previousSearchQuery = previousSearchQuery,
            )
            decision = if (fallback.shouldSearch) {
                AgentDecision.Search(
                    queries = listOf(fallback.query),
                    reason = fallback.reason.ifBlank { "本地规则判断需要搜索" },
                )
            } else {
                AgentDecision.Answer(
                    fallback.reason.ifBlank { "本地规则判断不需要搜索" },
                )
            }
            updateThinkingStep(planStepId) {
                it.copy(
                    status = HubThinkingStep.STATUS_ERROR,
                    detail = "模型规划不可用，已改用本地规则：" +
                        when (val value = decision) {
                            is AgentDecision.Search -> value.queries.joinToString("、")
                            is AgentDecision.Answer -> value.reason
                        },
                )
            }
        }
    }

    var searchRound = 0
    while (decision is AgentDecision.Search && searchRound < 2 && !cancelRequested()) {
        val searchDecision = decision
        updateThinkingStep(planStepId) {
            it.copy(
                status = HubThinkingStep.STATUS_DONE,
                detail = searchDecision.reason + "：" +
                    searchDecision.queries.joinToString("、"),
            )
        }
        searchDecision.queries.forEachIndexed { queryIndex, query ->
            val searchStepId = "search-$requestAt-$searchRound-$queryIndex"
            addThinkingStep(
                HubThinkingStep(
                    id = searchStepId,
                    kind = "search",
                    title = "搜索网页",
                    detail = query,
                    status = HubThinkingStep.STATUS_PENDING,
                ),
            )
            val traceIndex = ChatStore.messages.lastOrNull()?.searchTraces?.size ?: 0
            updateAssistant {
                it.copy(
                    searchTraces = it.searchTraces + HubSearchTrace(
                        query = query,
                        providerKinds = providerKinds,
                        queriedProviderKinds = providerKinds,
                        pending = true,
                    ),
                )
            }
            SearchContext.build(
                query = query,
                intentHint = searchIntentHint,
                allowIntentCache = searchRound == 0,
            ).fold(
                onSuccess = { outcome ->
                    val trace = outcome.trace ?: HubSearchTrace(query = query)
                    updateAssistant { message ->
                        message.copy(
                            searchTraces = message.searchTraces.mapIndexed { index, item ->
                                if (index == traceIndex) trace else item
                            },
                        )
                    }
                    evidence += outcome.evidence
                    if (outcome.context.isNotBlank()) searchContexts += outcome.context
                    updateThinkingStep(searchStepId) {
                        it.copy(
                            status = HubThinkingStep.STATUS_DONE,
                            detail = query,
                            items = trace.results,
                            cached = trace.cached,
                            providerKinds = trace.providerKinds,
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: "未知错误"
                    updateAssistant { item ->
                        item.copy(
                            searchTraces = item.searchTraces.mapIndexed { index, trace ->
                                if (index == traceIndex) {
                                    HubSearchTrace(
                                        query = query,
                                        providerKinds = providerKinds,
                                        queriedProviderKinds = providerKinds,
                                        error = message,
                                    )
                                } else {
                                    trace
                                }
                            },
                        )
                    }
                    updateThinkingStep(searchStepId) {
                        it.copy(
                            status = HubThinkingStep.STATUS_ERROR,
                            detail = message,
                        )
                    }
                    Toast.makeText(
                        appContext,
                        "联网搜索失败：" + message,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
        searchRound++
        if (searchRound >= 2 || cancelRequested() || evidence.isEmpty()) break

        val judgeStepId = "agent-judge-$requestAt-$searchRound"
        addThinkingStep(
            HubThinkingStep(
                id = judgeStepId,
                kind = "thinking",
                title = "核对资料",
                detail = "正在判断已有结果是否足够回答",
                status = HubThinkingStep.STATUS_PENDING,
            ),
        )
        val next = SearchAgent.decide(
            provider = plannerProvider,
            model = plannerModel,
            userText = userText,
            recentTurns = turns.dropLast(1),
            location = deviceInfo.searchLocation,
            previousSearchQuery = previousSearchQuery,
            evidence = evidence,
            handle = handle,
            includeCurrentTime = assistant.permTime && FeatureActivation.needsTimeContext(userText),
        ).getOrNull()
        if (next == null) {
            decision = AgentDecision.Answer("规划器不可用，使用已有结果回答")
            updateThinkingStep(judgeStepId) {
                it.copy(
                    status = HubThinkingStep.STATUS_ERROR,
                    detail = "规划器不可用，使用已有结果回答",
                )
            }
        } else {
            totalUsage += next.usage
            decision = next.decision
            addPlannerReasoning("reasoning-judge-$requestAt-$searchRound", next)
            updateThinkingStep(judgeStepId) {
                it.copy(
                    status = HubThinkingStep.STATUS_DONE,
                    detail = when (val value = next.decision) {
                        is AgentDecision.Search ->
                            "资料还不够，继续搜索：" + value.queries.joinToString("、")
                        is AgentDecision.Answer -> value.reason
                    },
                )
            }
        }
    }

    val searchContext = searchContexts
        .distinct()
        .joinToString("\n\n")
        .take(12_000)

    val answerStepId = "answer-$requestAt"
    addThinkingStep(
        HubThinkingStep(
            id = answerStepId,
            kind = "thinking",
            title = "组织回答",
            detail = "正在结合已确认的信息作答",
            status = HubThinkingStep.STATUS_PENDING,
        ),
    )
    val baseSettings = AssistantStore.effectiveChatSettings(
        assistant = assistant,
        activeSkillIds = activeSkills.map { it.id },
        userText = userText,
    )
    val extraContext = listOf(deviceContext, searchContext)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    val chatSettings = if (extraContext.isBlank()) {
        baseSettings
    } else {
        baseSettings.copy(
            dynamicContext = listOf(baseSettings.dynamicContext, extraContext)
                .filter { it.isNotBlank() }
                .joinToString("\n\n"),
        )
    }
    val answerStartedAt = System.currentTimeMillis()
    val finalReasoningStepId = "reasoning-answer-$requestAt"
    // 「思考了 X 秒」里的 X = 发出请求 → 收到第一段正文之间的时间（见 HubMessage.thoughtMs）
    var thoughtMs = 0L
    var finalReasoningMs = 0L
    var lastDeltaUiAt = 0L
    var lastReasoningUiAt = 0L
    // 令牌对不上 = 这次请求已经被「重新生成 / 重新发送」顶掉了，之后一个字都不许再写
    suspend fun update(transform: (HubMessage) -> HubMessage) {
        // AiClient 的 onDelta 跑在 IO 线程；ChatStore 的 entries 是普通变量，
        // 必须和切会话 / 删除 / 编辑重发这些主线程操作串行化，否则旧流可能
        // 把最后一片文字写进刚切过去的新会话。
        withContext(Dispatchers.Main.immediate) {
            if (isCurrent()) ChatStore.updateLastIf(conversationId, transform)
        }
    }
        val result = AiClient.stream(
            provider = provider,
            model = model,
            turns = turns,
            settings = chatSettings,
            streaming = UiPrefs.streamingOutput,
            isCancelled = cancelRequested,
            onDelta = { partial ->
                val now = System.currentTimeMillis()
                // 服务端可能几十毫秒就推一片；不逐片重建消息列表/重排 LazyColumn。
                // 首片立刻显示，之后最多约 20 次/秒；最终结果仍用 result 全量收尾。
                if (thoughtMs == 0L || now - lastDeltaUiAt >= 50L) {
                    val firstDelta = thoughtMs == 0L
                    if (firstDelta) {
                        thoughtMs = (now - requestAt).coerceAtLeast(1L)
                        finalReasoningMs = (now - answerStartedAt).coerceAtLeast(1L)
                    }
                    lastDeltaUiAt = now
                    update { message ->
                        message.copy(
                            text = partial,
                            thoughtMs = thoughtMs,
                            thinkingSteps = if (firstDelta) {
                                message.thinkingSteps.map { step ->
                                    when (step.id) {
                                        answerStepId -> step.copy(
                                            status = HubThinkingStep.STATUS_DONE,
                                        )
                                        finalReasoningStepId -> step.copy(
                                            status = HubThinkingStep.STATUS_DONE,
                                            durationMs = finalReasoningMs,
                                        )
                                        else -> step
                                    }
                                }
                            } else {
                                message.thinkingSteps
                            },
                        )
                    }
                }
            },
            onReasoning = { partial ->
                val now = System.currentTimeMillis()
                if (now - lastReasoningUiAt >= 80L) {
                    lastReasoningUiAt = now
                    val elapsed = (now - answerStartedAt).coerceAtLeast(1L)
                    update { message ->
                        val hasReasoningStep = message.thinkingSteps.any {
                            it.id == finalReasoningStepId
                        }
                        message.copy(
                            reasoning = partial,
                            thinkingSteps = if (hasReasoningStep) {
                                message.thinkingSteps.map {
                                    if (it.id == finalReasoningStepId) {
                                        it.copy(
                                            detail = partial,
                                            durationMs = elapsed,
                                            status = HubThinkingStep.STATUS_PENDING,
                                        )
                                    } else {
                                        it
                                    }
                                }
                            } else {
                                message.thinkingSteps + HubThinkingStep(
                                    id = finalReasoningStepId,
                                    kind = "reasoning",
                                    title = "思考",
                                    detail = partial,
                                    status = HubThinkingStep.STATUS_PENDING,
                                    durationMs = elapsed,
                                )
                            },
                        )
                    }
                }
            },
            onUsage = { usage ->
                totalUsage += usage
            },
            handle = handle,
        )
        update { message ->
            // 点过停止 / 出错：已经收到的半句话要留下。
            // 真出错且一个字都没收到才显示错误；点了停止就标一下「已停止」。
            val finalText = result.getOrElse { error ->
                message.text.ifBlank { "⚠️ " + (error.message ?: "请求失败") }
            }
            message.copy(
                // 点了停止 →（已停止）；请求成功却一个字都没有 →（空回复），别混为一谈
                text = finalText.ifBlank { if (cancelRequested()) stoppedLabel else emptyReplyLabel },
                thoughtMs = message.thoughtMs.takeIf { it > 0L }
                    ?: (System.currentTimeMillis() - requestAt).coerceAtLeast(1L),
                streaming = false,
                thinkingSteps = message.thinkingSteps.map { step ->
                    when (step.id) {
                        answerStepId -> step.copy(
                            status = if (result.isSuccess) {
                                HubThinkingStep.STATUS_DONE
                            } else {
                                HubThinkingStep.STATUS_ERROR
                            },
                        )
                        finalReasoningStepId -> step.copy(
                            status = if (result.isSuccess) {
                                HubThinkingStep.STATUS_DONE
                            } else {
                                HubThinkingStep.STATUS_ERROR
                            },
                            durationMs = finalReasoningMs.takeIf { it > 0L }
                                ?: (System.currentTimeMillis() - answerStartedAt)
                                    .coerceAtLeast(1L),
                        )
                        else -> step
                    }
                },
            )
        }

    return totalUsage
}

private data class CachedSummary(
    val routeKey: String,
    val covered: Int,
    val prefixHash: Int,
    val summary: String,
)

/** 进程内摘要缓存：长聊每轮只压缩新增加的那一小段，失败/重启后自动重算。 */
private object SummaryCache {
    private const val MAX_ENTRIES = 4
    private val entries = object : LinkedHashMap<String, CachedSummary>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSummary>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(conversationId: String, routeKey: String): CachedSummary? = synchronized(entries) {
        entries[conversationId]?.takeIf { it.routeKey == routeKey }
    }

    fun put(
        conversationId: String,
        routeKey: String,
        covered: Int,
        prefixHash: Int,
        summary: String,
    ) {
        synchronized(entries) {
            entries[conversationId] = CachedSummary(routeKey, covered, prefixHash, summary)
        }
    }
}

private suspend fun summarizeHistory(
    route: Pair<HubProvider, String>,
    turns: List<HubTurn>,
    handle: AiClient.StreamHandle?,
    previousSummary: String = "",
    onUsage: (HubTokenUsage) -> Unit = {},
): String {
    if (turns.isEmpty()) return previousSummary
    val transcript = turns.joinToString("\n") { turn ->
        (if (turn.role == "user") "用户：" else "助手：") +
            turn.content.replace('\n', ' ').take(1200)
    }.take(16_000)
    val mergedInput = buildString {
        if (previousSummary.isNotBlank()) {
            append("【已有摘要】\n").append(previousSummary.take(4_000)).append("\n\n")
            append("请把下面的新增对话合并进已有摘要，保留仍然有效的用户偏好、结论和未完成任务：\n\n")
        }
        append(transcript)
    }.take(20_000)
    val result = runCatching {
        AiClient.stream(
            provider = route.first,
            model = route.second,
            turns = listOf(
                HubTurn(
                    role = "user",
                    content = "请把下面较早的对话压缩成事实摘要，保留用户偏好、已确认结论和未完成任务；" +
                        "不要编造内容，不要输出标题，控制在 1200 字以内。\n\n$mergedInput",
                ),
            ),
            settings = HubChatSettings(
                systemPrompt = "你是对话历史压缩器，只输出简洁的事实摘要。",
                temperature = 0.2,
                maxTokens = 900,
            ),
            streaming = false,
            formatResponse = false,
            handle = handle,
            onDelta = {},
            onUsage = onUsage,
        )
    }.getOrNull()?.getOrNull()?.trim().orEmpty()
    return result.take(4000)
}
