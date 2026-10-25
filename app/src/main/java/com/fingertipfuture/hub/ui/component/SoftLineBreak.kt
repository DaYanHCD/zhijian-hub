package com.fingertipfuture.hub.ui.component

/**
 * 在英文/数字连续串内部加入零宽断行机会。
 *
 * Android 会把 `/`、`-` 等当成优先断点，可能先把前半段留下、再把后面的单词整体换行。
 * 这里在每个字母/数字之后插入 U+200B，让系统可以在单词内部继续断行；它没有可见宽度，
 * 不会增加字间空隙。
 */
internal fun String.withCharacterBreakOpportunities(): String {
    if (isEmpty()) return this
    var needsInsert = false
    for (index in 0 until lastIndex) {
        if (isAsciiWordCharacter(this[index]) && isAsciiWordCharacter(this[index + 1])) {
            needsInsert = true
            break
        }
    }
    if (!needsInsert) return this

    val output = StringBuilder(length + length / 3)
    for (index in indices) {
        output.append(this[index])
        if (
            index < lastIndex &&
            isAsciiWordCharacter(this[index]) &&
            isAsciiWordCharacter(this[index + 1])
        ) {
            output.append('\u200B')
        }
    }
    return output.toString()
}

private fun isAsciiWordCharacter(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'
