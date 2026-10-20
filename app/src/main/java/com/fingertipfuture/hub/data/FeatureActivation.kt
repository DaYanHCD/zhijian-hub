package com.fingertipfuture.hub.data

internal data class SearchPlan(
    val shouldSearch: Boolean,
    val query: String = "",
    /** 给思考时间线看的一句本地规划说明，不冒充模型的隐藏推理。 */
    val reason: String = "",
)

private enum class SearchComplaintKind {
    NOT_FRESH,
    WRONG_KEYWORDS,
    MISSED_SEARCH,
    ABUSIVE_FEEDBACK,
}

private data class SearchComplaint(
    val kind: SearchComplaintKind,
    val correction: String = "",
)

/**
 * 决定这一轮消息真正要用哪些“可用能力”。
 *
 * 开关打开只代表**可用**，不代表每条消息都强行调用：
 * - 技能：内置翻译只在明确要求翻译时注入；其它技能只在用户点名时注入。
 * - 搜索：只在问题明显需要实时 / 网页信息时发起。
 *
 * 纯本地规则，不额外消耗一次模型调用。以后接真正的工具调用时，这一层可以退化成兜底。
 */
internal object FeatureActivation {

    private val translationTerms = listOf(
        "翻译", "translate", "translation", "译成", "译一下", "翻成",
        "英译中", "中译英", "英文怎么说", "中文怎么说", "用英文说", "用中文说",
        "转成英文", "转成中文", "帮我翻",
    )

    /**
     * 只有用户明确在问“现在几点 / 今天几号”这类问题时才注入系统时间。
     *
     * 以前“时间信息”权限一开，每条消息都会塞一次当前时间，思考框里也每次都出现
     * 「读取时间信息」——看起来像随便说句“你好”都在调用权限。时间不是每轮必需的。
     */
    private val explicitTimeTerms = listOf(
        "几点", "几号", "多少号", "星期几", "周几", "日期", "今天是什么日子",
        "现在时间", "当前时间", "时间是多少", "现在是什么时候", "现在几点",
        "今年是", "本月", "这个月", "上个月", "下周是", "本周是",
        "what time", "current time", "today's date", "what date", "what day",
        "day of the week", "what's the date",
    )

    private val explicitSearchTerms = listOf(
        "搜索", "搜一下", "搜一搜", "搜网页", "联网", "上网查", "查一下", "查一查",
        "帮我查", "查询", "search", "look up", "web search",
    )

    private val freshInfoTerms = listOf(
        "最新", "实时",
        "新闻", "资讯", "动态", "进展", "比分", "赛程", "价格", "汇率", "股票",
        "天气", "气温", "热搜", "排行", "发布时间", "上线时间", "发售日期",
        "发生了什么", "怎么了",
        "latest", "news", "real-time", "realtime",
        "weather", "price", "schedule", "score", "release date", "what happened",
    )

    private val timeContextTerms = listOf(
        "今天", "今日", "现在", "目前", "当前", "最近", "近期", "几点", "几号",
        "today", "current", "right now",
    )

    private val timeQueryTerms = listOf(
        "几点", "几号", "几月", "日期", "星期", "多少号", "时间",
        "消息", "新闻", "信息", "情况", "动态", "进展", "天气", "价格", "谁",
    )

    private val searchCommand = Regex(
        """^(?:(?:请|麻烦|帮我|帮忙|替我|给我|你能|能不能|可以|可不可以|再|重新|顺便|然后)\s*)*(?:(?:联网|上网|在网页上|帮我)?(?:搜索|搜一下|搜一搜|搜网页|搜|查一下|查一查|查询|查|search(?:\s+for)?|look\s+up)\s*(?:一下|下)?\s*)""",
        RegexOption.IGNORE_CASE,
    )

    private val leadingFiller = Regex(
        """^(?:(?:请|麻烦|帮我|帮忙|替我|给我|你能|能不能|可以|可不可以|我想知道|我想了解|请告诉我|告诉我|看看|看一下|顺便|然后|再)\s*)+""",
        RegexOption.IGNORE_CASE,
    )

