package com.fingertipfuture.hub.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.ui.component.hubCardBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) error = "没有相机权限：到系统设置里允许「相机」再试"
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val controller = remember { CameraController() }
    controller.onError = { message -> error = message }
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 左上角返回（关键：系统相机那个界面没有它）
        Text(
            text = "‹ 返回",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .hubCardBorder(RoundedCornerShape(20.dp))
                .clickable { onClose() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        // 快门
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .size(74.dp)
                .clip(CircleShape)
                .background(Color.White)
                .hubCardBorder(CircleShape)
                .clickable { controller.takePicture() },
        )

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

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var view: TextureView? = null
    private var previewSize: Size? = null
    private var sensorOrientation = 90
    private var opening = false
    private var capturing = false

    fun attach(context: Context, surface: SurfaceTexture, textureView: TextureView) {
        if (device != null || opening) return
        view = textureView
        val manager = context.getSystemService(CameraManager::class.java) ?: run {
            onError("这台设备没有相机服务")
            return
        }
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: run {
            onError("找不到可用的相机")
            return
        }
        val characteristics = manager.getCameraCharacteristics(cameraId)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
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
                onJpeg(bytes)
            }, h)
        }

        opening = true
        try {
            manager.openCamera(
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
        val previewSurface = Surface(texture)
        try {
            camera.createCaptureSession(
                listOf(previewSurface, still.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        configureTransform(view)
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                            )
                        }.build()
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

    fun takePicture() {
        val camera = device ?: return
        val s = session ?: return
        val h = handler ?: return
        val still = reader ?: return
        if (capturing) return
        capturing = true
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(still.surface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
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
        return (sensorOrientation + deviceDegrees) % 360
    }

    /** 离开拍照页：停止预览、释放相机与线程。 */
    fun close() {
        closeSession()
        runCatching { device?.close() }
        device = null
        runCatching { reader?.close() }
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
        view = null
    }

    fun closeSession() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
    }

    private fun pickSize(sizes: Array<Size>?, maxWidth: Int): Size {
        if (sizes.isNullOrEmpty()) return Size(1280, 720)
        return sizes
            .filter { it.width <= maxWidth }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }!!
    }
}
