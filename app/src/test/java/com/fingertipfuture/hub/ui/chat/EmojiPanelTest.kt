package com.fingertipfuture.hub.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelTest {

    @Test
    fun deleteRemovesOneSurrogatePairEmoji() {
        assertEquals("😀", dropLastGrapheme("😀👍"))
    }

    @Test
    fun deleteKeepsJoinedEmojiTogether() {
        assertEquals("你好", dropLastGrapheme("你好👨‍👩‍👧‍👦"))
    }

    @Test
    fun deleteOnEmptyTextDoesNothing() {
        assertEquals("", dropLastGrapheme(""))
    }

    @Test
    fun notoEmojiNameDropsTrailingVariationSelector() {
        assertEquals("2764", notoEmojiAssetName("❤️"))
    }

    @Test
    fun notoEmojiNameUsesLowercaseCodePoints() {
        assertEquals("1f600", notoEmojiAssetName("😀"))
    }

    @Test
    fun singleEmojiDetectsOneEmojiWithWhitespace() {
        assertEquals("😀", singleEmojiOrNull("  😀  "))
    }

    @Test
    fun singleEmojiRejectsMixedTextAndMultipleEmoji() {
        assertEquals(null, singleEmojiOrNull("你好😀"))
        assertEquals(null, singleEmojiOrNull("😀👍"))
    }

    @Test
    fun everyAnimatedEmojiHasStaticThumbnail() {
        // 动画 WebP 是可选下载扩展，源文件仍保留在 app 模块中供扩展打包脚本使用。
        val animated = File("../extension-src/noto_emoji_animated")
            .listFiles { file -> file.extension == "webp" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .toSet()
        val static = File("src/main/assets/noto_emoji_static")
            .listFiles { file -> file.extension == "png" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .toSet()

        assertTrue(animated.isNotEmpty())
        assertEquals(emptySet<String>(), animated - static)
    }

}