    private val trailingFiller = Regex(
        """(?:可以吗|行吗|好吗|怎么样|是什么|是谁|是多少|多少钱|吗|呢|吧|啊|呀|谢谢|拜托)[？?！!。.\s]*$""",
        RegexOption.IGNORE_CASE,
    )

    private val abusePatterns = listOf(
        Regex("""(你|助手|ai).{0,8}(傻|笨|蠢|垃圾|废物|弱智|智障|有问题|不会搜|搜得烂)""", RegexOption.IGNORE_CASE),
        Regex("""(你|助手|ai).{0,10}(搜|查).{0,8}(什么|垃圾|傻|笨|烂|不行|不会)""", RegexOption.IGNORE_CASE),
        Regex("""(傻逼|傻比|妈的|他妈的|滚蛋|操你)""", RegexOption.IGNORE_CASE),
        Regex("""(骂|生气|气死|服了).{0,10}(你|助手|搜索)""", RegexOption.IGNORE_CASE),
        Regex("""(别|不要).{0,4}(搜|查)""", RegexOption.IGNORE_CASE),
    )

    private val genericQueries = setOf(
        "最新", "最新消息", "最新进展", "最近消息", "新闻", "资讯", "动态",
        "价格", "什么情况", "怎么回事", "怎么了", "进展", "消息",
    )

    private val referentialTerms = listOf(
        "这个", "那个", "它", "他", "她", "上述", "上面", "刚才", "前面", "继续",
        "再搜", "还搜", "再查", "还查", "这件事", "这东西", "那东西",
    )

