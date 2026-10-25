package com.fingertipfuture.hub.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AttachmentStore
import com.fingertipfuture.hub.data.HubAttachment
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import java.io.File
import kotlinx.coroutines.launch

/**
 * 点图片气泡后的**全屏看图**（纯 Compose 画的一层，不是新 Activity、更不是 WebView）。
 *
 * 退出方式：点图片界面的任意位置，或按系统返回键。
 * 长按：从底部弹出图片操作，可保存到系统相册。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImagePreviewOverlay(attachment: HubAttachment, onClose: () -> Unit) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }

    val bitmap = remember(attachment.path) {
        runCatching {
            val file = File(attachment.path)
            if (file.isFile) BitmapFactory.decodeFile(attachment.path) else null
        }.getOrNull()
    }

    fun saveToGallery() {
        showActions = false
        scope.launch {
            AttachmentStore.saveImageToGallery(context, attachment).fold(
                onSuccess = { location ->
                    context.showLocalizedToast("已保存到相册：$location")
                },
                onFailure = { error ->
                    context.showLocalizedToast("保存失败：" + (error.message ?: "未知错误"))
                },
            )
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            saveToGallery()
        } else {
            context.showLocalizedToast("没有存储权限，无法保存到相册")
        }
    }

    fun requestSave() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveToGallery()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 点击关闭；长按打开图片操作。不要水波纹（看图时出现涟漪很怪）。
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
                onLongClick = { showActions = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "图片已不在本机", color = Color.White, fontSize = 14.sp)
        }

    }

    if (showActions) {
        HubBottomSheet(
            onDismiss = { showActions = false },
            minHeight = 230.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "图片操作",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                val shape = RoundedCornerShape(18.dp)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::requestSave,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialSymbol(
                            resId = R.drawable.ic_settings_download,
                            contentDescription = null,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(13.dp))
                        Text(
                            text = "保存到手机",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
