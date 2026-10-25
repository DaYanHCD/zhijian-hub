package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import com.fingertipfuture.hub.ui.component.PAGE_HORIZONTAL_MARGIN
import com.fingertipfuture.hub.ui.theme.enforceHubTypography
import com.fingertipfuture.hub.ui.component.hubCardBorder

/** 全屏输入页：独立文件，避免继续把胶囊、语音和全屏输入堆在 ChatInput.kt。 */
@Composable
internal fun FullScreenInputOverlay(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    focusRequester: FocusRequester,
    requestFocusOnEnter: Boolean,
) {
    LaunchedEffect(Unit) {
        if (requestFocusOnEnter) focusRequester.requestFocus()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetTop = statusBarTop + ChatMetrics.TOP_BAR_TOP_MARGIN + ChatMetrics.TITLE_BAR_HEIGHT + 16.dp
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sheetTop)
                .hubCardBorder(sheetShape),
            color = MaterialTheme.colorScheme.background,
            shape = sheetShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = PAGE_HORIZONTAL_MARGIN),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_save),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onSave)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = HubInk,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                    ).enforceHubTypography(),
                    cursorBrush = SolidColor(HubInk),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 14.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_input_fullscreen_hint),
                                color = HubInkSoft,
                                fontSize = 17.sp,
                                lineHeight = 26.sp,
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }
    }
}
