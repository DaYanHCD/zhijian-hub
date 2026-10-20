package com.fingertipfuture.hub.ui.chat

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletList(val items: List<MarkdownListItem>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class CodeBlock(
        val language: String,
        val code: String,
        val closed: Boolean,
    ) : MarkdownBlock

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
    ) : MarkdownBlock

    /** 独立成行的数学公式；[source] 是去掉 $ / \[ \] 后的 TeX。 */
    data class Math(val source: String) : MarkdownBlock

    data object Divider : MarkdownBlock
}

internal data class MarkdownListItem(
    val level: Int,
    val marker: String,
    val text: String,
)

/**
 * 轻量 Markdown 分块解析器。
 *
 * 只处理聊天回复最常见的块：标题、段落、列表、引用、分隔线、围栏代码和管道表格。
 * 纯 Kotlin、无第三方库；流式输出时未闭合的 ``` 也会按代码块显示，不会闪回普通正文。
 */
internal object MarkdownParser {

    private val heading = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val unordered = Regex("^([-*+])\\s+(.*)$")
    private val ordered = Regex("^(\\d+)\\.\\s+(.*)$")
    private val divider = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")

    fun parse(source: String): List<MarkdownBlock> {
        if (source.isBlank()) return emptyList()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }

            if (isFence(line)) {
                val language = line.trim().removePrefix("```").trim().substringBefore(' ')
                val code = mutableListOf<String>()
                index++
                var closed = false
                while (index < lines.size) {
                    if (isFence(lines[index])) {
                        closed = true
                        index++
                        break
                    }
                    code += lines[index]
                    index++
                }
                blocks += MarkdownBlock.CodeBlock(
                    language = language,
                    code = code.joinToString("\n"),
                    closed = closed,
                )
                continue
            }

            val mathStart = mathFence(line.trim())
            if (mathStart != null) {
                val opening = line.trim()
                val rest = opening.removePrefix(mathStart.first).trim()
                if (rest.endsWith(mathStart.second)) {
                    blocks += MarkdownBlock.Math(
                        rest.removeSuffix(mathStart.second).trim(),
                    )
                    index++
                    continue
                }
                val math = mutableListOf<String>()
                if (rest.isNotEmpty()) math += rest
                index++
                var closed = false
                while (index < lines.size) {
                    val value = lines[index].trim()
                    if (value.endsWith(mathStart.second)) {
                        val body = value.removeSuffix(mathStart.second).trim()
                        if (body.isNotEmpty()) math += body
                        closed = true
                        index++
                        break
                    }
                    math += lines[index]
                    index++
                }
                val mathSource = math.joinToString("\n").trim()
                if (mathSource.isNotEmpty()) {
                    blocks += MarkdownBlock.Math(mathSource)
                } else if (!closed) {
                    blocks += MarkdownBlock.Paragraph(opening)
                }
                continue
            }

            val headingMatch = heading.matchEntire(line)
            if (headingMatch != null) {
                blocks += MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                )
                index++
                continue
            }

            if (divider.matches(line)) {
                blocks += MarkdownBlock.Divider
                index++
                continue
            }

            if (line.trimStart().startsWith(">")) {
                val quoted = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quoted += lines[index].trimStart().removePrefix(">").removePrefix(" ")
                    index++
                }
                blocks += MarkdownBlock.Quote(quoted.joinToString("\n"))
                continue
            }

            if (isTableStart(lines, index)) {
                val header = splitTableRow(lines[index])
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
                    rows += splitTableRow(lines[index])
                    index++
                }
                val columns = (rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(header.size)
                blocks += MarkdownBlock.Table(
                    header = pad(header, columns),
                    rows = rows.map { pad(it, columns) },
                )
                continue
            }

            if (listMarker(line) != null) {
                val items = mutableListOf<MarkdownListItem>()
                while (index < lines.size) {
                    val marker = listMarker(lines[index]) ?: break
                    items += marker
                    index++
                }
                blocks += MarkdownBlock.BulletList(items)
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                if (lines[index].isBlank() || isBlockStart(lines, index)) break
                paragraph += lines[index]
                index++
            }
            if (paragraph.isEmpty()) {
                // 兜底：遇到暂时识别不了的块起始行，也保证游标前进。
                paragraph += lines[index]
                index++
            }
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
        }
        return blocks
    }

    private fun isFence(line: String): Boolean = line.trimStart().startsWith("```")

    private fun isBlockStart(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        return isFence(line) ||
            heading.matches(line) ||
            divider.matches(line) ||
            line.trimStart().startsWith(">") ||
            listMarker(line) != null ||
            isTableStart(lines, index) ||
            mathFence(line.trim()) != null
    }

    private fun mathFence(line: String): Pair<String, String>? = when {
        line.startsWith("$$") -> "$$" to "$$"
        line.startsWith("\\[") -> "\\[" to "\\]"
        else -> null
    }

    private fun listMarker(line: String): MarkdownListItem? {
        val leading = line.takeWhile { it == ' ' || it == '\t' }
        val level = (leading.fold(0) { total, ch -> total + if (ch == '\t') 2 else 1 } / 2)
            .coerceIn(0, 3)
        val text = line.trimStart()

        unordered.matchEntire(text)?.let { match ->
            var body = match.groupValues[2].trim()
            val task = taskMarker(body)
            return if (task != null) {
                MarkdownListItem(level, task.first, task.second)
            } else {
                MarkdownListItem(level, "•", body)
            }
        }
        ordered.matchEntire(text)?.let { match ->
            return MarkdownListItem(level, match.groupValues[1] + ".", match.groupValues[2].trim())
        }
        return null
    }

    private fun taskMarker(body: String): Pair<String, String>? = when {
        body.startsWith("[ ] ") -> "☐" to body.removePrefix("[ ] ").trim()
        body.startsWith("[x] ", ignoreCase = true) -> "☑" to body.substring(4).trim()
        else -> null
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val header = lines[index]
        val separator = lines[index + 1]
        if (!header.contains('|') || !separator.contains('|')) return false
        val cells = splitTableRow(separator)
        return cells.isNotEmpty() && cells.all { it.matches(Regex("^:?-{3,}:?$")) }
    }

    private fun splitTableRow(line: String): List<String> {
        var value = line.trim()
        if (value.startsWith("|")) value = value.drop(1)
        if (value.endsWith("|")) value = value.dropLast(1)
        return value.split('|').map { it.trim() }
    }

    private fun pad(cells: List<String>, count: Int): List<String> =
        if (cells.size >= count) cells.take(count) else cells + List(count - cells.size) { "" }
}
