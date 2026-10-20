package com.fingertipfuture.hub.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fingertipfuture.hub.ui.component.HubInk
import com.fingertipfuture.hub.ui.component.HubInkSoft
import com.fingertipfuture.hub.ui.component.MaterialSymbol
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.fingertipfuture.hub.R
import com.fingertipfuture.hub.data.UiPrefs

/**
 * 轻量 TeX → Unicode 数学文本转换。
 *
 * 目标不是实现完整排版引擎，而是让模型最常见的公式不再显示成一串反斜杠和花括号：
 * 分式、根号、上下标、希腊字母、求和/积分和常用关系符都会转换。
 * 纯 Kotlin，没有 WebView、Markdown 或公式渲染第三方库。
 */
internal object MathText {

    fun format(source: String): String {
        val normalized = normalize(source)
        return Parser(normalized).parseSequence().trim()
    }

    /** 块级公式排版用：只做环境归一化，保留 \frac 等结构。 */
    internal fun normalizeForLayout(source: String): String = normalize(source)

    private fun normalize(source: String): String {
        var value = source
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        // 空定界符 \left. / \right. 在 TeX 里不产生字符，先单独处理，避免留下一个孤零零的点。
        value = value
            .replace(Regex("""\\left\s*\\."""), "")
            .replace(Regex("""\\right\s*\\."""), "")
            .replace(Regex("""\\left\s*"""), "")
            .replace(Regex("""\\right\s*"""), "")
            .replace(Regex("""\\begin\{array\}\s*\{[^}]*\}"""), "")
            .replace(Regex("""\\end\{array\}"""), "")

        // 分段函数 / 矩阵保留行结构；只去掉环境命令，不让它们变成一行乱码。
        value = replaceEnvironment(value, "cases", "{ ", "")
        value = replaceEnvironment(value, "pmatrix", "(", ")")
        value = replaceEnvironment(value, "bmatrix", "[", "]")
        value = replaceEnvironment(value, "Bmatrix", "{", "}")
        value = replaceEnvironment(value, "vmatrix", "|", "|")
        value = replaceEnvironment(value, "Vmatrix", "‖", "‖")
        value = replaceEnvironment(value, "matrix", "[", "]")
        value = replaceEnvironment(value, "array", "", "")
        value = replaceEnvironment(value, "aligned", "", "")
        value = replaceEnvironment(value, "align", "", "")
        value = value
            .replace(Regex("""\\(?:hline|hdashline)\b"""), "")
            .replace(Regex("""\\\\"""), "\n")
            .replace('&', ' ')
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n[ \t]+"""), "\n")
        return value
    }

    /**
     * 把一个 LaTeX 环境换成可读的文本行。
     * 矩阵的每一行都加同一对括号，分段函数则只在开头放一个左花括号。
     */
    private fun replaceEnvironment(
        value: String,
        name: String,
        open: String,
        close: String,
    ): String {
        val begin = Regex("""\\begin\{${Regex.escape(name)}\}""")
        val end = Regex("""\\end\{${Regex.escape(name)}\}""")
        if (!begin.containsMatchIn(value)) return value
        val body = value.replace(begin, "").replace(end, "")
        return if (name == "cases") {
            val rows = body.replace(Regex("""\\\\"""), "\n")
                .trim()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            rows.mapIndexed { index, row ->
                val brace = when {
                    rows.size == 1 -> "⎧"
                    index == 0 -> "⎧"
                    index == rows.lastIndex -> "⎩"
                    else -> "⎨"
                }
                "$brace $row"
            }.joinToString("\n")
        } else {
            body.replace(Regex("""\\\\"""), "\n")
                .trim()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n") { open + it + close }
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseSequence(stopAtBrace: Boolean = false): String {
            val out = StringBuilder()
            while (index < text.length) {
                val ch = text[index]
                when {
                    ch == '}' && stopAtBrace -> {
                        index++
                        return out.toString()
                    }
                    ch == '{' -> {
                        index++
                        out.append(parseSequence(stopAtBrace = true))
                    }
                    ch == '\\' -> out.append(parseCommand())
                    ch == '^' -> {
                        index++
                        out.append(script(readAtom(), superscript = true))
                    }
                    ch == '_' -> {
                        index++
                        out.append(script(readAtom(), superscript = false))
                    }
                    ch == '\n' -> {
                        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                        index++
                    }
                    ch.isWhitespace() -> {
                        if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                        index++
                    }
                    else -> {
                        out.append(ch)
                        index++
                    }
                }
            }
            return out.toString()
        }

        private fun parseCommand(): String {
            index++ // backslash
            if (index >= text.length) return "\\"
            if (!text[index].isLetter()) {
                val ch = text[index]
                index++
                return when (ch) {
                    ',', ';', ':', ' ' -> " "
                    '!' -> ""
                    '{', '}', '%', '&', '#', '_', '$' -> ch.toString()
                    '\\' -> "\n"
                    else -> ch.toString()
                }
            }

            val start = index
            while (index < text.length && text[index].isLetter()) index++
            val name = text.substring(start, index)
            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val numerator = parseFractionPart()
                    val denominator = parseFractionPart()
                    fraction(numerator, denominator)
                }
                "sqrt" -> {
                    val degree = optionalBracket()
                    val radicand = parseRequiredGroup()
                    val root = radical(radicand)
                    if (degree.isBlank()) root else script(degree, true) + root
                }
                "binom" -> {
                    val n = parseRequiredGroup().trim()
                    val k = parseRequiredGroup().trim()
                    "C($n, $k)"
                }
                "text", "textrm", "mathrm", "operatorname", "mathbf", "mathit", "mathcal" ->
                    parseRawGroup()
                "mathbb" -> blackboard(parseRawGroup())
                "textnormal", "mbox" -> parseRawGroup()
                "boxed" -> "[${parseRequiredGroup().trim()}]"
                "overline" -> overline(parseRequiredGroup())
                "underline" -> underline(parseRequiredGroup())
                "widehat" -> accent(parseRequiredGroup(), "\u0302")
                "widetilde" -> accent(parseRequiredGroup(), "\u0303")
                "cancel", "bcancel", "xcancel" -> strike(parseRequiredGroup())
                "color", "textcolor", "colorbox" -> {
                    // 颜色在纯文本里没有意义，丢掉颜色参数、保留内容。
                    parseRawGroup()
                    parseRequiredGroup()
                }
                "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
                "limits", "nolimits", "mathstrut", "strut", "smallskip", "medskip", "bigskip" -> ""
                "phantom", "hphantom", "vphantom", "label" -> {
                    parseRequiredGroup()
                    ""
                }
                "tag" -> "  (" + parseRawGroup() + ")"
                "vec", "overrightarrow", "bar", "hat", "dot", "ddot" -> {
                    val value = parseRequiredGroup()
                    when (name) {
                        "vec", "overrightarrow" -> "$value\u20D7"
                        "bar" -> "$value\u0304"
                        "hat" -> "$value\u0302"
                        else -> "$value\u0307"
                    }
                }
                "sum" -> "∑"
                "prod" -> "∏"
                "int" -> "∫"
                "iint" -> "∬"
                "iiint" -> "∭"
                "oint" -> "∮"
                "partial" -> "∂"
                "nabla" -> "∇"
                "infty" -> "∞"
                "pm" -> "±"
                "mp" -> "∓"
                "times" -> "×"
                "cdot" -> "·"
                "div" -> "÷"
                "ast" -> "∗"
                "star" -> "⋆"
                "circ" -> "∘"
                "le", "leq" -> "≤"
                "ge", "geq" -> "≥"
                "ne", "neq" -> "≠"
                "approx" -> "≈"
                "equiv" -> "≡"
                "sim" -> "∼"
                "propto" -> "∝"
                "ll" -> "≪"
                "gg" -> "≫"
                "in" -> "∈"
                "notin" -> "∉"
                "subset" -> "⊂"
                "supset" -> "⊃"
                "subseteq" -> "⊆"
                "supseteq" -> "⊇"
                "cup" -> "∪"
                "cap" -> "∩"
                "forall" -> "∀"
                "exists" -> "∃"
                "neg" -> "¬"
                "land", "wedge" -> "∧"
                "lor", "vee" -> "∨"
                "to", "rightarrow" -> "→"
                "leftarrow" -> "←"
                "leftrightarrow" -> "↔"
                "Rightarrow" -> "⇒"
                "Leftarrow" -> "⇐"
                "Leftrightarrow" -> "⇔"
                "mapsto" -> "↦"
                "uparrow" -> "↑"
                "downarrow" -> "↓"
                "degree" -> "°"
                "angle" -> "∠"
                "perp" -> "⊥"
                "parallel" -> "∥"
                "because" -> "∵"
                "therefore" -> "∴"
                "ldots", "dots", "cdots" -> "…"
                "vdots" -> "⋮"
                "ddots" -> "⋱"
                "quad" -> "  "
                "qquad" -> "    "
                "lvert", "rvert", "vert" -> "|"
                "lVert", "rVert", "Vert" -> "‖"
                "lceil" -> "⌈"
                "rceil" -> "⌉"
                "lfloor" -> "⌊"
                "rfloor" -> "⌋"
                "langle" -> "⟨"
                "rangle" -> "⟩"
                "lbrace" -> "{"
                "rbrace" -> "}"
                "backslash" -> "\\"
                "square" -> "□"
                "blacksquare" -> "■"
                "checkmark" -> "✓"
                "sin", "cos", "tan", "cot", "sec", "csc",
                "log", "ln", "exp", "lim", "max", "min", "det",
                "arg", "gcd", "mod" -> name
                "alpha" -> "α"
                "beta" -> "β"
                "gamma" -> "γ"
                "delta" -> "δ"
                "epsilon", "varepsilon" -> "ε"
                "zeta" -> "ζ"
                "eta" -> "η"
                "theta", "vartheta" -> "θ"
                "iota" -> "ι"
                "kappa" -> "κ"
                "lambda" -> "λ"
                "mu" -> "μ"
                "nu" -> "ν"
                "xi" -> "ξ"
                "omicron" -> "ο"
                "pi", "varpi" -> "π"
                "rho", "varrho" -> "ρ"
                "sigma", "varsigma" -> "σ"
                "tau" -> "τ"
                "upsilon" -> "υ"
                "phi", "varphi" -> "φ"
                "chi" -> "χ"
                "psi" -> "ψ"
                "omega" -> "ω"
                "Gamma" -> "Γ"
                "Delta" -> "Δ"
                "Theta" -> "Θ"
                "Lambda" -> "Λ"
                "Xi" -> "Ξ"
                "Pi" -> "Π"
                "Sigma" -> "Σ"
                "Upsilon" -> "Υ"
                "Phi" -> "Φ"
                "Psi" -> "Ψ"
                "Omega" -> "Ω"
                else -> name
            }
        }

        private fun parseRequiredGroup(): String {
            skipSpaces()
            if (index < text.length && text[index] == '{') {
                index++
                return parseSequence(stopAtBrace = true)
            }
            return readAtom()
        }

        /** \frac12 这种不带花括号的写法：分子分母各取一个 token。 */
        private fun parseFractionPart(): String {
            skipSpaces()
            if (index < text.length && text[index] == '{') {
                index++
                return parseSequence(stopAtBrace = true)
            }
            if (index >= text.length) return ""
            if (text[index] == '\\') return parseCommand()
            val start = index
            index++
            return text.substring(start, index)
        }

        /** 用于 \text / \operatorname：原样保留空格和换行，不再当成数学序列压缩。 */
        private fun parseRawGroup(): String {
            skipSpaces()
            if (index >= text.length) return ""
            if (text[index] != '{') return readAtom()
            index++
            val start = index
            var depth = 1
            while (index < text.length) {
                when (text[index]) {
                    '\\' -> {
                        index += 2
                        continue
                    }
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val value = text.substring(start, index)
                            index++
                            return value
                        }
                    }
                }
                index++
            }
            return text.substring(start)
        }

        private fun optionalBracket(): String {
            skipSpaces()
            if (index >= text.length || text[index] != '[') return ""
            index++
            val start = index
            while (index < text.length && text[index] != ']') index++
            val value = text.substring(start, index)
            if (index < text.length) index++
            return format(value)
        }

        private fun readAtom(): String {
            skipSpaces()
            if (index >= text.length) return ""
            if (text[index] == '{') {
                index++
                return parseSequence(stopAtBrace = true)
            }
            if (text[index] == '\\') return parseCommand()
            val start = index
            if (text[index].isDigit()) {
                while (index < text.length && text[index].isDigit()) index++
            } else {
                index++
            }
            return text.substring(start, index)
        }

        private fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }

    private fun fraction(numerator: String, denominator: String): String {
        val top = numerator.trim()
        val bottom = denominator.trim()
        return wrapFractionPart(top) + "⁄" + wrapFractionPart(bottom)
    }

    private fun wrapFractionPart(value: String): String =
        if (needsParens(value)) "($value)" else value

    private fun radical(value: String): String {
        val radicand = value.trim()
        return if (radicand.length <= 1 || !needsParens(radicand)) {
            "√$radicand"
        } else {
            "√($radicand)"
        }
    }

    /**
     * 分母 / 分子里只要出现顶层运算符或空格，就需要括号。
     * 括号内部的 `-` / `+` 不触发额外括号，例如 √(b²-4ac) 不会再被包一层。
     */
    private fun needsParens(value: String): Boolean {
        if (value.isBlank()) return false
        var depth = 0
        for (ch in value) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                else -> if (depth == 0 &&
                    (ch.isWhitespace() || ch in "+-−=<>≤≥±∓×÷⁄,;:")
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun overline(value: String): String =
        if (value.length <= 2 && value.none { it.isWhitespace() }) {
            value.map { "$it\u0305" }.joinToString("")
        } else {
            "‾($value)"
        }

    private fun underline(value: String): String =
        if (value.length <= 2 && value.none { it.isWhitespace() }) {
            value.map { "$it\u0332" }.joinToString("")
        } else {
            "_($value)"
        }

    private fun accent(value: String, mark: String): String =
        if (value.length <= 2 && value.none { it.isWhitespace() }) {
            value.map { "$it$mark" }.joinToString("")
        } else {
            "$value$mark"
        }

    private fun strike(value: String): String =
        value.map { "$it\u0338" }.joinToString("")

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ',
        'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'k' to 'ᵏ',
        'm' to 'ᵐ', 'p' to 'ᵖ', 't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ',
        'w' to 'ʷ',
    )
    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ',
    )

    private fun script(value: String, superscript: Boolean): String {
        val map = if (superscript) superscripts else subscripts
        val converted = value.map { map[it] ?: return@map null }
        return if (converted.all { it != null }) {
            converted.joinToString("")
        } else {
            (if (superscript) "^(" else "_(") + value + ")"
        }
    }

    private fun blackboard(value: String): String = buildString {
        value.forEach { ch ->
            append(
                when (ch) {
                    'R' -> "ℝ"
                    'N' -> "ℕ"
                    'Z' -> "ℤ"
                    'Q' -> "ℚ"
                    'C' -> "ℂ"
                    else -> ch
                },
            )
        }
    }
}

private sealed interface MathLine {
    data class Plain(val text: String) : MathLine
    data class Fraction(
        val prefix: String,
        val numerator: String,
        val denominator: String,
        val suffix: String,
    ) : MathLine
    data class Cases(val rows: List<MathLine>) : MathLine
    data class Matrix(
        val rows: List<MathLine>,
        val open: String,
        val close: String,
    ) : MathLine
}

private data class FractionSpan(
    val start: Int,
    val end: Int,
    val numerator: String,
    val denominator: String,
)

private data class GroupSpan(val text: String, val end: Int)

/**
 * 找出这一行里位于最外层、且只有一个的分式。
 * 有多个分式的行继续用纯文本排版，避免把一句话拆成几块互相错位。
 */
private fun findTopLevelFractions(line: String): List<FractionSpan> {
    val found = mutableListOf<FractionSpan>()
    var depth = 0
    var index = 0
    while (index < line.length) {
        when (line[index]) {
            '{' -> depth++
            '}' -> if (depth > 0) depth--
            '\\' -> if (depth == 0) {
                val name = listOf("\\dfrac", "\\tfrac", "\\frac")
                    .firstOrNull { line.startsWith(it, index) }
                if (name != null) {
                    var cursor = index + name.length
                    val numerator = readGroup(line, cursor)
                    val denominator = numerator?.let { readGroup(line, it.end) }
                    if (numerator != null && denominator != null) {
                        found += FractionSpan(
                            start = index,
                            end = denominator.end,
                            numerator = numerator.text,
                            denominator = denominator.text,
                        )
                        index = denominator.end
                        continue
                    }
                    cursor = index + name.length
                    index = cursor
                    continue
                }
            }
        }
        index++
    }
    return found
}

private fun readGroup(text: String, start: Int): GroupSpan? {
    var index = start
    while (index < text.length && text[index].isWhitespace()) index++
    if (index >= text.length) return null
    if (text[index] != '{') {
        val from = index
        if (text[index] == '\\') {
            index++
            while (index < text.length && text[index].isLetter()) index++
        } else {
            index++
        }
        return GroupSpan(text.substring(from, index), index)
    }
    val from = index + 1
    var depth = 0
    while (index < text.length) {
        when (text[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return GroupSpan(text.substring(from, index), index + 1)
                }
            }
        }
        index++
    }
    return null
}

private fun parseMathLine(line: String): MathLine {
    val fractions = findTopLevelFractions(line)
    return if (fractions.size == 1) {
        val fraction = fractions.single()
        MathLine.Fraction(
            prefix = MathText.format(line.substring(0, fraction.start)).trim(),
            numerator = fraction.numerator,
            denominator = fraction.denominator,
            suffix = MathText.format(line.substring(fraction.end)).trim(),
        )
    } else {
        MathLine.Plain(MathText.format(line))
    }
}

private fun parseMathLines(source: String): List<MathLine> {
    val lines = MathText.normalizeForLayout(source)
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()

    val caseMarkers = setOf('⎧', '⎨', '⎩')
    if (lines.size > 1 && lines.all { it.firstOrNull() in caseMarkers }) {
        return listOf(
            MathLine.Cases(
                lines.map { parseMathLine(it.drop(1).trimStart()) },
            ),
        )
    }

    val first = lines.first()
    val open = first.firstOrNull()
    val close = first.lastOrNull()
    if (lines.size > 1 &&
        open != null &&
        close != null &&
        open in "([|" &&
        close in ")]|" &&
        lines.all { it.length >= 2 && it.first() == open && it.last() == close }
    ) {
        return listOf(
            MathLine.Matrix(
                rows = lines.map { parseMathLine(it.substring(1, it.length - 1)) },
                open = open.toString(),
                close = close.toString(),
            ),
        )
    }

    return lines.map { parseMathLine(it) }
        }

@Composable
internal fun MathFormulaBlock(source: String) {
    val value = source.trim()
        .removeSurrounding("$$")
        .removeSurrounding("\\[", "\\]")
        .trim()
    val formatted = remember(value) { MathText.format(value).ifBlank { value } }
    val lines = remember(value) { parseMathLines(value) }
    val structured = lines.any {
        it is MathLine.Fraction || it is MathLine.Cases || it is MathLine.Matrix
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val panel = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.42f,
    )
    Surface(
        color = panel,
        shape = shape,
        border = if (UiPrefs.cardBorders) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val contentMinWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    if (structured) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = contentMinWidth)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lines
                                .filterNot { it is MathLine.Plain && it.text.isBlank() }
                                .forEach { line -> MathLineView(line, HubInk) }
                        }
                    } else {
                        Text(
                            text = formatted,
                            color = HubInk,
                            fontFamily = FontFamily.Serif,
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            modifier = Modifier
                                .widthIn(min = contentMinWidth)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
            }
            MaterialSymbol(
                resId = R.drawable.ic_copy,
                contentDescription = "复制公式",
                size = 16.dp,
                tint = HubInkSoft,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .clickable {
                        clipboard.setText(AnnotatedString(formatted))
                        Toast.makeText(context, "已复制公式", Toast.LENGTH_SHORT).show()
                    }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun MathLineView(line: MathLine, color: androidx.compose.ui.graphics.Color) {
    when (line) {
        is MathLine.Plain -> Text(
            text = line.text,
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 28.sp,
            softWrap = false,
        )
        is MathLine.Fraction -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (line.prefix.isNotBlank()) {
                Text(
                    text = line.prefix,
                    color = color,
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    softWrap = false,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            FractionView(
                numerator = line.numerator,
                denominator = line.denominator,
                color = color,
            )
            if (line.suffix.isNotBlank()) {
                Text(
                    text = line.suffix,
                    color = color,
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        is MathLine.Cases -> MathDelimited(
            open = "{",
            close = "",
            rows = line.rows,
            color = color,
        )
        is MathLine.Matrix -> MathDelimited(
            open = line.open,
            close = line.close,
            rows = line.rows,
            color = color,
        )
    }
}

@Composable
private fun MathDelimited(
    open: String,
    close: String,
    rows: List<MathLine>,
    color: androidx.compose.ui.graphics.Color,
) {
    val delimiterSize = (26 + rows.size * 8).sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (open.isNotBlank()) {
            Text(
                text = open,
                color = color.copy(alpha = 0.75f),
                fontFamily = FontFamily.Serif,
                fontSize = delimiterSize,
                lineHeight = delimiterSize,
                softWrap = false,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { row -> MathLineView(row, color) }
        }
        if (close.isNotBlank()) {
            Text(
                text = close,
                color = color.copy(alpha = 0.75f),
                fontFamily = FontFamily.Serif,
                fontSize = delimiterSize,
                lineHeight = delimiterSize,
                softWrap = false,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun FractionView(
    numerator: String,
    denominator: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MathText.format(numerator),
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            thickness = 1.dp,
            color = color.copy(alpha = 0.65f),
        )
        Text(
            text = MathText.format(denominator),
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}
