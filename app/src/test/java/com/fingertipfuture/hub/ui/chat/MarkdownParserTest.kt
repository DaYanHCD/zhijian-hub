package com.fingertipfuture.hub.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses headings paragraphs and divider`() {
        val blocks = MarkdownParser.parse(
            """
            # 标题

            普通正文

            ---
            """.trimIndent(),
        )
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertEquals(MarkdownBlock.Divider, blocks[2])
    }

    @Test
    fun `parses fenced code including unfinished stream`() {
        val blocks = MarkdownParser.parse(
            """
            ```kotlin
            val x = 1
            println(x)
            """.trimIndent(),
        )
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("println"))
        assertEquals(false, code.closed)
    }

    @Test
    fun `parses markdown table`() {
        val blocks = MarkdownParser.parse(
            """
            | 名称 | 数量 | 说明 |
            | --- | ---: | --- |
            | 苹果 | 3 | 水果 |
            | 香蕉 | 5 | 更长的那一类 |
            """.trimIndent(),
        )
        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("名称", "数量", "说明"), table.header)
        assertEquals(listOf("苹果", "3", "水果"), table.rows[0])
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `parses nested and task lists`() {
        val blocks = MarkdownParser.parse(
            """
            - 第一项
              - 子项
            - [x] 已完成
            - [ ] 未完成
            """.trimIndent(),
        )
        val list = blocks.single() as MarkdownBlock.BulletList
        assertEquals(4, list.items.size)
        assertEquals(1, list.items[1].level)
        assertEquals("☑", list.items[2].marker)
        assertEquals("☐", list.items[3].marker)
    }

    @Test
    fun `parses quote`() {
        val blocks = MarkdownParser.parse(
            """
            > 第一行
            > 第二行
            """.trimIndent(),
        )
        assertEquals("第一行\n第二行", (blocks.single() as MarkdownBlock.Quote).text)
    }
}
