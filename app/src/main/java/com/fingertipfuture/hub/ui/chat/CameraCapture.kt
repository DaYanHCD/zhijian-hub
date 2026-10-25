package com.fingertipfuture.hub.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface as ComposeSurface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.provider.FloatingBackBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * App 自己的拍照页（Camera2 + TextureView，纯安卓原生，没引 CameraX 之类的库）。
 *
 * 为什么不用系统相机（ACTION_IMAGE_CAPTURE）：那样会跳到别的 App，而模拟器 / 部分机型上
 * 那个界面**没有返回按钮**（用户 2026-10-14 反馈「点拍照没有返回按钮」），只能靠系统返回手势退出。
 * 自己画就什么都有：左上角返回、中间快门。
 *
 * 拍到的 JPEG 先落到 cacheDir/photo/，交给调用方按图片附件处理（压缩 + 存私有目录）。
 */
@Composable
internal fun CameraCaptureScreen(
    onClose: () -> Unit,
    onCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var canFlip by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) error = "没有相机权限：到系统设置里允许「相机」再试"
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val controller = remember { CameraController() }
    controller.onError = { message -> error = message }
    controller.onCameraState = { _, canSwitch, hasFlash ->
        scope.launch {
            canFlip = canSwitch
            flashAvailable = hasFlash
            if (!hasFlash) flashOn = false
        }
    }
    controller.onZoomChanged = { ratio ->
        scope.launch { zoomRatio = ratio }
    }
    controller.onJpeg = { bytes ->
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "photo").apply { mkdirs() }
                File(dir, "IMG_" + System.currentTimeMillis() + ".jpg").apply { writeBytes(bytes) }
            }
            onCaptured(file)
        }
    }

    DisposableEffect(Unit) { onDispose { controller.close() } }
    BackHandler { onClose() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                controller.attach(context, st, this@apply)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                controller.configureTransform(this@apply)
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                controller.closeSession()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            controller.zoomBy(zoom)
                        }
                    },
            )
        }

        FloatingBackBar(
            title = "拍照",
            onBack = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = PAGE_HORIZONTAL_MARGIN, top = 8.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCircleAction(
                icon = R.drawable.ic_camera_flash,
                contentDescription = if (flashOn) "关闭闪光灯" else "打开闪光灯",
                enabled = flashAvailable,
                tint = if (flashOn) Color(0xFFFFD45C) else Color.White,
                onClick = {
                    flashOn = !flashOn
                    controller.setFlash(flashOn)
                },
            )

            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .hubCardBorder(CircleShape)
                    .clickable { controller.takePicture() },
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = R.drawable.ic_attach_lite_camera_v2,
                    contentDescription = "确认拍摄",
                    size = 34.dp,
                    tint = Color.Black,
                )
            }

            CameraCircleAction(
                icon = R.drawable.ic_camera_flip,
                contentDescription = "切换前后摄像头",
                enabled = canFlip,
                onClick = {
                    flashOn = false
                    controller.flip(context)
                },
            )
        }

        if (zoomRatio > 1.01f) {
            Text(
                text = String.format(Locale.US, "%.1f×", zoomRatio),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = PAGE_HORIZONTAL_MARGIN, bottom = 64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        error?.let { message ->
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun CameraCircleAction(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    ComposeSurface(
        color = Color.Black.copy(alpha = 0.46f),
        shape = CircleShape,
        modifier = Modifier
            .size(48.dp)
            .hubCardBorder(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(
                resId = icon,
                contentDescription = contentDescription,
                size = 23.dp,
                tint = if (enabled) tint else Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Camera2 的一层薄封装：开相机 → 预览 → 拍一张 → 关。
 *
 * 相机 API 全是回调 + 后台线程，所以这里管一条 HandlerThread 生命周期；
 * close() 会把它一起收掉（离开拍照页就释放相机，别占着）。
 */
internal class CameraController {

    var onError: (String) -> Unit = {}
    var onJpeg: (ByteArray) -> Unit = {}
    var onCameraState: (facing: Int, canFlip: Boolean, flashAvailable: Boolean) -> Unit = { _, _, _ -> }
    var onZoomChanged: (Float) -> Unit = {}

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var view: TextureView? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var sensorOrientation = 90
    private var facing = CameraCharacteristics.LENS_FACING_BACK
    private var flashAvailable = false
    private var flashEnabled = false
    private var maxZoom = 1f
    private var zoomRatio = 1f
    private var activeArray: Rect? = null
    private var opening = false
    private var capturing = false
    private var captureGeneration = 0

    fun attach(context: Context, surface: SurfaceTexture, textureView: TextureView) {
        if (device != null || opening) return
        view = textureView
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: run {
            onError("这台设备没有相机服务")
            return
        }
        val cameraIds = cameraManager.cameraIdList
        val hasBack = cameraIds.any {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        val hasFront = cameraIds.any {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }
        if (facing == CameraCharacteristics.LENS_FACING_FRONT && !hasFront) {
            facing = CameraCharacteristics.LENS_FACING_BACK
        } else if (facing == CameraCharacteristics.LENS_FACING_BACK && !hasBack) {
            facing = CameraCharacteristics.LENS_FACING_FRONT
        }
        val cameraId = cameraIds.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: cameraIds.firstOrNull() ?: run {
            onError("找不到可用的相机")
            return
        }
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            ?: CameraCharacteristics.LENS_FACING_BACK
        flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        if (!flashAvailable) flashEnabled = false
        activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?.coerceAtLeast(1f) ?: 1f
        zoomRatio = zoomRatio.coerceIn(1f, maxZoom)
        onCameraState(facing, hasFront && hasBack, flashAvailable)
        onZoomChanged(zoomRatio)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        previewSize = pickSize(map?.getOutputSizes(SurfaceTexture::class.java), 1440)
        val still = pickSize(map?.getOutputSizes(ImageFormat.JPEG), 2560)

        val preview = previewSize ?: Size(1280, 720)
        surface.setDefaultBufferSize(preview.width, preview.height)

        val ht = HandlerThread("hub-camera").also { it.start() }
        thread = ht
        val h = Handler(ht.looper)
        handler = h

        // ImageReader 收拍下来的 JPEG
        reader = ImageReader.newInstance(still.width, still.height, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bytes = image.planes[0].buffer.let { buffer ->
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
                image.close()
                capturing = false
                captureGeneration += 1
                onJpeg(bytes)
            }, h)
        }

        opening = true
        try {
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        opening = false
                        device = camera
                        startPreview()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        opening = false
                        camera.close()
                        device = null
                    }

                    override fun onError(camera: CameraDevice, errorCode: Int) {
                        opening = false
                        camera.close()
                        device = null
                        onError("相机打不开（错误码 " + errorCode + "）")
                    }
                },
                h,
            )
        } catch (e: SecurityException) {
            opening = false
            onError("没有相机权限")
        } catch (e: Exception) {
            opening = false
            onError("相机打不开：" + (e.message ?: "未知错误"))
        }
    }

    @Suppress("DEPRECATION")
    private fun startPreview() {
        val camera = device ?: return
        val h = handler ?: return
        val texture = view?.surfaceTexture ?: return
        val still = reader ?: return
        previewSurface?.release()
        val surface = Surface(texture).also { previewSurface = it }
        try {
            camera.createCaptureSession(
                listOf(surface, still.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        configureTransform(view)
                        val request = buildPreviewRequest(camera, surface)
                        runCatching { s.setRepeatingRequest(request, null, h) }
                            .onFailure { onError("预览起不来：" + (it.message ?: "未知错误")) }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        onError("相机配置失败")
                    }
                },
                h,
            )
        } catch (e: Exception) {
            onError("预览起不来：" + (e.message ?: "未知错误"))
        }
    }

    private fun buildPreviewRequest(camera: CameraDevice, surface: Surface): CaptureRequest =
        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
            if (flashEnabled && flashAvailable) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            cropRegion()?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
        }.build()

    private fun updatePreviewRequest() {
        val camera = device ?: return
        val s = session ?: return
        val h = handler ?: return
        val surface = previewSurface ?: return
        runCatching {
            s.setRepeatingRequest(buildPreviewRequest(camera, surface), null, h)
        }.onFailure {
            onError("更新相机预览失败：" + (it.message ?: "未知错误"))
        }
    }

    fun setFlash(enabled: Boolean) {
        flashEnabled = enabled && flashAvailable
        handler?.post { updatePreviewRequest() }
    }

    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        val next = (zoomRatio * factor).coerceIn(1f, maxZoom)
        if (kotlin.math.abs(next - zoomRatio) < 0.001f) return
        zoomRatio = next
        onZoomChanged(next)
        handler?.post { updatePreviewRequest() }
    }

    fun flip(context: Context) {
        val textureView = view ?: return
        val surfaceTexture = textureView.surfaceTexture ?: return
        val nextFacing = if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        releaseCamera(keepView = true)
        flashEnabled = false
        facing = nextFacing
        attach(context, surfaceTexture, textureView)
    }

    fun takePicture() {
        val camera = device ?: return
        val s = session ?: return
        val h = handler ?: return
        val still = reader ?: return
        if (capturing) return
        capturing = true
        val generation = ++captureGeneration
        h.postDelayed({
            if (capturing && captureGeneration == generation) {
                capturing = false
                onError("相机拍摄超时，正在重新启动")
            }
        }, 2_500)
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(still.surface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
            set(
                CaptureRequest.CONTROL_AE_MODE,
                if (flashEnabled && flashAvailable) {
                    CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                } else {
                    CaptureRequest.CONTROL_AE_MODE_ON
                },
            )
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            cropRegion()?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
        }.build()
        runCatching { s.capture(request, null, h) }.onFailure {
            capturing = false
            onError("拍照失败：" + (it.message ?: "未知错误"))
        }
    }

    /** 预览画面按屏幕方向旋转 / 裁剪，不然预览是横躺的。 */
    fun configureTransform(textureView: TextureView?) {
        val tv = textureView ?: view ?: return
        val preview = previewSize ?: return
        val w = tv.width
        val h = tv.height
        if (w == 0 || h == 0) return
        val rotation = tv.display?.rotation ?: Surface.ROTATION_0
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val bufferRect = RectF(0f, 0f, preview.height.toFloat(), preview.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = maxOf(h.toFloat() / preview.height, w.toFloat() / preview.width)
        matrix.postScale(scale, scale, centerX, centerY)
        when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 ->
                matrix.postRotate(90f * (rotation - 2), centerX, centerY)
            Surface.ROTATION_180 -> matrix.postRotate(180f, centerX, centerY)
        }
        tv.setTransform(matrix)
    }

    private fun jpegOrientation(): Int {
        val tv = view ?: return sensorOrientation
        val deviceDegrees = when (tv.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation - deviceDegrees + 360) % 360
        } else {
            (sensorOrientation + deviceDegrees) % 360
        }
    }

    /** 离开拍照页：停止预览、释放相机与线程。 */
    fun close() {
        captureGeneration += 1
        capturing = false
        releaseCamera(keepView = false)
    }

    fun closeSession() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
    }

    private fun releaseCamera(keepView: Boolean) {
        captureGeneration += 1
        capturing = false
        closeSession()
        runCatching { device?.close() }
        device = null
        runCatching { reader?.close() }
        reader = null
        previewSurface?.release()
        previewSurface = null
        thread?.quitSafely()
        thread = null
        handler = null
        opening = false
        if (!keepView) view = null
    }

    private fun cropRegion(): Rect? {
        val active = activeArray ?: return null
        val ratio = zoomRatio.coerceIn(1f, maxZoom)
        val width = (active.width() / ratio).toInt().coerceAtLeast(1)
        val height = (active.height() / ratio).toInt().coerceAtLeast(1)
        val left = active.centerX() - width / 2
        val top = active.centerY() - height / 2
        return Rect(left, top, left + width, top + height)
    }

    private fun pickSize(sizes: Array<Size>?, maxWidth: Int): Size {
        if (sizes.isNullOrEmpty()) return Size(1280, 720)
        return sizes
            .filter { it.width <= maxWidth }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }!!
    }
}
