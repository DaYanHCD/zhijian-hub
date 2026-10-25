package com.fingertipfuture.hub.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.fingertipfuture.hub.ui.component.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设置页里可复用的双段/多段选择栏，选中底色会平滑滑动。 */
@Composable
internal fun AnimatedSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val position by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "segmented-tabs-position",
    )
    val outerShape = RoundedCornerShape(18.dp)
    val innerShape = RoundedCornerShape(14.dp)
    val gap = 4.dp

    Surface(
        color = settingsCardColor(),
        shape = outerShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(40.dp)
                .clip(innerShape)
                .clipToBounds(),
        ) {
            val segmentWidth = (maxWidth - gap * (labels.size - 1)) / labels.size
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = innerShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .offset(x = (segmentWidth + gap) * position)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(innerShape),
            ) {}
            Row(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    val active = index == safeIndex
                    val textColor by animateColorAsState(
                        targetValue = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "segmented-tabs-color-$index",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(innerShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (index != labels.lastIndex) {
                        Box(Modifier.width(gap))
                    }
                }
            }
        }
    }
}
