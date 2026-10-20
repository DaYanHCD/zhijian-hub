package com.fingertipfuture.hub.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.ChatSearchHit
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubConversation
import com.fingertipfuture.hub.data.HubMessage
import com.fingertipfuture.hub.data.PermissionAccess
import com.fingertipfuture.hub.data.VoicePlayer
import com.fingertipfuture.hub.data.VoiceRecorder
import com.fingertipfuture.hub.data.isFromUser
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.data.SearchStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.data.plus
import com.fingertipfuture.hub.ui.chat.ATTACH_DEFAULT_HEIGHT
import com.fingertipfuture.hub.ui.chat.AttachAction
import com.fingertipfuture.hub.ui.chat.CameraCaptureScreen
import com.fingertipfuture.hub.ui.chat.ChatAttachPanel
import com.fingertipfuture.hub.ui.chat.ChatDrawerContent
import com.fingertipfuture.hub.ui.chat.ChatEmojiPanel
import com.fingertipfuture.hub.ui.chat.ChatInputCapsule
import com.fingertipfuture.hub.ui.chat.ChatMetrics
import com.fingertipfuture.hub.ui.chat.ChatRecommendationDeck
import com.fingertipfuture.hub.ui.chat.ChatRecommendationDestination
import com.fingertipfuture.hub.ui.chat.ChatRecommendationKind
import com.fingertipfuture.hub.data.ChatStore
import com.fingertipfuture.hub.ui.chat.ChatRuntime
import com.fingertipfuture.hub.ui.chat.ChatTopBar
import com.fingertipfuture.hub.ui.chat.DRAWER_DRAFT_ID
import com.fingertipfuture.hub.ui.chat.FullScreenInputOverlay
import com.fingertipfuture.hub.ui.chat.ImagePreviewOverlay
import com.fingertipfuture.hub.ui.chat.ChatMoreSheet
import com.fingertipfuture.hub.ui.chat.MessageAction
import com.fingertipfuture.hub.ui.chat.MessageBlock
import com.fingertipfuture.hub.ui.chat.RenameFolderDialog
import com.fingertipfuture.hub.ui.chat.runChatRequest
import com.fingertipfuture.hub.ui.chat.PanelHeightStore
import com.fingertipfuture.hub.ui.chat.TimeLabel
import com.fingertipfuture.hub.ui.chat.VoiceAction
import com.fingertipfuture.hub.ui.chat.buildChatRecommendations
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import com.fingertipfuture.hub.ui.provider.brandIcon
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 键盘"动画停下来"的判定：ime 高度安静这么久，才认为键盘已经落定。
 * 用来（1）把面板高度对齐到真实键盘高度（2）键盘彻底盖上来之后把面板收掉。
 */
private const val KEYBOARD_SETTLE_MS = 180L

/**
 * 对话页（本 App 只有这一个页面）。
 *
 * 结构参考 RikkaHub：**主界面 = 聊天页 + 左侧抽屉**，没有底部导航栏。
 * 入口分工（2026-10-13 用户重排过）：
 * - 顶栏左侧那颗胶囊 → 侧栏：新对话 / 提供商 / 设置 ‖ 检查更新 / 更新日志 / 关于
 * - 顶栏右侧功能栏 → 新对话 / 搜索（搜索 = 跨全部会话搜标题与消息）
 */
