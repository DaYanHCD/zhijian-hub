package com.fingertipfuture.hub.ui.chat

/**
 * 去掉 emoji 与紧随其后的文字之间的水平空白。
 *
 * 换行保留，避免把上下两段粘成一段；emoji 本身的 ZWJ / 肤色序列不受影响。
 */
internal fun removeSpacesAfterEmoji(text: String): String {
    if (text.isEmpty()) return text
    val output = StringBuilder(text.length)
    var index = 0
    var inEmoji = false
    while (index < text.length) {
        if (
            inEmoji &&
            text[index] != '\n' &&
            text[index] != '\r' &&
            text[index].isWhitespace()
        ) {
            index++
            continue
        }
        val codePoint = text.codePointAt(index)
        output.appendCodePoint(codePoint)
        index += Character.charCount(codePoint)
        inEmoji = isEmojiCodePoint(codePoint) ||
            (inEmoji && (codePoint == 0x200D || codePoint == 0xFE0E || codePoint == 0xFE0F))
    }
    return output.toString()
}

/** 和表情栏的单表情识别共用同一组 Unicode 范围。 */
internal fun isEmojiCodePoint(codePoint: Int): Boolean =
    codePoint in 0x1F000..0x1FAFF ||
        codePoint in 0x2600..0x27BF ||
        codePoint in 0x2300..0x23FF ||
        codePoint in 0x2B00..0x2BFF ||
        codePoint in 0x2190..0x21FF ||
        codePoint == 0x00A9 ||
        codePoint == 0x00AE ||
        codePoint == 0x203C ||
        codePoint == 0x2049 ||
        codePoint == 0x20E3 ||
        codePoint == 0x2122 ||
        codePoint == 0x2139 ||
        codePoint == 0x3030 ||
        codePoint == 0x303D ||
        codePoint == 0x3297 ||
        codePoint == 0x3299
