package com.fingertipfuture.hub.util

import java.util.Locale
import kotlin.math.abs

/** 把字节数格式化成人类可读的字符串（如 `8.2 MB`）。 */
fun formatBytes(bytes: Long, unknown: String = "未知大小"): String {
    if (bytes < 0) return unknown
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

/** 下载百分比（0~100）；总大小未知时返回 -1。 */
fun progressPercent(downloaded: Long, total: Long): Int {
    if (total <= 0) return -1
    val ratio = downloaded.toDouble() / total.toDouble()
    return (abs(ratio) * 100).toInt().coerceIn(0, 100)
}