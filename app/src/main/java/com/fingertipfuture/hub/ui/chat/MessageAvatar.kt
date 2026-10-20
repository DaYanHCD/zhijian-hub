package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.AvatarKind
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.UiPrefs
import com.fingertipfuture.hub.data.ProviderStore
import com.fingertipfuture.hub.ui.provider.brandIcon
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.hubCardBorder

/**
 * 头像：设过自定义图片就用图片，否则画默认样式。整颗带外阴影（与两颗胶囊同一条）。
 *
 * 默认样式两边刻意不同色（我用 secondaryContainer、助手用主色），避免同屏撞色。
 *
 * [size] 默认是聊天页的尺寸（ChatMetrics.BODY_AVATAR_SIZE = 40dp）；
 * 副屏那个头像是 60×60（用户 2026-10-16 的规格），由调用方传进来。
 */
@Composable
internal fun MessageAvatar(isSelf: Boolean, size: Dp = ChatMetrics.BODY_AVATAR_SIZE) {
    val kind = if (isSelf) AvatarKind.SELF else AvatarKind.ASSISTANT
    // 读一下版本号，换头像后能立刻重绘（见 AvatarStore.version）
    val version = AvatarStore.version
    // 助手这颗：先看**当前助手自己**的头像（设置 › 助手管理 › 基础设定），没有再退回旧的全局助手头像
    val assistantPath = if (isSelf) "" else AssistantStore.current.avatarPath
    val assistantBitmap = remember(assistantPath) {
        assistantPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val bitmap = assistantBitmap ?: remember(kind, version) { AvatarStore.load(kind) }

    if (bitmap != null) {
        // ⚠️ 必须 remember：asImageBitmap() 每次都新建一个包装对象，而 Compose 的
        // BitmapPainter 按包装对象的身份做缓存 —— 每帧新建会让同一张图反复走一遍纹理上传。
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = stringResource(
                if (isSelf) R.string.avatar_me else R.string.avatar_assistant,
            ),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .hubDropShadow(CircleShape)
                .clip(CircleShape)
                .hubCardBorder(CircleShape),
        )
        return
    }

    // 助手这颗：没设自定义头像时，按「界面 › 使用助手头像」来 ——
    // 开着就优先用**当前模型的 Logo**（LobeHub 的官方标志）；
    // 没有模型（或这家没配 Logo）时继续走下面的默认圆底机器人头像，
    // 聊天里不能因此整颗头像消失。
    if (!isSelf && UiPrefs.useAssistantAvatar) {
        val icon = ProviderStore.currentProvider?.logo?.let { brandIcon(it) }
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(size)
                    .hubDropShadow(CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .hubCardBorder(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = icon.res,
                    contentDescription = stringResource(R.string.avatar_assistant),
                    size = if (size >= 48.dp) 34.dp else 24.dp,
                    // 官方彩色版不要再 tint（tint 会把它压成单色）
                    tint = if (icon.colored) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                )
            }
            return
        }
    }

    val container = if (isSelf) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val content = if (isSelf) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = Modifier
            .size(size)
            .hubDropShadow(CircleShape)
            .clip(CircleShape)
            .background(container)
            .hubCardBorder(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelf) {
            Text(
                text = stringResource(R.string.avatar_me),
                color = content,
                // 头像放大到 60（副屏）时，"我"和助手图标也要跟着放大，否则显得中间空
                fontSize = if (size >= 48.dp) 20.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            MaterialSymbol(
                resId = R.drawable.smart_toy,
                contentDescription = stringResource(R.string.avatar_assistant),
                size = if (size >= 48.dp) 30.dp else 22.dp,
                tint = content,
            )
        }
    }
}


// ---------------------------------------------------------------------------
// 底部：悬浮输入胶囊（打字态 / 语音态）
