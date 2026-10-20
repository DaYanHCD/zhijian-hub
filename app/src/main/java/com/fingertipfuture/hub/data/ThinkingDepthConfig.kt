package com.fingertipfuture.hub.data

/**
 * 思考强度现在有 7 档：关闭 / 自动 / 低 / 中等 / 高 / 超高 / 最大。
 * 旧版只有 0 快速、1 标准、2 深度；升级时做一次映射。
 */
internal object ThinkingDepthConfig {
    const val OFF = 0
    const val AUTO = 1
    const val LOW = 2
    const val MEDIUM = 3
    const val HIGH = 4
    const val XHIGH = 5
    const val MAX = 6

    fun fromLegacy(value: Int): Int = when (value) {
        0 -> LOW
        1 -> AUTO
        2 -> HIGH
        else -> value.coerceIn(OFF, MAX)
    }
}
