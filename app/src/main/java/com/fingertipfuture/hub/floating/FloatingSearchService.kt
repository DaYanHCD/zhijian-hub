package com.fingertipfuture.hub.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fingertipfuture.hub.MainActivity
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.localizedUiString
import com.fingertipfuture.hub.core.localizedUiText
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AiClient
import com.fingertipfuture.hub.data.ChatDatabase
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.data.HubProvider
import com.fingertipfuture.hub.data.HubTokenUsage
import com.fingertipfuture.hub.data.HubTurn
import com.fingertipfuture.hub.data.ModelRoleStore
import com.fingertipfuture.hub.data.ProviderStore
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private data class VisionRequestResult(
    val answer: String,
    val usage: HubTokenUsage,
)

/**
 * 悬浮搜题前台服务。
 *
 * 权限边界：
 * - 屏幕捕获来自用户刚才在系统弹窗里的明确授权；
 * - 只有点悬浮球的那一下才抓一帧，不持续录屏；
 * - 用户先框选题目，只有框选出的图片会交给图像模型。
 */
class FloatingSearchService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_START = "com.fingertipfuture.hub.floating.START"
        const val ACTION_STOP = "com.fingertipfuture.hub.floating.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "floating_search"
        private const val NOTIFICATION_ID = 4107
        private const val TAG = "FloatingQuestion"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var projection: MediaProjection? = null
    private var captureReader: ImageReader? = null
    private var captureDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var activeView: View? = null
    private var busy = false
    private var solveJob: Job? = null
    private var solveHandle: AiClient.StreamHandle? = null
    private var solveGeneration = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection stopped by system")
            FloatingSearchSession.setRunning(false)
            this@FloatingSearchService.showLocalizedToast(
                "屏幕共享已停止，请回到悬浮搜题重新开启",
                Toast.LENGTH_LONG,
            )
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        FloatingSearchSession.refresh(this)
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14 要求 MediaProjection 前台服务先进入前台，再取得投影对象。
        startProjectionForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getProjectionIntent()
        if (projection == null && resultData != null) {
            startProjection(resultCode, resultData)
        } else if (projection == null) {
            FloatingSearchSession.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        if (!Settings.canDrawOverlays(this)) {
            FloatingSearchSession.setRunning(false)
            stopSelf()
            return
        }
        runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
            (manager.getMediaProjection(resultCode, resultData) ?: error("系统未返回屏幕捕获会话")).also { created ->
                projection = created
                created.registerCallback(projectionCallback, Handler(mainLooper))
            }
            createCapturePipeline()
            FloatingSearchSession.setRunning(true)
            updateNotification("点悬浮球，框选题目")
            showBubble()
        }.onFailure {
            Log.e(TAG, "Unable to start floating question", it)
            FloatingSearchSession.setRunning(false)
            this.showLocalizedToast(
                "开启悬浮搜题失败：" + it.message.orEmpty().take(120),
                Toast.LENGTH_LONG,
            )
            stopSelf()
        }
    }

    override fun onDestroy() {
        FloatingSearchSession.setRunning(false)
        cancelSolve()
        removeActiveView()
        releaseCapturePipeline()
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        busy = false
        val density = resources.displayMetrics.density
        val size = (FLOATING_BUBBLE_SIZE_DP * density).toInt()
        val bounds = screenBounds()
        val params = overlayParams(
            width = size,
            height = size,
            gravity = Gravity.TOP or Gravity.START,
        ).apply {
            x = (bounds.width() - size - (14 * density).toInt()).coerceAtLeast(0)
            y = (bounds.height() * 0.32f).toInt()
            flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        val view = runCatching {
            composeView {
                FloatingSearchBubble(
                    onClick = ::captureFlow,
                    onCloseGesture = ::closeFromBubble,
                    onDrag = { dx, dy ->
                        val viewBounds = screenBounds()
                        params.x = (params.x + dx.toInt()).coerceIn(
                            0,
                            (viewBounds.width() - params.width).coerceAtLeast(0),
                        )
                        params.y = (params.y + dy.toInt()).coerceIn(
                            0,
                            (viewBounds.height() - params.height).coerceAtLeast(0),
                        )
                        activeView?.let { current ->
                            runCatching { windowManager.updateViewLayout(current, params) }
                        }
                    },
                )
            }
        }.getOrElse {
            FloatingSearchSession.setRunning(false)
            showLocalizedToast(
                "无法创建悬浮搜题按钮：" + it.message.orEmpty().take(100),
                Toast.LENGTH_LONG,
            )
            stopSelf()
            return
        }
        if (!replaceActiveView(view, params)) {
            FloatingSearchSession.setRunning(false)
            showLocalizedToast("无法显示悬浮搜题按钮，请检查悬浮窗权限", Toast.LENGTH_LONG)
            stopSelf()
        }
    }

    /** 悬浮球双击或长按：直接结束前台服务、屏幕捕获和悬浮窗。 */
    private fun closeFromBubble() {
        showLocalizedToast("悬浮搜题已关闭")
        stopSelf()
    }

    private fun captureFlow() {
        if (busy || projection == null) return
        busy = true
        // 只把悬浮球临时设为不可见，不立刻销毁它。截屏失败或窗口添加失败时
        // 原球可以立刻恢复，不会出现“点一下球没了”的假死状态。
        val bubbleView = activeView
        bubbleView?.alpha = 0f
        serviceScope.launch {
            try {
                // 让悬浮球先从合成画面消失，避免把自己截进候选框。
                delay(140)
                val screenshot = withTimeoutOrNull(3_500) { captureScreenshot() }
                    ?: error("读取屏幕超时")
                // 不在框选前做 OCR：用户先用手指自由圈选题目，再把圈选范围交给模型。
                showCrop(screenshot)
            } catch (t: Throwable) {
                Log.e(TAG, "Capture flow failed", t)
                if (bubbleView === activeView) bubbleView?.alpha = 1f
                val shown = showResult(
                    answer = FloatingSearchAnswer(
                        question = "",
                        message = "截取屏幕失败：" +
                            t.message?.take(160).orEmpty().ifBlank { "请重试" },
                    ),
                    title = "截屏失败",
                )
                if (!shown) showBubble()
            } finally {
                if (bubbleView === activeView) bubbleView?.alpha = 1f
            }
        }
    }

    private fun showCrop(bitmap: Bitmap) {
        busy = false
        val params = fullscreenParams()
        val view = runCatching {
            composeView {
                FloatingSearchCrop(
                    bitmap = bitmap,
                    onCancel = {
                        bitmap.recycle()
                        showBubble()
                    },
                    onSolve = { selection ->
                        solveSelection(bitmap, selection)
                    },
                )
            }
        }.getOrElse {
            Log.e(TAG, "Unable to build crop overlay", it)
            bitmap.recycle()
            showBubble()
            showLocalizedToast(
                "无法显示框选层：" + it.message.orEmpty().take(100),
                Toast.LENGTH_LONG,
            )
            return
        }
        if (!replaceActiveView(view, params, fullscreen = true)) {
            bitmap.recycle()
            showBubble()
            showLocalizedToast("无法显示框选层，请检查悬浮窗权限")
        }
    }

    private fun solveSelection(
        bitmap: Bitmap,
        selection: ComposeRect,
    ) {
        if (busy) return
        busy = true
        val left = (selection.left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val top = (selection.top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val right = (selection.right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val bottom = (selection.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()
        if (right - left < 12 || bottom - top < 12) {
            busy = false
            return
        }
        val crop = runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrElse {
            busy = false
            showResult(
                FloatingSearchAnswer("", message = "框选区域无效，请重新选择"),
                title = "框选区域无效",
            )
            return
        }
        val loadingShown = showResult(
            answer = FloatingSearchAnswer(""),
            title = "AI 正在解题",
            loading = true,
        )
        if (!loadingShown) {
            crop.recycle()
            bitmap.recycle()
            showBubble()
            showLocalizedToast("无法显示解题面板，请重试", Toast.LENGTH_LONG)
            return
        }

        val generation = ++solveGeneration
        solveJob = serviceScope.launch {
            val handle = AiClient.newStreamHandle()
            solveHandle = handle
            try {
                val imageRoute = ModelRoleStore.route(ModelRoleStore.image)
                    ?: ProviderStore.currentProvider?.let { it to ProviderStore.currentModel }
                    ?: throw IllegalStateException(
                        "还没有可用模型，请先在「默认模型」里设置图像模型",
                    )
                val vision = withTimeoutOrNull(120_000) {
                    visionRequest(
                        bitmap = crop,
                        route = imageRoute,
                        handle = handle,
                        prompt = """
                            你是搜题助手。仔细识别这张框选图片中的题目、选项、公式和图表，
                            严格按照下面的顺序回答：
                            1. 第一段必须以“答案：”开头，先给出最终答案。
                            2. 然后用“解析：”开头，分步骤解释，计算题要保留关键算式。
                            3. 若图中是多个题目，按原顺序逐题解答，每道题都先给答案再解析。
                            4. 不要复述整张图片，不要编造看不见的条件。
                            5. 所有公式必须使用 \( ... \) 包裹；分式用 \frac，根号用 \sqrt，
                               上下标用 ^ 和 _，不要输出裸公式或不可读的符号串。
                            6. 不要使用星号（*）或 Markdown 强调符号，用中文短标题和编号排版。
                        """.trimIndent(),
                    )
                }
                if (generation != solveGeneration) return@launch
                if (vision == null) {
                    showResult(
                        answer = FloatingSearchAnswer(
                            question = "",
                            message = "连接模型服务超时，请检查网络或稍后重试，请确认" +
                                "“设置-默认模型设置-图像模型”中选择的模型为可识别图像模型。",
                        ),
                        title = "解题超时",
                    )
                    return@launch
                }
                runCatching {
                    ChatDatabase.recordFloatingUsage(
                        provider = imageRoute.first.name,
                        model = imageRoute.second,
                        usage = vision.usage,
                    )
                }.onFailure {
                    Log.e(TAG, "记录悬浮搜题 token 用量失败", it)
                }
                val answer = vision.answer
                if (answer.isBlank()) {
                    showResult(
                        answer = FloatingSearchAnswer(
                            question = "",
                            message = "模型已经连接，但没有返回可显示的答案。请关闭面板后重新框选。",
                        ),
                        title = "没有拿到答案",
                    )
                    return@launch
                }
                showResult(
                    answer = FloatingSearchAnswer(
                        question = "",
                        answer = answer,
                    ),
                    title = "AI 解题",
                )
            } catch (cancelled: CancellationException) {
                // 用户关闭面板：旧请求静默退出，不覆盖新界面。
            } catch (t: Throwable) {
                if (generation != solveGeneration) return@launch
                Log.e(TAG, "Floating solve failed", t)
                val shown = showResult(
                    answer = FloatingSearchAnswer(
                        question = "",
                        message = friendlySolveError(t),
                    ),
                    title = "解题失败",
                )
                if (!shown) showBubble()
            } finally {
                crop.recycle()
                bitmap.recycle()
                if (generation == solveGeneration) {
                    solveJob = null
                    solveHandle = null
                }
            }
        }
    }

    /**
     * 把框选图片直接交给图像模型。
     */
    private suspend fun visionRequest(
        bitmap: Bitmap,
        route: Pair<HubProvider, String>,
        handle: AiClient.StreamHandle,
        prompt: String,
    ): VisionRequestResult {
        val dir = java.io.File(cacheDir, "floating_search").apply { mkdirs() }
        val file = java.io.File(dir, "vision-${System.currentTimeMillis()}.jpg")
        file.outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "图片编码失败" }
        }
        try {
            var usage = HubTokenUsage()
            val attachment = HubAttachment(
                kind = HubAttachment.KIND_IMAGE,
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                mime = "image/jpeg",
            )
            val result = AiClient.stream(
                provider = route.first,
                model = route.second,
                turns = listOf(HubTurn("user", prompt, listOf(attachment))),
                streaming = false,
                formatResponse = false,
                onDelta = {},
                onUsage = {
                    usage = HubTokenUsage(
                        input = usage.input + it.input,
                        output = usage.output + it.output,
                        cached = usage.cached + it.cached,
                    )
                },
                handle = handle,
            )
            val answer = result.getOrThrow()
            return VisionRequestResult(answer = answer.trim(), usage = usage)
        } finally {
            runCatching { file.delete() }
        }
    }

    private fun showResult(
        answer: FloatingSearchAnswer,
        title: String,
        loading: Boolean = false,
    ): Boolean {
        busy = loading
        val params = fullscreenParams()
        val view = runCatching {
            composeView {
                FloatingSearchResults(
                    answer = answer,
                    title = title,
                    loading = loading,
                    onClose = {
                        cancelSolve()
                        showBubble()
                    },
                )
            }
        }.getOrElse {
            busy = false
            return false
        }
        val shown = replaceActiveView(view, params, fullscreen = true)
        if (!shown) busy = false
        return shown
    }

    /** 把网络、HTTP、证书等技术异常翻译成用户能判断下一步的文字。 */
    private fun friendlySolveError(error: Throwable): String {
        val raw = error.message.orEmpty().trim()
        val status = Regex("""HTTP\s+(\d{3})""").find(raw)?.groupValues?.getOrNull(1)
        return when {
            error is UnknownHostException ->
                "找不到模型服务地址，请检查网络和接口地址后重试。"
            error is SSLException ->
                "模型服务的安全连接失败，请检查 HTTPS 地址和证书设置。"
            error is SocketTimeoutException || raw.contains("timeout", ignoreCase = true) ->
                "连接模型服务超时，请检查网络或稍后重试，请确认" +
                    "“设置-默认模型设置-图像模型”中选择的模型为可识别图像模型。"
            error is ConnectException ||
                raw.contains("failed to connect", ignoreCase = true) ||
                raw.contains("connection refused", ignoreCase = true) ->
                "无法连接模型服务，请检查接口地址、网络，或确认模型服务已经启动。"
            status == "401" || status == "403" ->
                "模型服务拒绝了请求，请检查 API Key、账户权限和模型权限。"
            status == "404" ->
                "没有找到模型接口，请检查接口地址、接口路径和模型名称。"
            status == "429" ->
                "模型服务请求过于频繁或额度不足，请稍后重试。"
            status == "400" || status == "422" ->
                "模型没有接受这次请求，请确认“设置-默认模型设置-图像模型”中选择的模型支持图片识别。"
            status != null && status.startsWith("5") ->
                "模型服务暂时不可用，请稍后重试。"
            raw.contains("还没有可用模型") || raw.contains("图像模型") ->
                raw.ifBlank { "还没有配置可用的图像模型。" }
            raw.contains("图片编码失败") ->
                "题目图片处理失败，请关闭面板后重新框选。"
            else ->
                "模型暂时无法完成这次解题，请检查网络、接口地址和图像模型设置后重试。"
        }
    }

    /** 关闭结果面板时取消当前请求，同时解除 busy 与网络读取。 */
    private fun cancelSolve() {
        solveGeneration += 1
        AiClient.interrupt(solveHandle)
        solveHandle = null
        solveJob?.cancel()
        solveJob = null
        busy = false
    }

    private fun fullscreenParams(): WindowManager.LayoutParams = overlayParams(
        width = WindowManager.LayoutParams.MATCH_PARENT,
        height = WindowManager.LayoutParams.MATCH_PARENT,
        gravity = Gravity.TOP or Gravity.START,
    ).apply {
        flags = flags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 不让系统导航栏 inset 截短窗口本身；正文仍由 navigationBarsPadding 避让。
            fitInsetsTypes = 0
        }
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        gravity: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
    }

    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            Rect(
                0,
                0,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
            )
        }

    private fun replaceActiveView(
        view: View,
        params: WindowManager.LayoutParams,
        fullscreen: Boolean = false,
    ): Boolean {
        removeActiveView()
        activeView = view
        return runCatching {
            if (fullscreen) {
                @Suppress("DEPRECATION")
                view.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
            windowManager.addView(view, params)
            if (fullscreen) view.requestApplyInsets()
        }.onFailure {
            activeView = null
            runCatching { windowManager.removeViewImmediate(view) }
        }.isSuccess
    }

    private fun removeActiveView() {
        val view = activeView ?: return
        activeView = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun composeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingSearchService)
            setViewTreeSavedStateRegistryOwner(this@FloatingSearchService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }

    /**
     * Android 14 起，一个 MediaProjection 会话只能创建一次 VirtualDisplay。
     * 所以授权成功后就把捕获管线建好并一直复用；每次点击只读取最新一帧。
     */
    private fun createCapturePipeline() {
        if (captureReader != null) return
        val activeProjection = projection ?: error("屏幕捕获已停止")
        val bounds = screenBounds()
        captureWidth = bounds.width()
        captureHeight = bounds.height()
        val density = resources.displayMetrics.densityDpi
        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            3,
        )
        val thread = HandlerThread("hub-floating-capture").also { it.start() }
        val handler = Handler(thread.looper)
        val display = activeProjection.createVirtualDisplay(
            "hub-floating-search",
            captureWidth,
            captureHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        ) ?: error("无法创建屏幕捕获画面")
        captureReader = reader
        captureDisplay = display
        captureThread = thread
        captureHandler = handler
    }

    private suspend fun captureScreenshot(): Bitmap {
        val reader = captureReader ?: error("屏幕捕获尚未准备好")
        val handler = captureHandler ?: error("屏幕捕获线程不可用")
        val width = captureWidth
        val height = captureHeight
        if (width <= 0 || height <= 0) error("屏幕尺寸无效")
        return suspendCancellableCoroutine { continuation ->
            var delivered = false
            fun deliver(image: Image?) {
                if (image == null || delivered) {
                    image?.close()
                    return
                }
                delivered = true
                reader.setOnImageAvailableListener(null, handler)
                try {
                    if (continuation.isActive) {
                        continuation.resume(image.toBitmap(width, height))
                    }
                } catch (t: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(t)
                } finally {
                    image.close()
                }
            }

            reader.setOnImageAvailableListener({ source ->
                deliver(source.acquireLatestImage())
            }, handler)
            // 静止画面可能不会立刻再产生一帧；先取最新缓存帧，保证单击始终有响应。
            deliver(reader.acquireLatestImage())
            continuation.invokeOnCancellation {
                reader.setOnImageAvailableListener(null, handler)
            }
        }
    }

    private fun releaseCapturePipeline() {
        runCatching { captureDisplay?.release() }
        captureDisplay = null
        runCatching { captureReader?.close() }
        captureReader = null
        captureHandler = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureWidth = 0
        captureHeight = 0
    }

    private fun Image.toBitmap(width: Int, height: Int): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == width) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        }
    }

    private fun startProjectionForeground() {
        val notification = buildNotification("悬浮搜题准备中")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingSearchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_floating_search)
            .setContentTitle(localizedUiString(R.string.floating_search_notification_title))
            .setContentText(localizedUiText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, localizedUiString(R.string.action_stop), stop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localizedUiString(R.string.floating_search_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = localizedUiString(R.string.floating_search_channel_description)
                setShowBadge(false)
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.getProjectionIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            getParcelableExtra(EXTRA_RESULT_DATA)
        }
}
