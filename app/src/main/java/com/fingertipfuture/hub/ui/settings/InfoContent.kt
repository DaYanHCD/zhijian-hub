package com.fingertipfuture.hub.ui.settings

/** 列表式内容页里的一条（致谢 / 说明书都用它）。 */
internal data class InfoEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val links: List<InfoLink> = emptyList(),
)

/** 致谢页底部的可点击链接。 */
internal data class InfoLink(
    val label: String,
    val url: String,
)

/**
 * 致谢：整页直接展示正文（2026-10-17 用户要求），只保留两条。
 *
 * 一条是 RikkaHub（信息架构与界面思路的参考），另一条把所有**图标来源**合成「图标集」。
 */
internal val THANKS_ENTRIES: List<InfoEntry> = listOf(
    InfoEntry(
        id = "rikkahub",
        title = "RikkaHub",
        subtitle = "",
        body = "RikkaHub 是一款优秀的原生 Android AI 对话客户端。指尖Hub 在信息架构与交互上参考了它：" +
            "“聊天页 + 侧栏”的布局、助手 / 提供商 / 技能的组织方式，以及消息操作与思考过程的呈现。\n\n" +
            "RikkaHub 采用 AGPL-3.0 许可。本项目只借鉴产品思路与交互设计，没有复制它的代码；" +
            "指尖Hub 也坚持纯原生实现，没有引入 WebView 或第三方 UI / 网络 / Markdown 渲染库。\n\n" +
            "感谢 RikkaHub 的作者与贡献者，给原生 AI 客户端提供了很好的参考。",
        links = listOf(
            InfoLink("RikkaHub 官网", "https://rikka-ai.com/"),
            InfoLink("GitHub 仓库", "https://github.com/rikkahub/rikkahub"),
        ),
    ),
    InfoEntry(
        id = "icons",
        title = "图标集",
        subtitle = "",
        body = "界面里的细线图标、厂商标志与搜索服务标志来自以下开源图标集，均按各自许可使用：\n\n" +
            "Lucide（ISC 许可）：顶栏、输入胶囊与消息操作里的细线图标。\n\n" +
            "Google Material Symbols（Apache-2.0 许可）：设置页、提供商与更新页面的图标。\n\n" +
            "LobeHub icons（MIT 许可）：DeepSeek、OpenAI、Gemini、Kimi、智谱、千问等厂商标志。\n\n" +
            "Simple Icons（CC0-1.0）：联系作者页里的 QQ、Telegram 与 X 标志。\n\n" +
            "感谢这些项目的维护者，让界面可以保持清晰、统一，同时不增加额外的渲染依赖。",
    ),
)