    private val notFreshPatterns = listOf(
        Regex("""(不是|不够|没有|不).{0,5}(最新|实时|新)"""),
        Regex("""(太旧|过时|旧了|老消息|去年的|前年的)"""),
        Regex(
            """(outdated|old\s+news|stale|not\s+(the\s+|a\s+)?(latest|current)|isn'?t\s+(the\s+)?latest|up[\s-]*to[\s-]*date)""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val wrongKeywordPatterns = listOf(
        Regex("""(关键词|关键字).{0,6}(错|不对|不准|偏了)"""),
        Regex("""(搜错|搜得不对|搜得不准|搜的不是|不是这个|完全不对|答非所问)"""),
        Regex("""(你|助手|ai).{0,10}(搜|查).{0,8}(什么|垃圾|乱七八糟|驴唇不对马嘴)""", RegexOption.IGNORE_CASE),
        Regex("""(wrong\s+keyword|searched\s+the\s+wrong|not\s+what\s+i\s+asked)""", RegexOption.IGNORE_CASE),
        Regex("""(this|that|your\s+search).{0,12}(garbage|crap|useless|stupid)""", RegexOption.IGNORE_CASE),
        Regex("""\b(garbage|crap|useless|stupid|idiot)\b""", RegexOption.IGNORE_CASE),
    )

    private val missedSearchPatterns = listOf(
        Regex("""(为什么|怎么|咋|明明).{0,10}(不搜|没搜|不搜索|没搜索|不联网|没联网|可以搜)"""),
        Regex("""(没有|没).{0,4}(搜索|联网|查网页)"""),
        Regex("""(didn'?t|did\s+not|why\s+not).{0,12}(search|browse|look\s+up)""", RegexOption.IGNORE_CASE),
    )

    private val correctionPattern = Regex(
        """(?:应该(?:是|搜)|我要的是|我想搜的是|正确的是|改成|关键词是|关键字是|要搜的是|换一个关键词)\s*[：:]?\s*([^\n，。！？!?]{2,100})""",
    )

    /** 这句话是否真的需要先查网页。 */
    fun shouldSearch(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false
        val explicit = explicitSearchTerms.any(value::contains)
        // 翻译任务默认只处理用户给的原文，不额外把原文拿去搜网页。
        if (!explicit && hasTranslationIntent(value)) return false
        if (explicit) return true
        if (freshInfoTerms.any(value::contains)) return true
        return timeContextTerms.any(value::contains) &&
            timeQueryTerms.any(value::contains)
    }

    /**
     * 把用户口语整理成真正该发给搜索引擎的关键词。
     *
     * [recentTurns] 是本轮之前的历史（按时间顺序），只用来解析“这个 / 它 / 刚才那个”
     * 这类指代；最终查询仍然保持短关键词，不把整段聊天塞给搜索引擎。
     */
    fun planSearch(
        text: String,
        recentTurns: List<String> = emptyList(),
        location: String = "",
        previousSearchQuery: String = "",
    ): SearchPlan {
        val original = text.trim()
        if (original.isBlank()) return SearchPlan(false)
        val lower = original.lowercase()
        val explicit = explicitSearchTerms.any(lower::contains)
        val fresh = freshInfoTerms.any(lower::contains) ||
            (timeContextTerms.any(lower::contains) && timeQueryTerms.any(lower::contains))
        val complaint = searchComplaint(original)
        if (!explicit && !fresh && complaint == null) return SearchPlan(false)
        if (!explicit && hasTranslationIntent(lower)) return SearchPlan(false)

        val context = recentSearchTopic(recentTurns)
        if (complaint != null) {
            val plan = planCorrectiveSearch(
                complaint = complaint,
                context = context,
                previousSearchQuery = previousSearchQuery,
            )
            if (plan != null) return plan
            return SearchPlan(
                shouldSearch = false,
                reason = "收到搜索投诉，但还没有上一轮问题或明确的新关键词",
            )
        }

        val quoted = quotedTarget(original)
        val rawQuery = quoted ?: stripSearchCommand(original)
        var query = cleanQuery(rawQuery)
        val usedContext = query.isBlank() ||
            referentialTerms.any { rawQuery.contains(it) } ||
            (query.lowercase() in genericQueries && context.isNotBlank()) ||
            (query.length <= 3 && context.isNotBlank())
        if (usedContext && context.isNotBlank() && !query.contains(context)) {
            query = (context + " " + query).trim()
        }

        if (query.isBlank() || isOnlyFiller(query)) {
            return SearchPlan(
                shouldSearch = false,
                reason = "这句话里没有可执行的具体搜索目标",
            )
        }

        if (isWeatherQuery(original)) {
            val askedPlace = weatherPlace(original)
            val place = askedPlace.ifBlank { location.trim() }
            query = weatherSearchQuery(place)
            return SearchPlan(
                shouldSearch = true,
                query = query.take(200),
                reason = when {
                    askedPlace.isNotBlank() -> "问题点名了 $askedPlace，按这个地点查天气"
                    place.isNotBlank() -> "天气问题需要实时数据，结合当前位置查询"
                    else -> "天气问题需要实时数据"
                },
            )
        }

        val reason = if (usedContext && context.isNotBlank()) {
            "结合上一轮话题“${context.take(36)}”收窄了关键词"
        } else {
            "从这句话里提取了搜索关键词"
        }
        return SearchPlan(true, query.take(200), reason)
    }

    /** 信任模型给出的查询前，仍去掉口令和首尾标点，避免把 JSON 残片直接发给搜索引擎。 */
    fun sanitizePlannedQuery(raw: String): String =
        cleanQuery(raw).replace(Regex("""\s+"""), " ").take(200)

    /**
     * 用户骂搜索时，真正要做的是纠错重搜，而不是跳过搜索。
     *
     * - 不是最新 → 沿用上一轮搜索词，补上“最新”；
     * - 关键词错了 → 优先从上一轮原始问题重新提取；有“应该搜…”就按用户给的新词；
     * - 明明没搜 → 用上一轮原始问题立即补搜；
     * - 只有一句骂、没有任何上下文 → 仍返回 null，避免把脏话当关键词。
     */
    private fun planCorrectiveSearch(
        complaint: SearchComplaint,
        context: String,
        previousSearchQuery: String,
    ): SearchPlan? {
        val correction = cleanQuery(complaint.correction)
        val previous = cleanQuery(previousSearchQuery)
        val base = when {
            correction.isNotBlank() -> correction
            complaint.kind == SearchComplaintKind.NOT_FRESH && previous.isNotBlank() -> previous
            context.isNotBlank() -> context
            previous.isNotBlank() -> previous
            else -> ""
        }
        if (base.isBlank()) return null

        var query = base
        if (complaint.kind == SearchComplaintKind.NOT_FRESH &&
            !listOf("最新", "实时", "今天", "latest", "today").any(query::contains)
        ) {
            query = (query + " 最新").trim()
        }
        query = query.take(200)
        val reason = when (complaint.kind) {
            SearchComplaintKind.NOT_FRESH ->
                "收到“不是最新”的反馈，沿用上一轮主题并补上最新要求重搜"
            SearchComplaintKind.WRONG_KEYWORDS ->
                "收到“关键词不对”的反馈，从上一轮原始问题重新提取关键词"
            SearchComplaintKind.MISSED_SEARCH ->
                "用户指出刚才没有联网，立即按上一轮原始问题补搜"
            SearchComplaintKind.ABUSIVE_FEEDBACK ->
                "收到搜索投诉，不搜索脏话本身，改用上一轮主题重新搜索"
        }
        return SearchPlan(true, query, reason)
    }

    private fun searchComplaint(text: String): SearchComplaint? {
        val rawCorrection = correctionPattern.find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: quotedTarget(text)
        val correction = rawCorrection
            ?.let { quotedTarget(it) ?: it }
            ?.trim()
            ?.trim('“', '”', '"', '「', '」', '『', '』', '`')
            .orEmpty()
        if (notFreshPatterns.any { it.containsMatchIn(text) }) {
            return SearchComplaint(SearchComplaintKind.NOT_FRESH, correction)
        }
        if (wrongKeywordPatterns.any { it.containsMatchIn(text) }) {
            return SearchComplaint(SearchComplaintKind.WRONG_KEYWORDS, correction)
        }
        if (missedSearchPatterns.any { it.containsMatchIn(text) }) {
            return SearchComplaint(SearchComplaintKind.MISSED_SEARCH, correction)
        }
        if (abusePatterns.any { it.containsMatchIn(text) }) {
            return SearchComplaint(SearchComplaintKind.ABUSIVE_FEEDBACK, correction)
        }
        return null
    }

    /**
     * 地名反查失败时 DeviceContext 会退成 "31.2304,121.4737"。
     * 直接把这个坐标交给 Bing 中文搜索会被拆成数字 "31"，所以改写成明确的
     * 英文天气查询；地名正常时仍用中文关键词。
     */
    private fun weatherSearchQuery(place: String): String {
        if (place.isBlank()) return "今天天气 天气预报"
        val parts = place.split(',')
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null) {
                val latText = "%.4f".format(java.util.Locale.US, lat)
                val lonText = "%.4f".format(java.util.Locale.US, lon)
                return "weather forecast near ${latText}N ${lonText}E"
            }
        }
        return (place + " 今天天气 天气预报").take(200)
    }

