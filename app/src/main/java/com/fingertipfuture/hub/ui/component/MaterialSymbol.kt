package com.fingertipfuture.hub.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全应用统一的图标入口。
 *
 * 项目约定：图标都是 VectorDrawable，集中放在 `res/drawable/ic_*.xml`，不引第三方图标库。
 * 目前有两套来源（都是拿官方 SVG 转的，颜色留黑、由 [tint] 上色）：
 *   - **Lucide**（ISC，https://lucide.dev）：聊天页三块区域用的就是它 ——
 *     `开发脚本\资源\生成Lucide图标.js` 一条命令重生成；
 *   - Material Symbols（Outlined）：其余页面 —— `开发脚本\资源\生成Material图标.ps1`。
 */
@Composable
fun MaterialSymbol(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
