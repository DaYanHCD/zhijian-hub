package com.fingertipfuture.hub.data.agent

/**
 * 本机允许模型请求的工具白名单。
 *
 * 这里只声明协议，真正执行在 AgentToolExecutor，权限和真实数据从不由模型直接碰。
 */
internal object AgentToolRegistry {
    const val LOCATION_CURRENT = "location.current"
    const val TIME_CURRENT = "time.current"
    const val WEB_SEARCH = "web.search"
    const val REMINDER_LIST = "reminder.list"
    const val REMINDER_SAVE = "reminder.save"
    const val REMINDER_CLOSE = "reminder.close"
    const val REMINDER_DELETE = "reminder.delete"
    const val SCREEN_TIME = "device.screen_time"
    const val CALENDAR = "device.calendar"
    const val INSTALLED_APPS = "device.installed_apps"
    const val CONTACTS = "device.contacts"
    const val SMS = "device.sms"
    const val MMS = "device.mms"
    const val STEPS = "device.steps"
    const val CALENDAR_WRITE = "device.calendar_write"
    const val CLIPBOARD_READ = "device.clipboard"
    const val BLUETOOTH_DEVICES = "device.bluetooth"
    const val NOTIFICATIONS_READ = "device.notifications"
    const val CONTACTS_WRITE = "device.contacts_write"
    const val SMS_COMPOSE = "device.sms_compose"
    const val WIFI_DETAILS = "device.wifi"
    const val OVERLAY_SCREEN = "device.overlay_screen"
    const val MEDIA_LIST = "device.media"
    const val MEMORY_LIST = "memory.list"
    const val MEMORY_SAVE = "memory.save"
    const val MEMORY_UPDATE = "memory.update"
    const val MEMORY_DELETE = "memory.delete"