    /** 是否在问当前天气 / 气温，用来把定位拼成更容易命中的天气搜索词。 */
    fun isWeatherQuery(text: String): Boolean {
        val value = text.trim().lowercase()
        return listOf("天气", "气温", "温度", "下雨", "降雨", "weather", "temperature")
            .any(value::contains)
    }

    /** 这条消息是否会用到当前位置（与 [DeviceContext] 的定位触发条件保持一致）。 */
    fun needsLocationContext(text: String): Boolean {
        val value = text.trim().lowercase()
        return listOf(
            "天气", "气温", "温度", "下雨", "降雨", "附近", "周边", "本地", "当地",
            "位置", "在哪", "导航", "路线", "weather", "temperature", "nearby",
        ).any(value::contains)
    }

    /** 这条消息是否真的需要当前日期 / 时间，而不是“开着时间权限就每轮都读”。 */
    fun needsTimeContext(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false
        return explicitTimeTerms.any(value::contains)
    }

    /** 这句话是否依赖上一轮对话才能理解（也用于搜索缓存的意图指纹）。 */
    fun needsConversationContext(text: String): Boolean {
        val value = text.trim().lowercase()
        return referentialTerms.any(value::contains) ||
            listOf(
                "刚才", "上次", "不是最新", "关键词", "关键字", "明明", "你搜", "搜的",
                "没搜", "继续", "再来", "重新搜", "再搜", "再查",
            ).any(value::contains)
    }

