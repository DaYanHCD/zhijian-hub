package com.fingertipfuture.hub.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 习惯类设置（设置 › 通用设置 › 偏好设置 › 常规 / 界面）。
 *
 * 都是开关，存本机（`hub_ui`，和主题、键盘高度共用一个 prefs 文件），
 * 用 Compose 状态存当前值 —— 开关一拨，聊天页那边立刻按新值走，不用重启。
 *
 * 每一项都**真的接在行为上**（不是摆样子）：
 * - [newChatOnStart]：ChatStore.initialize 里决定**要不要读回上一段对话**（不删库里的东西）；
 * - [autoScroll]：ChatScreen 里"新内容进来就滚到底"那段的开关；
 * - [showUserAvatar]：聊天页「我」那条消息要不要画头像；
 * - [showThinking]：思考框默认展开（新消息进来就是展开的）；
 * - [autoCollapseThinking]：一轮生成结束后自动把思考框收起来。
 * - [hideThinking]：彻底隐藏思考框、思考时长和思考内容。
 * - [hideEmojiButton]：隐藏输入胶囊的表情图标，输入框自动占满空出来的宽度。
 */
object UiPrefs {

    private const val PREFS = "hub_ui"
    private const val KEY_NEW_CHAT_ON_START = "new_chat_on_start"
    private const val KEY_AUTO_SCROLL = "auto_scroll"
    private const val KEY_SHOW_USER_AVATAR = "show_user_avatar"
    private const val KEY_SHOW_THINKING = "show_thinking"
    private const val KEY_AUTO_COLLAPSE_THINKING = "auto_collapse_thinking"
    private const val KEY_HIDE_THINKING = "hide_thinking"
    private const val KEY_STREAMING_OUTPUT = "streaming_output"
    private const val KEY_USE_ASSISTANT_AVATAR = "use_assistant_avatar"
    private const val KEY_HIDE_EMOJI_BUTTON = "hide_emoji_button"
    private const val KEY_TAP_MESSAGE_CLOSE_KEYBOARD = "tap_message_close_keyboard"
    private const val KEY_CARD_BORDERS = "card_borders"
    private const val KEY_APPROXIMATE_PERMISSIONS = "approximate_permissions"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_ACTIVE_CONVERSATION = "active_conversation"

    /** 启动时就开一段新对话（不读回上一段；上一段还在库里）。 */
    var newChatOnStart by mutableStateOf(false)
        private set

    /** 生成内容时自动滚到底部。 */
    var autoScroll by mutableStateOf(true)
        private set

    /** 聊天里显示「我」的头像。 */
    var showUserAvatar by mutableStateOf(true)
        private set

    /** 默认展开助手的思考内容。 */
    var showThinking by mutableStateOf(false)
        private set

    /** 思考完自动折叠。 */
    var autoCollapseThinking by mutableStateOf(false)
        private set

    /** 隐藏整块思考框（包括流式生成中的思考占位和已完成后的时长/内容）。 */
    var hideThinking by mutableStateOf(false)
        private set

    /** 流式输出：助手的思考和回答逐字生成（关掉就等全部生成完一次性显示）。 */
    var streamingOutput by mutableStateOf(true)
        private set

    /** 聊天里用助手头像（自定义的，没有就用当前模型的 Logo；都没有就留空）。 */
    var useAssistantAvatar by mutableStateOf(true)
        private set

    /** 隐藏输入胶囊里的表情按钮；输入框会自动占满它原来的位置。 */
    var hideEmojiButton by mutableStateOf(false)
        private set

    /**
     * 「消息区逻辑」：键盘弹着时，点消息区要不要自动收起键盘。**默认关**。
     * 关着的时候点消息区没反应（原来的行为）；开了之后点一下就收键盘。
     */
    var tapMessageClosesKeyboard by mutableStateOf(false)
        private set

    /** 给标题栏、输入胶囊、卡片、弹层和对话框显示描边。 */
    var cardBorders by mutableStateOf(true)
        private set

    /** 隐私能力默认只向助手提供摘要或脱敏结果。 */
    var approximatePermissions by mutableStateOf(true)
        private set

    /** 我的昵称（副屏用户区那个，点一下能改）。 */
    var userName: String by mutableStateOf("用户")
        private set