    private val all: Map<String, AgentToolSpec> = listOf(
        AgentToolSpec(
            id = LOCATION_CURRENT,
            name = "获取当前位置",
            description = "读取用户已授权的当前 Android 设备位置，返回地点名、经纬度和获取时间。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read",
        ),
        AgentToolSpec(
            id = TIME_CURRENT,
            name = "获取当前时间",
            description = "读取手机当前的本地日期、时间和星期。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read",
        ),
        AgentToolSpec(
            id = WEB_SEARCH,
            name = "联网搜索",
            description = "使用用户配置的搜索供应商查询互联网，返回标题、摘要、链接和来源。",
            parameters = """
                {"type":"object","properties":{
                  "query":{"type":"string","description":"简洁、可执行的搜索关键词"}
                },"required":["query"],"additionalProperties":false}
            """.trimIndent(),
            risk = "read_network",
        ),
        AgentToolSpec(
            id = REMINDER_LIST,
            name = "查看自动化任务",
            description = "读取当前助手的全部自动化任务，包括 id、标题、任务、时间和执行日期。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_local",
        ),
        AgentToolSpec(
            id = REMINDER_SAVE,
            name = "创建或修改自动化任务",
            description = "创建、修改或重新启用自动化任务。执行前 Android 会弹窗让用户确认。",
            parameters = """
                {"type":"object","properties":{
                  "reminderId":{"type":"string","description":"修改已有提醒时填列表中的 id；新建留空"},
                  "title":{"type":"string","description":"简短标题"},
                  "prompt":{"type":"string","description":"到点后要执行的具体任务"},
                  "time":{"type":"string","description":"24 小时制 HH:mm"},
                  "repeat":{"type":"string","enum":["daily","once","weekly"]},
                  "date":{"type":"string","description":"一次性任务日期 yyyy-MM-dd；repeat=once 时必填"},
                  "weekday":{"type":"integer","minimum":1,"maximum":7,"description":"每周任务：1=周一，7=周日"},
                  "displayLocation":{"type":"string","enum":["recent","new"],"description":"结果显示在最近会话还是新建会话"}
                },"required":["title","prompt","time","repeat"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_local_confirm",
        ),
        AgentToolSpec(
            id = REMINDER_CLOSE,
            name = "关闭自动化任务",
            description = "按唯一 id 关闭一个已有自动化任务。执行前 Android 会弹窗让用户确认。",
            parameters = """
                {"type":"object","properties":{
                  "reminderId":{"type":"string","description":"先从 reminder.list 获得的唯一 id"}
                },"required":["reminderId"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_local_confirm",
        ),
        AgentToolSpec(
            id = REMINDER_DELETE,
            name = "删除自动化任务",
            description = "按唯一 id 永久删除一个已有自动化任务。执行前 Android 会弹窗让用户确认。",
            parameters = """
                {"type":"object","properties":{
                  "reminderId":{"type":"string","description":"先从 reminder.list 获得的唯一 id"}
                },"required":["reminderId"],"additionalProperties":false}
            """.trimIndent(),
            risk = "destructive_local_confirm",
        ),
        AgentToolSpec(
            id = SCREEN_TIME,
            name = "读取屏幕时间",
            description = "读取 Android 使用情况访问数据，返回用户今天使用手机的时长摘要。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = CALENDAR,
            name = "读取日历",
            description = "读取用户已授权的 Android 日历事项。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = INSTALLED_APPS,
            name = "读取已安装应用",
            description = "读取设备上用户可见的应用名称和包名。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = CONTACTS,
            name = "读取联系人",
            description = "读取用户已授权的 Android 联系人。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = SMS,
            name = "读取短信",
            description = "读取用户已授权的 Android 短信摘要。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = MMS,
            name = "读取彩信",
            description = "读取用户已授权的 Android 彩信摘要。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = STEPS,
            name = "读取今日步数",
            description = "读取 Android 计步器提供的今日步数。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = CALENDAR_WRITE,
            name = "创建日历事项",
            description = "在用户授权的设备日历中创建事项。只在用户明确要求添加日程或会议时调用。",
            parameters = """
                {"type":"object","properties":{
                  "title":{"type":"string","description":"事项标题"},
                  "begin":{"type":"string","description":"开始时间，格式 yyyy-MM-dd HH:mm"},
                  "end":{"type":"string","description":"结束时间，格式 yyyy-MM-dd HH:mm；省略则默认一小时"},
                  "description":{"type":"string","description":"事项说明"}
                },"required":["title","begin"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_private_confirm",
        ),
        AgentToolSpec(
            id = CLIPBOARD_READ,
            name = "读取剪贴板",
            description = "读取用户当前剪贴板。大致权限开启时只返回条目数和字符数，不返回正文。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = BLUETOOTH_DEVICES,
            name = "查找附近蓝牙设备",
            description = "读取已配对设备并短暂扫描附近蓝牙设备；大致权限开启时隐藏名称和地址。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = NOTIFICATIONS_READ,
            name = "读取通知",
            description = "读取系统当前通知。大致权限开启时只返回数量和来源应用数量。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = CONTACTS_WRITE,
            name = "创建联系人",
            description = "在用户通讯录中创建联系人。只在用户明确要求保存姓名或号码时调用。",
            parameters = """
                {"type":"object","properties":{
                  "name":{"type":"string","description":"联系人姓名"},
                  "phone":{"type":"string","description":"电话号码，可为空"}
                },"required":["name"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_private_confirm",
        ),
        AgentToolSpec(
            id = SMS_COMPOSE,
            name = "准备发送短信",
            description = "打开系统短信编辑页并预填号码与正文，最终由用户确认发送，不会静默发送短信。",
            parameters = """
                {"type":"object","properties":{
                  "number":{"type":"string","description":"接收号码"},
                  "text":{"type":"string","description":"短信正文"}
                },"required":["number","text"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_private_confirm",
        ),
        AgentToolSpec(
            id = WIFI_DETAILS,
            name = "读取 Wi-Fi 与网络详情",
            description = "读取当前网络类型、Wi-Fi 名称、BSSID、速率、频段和附近热点。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = OVERLAY_SCREEN,
            name = "读取悬浮窗与屏幕捕获状态",
            description = "读取悬浮窗系统授权、悬浮搜题服务和屏幕捕获会话状态。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_local",
        ),
        AgentToolSpec(
            id = MEDIA_LIST,
            name = "读取媒体库",
            description = "读取设备图片、视频和音频的完整文件元数据。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_private",
        ),
        AgentToolSpec(
            id = MEMORY_LIST,
            name = "读取长期记忆",
            description = "读取用户为当前助手启用的全部长期记忆，由助手判断其中是否有完成任务所需的信息。",
            parameters = """{"type":"object","properties":{},"additionalProperties":false}""",
            risk = "read_local",
        ),
        AgentToolSpec(
            id = MEMORY_SAVE,
            name = "保存长期记忆",
            description = "把用户明确透露且值得长期保留的偏好、习惯或背景保存到本机长期记忆。",
            parameters = """
                {"type":"object","properties":{
                  "text":{"type":"string","description":"简洁、独立成句的长期事实，不要保存密码、密钥或一次性信息"},
                  "scope":{"type":"string","enum":["assistant","global"],"description":"assistant=仅当前助手；global=所有助手共用"}
                },"required":["text","scope"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_local_confirm",
        ),
        AgentToolSpec(
            id = MEMORY_UPDATE,
            name = "修改长期记忆",
            description = "按 id 修改一条已有长期记忆的内容。修改前必须先用 memory.list 获取准确 id。",
            parameters = """
                {"type":"object","properties":{
                  "id":{"type":"string","description":"memory.list 返回的记忆 id"},
                  "text":{"type":"string","description":"修改后的完整记忆内容"}
                },"required":["id","text"],"additionalProperties":false}
            """.trimIndent(),
            risk = "write_local",
        ),
        AgentToolSpec(
            id = MEMORY_DELETE,
            name = "删除长期记忆",
            description = "按 id 永久删除一条已有长期记忆。删除前必须先用 memory.list 获取准确 id。",
            parameters = """
                {"type":"object","properties":{
                  "id":{"type":"string","description":"memory.list 返回的记忆 id"}
                },"required":["id"],"additionalProperties":false}
            """.trimIndent(),
            risk = "destructive_local",
        ),
    ).associateBy { it.id }

    fun byId(id: String): AgentToolSpec? = all[id]

    fun forSkills(skills: List<AgentSkillDefinition>): List<AgentToolSpec> =
        skills.asSequence()
            .flatMap { it.toolIds.asSequence() }
            .distinct()
            .mapNotNull(all::get)
            .toList()
}