    /** 把当前位置拼进搜索词；天气问题用中文天气关键词，普通问题只附加地点。 */
    fun searchQuery(text: String, location: String): String {
        val plan = planSearch(text, emptyList(), location)
        return plan.query.ifBlank { text.trim().take(200) }
    }

    /** 从助手里勾选的“可用技能”中，选出这一条消息真正该用的。 */
    fun activeSkills(skills: List<HubSkill>, text: String): List<HubSkill> {
        val value = text.trim().lowercase()
        if (value.isBlank()) return emptyList()
        return skills.filter { skillIsRelevant(it, value) }
    }

    private fun skillIsRelevant(skill: HubSkill, value: String): Boolean {
        if (skill.id == TRANSLATION_SKILL_ID) return hasTranslationIntent(value)
        val name = skill.name.trim().lowercase()
        val id = skill.id.trim().lowercase()
        return (name.isNotBlank() && value.contains(name)) ||
            (id.isNotBlank() && value.contains(id))
    }

    private fun hasTranslationIntent(value: String): Boolean =
        translationTerms.any(value::contains)

    private fun quotedTarget(text: String): String? {
        val patterns = listOf(
            Regex("""[“"「『]([^”"」』]{2,120})[”"」』]"""),
            Regex("""`([^`]{2,120})`"""),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.findAll(text).map { it.groupValues[1].trim() }.maxByOrNull { it.length }
        }
    }

    private fun stripSearchCommand(text: String): String {
        var value = text.trim()
        value = searchCommand.replace(value, "")
        value = leadingFiller.replace(value, "")
        value = value.replace(Regex("""^[：:，,。\s]+"""), "")
        return value.trim()
    }

    private fun cleanQuery(text: String): String {
        var value = text
            .replace('\u3000', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        value = value.replace(leadingFiller, "")
        value = value.replace(Regex("""^(?:关于|有关)\s*"""), "")
        value = value.replace(trailingFiller, "")
        value = value.replace(Regex("""[，,。；;：:\s]+$"""), "")
        value = value.replace(Regex("""^(?:看看|查查|说说)\s*"""), "")
        return value.trim()
    }

    private fun recentSearchTopic(recentTurns: List<String>): String {
        val candidates = recentTurns
            .asReversed()
            .filter { it.isNotBlank() && !it.startsWith("【") }
            .take(4)
        for (turn in candidates) {
            val clean = cleanQuery(stripSearchCommand(turn))
            if (clean.length >= 2 && !isOnlyFiller(clean) && searchComplaint(turn) == null) {
                return clean
                    .replace(Regex("""\s+"""), " ")
                    .take(80)
            }
        }
        return ""
    }

    private fun isOnlyFiller(value: String): Boolean {
        val compact = value.replace(Regex("""[\s，,。.!！?？、~～]+"""), "")
        return compact.length < 2 ||
            compact in setOf(
                "这个", "那个", "它", "他", "她", "一下", "继续", "然后", "还有", "再说",
                "搜索", "搜索一下", "搜一下", "查一下", "查询", "联网搜索", "上网查",
                "某个东西", "某个事情", "一个东西", "东西", "事情",
            )
    }

    private fun weatherPlace(text: String): String {
        val marker = listOf("天气", "气温", "温度")
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
        val before = marker?.let { text.substring(0, it) }.orEmpty()
        if (before.isBlank()) return ""
        var place = before
            .replace(Regex("""^(?:请问|请|帮我|帮忙|查一下|查询|搜索|搜一下)\s*"""), "")
            .replace(Regex("""(?:今天|今日|明天|后天|现在|目前|当前|最近|这周末|周末)\s*$"""), "")
            .replace(Regex("""^(?:今天|今日|明天|后天|现在|目前|当前|最近)\s*"""), "")
            .replace(Regex("""(?:的|当地|本地|这里|那里)\s*$"""), "")
            .trim(' ', '，', ',', '。', '？', '?')
        if (place in setOf("", "今天", "现在", "本地", "这里", "当地", "我")) place = ""
        return place.take(20)
    }
}
