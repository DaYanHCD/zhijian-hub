package com.fingertipfuture.hub.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathFormulaTest {

    @Test
    fun `fraction becomes readable instead of raw tex`() {
        assertEquals("(a+b)⁄2", MathText.format("\\frac{a+b}{2}"))
        assertEquals("1⁄2", MathText.format("\\frac12"))
    }

    @Test
    fun `scripts use unicode when possible`() {
        assertEquals("x² + y₁", MathText.format("x^2 + y_1"))
    }

    @Test
    fun `roots and greek symbols are converted`() {
        assertEquals("√(x²+1)", MathText.format("\\sqrt{x^2+1}"))
        assertEquals("α ≤ β", MathText.format("\\alpha \\le \\beta"))
    }

    @Test
    fun `quadratic formula is fully readable`() {
        assertEquals(
            "x = (-b ± √(b² - 4ac))⁄2a",
            MathText.format("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
        )
    }

    @Test
    fun `text commands keep spaces and ignored commands disappear`() {
        assertEquals("if x>0", MathText.format("\\text{if } x>0"))
        assertEquals("x", MathText.format("\\displaystyle x"))
        assertEquals("sin(x)", MathText.format("\\operatorname{sin}(x)"))
    }

    @Test
    fun `binomial and accents become readable`() {
        assertEquals("C(n, k)", MathText.format("\\binom{n}{k}"))
        assertEquals("A̅B̅", MathText.format("\\overline{AB}"))
        assertEquals("a+b", MathText.format("\\cancel{a+b}").replace("\u0338", ""))
    }

    @Test
    fun `cases and matrices keep their rows`() {
        assertEquals(
            "⎧ x x≥0\n⎩ -x x<0",
            MathText.format(
                "\\begin{cases} x & x\\ge0 \\\\ -x & x<0 \\end{cases}",
            ),
        )
        assertEquals(
            "(a b)\n(c d)",
            MathText.format(
                "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
            ),
        )
        assertEquals(
            "1 2\n3 4",
            MathText.format(
                "\\begin{array}{cc} 1 & 2 \\\\ 3 & 4 \\end{array}",
            ),
        )
    }

    @Test
    fun `display math becomes its own markdown block`() {
        val blocks = MarkdownParser.parse("$$\nx^2 + \\frac{1}{2}\n$$")
        assertTrue(blocks.single() is MarkdownBlock.Math)
    }

    @Test
    fun `inline delimiters are recognized`() {
        val token = Regex(
            """\$\$[^$\n]+\$\$|\$[^$\n]+\$|\\\([^\n]*?\\\)|\\\[[^\n]*?\\\]""",
        )
        assertTrue(token.containsMatchIn("设 \\(x^2\\) 为已知"))
        assertTrue(token.containsMatchIn("结果是 \$x_1\$"))
    }
}