    fun initialize(context: Context) {
        val prefs = prefs(context)
        newChatOnStart = prefs.getBoolean(KEY_NEW_CHAT_ON_START, false)
        autoScroll = prefs.getBoolean(KEY_AUTO_SCROLL, true)
        showUserAvatar = prefs.getBoolean(KEY_SHOW_USER_AVATAR, true)
        showThinking = prefs.getBoolean(KEY_SHOW_THINKING, false)
        autoCollapseThinking = prefs.getBoolean(KEY_AUTO_COLLAPSE_THINKING, false)
        hideThinking = prefs.getBoolean(KEY_HIDE_THINKING, false)
        streamingOutput = prefs.getBoolean(KEY_STREAMING_OUTPUT, true)
        useAssistantAvatar = prefs.getBoolean(KEY_USE_ASSISTANT_AVATAR, true)
        hideEmojiButton = prefs.getBoolean(KEY_HIDE_EMOJI_BUTTON, false)
        tapMessageClosesKeyboard = prefs.getBoolean(KEY_TAP_MESSAGE_CLOSE_KEYBOARD, false)
        cardBorders = prefs.getBoolean(KEY_CARD_BORDERS, true)
        approximatePermissions = prefs.getBoolean(KEY_APPROXIMATE_PERMISSIONS, true)
        userName = prefs.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() } ?: "用户"
    }

    fun setNewChatOnStart(context: Context, value: Boolean) {
        newChatOnStart = value
        prefs(context).edit().putBoolean(KEY_NEW_CHAT_ON_START, value).apply()
    }

    fun setAutoScroll(context: Context, value: Boolean) {
        autoScroll = value
        prefs(context).edit().putBoolean(KEY_AUTO_SCROLL, value).apply()
    }

    fun setShowUserAvatar(context: Context, value: Boolean) {
        showUserAvatar = value
        prefs(context).edit().putBoolean(KEY_SHOW_USER_AVATAR, value).apply()
    }

    fun setShowThinking(context: Context, value: Boolean) {
        showThinking = value
        prefs(context).edit().putBoolean(KEY_SHOW_THINKING, value).apply()
    }

    fun setAutoCollapseThinking(context: Context, value: Boolean) {
        autoCollapseThinking = value
        prefs(context).edit().putBoolean(KEY_AUTO_COLLAPSE_THINKING, value).apply()
    }

    fun setHideThinking(context: Context, value: Boolean) {
        hideThinking = value
        prefs(context).edit().putBoolean(KEY_HIDE_THINKING, value).apply()
    }

    fun setStreamingOutput(context: Context, value: Boolean) {
        streamingOutput = value
        prefs(context).edit().putBoolean(KEY_STREAMING_OUTPUT, value).apply()
    }

    fun setUseAssistantAvatar(context: Context, value: Boolean) {
        useAssistantAvatar = value
        prefs(context).edit().putBoolean(KEY_USE_ASSISTANT_AVATAR, value).apply()
    }

    fun setHideEmojiButton(context: Context, value: Boolean) {
        hideEmojiButton = value
        prefs(context).edit().putBoolean(KEY_HIDE_EMOJI_BUTTON, value).apply()
    }

    fun setTapMessageClosesKeyboard(context: Context, value: Boolean) {
        tapMessageClosesKeyboard = value
        prefs(context).edit().putBoolean(KEY_TAP_MESSAGE_CLOSE_KEYBOARD, value).apply()
    }

    fun setCardBorders(context: Context, value: Boolean) {
        cardBorders = value
        prefs(context).edit().putBoolean(KEY_CARD_BORDERS, value).apply()
    }

    fun setApproximatePermissions(context: Context, value: Boolean) {
        approximatePermissions = value
        prefs(context).edit().putBoolean(KEY_APPROXIMATE_PERMISSIONS, value).apply()
    }

    fun setUserName(context: Context, value: String) {
        userName = value.ifBlank { "用户" }
        prefs(context).edit().putString(KEY_USER_NAME, userName).apply()
    }

    /** 上次打开的那段对话（多会话切换后重启还回到这里）。 */
    fun activeConversationId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_CONVERSATION, null)?.takeIf { it.isNotBlank() }

    fun setActiveConversationId(context: Context, conversationId: String?) {
        prefs(context).edit().apply {
            if (conversationId == null) remove(KEY_ACTIVE_CONVERSATION)
            else putString(KEY_ACTIVE_CONVERSATION, conversationId)
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