/** 说明书：一节一条，点进去看那一节的正文。 */
internal val MANUAL_ENTRIES: List<InfoEntry> = listOf(
    InfoEntry(
        id = "provider",
        title = "一、接入模型",
        subtitle = "提供商 / API Key / 模型",
        body = "进入「设置 › 模型与服务 › 提供商」，选择一家服务商并打开开关，填入你自己的 API Key。" +
            "点「自动检测模型」拉取可用模型；保存后回到聊天页，顶栏会显示当前模型。\n\n" +
            "如果没有自动选中模型，可以在聊天页顶栏打开「供应商与模型」手动选择。\n\n" +
            "API Key 只保存在本机，指尖Hub 不连接任何自有后端。",
    ),
    InfoEntry(
        id = "chat",
        title = "二、开始聊天",
        subtitle = "输入 / 流式回复 / 停止与重答",
        body = "在底部输入框输入问题并发送。回复会流式显示；点「思考了 x 秒」可以展开思考与搜索过程，" +
            "生成过程中可以随时停止，已经收到的内容会保留。\n\n" +
            "长按或用消息下方的操作可以复制、编辑重发、重新生成或删除。点顶栏左侧胶囊打开侧栏，" +
            "可以新建、切换、重命名或整理会话。",
    ),
    InfoEntry(
        id = "search",
        title = "三、联网搜索",
        subtitle = "自动判断 / 多供应商 / 并行搜索",
        body = "自动联网搜索始终开启，但只有问题需要实时信息时才会触发，例如新闻、天气、价格、赛事、" +
            "最新进展，或者你明确要求“搜索 / 查一下”。普通闲聊、写作、翻译和代码解释会直接回答。\n\n" +
            "在「设置 › 模型与服务 › 搜索设置」添加搜索供应商；开启「参与并行搜索」后，多家供应商会同时查询，" +
            "结果会合并后交给模型。搜索失败时会显示失败原因，不会悄悄跳过。",
    ),
    InfoEntry(
        id = "permission",
        title = "四、权限与隐私",
        subtitle = "一处授权 / 按需调用",
        body = "所有能力统一在「设置 › 模型与服务 › 权限与隐私」管理，助手不再单独保存一套权限页。" +
            "在这里开启位置、日历、联系人、短信、步数等能力后，所有助手默认获得对应权限。\n\n" +
            "权限只会在问题明确需要时读取：问天气才会取位置，问日程才会读日历，问步数才会读计步器。" +
            "普通聊天不会读取设备信息。系统权限页里也可以查看和修改本 App 的真实授权状态。",
    ),
    InfoEntry(
        id = "assistant",
        title = "五、助手与技能",
        subtitle = "人格 / 提示词 / 记忆 / 技能",
        body = "「设置 › 通用设置 › 助手管理」可以新建、复制和切换助手。每个助手可以设置头像、名称、" +
            "默认模型、温度、最大 token、系统提示词、记忆与技能；点底部的助手按钮可以快速切换。\n\n" +
            "「技能管理」支持新建、编辑、从文件导入或从 GitHub 原始链接导入。默认的翻译技能只在明确要求翻译时启用。",
    ),
    InfoEntry(
        id = "attach",
        title = "六、图片、文件与语音",
        subtitle = "相册 / 拍照 / 文件 / 语音",
        body = "输入框左侧的「＋」可以打开相册、拍照、选择文件或发送位置。图片会先压缩再发送；" +
            "文本文件会把内容拼接进提示词。\n\n" +
            "长按麦克风可以录制语音消息，最长 60 秒，松手后可发送或取消。语音转文字暂未开放。",
    ),
    InfoEntry(
        id = "backup",
        title = "七、备份与恢复",
        subtitle = "多台 WebDAV / 本机导出 / 恢复",
        body = "「设置 › 数据 › 数据备份」支持本机导出与导入，也可以保存多台 WebDAV 服务器。" +
            "备份内容默认是「聊天记录 + 文件」；如果本机暂时没有附件，会正常备份聊天记录。\n\n" +
            "WebDAV 备份会在上传前用「地址 + 用户名 + 密码」派生的密钥加密；旧版明文备份仍可恢复。" +
            "换设备时使用同一台 WebDAV 的地址与账号密码即可解密。\n\n" +
            "在服务器列表里点一台即可切换当前服务器。WebDAV 页可以测试连接、立即备份、从最新备份恢复，" +
            "也可以只删除本机保存的服务器配置，不会删除服务器上的备份文件。",
    ),
    InfoEntry(
        id = "update",
        title = "八、主题、更新与数据安全",
        subtitle = "外观 / 更新 / 本地存储",
        body = "「设置 › 通用设置 › 偏好设置」可以切换浅色、深色或跟随系统，并调整头像、思考展开等界面选项。\n\n" +
            "「设置 › 关于」可以检查更新和查看更新日志。更新只提供最新正式版本，不再提供回退版。" +
            "安装更新不会删除本机对话和设置。\n\n" +
            "App 不注册账号、不做统计、不上传对话；API Key、聊天记录和附件都只保存在你的设备上。",
    ),
)
