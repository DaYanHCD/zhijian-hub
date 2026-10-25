package com.fingertipfuture.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import com.fingertipfuture.hub.ui.theme.HubTheme

class LocationBubblePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            HubTheme(darkTheme = false) {
                LocationBubblePreviewScreen()
            }
        }
    }
}

@Composable
private fun LocationBubblePreviewScreen() {
    val sampleLabel = "上海市 · 人民广场"
    val sampleCoordinates = "31.23040, 121.47370"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "位置气泡方案",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "回复 A–F 选择最终样式",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(26.dp))
        PreviewSection("A · 信息卡") {
            LocationInfoCard(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(24.dp))
        PreviewSection("B · 地图卡") {
            LocationMapCard(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(24.dp))
        PreviewSection("C · 轻量气泡") {
            LocationCompactBubble(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(24.dp))
        PreviewSection("D · 渐变强调") {
            LocationGradientBubble(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(24.dp))
        PreviewSection("E · 路线信息") {
            LocationRouteCard(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(24.dp))
        PreviewSection("F · 缩略图卡") {
            LocationThumbnailCard(sampleLabel, sampleCoordinates)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PreviewSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun LocationInfoCard(label: String, coordinates: String) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            ) {
                MiniMapLines(
                    modifier = Modifier.fillMaxSize(),
                    lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = coordinates,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "在地图中打开",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MaterialSymbol(
                        resId = R.drawable.ic_expand,
                        contentDescription = null,
                        size = 17.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationMapCard(label: String, coordinates: String) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = Color(0xFFEAF0E8),
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFFEAF0E8))
                    drawLine(
                        color = Color(0xFFD3E0D2),
                        start = Offset(0f, size.height * 0.24f),
                        end = Offset(size.width, size.height * 0.72f),
                        strokeWidth = 22.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0xFFFFFFFF),
                        start = Offset(0f, size.height * 0.24f),
                        end = Offset(size.width, size.height * 0.72f),
                        strokeWidth = 14.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0xFFD7E4D7),
                        start = Offset(size.width * 0.16f, 0f),
                        end = Offset(size.width * 0.74f, size.height),
                        strokeWidth = 8.dp.toPx(),
                    )
                    drawCircle(
                        color = Color(0xFFC7DCC7),
                        radius = 28.dp.toPx(),
                        center = Offset(size.width * 0.82f, size.height * 0.24f),
                    )
                    drawCircle(
                        color = Color(0xFFBCD6BC),
                        radius = 18.dp.toPx(),
                        center = Offset(size.width * 0.12f, size.height * 0.82f),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = coordinates,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "地图",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCompactBubble(label: String, coordinates: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(274.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(
                        resId = R.drawable.ic_location_on,
                        contentDescription = null,
                        size = 19.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = coordinates,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MaterialSymbol(
                resId = R.drawable.ic_expand,
                contentDescription = null,
                size = 17.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationGradientBubble(label: String, coordinates: String) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .width(286.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3759C8),
                        Color(0xFF6F8EF0),
                    ),
                ),
            )
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 20.dp,
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "当前位置",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 10.5.sp,
                    )
                }
                MaterialSymbol(
                    resId = R.drawable.ic_expand,
                    contentDescription = null,
                    size = 18.dp,
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = coordinates,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.5.sp,
            )
        }
    }
}

@Composable
private fun LocationRouteCard(label: String, coordinates: String) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier.width(294.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "发送当前位置",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = label,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = coordinates,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "查看地图",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    MaterialSymbol(
                        resId = R.drawable.ic_expand,
                        contentDescription = null,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationThumbnailCard(label: String, coordinates: String) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier.width(300.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(108.dp)
                    .clipToBounds()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            ) {
                MiniMapLines(
                    modifier = Modifier.fillMaxSize(),
                    lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            resId = R.drawable.ic_location_on,
                            contentDescription = null,
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(108.dp)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = coordinates,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun MiniMapLines(
    modifier: Modifier,
    lineColor: Color,
) {
    Canvas(modifier = modifier) {
        val step = 28.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            x += step
        }
        drawCircle(
            color = lineColor,
            radius = size.minDimension * 0.36f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
