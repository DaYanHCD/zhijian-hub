package com.fingertipfuture.hub.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 非底部弹层的统一容器：标题居中，双按钮分居左右，单按钮靠右。
 */
@Composable
internal fun HubAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = shape,
            tonalElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth()
                .hubCardBorder(shape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                title?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            it()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                text?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) {
                        it()
                    }
                }
                if (confirmButton != null || dismissButton != null) {
                    Spacer(Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.align(Alignment.CenterStart),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            dismissButton?.invoke()
                        }
                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            confirmButton?.invoke()
                        }
                    }
                }
            }
        }
    }
}
