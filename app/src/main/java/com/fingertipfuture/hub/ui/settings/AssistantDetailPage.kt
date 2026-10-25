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

@Composable
internal fun AssistantDetailScreen(
    assistantId: String,
    onBack: () -> Unit,
    onOpenBasic: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenSkill: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val assistant = AssistantStore.byId(assistantId)
    if (assistant == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    AssistantStandalonePage(title = assistant.name, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssistantAvatar(assistant = assistant, size = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = assistant.name,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AssistantActionGroup(
            onOpenBasic = onOpenBasic,
            onOpenPrompt = onOpenPrompt,
            onOpenSkill = onOpenSkill,
            onOpenMemory = onOpenMemory,
        )
    }
}

@Composable
internal fun AssistantStandalonePage(
    title: String? = null,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val statusBarTop = statusBarTopPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = PAGE_HORIZONTAL_MARGIN,
                    end = PAGE_HORIZONTAL_MARGIN,
                    top = statusBarTop + FLOATING_BAR_SPACE,
                    bottom = 40.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        TopFadeLayer(modifier = Modifier.align(Alignment.TopCenter))
        val topModifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = PAGE_HORIZONTAL_MARGIN,
                end = PAGE_HORIZONTAL_MARGIN,
                top = statusBarTop + FLOATING_BAR_TOP_MARGIN,
            )
        if (title == null) {
            FloatingCircleAction(
                icon = R.drawable.ic_arrow_back,
                contentDescription = "返回",
                onClick = onBack,
                modifier = topModifier,
            )
        } else {
            FloatingBackBar(
                title = title,
                onBack = onBack,
                modifier = topModifier,
            )
        }
    }
}

@Composable
private fun AssistantActionGroup(
    onOpenBasic: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenSkill: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = settingsCardColor(),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .hubDropShadow(shape)
            .hubCardBorder(shape),
    ) {
        Column {
            AssistantActionRow(
                title = "基础设定",
                subtitle = "配置名称、头像、模型和参数",
                icon = R.drawable.ic_settings,
                onClick = onOpenBasic,
            )
            AssistantActionDivider()
            AssistantActionRow(
                title = "提示词",
                subtitle = "设置系统提示、消息模板和规则",
                icon = R.drawable.ic_new_chat,
                onClick = onOpenPrompt,
            )
            AssistantActionDivider()
            AssistantActionRow(
                title = "技能管理",
                subtitle = "管理提示词注入和技能等扩展",
                icon = R.drawable.ic_attach_lite_puzzle,
                onClick = onOpenSkill,
            )
            AssistantActionDivider()
            AssistantActionRow(
                title = "记忆",
                subtitle = "管理助手记忆和最近的聊天引用",
                icon = R.drawable.ic_history,
                onClick = onOpenMemory,
            )
        }
    }
}

@Composable
private fun AssistantActionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(start = 86.dp),
    )
}

@Composable
private fun AssistantActionRow(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                resId = icon,
                contentDescription = null,
                size = 25.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MaterialSymbol(
            resId = R.drawable.ic_expand,
            contentDescription = null,
            modifier = Modifier.graphicsLayer(rotationZ = -90f),
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 基础设定：按参考图拆成一组独立表单卡。 */
