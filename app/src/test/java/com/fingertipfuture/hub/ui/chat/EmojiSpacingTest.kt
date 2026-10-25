package com.fingertipfuture.hub.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiSpacingTest {

    @Test
    fun removesHorizontalSpacesAfterEmoji() {
        assertEquals("🥶冷到了？", removeSpacesAfterEmoji("🥶 冷到了？"))
        assertEquals("hello 😊world", removeSpacesAfterEmoji("hello 😊 world"))
    }

    @Test
    fun keepsLineBreaksAndNormalWordSpaces() {
        assertEquals("😀\n下一行", removeSpacesAfterEmoji("😀\n下一行"))
        assertEquals("hello world", removeSpacesAfterEmoji("hello world"))
    }

    @Test
    fun handlesVariationSelectorAndJoinedEmoji() {
        assertEquals("☺️好的", removeSpacesAfterEmoji("☺️ 好的"))
        assertEquals("👨‍👩‍👧‍👦一家人", removeSpacesAfterEmoji("👨‍👩‍👧‍👦 一家人"))
    }
}