@Composable
internal fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenRecommendation: (ChatRecommendationDestination) -> Unit = {},
    /** 从数据管理的搜索结果点进来时，定位到对应会话与消息。 */
    openSearchHitOnStart: ChatSearchHit? = null,
    onSearchHitStartHandled: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 输入草稿仍走 rememberSaveable；其余状态和主链路动作在 ChatScreenState。
    var savedInput by rememberSaveable { mutableStateOf("") }
    val state = remember { ChatScreenState(context, scope, savedInput) }
    LaunchedEffect(state.input) { savedInput = state.input }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从系统权限页回来时真实同步一次：开启过的能力直接给所有助手。
                PermissionAccess.syncAssistantGrants(context)
                permissionRevision++
            }
            if (event == Lifecycle.Event.ON_STOP) {
                // 进程被系统回收前先把流式尾部和最新的内存窗口写盘。
                ChatStore.flush()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val focusRequester = remember { FocusRequester() }
    val fullScreenFocusRequester = remember { FocusRequester() }

    // 消息只有一个来源：ChatStore（落盘在 SQLite：databases/hub.db）。
    // 转屏 / 深色切换不会重建它；进程被杀、装更新、重启手机后从盘上读回来。
    // 以前这里挂的是 rememberSaveable —— 那只扛得住配置变更，装个更新就整段没了
    // （用户 2026-10-14：「就算我在手机上更新了，测试对话也应该还在」）。
    val messages = ChatStore.messages
    val listState = rememberLazyListState()
    // 自己刚插进去的消息（附件 / 本机提示 / 助手占位）必须滚到底露出来 ——
    // 用户反馈过"点了相册/文件，上面聊天区什么都没显示"，就是因为新气泡在可视区下面。
    //
    // 用递增计数而不是 Boolean：发一条消息会连续插「我」和「助手占位」两条，
    // 两次请求都必须各自生效。之前用 Boolean + animateScrollToItem，助手的插入
    // 赶在动画目标测量之前，动画停在旧的那一条上，助手整条就落到了输入胶囊下面
    // （2026-10-17 用户截图：只露出 AI 头像，思考条和正文都被胶囊压住）。
    var scrollToBottomRequest by state::scrollToBottomRequest
    // 搜索结果要定位到具体消息时，等新会话的消息读进来再滚过去
    var pendingJumpMessageIndex by state::pendingJumpMessageIndex

    var input by state::input
    var fullScreen by state::fullScreen
    var fullScreenKeyboardVisible by state::fullScreenKeyboardVisible
    // 这些状态放在进程级 ChatRuntime：转屏 / 切页面后请求继续，新的页面直接接上。
    var streaming by ChatRuntime.streamingState
    var titleStreamHandle by state::titleStreamHandle

    // 聊天页的弹层状态单独收在 ChatOverlayState 里，见 ChatScreenOverlays.kt
    val overlay = state.overlay
    var showChatSearch by overlay::showChatSearch
    var searchAllConversations by overlay::searchAllConversations
    var conversationMenu by overlay::conversationMenu
    var showProfile by overlay::showProfile
    var showModelPicker by overlay::showModelPicker
    // 输入胶囊「＋」弹出来的添加栏（相册 / 拍照 / 切换模型 …）
    var attachOpen by remember { mutableStateOf(false) }
    // 表情栏：和添加栏互斥；两块与键盘占同一块地方（见《技术说明》9.3）
    var emojiOpen by remember { mutableStateOf(false) }
    // 语音态（只做 UI）：放这里而不是胶囊内部，是为了让「＋」那条路径也知道现在是语音态
    var voiceMode by state::voiceMode
    // 消息那一排的「更多」弹出来的更多栏（2026-10-15 用户给的图：从下方滑出）。
    // 存的是**点「更多」的那条消息在列表里的下标** —— 面板里的「删除」就删它（只删这一条）。
    var moreSheetIndex by state::moreSheetIndex
    // 录音到 60 秒被自动结束/发送了 —— 之后手指再松开不该再处理一次
    // 每次录音只允许有一个计时器；旧的必须在开始、结束、取消时立刻作废
    var voiceTimerJob by state::voiceTimerJob

    /**
     * 正在编辑的那条消息（null = 没在编辑）。
     *
     * 用户 2026-10-15 定的行为：点「编辑」→ 这条的文字**回到输入框**，但列表不动；
     * 等改完**发送**时，才把"这条及其之后"的全部清掉，再把编辑后的内容当新消息发出去。
     * 只支持**我发的最近三条**（更早的连「编辑」都不显示，见 editableIndices）。
     */
    var editingIndex by state::editingIndex
    // 编辑那条原本带的附件：发送时一起带回去（编辑本身只回填文字）
    var editingAttachments by state::editingAttachments

    /** 「我发的」最近三条在 messages 里的下标 —— 只有这些能编辑。 */
    val editableIndices = remember(messages) {
        messages.withIndex()
            .filter { it.value.isFromUser && !it.value.localOnly }
            .map { it.index }
            .takeLast(3)
            .toSet()
    }
    var showThinkingDepth by overlay::showThinkingDepth
    var showSkills by overlay::showSkills
    var showSearchSheet by overlay::showSearchSheet
    // 输入胶囊的**实际**高度：输入多行时会从 50dp 长到最多 6 行，
    // 列表底部留白必须跟着它走，否则最后一条会被胶囊盖住（2026-10-17 用户反馈"错位"）
    var inputCapsuleHeight by remember { mutableStateOf(ChatMetrics.INPUT_CAPSULE_HEIGHT) }
    // 副屏底部「助手」那颗图标：弹出来选助手
    var showAssistantPicker by overlay::showAssistantPicker
    // 自己的拍照页（Camera2 画的，带返回按钮）
    var showCamera by remember { mutableStateOf(false) }
    // 点图片气泡要看的全屏大图（null = 没在看）
    var previewImage by remember { mutableStateOf<HubAttachment?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val focusManager = LocalFocusManager.current


    // ---- 当前助手（设置 › 通用设置 › 助手管理）----
    // 助手自己指定了默认聊天模型就用它的；没指定就跟「模型与服务」里全局选中的那家。
    val assistant = AssistantStore.current
    val assistantProvider = assistant.providerId
        .takeIf { it.isNotBlank() && assistant.model.isNotBlank() }
        ?.let { id -> ProviderStore.providers.firstOrNull { it.id == id && it.enabled } }
    val activeModel = if (assistantProvider != null) assistant.model else ProviderStore.currentModel

    // 顶栏副标题 / 抽屉头部都用它：当前对话的模型名（不带助手名、不带供应商）
    val modelLabel = activeModel.ifBlank {
        stringResource(R.string.settings_model_unset)
    }

    // 新对话的空状态推荐。只读取真实配置状态；滑牌顺序由 Deck 自己维护。
    val hasAssistantCapability = assistant.permTime ||
        assistant.permLocation ||
        assistant.permScreenTime ||
        assistant.permCalendar ||
        assistant.permInstalledApps ||
        assistant.permSms ||
        assistant.permMms ||
        assistant.permContacts ||
        assistant.permActivity
    val recommendationCards = remember(
        assistant,
        ProviderStore.providers,
        ProviderStore.currentProviderId,
        ProviderStore.currentModel,
        permissionRevision,
        SearchStore.items,
    ) {
        buildChatRecommendations(
            hasProvider = ProviderStore.usableProviders.isNotEmpty(),
            hasSearchProvider = SearchStore.enabled.isNotEmpty(),
            hasAnyPermission = hasAssistantCapability ||
                PermissionAccess.hasAnyCapability(context),
        )
    }

    // 顶栏标题：优先用会话表里的标题（首条用户消息写入），新建的空会话就叫「新聊天」。
    val defaultChatTitle = stringResource(R.string.chat_title_new)
    val chatTitle = ChatStore.conversations
        .firstOrNull { it.id == ChatStore.currentConversationId }
        ?.title?.takeIf { it.isNotBlank() }
        ?: messages.firstOrNull { it.role == "user" && it.text.isNotBlank() }
            ?.text?.replace('\n', ' ')?.take(24)
            ?.ifBlank { defaultChatTitle }
        ?: defaultChatTitle
    // ---- 底部空当：键盘和添加栏占同一块地方，所以只留一份 ----
    //
    // 面板开着 → 空当恒等于【面板高度】（键盘在不在都一样，切换时胶囊一动不动）；
    // 面板关着 → 空当交给 `Modifier.windowInsetsPadding(WindowInsets.ime)`：它在**布局阶段**
    //            读 insets，键盘动画期间不触发重组。以前是每帧在组合里读一次 ime 高度，
    //            等于每帧重组整页 —— 就是"跟随慢 + 有点卡"的根子（用户 2026-10-14 反馈）。
    val density = LocalDensity.current
    // 注意：WindowInsets.ime 是 @Composable 的 getter，必须在组合里取出来，
    // 之后（snapshotFlow / derivedStateOf 里）才能用。取这个对象本身不会订阅每帧变化，
    // 每帧变化的 state 在它内部，只有 getBottom() 才读。
    val imeInsets = WindowInsets.ime
    var attachPanelHeight by remember {
        // 上次量到的键盘高度直接用上；一次都没量到过才退回兜底值
        mutableStateOf(PanelHeightStore.loadDp(context)?.dp ?: ATTACH_DEFAULT_HEIGHT)
    }
    // 「键盘是不是弹着」：insets 每帧都在变，但这个布尔值只在键盘出现/消失时翻一次，
    // 所以用 derivedStateOf 包一层 —— 整页只重组两次，而不是几十次。
    val keyboardVisible by remember(density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    fun exitFullScreen() {
        val restoreKeyboard = fullScreenKeyboardVisible
        fullScreen = false
        if (restoreKeyboard && !voiceMode) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    // 全屏里如果用户手动收起 / 唤出键盘，退出时按最后一次状态决定要不要把键盘还给胶囊。
    LaunchedEffect(fullScreen, keyboardVisible) {
        if (fullScreen) fullScreenKeyboardVisible = keyboardVisible
    }

    // 键盘**落定**（高度安静 KEYBOARD_SETTLE_MS 没再变）之后做两件事：
    //   1. 面板高度对齐到真实键盘高度并存下来（下次启动直接用，避免"第一次点＋弹一下"）
    //   2. 面板还开着就**彻底收掉** —— 键盘已经完全盖住它了；不收掉就会出现
    //      "键盘收下去了、添加栏还杵在那"（用户 2026-10-14 反馈）
    // collectLatest：高度一变就取消上一次的等待，所以只有真的停下来才会走到最后。
    // 读 insets 这件事放在 snapshotFlow 里（不在组合里），所以这段逻辑本身不引起重组。
    LaunchedEffect(density) {
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { px ->
                if (px <= 0) return@collectLatest
                delay(KEYBOARD_SETTLE_MS)
                val settled = with(density) { px.toDp() }
                attachPanelHeight = settled
                PanelHeightStore.saveDp(context, settled.value)
                if (attachOpen) attachOpen = false
                if (emojiOpen) emojiOpen = false
            }
    }


    /** 抽屉里的动作：先把抽屉收起来再执行，免得动作的界面被抽屉盖住。 */
    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    // 面板开着时，返回键先收面板 —— 不然按返回就直接退出 App 了
    // （"点开添加栏 → 按返回"是很自然的动作；键盘在的时候返回由系统收键盘）。
    BackHandler(enabled = attachOpen || emojiOpen || moreSheetIndex != null) {
        attachOpen = false
        emojiOpen = false
        moreSheetIndex = null
    }
    BackHandler(enabled = fullScreen) { exitFullScreen() }

    // 界面被销毁时，只收掉明确属于这个页面的临时任务；聊天主请求由 ChatRuntime 保留。
    DisposableEffect(Unit) {
        onDispose {
            titleStreamHandle?.cancel()
            voiceTimerJob?.cancel()
            // 录到一半转屏/退出：麦克风要放开、半截文件要删掉，不然一直占着麦克风（2026-10-15 自查）
            VoiceRecorder.cancel()
            VoicePlayer.stop()
            // 界面没了（转屏 / 退出）：把还攒着的对话立刻落到盘上
            ChatStore.flush()
        }
    }

    // 只在消息真的变了才重算行（打字、开关抽屉等重组都不该重算）
    // 搜索层盖住整页时不生成聊天行；否则从数据管理进入搜索仍会白算一遍时间分隔和消息行。
    val rows = remember(messages, showChatSearch) {
        if (showChatSearch) emptyList() else buildChatRows(messages)
    }

    // 列表用了**反向布局**（reverseLayout = true）：index 0 = 最新消息，画在屏幕底部。
    // 流式长高是"向上长"，视口天然锚在底部 —— 不需要每来一片字就 scrollToItem/scrollBy 一次；
    // 那样每片增量都要重新测量 + 跳位置，正是"生成时卡顿、内容瞬间掉到输入胶囊下面"的来源。
    // 键盘 / 添加栏改变底部空当时，视口从底部收缩，内容也天然跟着锚住。
    //
    // 需要"回到底部"的地方（发消息 / 重新生成 / 本机插消息）显式滚到 index 0。
    LaunchedEffect(scrollToBottomRequest) {
        if (scrollToBottomRequest == 0) return@LaunchedEffect
        // 等新行真正进入布局再贴底；同步滚动不会像 animateScrollToItem 那样
        // 被紧接着的插入/测量打断，发送后必须一次到位。
        withFrameNanos { }
        listState.scrollToItem(0)
        // 列表高度/底部留白可能在同一帧后还有一次测量，再补一帧确认。
        withFrameNanos { }
        if (listState.canScrollBackward) {
            listState.scrollToItem(0)
        }
    }

    // 反向布局下"插入新消息"不会自动贴底：LazyColumn 会保持**原来那条**的位置，
    // 新消息（index 0）被挤到屏幕下方（用户 2026-10-17：发送后内容出现在胶囊下面）。
    // 所以记住"现在是不是贴底"，消息条数一变、原本贴底就再贴一次。
    var wasAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { wasAtBottom = it }
    }
    // 反向列表的“顶部”是下标最大的方向：滚到旧消息尽头时再取一页。
    val nearOldest by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearOldest) {
        if (nearOldest && ChatStore.hasOlder) ChatStore.loadOlder()
    }
    LaunchedEffect(rows.size) {
        if (UiPrefs.autoScroll &&
            rows.isNotEmpty() &&
            wasAtBottom &&
            pendingJumpMessageIndex == null
        ) {
            withFrameNanos { }
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(pendingJumpMessageIndex, ChatStore.currentConversationId, rows.size) {
        val target = pendingJumpMessageIndex ?: return@LaunchedEffect
        // rows 里还有时间分隔行；先找到目标消息在正序 rows 里的位置，
        // 再换算成反向 LazyColumn 的下标。
        val rowIndex = rows.indexOfFirst { it is ChatRow.Msg && it.index == target }
        if (rowIndex >= 0) {
            withFrameNanos { }
            listState.scrollToItem(rows.size - 1 - rowIndex)
            pendingJumpMessageIndex = null
        }
    }

    /** 往聊天里插一条只有本机看得到的提示（不发给模型）。 */
    fun notice(text: String) = state.notice(text)

    // 系统剪贴板（「复制」那颗按钮用；不引第三方库）
    val clipboard = LocalClipboardManager.current

    /**
     * 掐掉正在跑的那条流（**不写「（已停止）」** —— 调用方马上还要发一条新的）。
     *
     * 令牌先加一：旧协程的收尾（写正文 / 复位 streaming）立刻作废 ——
     * 「重新生成 / 重新发送」都是**先删再发**，旧协程要是还能写，就写到刚被 truncate 掉的位置上了。
     */
    fun stopStreaming() = state.stopStreaming()

    /** 新对话：新建一段空会话；**当前这段原样留在抽屉里**，随时能切回来。 */
    fun startNewChat() = state.startNewChat()

    /** 长按会话标题后的「重新生成标题」：让当前模型读前几轮，生成一个短标题。 */
    fun regenerateConversationTitle(conversation: HubConversation) =
        state.regenerateConversationTitle(conversation)

    fun deleteConversation(conversation: HubConversation) = state.deleteConversation(conversation)

    fun openSearchHit(hit: ChatSearchHit) = state.openSearchHit(hit)

    // 数据管理里的搜索结果选择后，聊天页是新建的组合；等页面进入后再切换会话，
    // 避免在路由切换的同一帧里做数据库读取和消息列表重建。
    LaunchedEffect(openSearchHitOnStart) {
        val hit = openSearchHitOnStart ?: return@LaunchedEffect
        onSearchHitStartHandled()
        openSearchHit(hit)
    }

    /**
     * 「更多」栏里的「删除」：**只删这一条**（前后的输入/输出都不动）。
     *
     * 边界：删的正好是**正在生成的那条** → 先把流掐了（不然它还会往那条空气泡里写）；
     * 删的是**正在编辑的那条** → 退出编辑态；删的是它**前面**的 → 编辑下标往前挪一位。
     */
    fun deleteMoreSheetMessage() = state.deleteMoreSheetMessage()

    /**
     * 发送一条消息。
     *
     * [attachments] 非空时（相册 / 拍照 / 文件 / 位置）：**先把气泡插进聊天区**，
     * 再检查供应商 —— 没配好也只是补一条提示，不会出现"点了没反应、上方啥也没有"。
     */
    fun send(text: String = input.trim(), attachments: List<HubAttachment> = emptyList()) =
        state.send(text, attachments)

    fun resend(message: HubMessage) = state.resend(message)

    fun regenerate(message: HubMessage) = state.regenerate(message)

    /**
     * 正文里那一排小按钮的动作。
     *
     * 只有「复制」是真做的（写进系统剪贴板）；重新生成 / 重新发送 / 翻译 / 编辑 / 更多
     * 是里程碑 2 的活 —— 这里给一句明确提示，不做"点了没反应"的假按钮。
     */
    fun onMessageAction(action: MessageAction, message: HubMessage) {
        when (action) {
            // 编辑（只对我发的那几条显示）：文字回填进输入框，列表**先不动**；
            // 真正生效在 send() 里 —— 发送时才把这条及其之后清掉重发。
            MessageAction.Edit -> {
                val index = ChatStore.messages.indexOf(message)
                if (index < 0) return
                if (index !in editableIndices) return
                // ⚠️ 先把正在生成的回复停掉：编辑这条之后要重发，重发会把"这条及之后"删掉，
                // 而正在跑的流还在往那个位置写 —— 不停就会写到被删掉的位置上（脏数据）。
                // 顺带一个副作用正好是用户要的：那颗按钮从「停止」回到「发送」。
                stopStreaming()
                // 编辑只能打字：语音态下输入框那颗换成了「长按说话」，文字回填了也看不见 ——
                // 所以点「编辑」顺手切回打字态（用户 2026-10-15 报的编辑流程相关）。
                voiceMode = false
                editingIndex = index
                editingAttachments = message.attachments
                // 只把文字放回输入框，**不弹任何提示**（用户 2026-10-15：不要弹窗提示）
                input = message.text
            }

            MessageAction.Copy -> {
                if (message.text.isBlank()) return
                clipboard.setText(AnnotatedString(message.text))
                Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
            }

            // 重新发送（我发的那条）/ 重新生成（助手那条）：真跑 —— 清掉这条及其之后，再发一次请求
            MessageAction.Resend -> resend(message)
            MessageAction.Regenerate -> regenerate(message)

            // 「更多」：从界面下方滑出更多栏（面板本身画在本页最底下那层）
            MessageAction.More -> {
                focusManager.clearFocus()
                emojiOpen = false
                attachOpen = false
                // 记下是**哪一条**（面板里的「删除」只删它）
                moreSheetIndex = ChatStore.messages.indexOf(message).takeIf { it >= 0 }
            }

        }
    }

    // ---------------- 附件：相册 / 拍照 / 文件 / 位置 ----------------

    suspend fun attachLocation() {
        // 定位要几秒（反查地名还要联网），先说一声，别让人以为点漏了
        // 「助手管理 › 权限与隐私 › 位置」没开就不给这个助手用（2026-10-16）
        if (!assistant.permLocation) {
            Toast.makeText(context, "还没有开启位置权限：设置 › 权限与隐私", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(context, "正在取当前位置…", Toast.LENGTH_SHORT).show()
        val location = AttachmentStore.currentLocation(context)
        if (location == null) {
            notice("定位没成功（信号差，或系统没给定位权限），等会儿再试一次")
        } else {
            send(attachments = listOf(location))
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val attachment = AttachmentStore.importImage(context, uri)
                if (attachment == null) notice("这张图读不出来，换一张试试") else send(attachments = listOf(attachment))
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val attachment = AttachmentStore.importFile(context, uri)
                if (attachment == null) notice("这个文件读不出来") else send(attachments = listOf(attachment))
            }
        }
    }

    // 录音权限：第一次长按说话时问一次；没给就提示，不硬崩
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "没有麦克风权限：到系统设置里允许「麦克风」再长按说话", Toast.LENGTH_LONG).show()
        }
    }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch { attachLocation() }
        } else {
            notice("没有定位权限，发不了位置 —— 到系统设置里允许「位置」权限再试")
        }
    }
    fun handleRecommendation(kind: ChatRecommendationKind) {
        when (kind) {
            ChatRecommendationKind.Provider ->
                onOpenRecommendation(ChatRecommendationDestination.Providers)
            ChatRecommendationKind.Search ->
                onOpenRecommendation(ChatRecommendationDestination.SearchSettings)
            ChatRecommendationKind.Permissions -> {
                // 直接进系统的“应用信息 › 权限”，不是某个助手自己的权限页。
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }.onFailure {
                    Toast.makeText(context, "打不开系统设置页", Toast.LENGTH_SHORT).show()
                }
            }
            ChatRecommendationKind.WeatherPrompt ->
                send("现在天气怎么样")
            ChatRecommendationKind.UsagePrompt ->
                send("看看今天用了多久的设备")
            ChatRecommendationKind.VirusPrompt ->
                send("手机里有没有病毒软件呢？")
        }
    }

    /** 把刚录好的这段发成一条语音消息（「发送」和"到 60 秒自动结束"共用）。 */
    /** 长按「长按说话」：权限弹窗仍由界面发起，录音/计时交给状态层。 */
    fun handleVoiceStart() {
        if (!AttachmentStore.hasRecordPermission(context)) {
            recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        state.startVoice()
    }


    /**
     * 松手：**必须落在某颗按钮上**才算数（没滑上去 = 取消，用户 2026-10-15 定）。
     *
     * - [VoiceAction.Cancel]：停掉并把文件删了；
     * - [VoiceAction.Send]：录的这段**当成一条语音消息发出去**（聊天里出现语音气泡，点一下能放）；
     * - [VoiceAction.ToText]：当前版本暂不支持，提示用户等待后续更新。

     */
    fun handleVoiceEnd(action: VoiceAction) = state.endVoice(action)



    fun stop() = state.stop()

    // 搜索是独立整页，不是叠在聊天页上的透明弹层：打开时整块聊天 UI 不组合，
    // 关闭后再按当前会话状态重建，既省掉入口卡顿，也避免键盘和消息列表抢布局。
    if (!showChatSearch) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                conversations = ChatStore.conversations,
                currentDraftTitle = if (ChatStore.currentConversationId == null) chatTitle else null,
                onSearchClick = {
                    closeDrawerThen {
                        searchAllConversations = false
                        showChatSearch = true
                    }
                },
                onChatClick = { id ->
                    closeDrawerThen {
                        if (id == DRAWER_DRAFT_ID) return@closeDrawerThen
                        if (id == ChatStore.currentConversationId) return@closeDrawerThen
                        // 切走前先掐掉旧会话的流，避免内容续写到新会话的内存里
                        stopStreaming()
                        ChatStore.switchTo(id)
                        input = ""
                        editingIndex = null
                        editingAttachments = emptyList()
                        scrollToBottomRequest++
                    }
                },
                onChatLongClick = { conversation ->
                    closeDrawerThen { conversationMenu = conversation }
                },
                onProfileClick = { closeDrawerThen { showProfile = true } },
                onAssistantsClick = {
                    // 点「助手」= 选助手（用户 2026-10-16）：选完记成当前助手，之后所有对话都用它
                    closeDrawerThen { showAssistantPicker = true }
                },
                onStatsClick = {
                    closeDrawerThen { onOpenStats() }
                },
                onSettingsClick = { closeDrawerThen { onOpenSettings() } },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 状态栏高度：列表要铺到屏幕最上面（连状态栏那块也铺），这里只用来给内容留空当
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            // 底部空当：面板开着用面板高度；面板关着让真实 ime insets 说话（布局阶段读，不重组）
            val bottomInset = if (attachOpen || emojiOpen) {
                Modifier.padding(bottom = attachPanelHeight)
            } else {
                Modifier.windowInsetsPadding(WindowInsets.ime)
            }
            Box(modifier = Modifier.fillMaxSize().then(bottomInset)) {
                if (rows.isEmpty() && recommendationCards.isNotEmpty()) {
                    ChatRecommendationDeck(
                        cards = recommendationCards,
                        onAction = ::handleRecommendation,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = statusBarTop + ChatMetrics.TOP_BAR_TOP_MARGIN +
                                    ChatMetrics.TITLE_BAR_HEIGHT + 12.dp,
                                bottom = inputCapsuleHeight +
                                    ChatMetrics.INPUT_CAPSULE_BOTTOM_MARGIN + 10.dp,
                            ),
                    )
                } else {
                // ① 消息列表：铺满整屏，内容从**悬浮**的顶栏、输入胶囊底下穿过去
                LazyColumn(
                    state = listState,
                    // 最新消息在 index 0（屏幕底部），流式长高只会向上长（见上面的说明）
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        // 添加栏开着时，点一下聊天区就把它收掉。
                        // 注意：**不消费按下事件**（requireUnconsumed = false），
                        // 所以手指一划还是正常滚动列表 —— 滑走不算"点"，不会误关。
                        .pointerInput(attachOpen, emojiOpen, keyboardVisible, UiPrefs.tapMessageClosesKeyboard) {
                            val panelsOpen = attachOpen || emojiOpen
                            // 「消息区逻辑」开着、键盘弹着、又没开面板时，点一下就收键盘
                            val dismissKeyboard = !panelsOpen && keyboardVisible && UiPrefs.tapMessageClosesKeyboard
                            if (!panelsOpen && !dismissKeyboard) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                // 抬手 = 真的是点；中途被滚动吃掉 → 返回 null，不关面板/不收键盘
                                if (waitForUpOrCancellation() != null) {
                                    if (panelsOpen) {
                                        attachOpen = false
                                        emojiOpen = false
                                    } else {
                                        focusManager.clearFocus()
                                    }
                                }
                            }
                        },
                    // 上下留位：上面是悬浮顶栏（含状态栏），下面是悬浮输入胶囊 ——
                    // 内容会从它们**底下**滚过去（真悬浮）
                    contentPadding = PaddingValues(
                        top = statusBarTop + ChatMetrics.TOP_BAR_TOP_MARGIN +
                            ChatMetrics.TITLE_BAR_HEIGHT + ChatMetrics.BODY_TOP_GAP,
                        bottom = inputCapsuleHeight +
                            ChatMetrics.INPUT_CAPSULE_BOTTOM_MARGIN + ChatMetrics.BODY_BOTTOM_GAP,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChatMetrics.MESSAGE_GAP),
                ) {
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.chat_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = PAGE_HORIZONTAL_MARGIN, top = 40.dp),
                            )
                        }
                    }
                    // 反向布局：把行倒着喂进去 —— 时间分隔仍然显示在同一组消息的**上方**
                    items(rows.asReversed(), key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Time -> TimeLabel(at = row.at, now = System.currentTimeMillis())
                            is ChatRow.Msg -> MessageBlock(
                                message = row.message,
                                // 只有"我发的最近三条"能编辑（用户 2026-10-15）
                                canEdit = row.message.isFromUser && row.index in editableIndices,
                                onAction = { action, message -> onMessageAction(action, message) },
                                onImageClick = { attachment ->
                                    // 看图之前先把键盘/添加栏收掉，免得挡着
                                    focusManager.clearFocus()
                                    attachOpen = false
                                    previewImage = attachment
                                },
                            )
                        }
                    }
                }
                }

                // ② 状态栏渐隐：消息从顶栏下面滚过去时"慢慢变淡"（和提供商 / 设置页同一个组件，
                //    只是矮一点：刚好盖住悬浮顶栏那一条 + 一点点尾巴）
                TopFadeLayer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    extraTop = ChatMetrics.TOP_BAR_TOP_MARGIN + ChatMetrics.TITLE_BAR_HEIGHT,
                    tail = 24.dp,
                )
                // ② 顶栏：贴着屏幕顶悬浮（自己带状态栏留白）
                ChatTopBar(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                    // 会话名：取第一条用户消息（和抽屉里的会话列表同一个来源）。
                    // 用户 2026-10-15 的规格里这一栏叫「当前会话名称」，所以别写死成"新聊天"。
                    title = chatTitle,
                    subtitle = modelLabel,
                    // 打开左侧抽屉（副屏）前先把键盘收掉：不然抽屉推上来，键盘还杵在那儿
                    onTitleClick = {
                        focusManager.clearFocus()
                        if (attachOpen) attachOpen = false
                        if (emojiOpen) emojiOpen = false
                        scope.launch { drawerState.open() }
                    },
                    onNewChatClick = { startNewChat() },
                    onSearchClick = {
                        // 搜索页自己会接管焦点；先把输入胶囊的键盘/面板收掉
                        focusManager.clearFocus()
                        if (attachOpen) attachOpen = false
                        if (emojiOpen) emojiOpen = false
                        searchAllConversations = false
                        showChatSearch = true
                    },
                )

                // ③ 输入胶囊：贴着屏幕底悬浮（键盘 / 添加栏起来时整体让位）
                ChatInputCapsule(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onCapsuleHeightChanged = { inputCapsuleHeight = it },
                    fullScreen = fullScreen,
                    onFullScreenChange = { open ->
                        if (open) {
                            fullScreenKeyboardVisible = keyboardVisible
                            attachOpen = false
                            emojiOpen = false
                            fullScreen = true
                        }
                    },
                    value = input,
                    onValueChange = { text ->
                        input = text
                        // 手动把输入框清空 = 放弃这次编辑（不然下次发送会把旧的那条也清掉）
                        if (text.isEmpty()) {
                            editingIndex = null
                            editingAttachments = emptyList()
                        }
                    },
                    // 编辑态下发送：把编辑那条原本带的附件一起送回去
                    onSend = { send(attachments = editingAttachments) },
                    onStop = { stop() },
                    streaming = streaming,
                    // 「表情」的循环（和「＋」对称）：
                    //   表情栏没开            → 收键盘（键盘下滑**露出**表情栏）+ 关掉添加栏（它下滑）
                    //   表情栏开着、键盘也在  → 收键盘，露出表情栏
                    //   表情栏开着、键盘不在  → 叫键盘，让它**上滑盖住**表情栏（落定后表情栏收掉）
                    onEmojiClick = {
                        when {
                            // 添加栏正盖在表情栏上（或者表情栏还没开）→ 收掉添加栏 / 收键盘，
                            // 让它们**下滑露出表情栏**；没有表情栏时顺手开一个
                            attachOpen || !emojiOpen -> {
                                focusManager.clearFocus()
                                attachOpen = false
                                emojiOpen = true
                            }
                            // 表情栏开着、键盘也在 → 收键盘，露出表情栏
                            keyboardVisible -> focusManager.clearFocus()
                            // 表情栏开着、键盘不在 → 叫键盘，让它**上滑盖住**表情栏（落定后表情栏收掉）
                            // 面板开着、键盘不在 → 叫键盘盖住面板。
                            // ⚠️ 语音态下输入框根本没组合出来，这时 requestFocus() 会抛
                            // "FocusRequester is not initialized" 把 App 干掉 —— 所以直接跳过。
                            !voiceMode -> {
                                focusRequester.requestFocus()
                            }
                        }
                    },
                    emojiOpen = emojiOpen,
                    showEmojiButton = !UiPrefs.hideEmojiButton,
                    voiceMode = voiceMode,
                    onVoiceModeChange = { voiceMode = it },
                    onVoiceStart = { handleVoiceStart() },
                    onVoiceEnd = { action -> handleVoiceEnd(action) },
                    // 「＋」的循环（用户 2026-10-14 定）：
                    //   面板没开          → 收键盘 + 弹出面板
                    //   面板开着、键盘也在 → 收键盘（面板露出来）
                    //   面板开着、键盘不在 → 叫出键盘，让它**上滑盖住面板**（落定后面板收掉）
                    onAddClick = {
                        when {
                            !attachOpen -> {
                                focusManager.clearFocus()
                                // 两块面板互斥：切到添加栏时把表情栏关掉，它的**下滑退场动画**
                                // 才会跑起来（"前一块下滑、露出新块"，用户 2026-10-15 要的效果）
                                emojiOpen = false
                                attachOpen = true
                            }
                            // 面板开着、键盘也在 → 收键盘（面板露出来）
                            keyboardVisible -> focusManager.clearFocus()
                            // 面板开着、键盘不在 → 叫键盘盖住面板（键盘落定后面板会自动收掉）
                            // 面板开着、键盘不在 → 叫键盘盖住面板。
                            // ⚠️ 语音态下输入框根本没组合出来，这时 requestFocus() 会抛
                            // "FocusRequester is not initialized" 把 App 干掉 —— 所以直接跳过。
                            !voiceMode -> {
                                focusRequester.requestFocus()
                            }
                        }
                    },
                    addOpen = attachOpen,
                    focusRequester = focusRequester,
                )
            }

            // 表情栏：和添加栏同一块地方、同一个高度（= 键盘高度）。
            // **画在添加栏之前** —— 于是切到添加栏时，添加栏是从下面「上滑盖住它」
            // （用户 2026-10-15 要的效果）；点添加栏的图标切回来时，添加栏下滑又露出它。
            AnimatedVisibility(
                visible = emojiOpen,
                // 进场：**不动**（它要么被键盘/添加栏"露出来"，要么本来就在原位）
                enter = EnterTransition.None,
                // 退场：往下滑走 —— 让"前一个界面下滑、露出新界面"这件事看得见
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)),
                // 正在退场的那块要画在新块**上面**，否则下滑会被新块直接挡住看不见
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(if (!emojiOpen && attachOpen) 1f else 0f),
            ) {
                ChatEmojiPanel(
                    panelHeight = attachPanelHeight,
                    // 点一下就插到输入框光标处（输入框留着内容，可以连着点好几个）
                    onPick = { symbol -> input = input + symbol },
                    // 左下角那颗「返回」：关掉表情栏（它自己会下滑退场）
                    onClose = { emojiOpen = false },
                )
            }
            // 「＋」的添加栏：和键盘同一块地方（同一个高度），从下面滑出来（照微信那个）
            // 上滑滑进来、下滑滑出去 —— 配合上面那个表情栏，就是「上滑覆盖 / 下滑露出」
            AnimatedVisibility(
                visible = attachOpen,
                // 进场同样不动：键盘收下去、或表情栏下滑之后，它就在原位被"露出来"
                enter = EnterTransition.None,
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(if (!attachOpen && emojiOpen) 1f else 0f),
            ) {
                ChatAttachPanel(
                    // 面板高度 = 底部空当（面板开着时两者就是同一个值，结构上不可能对不上）
                    panelHeight = attachPanelHeight,
                    // 「切换模型」那颗显示当前模型那家的官方标志（用户 2026-10-15）
                    modelIcon = ProviderStore.currentProvider?.logo?.let { brandIcon(it) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                        onAction = { action ->
                            when (action) {
                                AttachAction.Gallery -> {
                                    attachOpen = false
                                    pickImage.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                }

                                AttachAction.Camera -> {
                                    attachOpen = false
                                    // 用 App 自己的拍照页（系统相机那个界面没有返回按钮）
                                    focusManager.clearFocus()
                                    showCamera = true
                                }

                                AttachAction.SwitchModel -> {
                                    attachOpen = false
                                    showModelPicker = true
                                }

                                AttachAction.ThinkingDepth -> {
                                    attachOpen = false
                                    showThinkingDepth = true
                                }

                                AttachAction.Plugins -> {
                                    attachOpen = false
                                    showSkills = true
                                }

                                AttachAction.SendFile -> {
                                    attachOpen = false
                                    pickFile.launch(arrayOf("*/*"))
                                }

                                AttachAction.PermissionPrivacy -> {
                                    attachOpen = false
                                    onOpenPrivacy()
                                }

                                AttachAction.SendLocation -> {
                                    attachOpen = false
                                    if (AttachmentStore.hasLocationPermission(context)) {
                                        scope.launch { attachLocation() }
                                    } else {
                                        askLocation.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            ),
                                        )
                                    }
                                }

                                AttachAction.SearchSettings -> {
                                    attachOpen = false
                                    showSearchSheet = true
                                }
                            }
                    },
                )
            }

            // 「更多」栏（消息那一排的「更多」）：从界面下方滑出来。
            // 面板盖在整页之上（设计稿里面板起来时输入胶囊也是看不见的）；
            // 点面板以外的地方、或按返回键收掉它。上半屏那层是透明的（不压暗聊天区，照设计稿）。
            if (moreSheetIndex != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { moreSheetIndex = null },
                )
            }
            AnimatedVisibility(
                visible = moreSheetIndex != null,
                enter = slideInVertically(animationSpec = tween(220)) { it },
                exit = slideOutVertically(animationSpec = tween(220)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChatMoreSheet(onDelete = { deleteMoreSheetMessage() })
            }
            // 全屏看图：盖在整页之上（含顶栏）
            previewImage?.let { attachment ->
                ImagePreviewOverlay(attachment = attachment, onClose = { previewImage = null })
            }

            // 拍照页：整页黑底 + 自己的返回按钮（盖在所有东西上面）
            if (showCamera) {
                CameraCaptureScreen(
                    onClose = { showCamera = false },
                    onCaptured = { file ->
                        showCamera = false
                        scope.launch {
                            val attachment = AttachmentStore.importImage(
                                context,
                                android.net.Uri.fromFile(file),
                            )
                            runCatching { file.delete() }
                            if (attachment == null) notice("照片读不出来") else send(attachments = listOf(attachment))
                        }
                    },
                )
            }

            // 全屏输入：铺在聊天页最上层，发送 / 完成 / 返回都回到原来的聊天位置。
            if (fullScreen) {
                FullScreenInputOverlay(
                    value = input,
                    onValueChange = { text ->
                        input = text
                        if (text.isEmpty()) {
                            editingIndex = null
                            editingAttachments = emptyList()
                        }
                    },
                    onSave = { exitFullScreen() },
                    focusRequester = fullScreenFocusRequester,
                    requestFocusOnEnter = fullScreenKeyboardVisible,
                )
            }

            // 所有弹窗统一挂在最外层
        }
    }
    }


    ChatScreenOverlays(
        context = context,
        state = overlay,
        onOpenSearchHit = { hit -> openSearchHit(hit) },
        onRegenerateTitle = { conversation -> regenerateConversationTitle(conversation) },
        onDeleteConversation = { conversation -> deleteConversation(conversation) },
    )

}



