package com.fingertipfuture.hub.ui.settings

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.core.showLocalizedToast
import com.fingertipfuture.hub.data.AssistantStore
import com.fingertipfuture.hub.data.AvatarStore
import com.fingertipfuture.hub.data.SkillStore
import com.fingertipfuture.hub.data.HubAssistant
import com.fingertipfuture.hub.data.MemoryEntry
import com.fingertipfuture.hub.data.MemoryStore
import com.fingertipfuture.hub.ui.ModelPickerDialog
import com.fingertipfuture.hub.ui.chat.THINKING_DEPTHS
import com.fingertipfuture.hub.ui.chat.ThinkingDepthDialog
import com.fingertipfuture.hub.ui.component.HubBottomSheet
import com.fingertipfuture.hub.ui.component.HubAlertDialog as AlertDialog
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.component.hubCardBorder
import com.fingertipfuture.hub.ui.component.hubDropShadow
import com.fingertipfuture.hub.ui.component.settingsCardColor
import com.fingertipfuture.hub.ui.provider.FLOATING_BAR_SPACE
import com.fingertipfuture.hub.ui.provider.FLOATING_BAR_TOP_MARGIN
import com.fingertipfuture.hub.ui.provider.FloatingBackBar
import com.fingertipfuture.hub.ui.provider.FloatingCircleAction
import com.fingertipfuture.hub.ui.provider.TopFadeLayer
import com.fingertipfuture.hub.ui.provider.statusBarTopPadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** 助手的头像：设过图就用图，没设就用「名字首字 + 圆底」。 */
@Composable
internal fun AssistantAvatar(
    assistant: HubAssistant,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(assistant.avatarPath) {
        assistant.avatarPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = assistant.name.take(1).ifBlank { "AI" },
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 把选中的图片存成这个助手的头像（方图 + 压到 256）。 */
internal fun saveAssistantAvatar(context: Context, assistant: HubAssistant, uri: android.net.Uri) {
    val bitmap: Bitmap = AvatarStore.decodeSquare(context, uri) ?: return
    val file: File = AssistantStore.avatarFile(context, assistant.id)
    runCatching {
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    }.onSuccess {
        AssistantStore.upsert(context, assistant.copy(avatarPath = file.absolutePath))
    }
}

/** 助手列表：每个助手一张独立卡片，顶部可搜索，右上角新建。 */
@Composable
internal fun AssistantListScreen(
    onBack: () -> Unit,
    onOpenAssistant: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HubAssistant?>(null) }
    var actionTarget by remember { mutableStateOf<HubAssistant?>(null) }
    var query by remember { mutableStateOf("") }
    val items = AssistantStore.items.filterNot { it.underDevelopment }
    val filtered = items.filter { assistant ->
        query.isBlank() ||
            assistant.name.contains(query.trim(), ignoreCase = true) ||
            assistant.model.contains(query.trim(), ignoreCase = true) ||
            assistant.tags.any { it.contains(query.trim(), ignoreCase = true) }
    }
    val statusBarTop = statusBarTopPadding()

    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_HORIZONTAL_MARGIN,
                end = PAGE_HORIZONTAL_MARGIN,
                top = statusBarTop + FLOATING_BAR_SPACE,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AssistantSearchField(
                    value = query,
                    onValueChange = { query = it },
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "没有找到助手",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
                    )
                }
            } else {
                items(filtered, key = { it.id }) { assistant ->
                    AssistantListCard(
                        assistant = assistant,
                        isCurrent = assistant.id == AssistantStore.current.id,
                        onOpen = { onOpenAssistant(assistant.id) },
                        onMore = { actionTarget = assistant },
                    )
                }
            }
        }

        TopFadeLayer(modifier = Modifier.align(Alignment.TopCenter))

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .fillMaxWidth()
                .padding(
                    start = PAGE_HORIZONTAL_MARGIN,
                    end = PAGE_HORIZONTAL_MARGIN,
                    top = statusBarTop + FLOATING_BAR_TOP_MARGIN,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingBackBar(title = "助手管理", onBack = onBack)
            Spacer(Modifier.weight(1f))
            FloatingCircleAction(
                icon = R.drawable.ic_add,
                contentDescription = "新建助手",
                onClick = {
                    val assistant = AssistantStore.createAssistant(context, "新助手")
                    onOpenAssistant(assistant.id)
                },
            )
        }
    }

    actionTarget?.let { target ->
        AssistantMoreSheet(
            assistant = target,
            onDismiss = { actionTarget = null },
            onDelete = {
                actionTarget = null
                pendingDelete = target
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("是否删除助手？") },
            text = {
                Text("删除之后无法恢复此助手的提醒和记忆；已有聊天记录会保留。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AssistantStore.remove(context, target.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AssistantSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜索助手", fontSize = 15.sp) },
        leadingIcon = {
            MaterialSymbol(
                resId = R.drawable.ic_search,
                contentDescription = null,
                size = 21.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = settingsCardColor(),
            unfocusedContainerColor = settingsCardColor(),
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(RoundedCornerShape(18.dp))
            .hubCardBorder(RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun AssistantListCard(
    assistant: HubAssistant,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    androidx.compose.material3.Surface(
        color = settingsCardColor(),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .hubCardBorder(shape)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantAvatar(assistant = assistant, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = assistant.name,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (assistant.underDevelopment) {
                    Spacer(Modifier.width(8.dp))
                    SettingsTag("正在开发")
                }
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    SettingsTag("当前")
                }
            }
            MaterialSymbol(
                resId = R.drawable.ic_more_vertical,
                contentDescription = "更多",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onMore)
                    .padding(8.dp),
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantMoreSheet(
    assistant: HubAssistant,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    HubBottomSheet(
        onDismiss = onDismiss,
        minHeight = 340.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistantAvatar(assistant = assistant, size = 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            AssistantSheetAction(
                title = "删除",
                subtitle = "删除助手及其本机记忆",
                icon = R.drawable.ic_more_delete,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun AssistantSheetAction(
    title: String,
    subtitle: String,
    icon: Int,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val container = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        settingsCardColor()
    }
    val content = if (destructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = if (destructive) 0.18f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    resId = icon,
                    contentDescription = null,
                    size = 21.dp,
                    tint = content,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = content.copy(alpha = 0.86f),
                )
            }
        }
    }
}

/** 助手界面：大头像、名称，以及一张包含三个入口的无标题功能卡。 */
