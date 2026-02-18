package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OutputFormatterTest {
    @Test
    fun `ClaudeCodeFormatter formats single line`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(path = "src/App.kt", startLine = 42, endLine = 42)

        val result = formatter.format(context)

        assertEquals(" @src/App.kt#L42 ", result)
    }

    @Test
    fun `ClaudeCodeFormatter formats line range`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(path = "src/App.kt", startLine = 42, endLine = 53)

        val result = formatter.format(context)

        assertEquals(" @src/App.kt#L42-53 ", result)
    }

    @Test
    fun `ClaudeCodeFormatter formats with code block`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = "fun main() {}",
            language = "kotlin"
        )

        val result = formatter.format(context)

        assertTrue(result.contains("```kotlin\nfun main() {}\n```"))
    }

    @Test
    fun `PathLineFormatter formats single line`() {
        val formatter = PathLineFormatter()
        val context = FormatContext(path = "src/App.kt", startLine = 42, endLine = 42)

        val result = formatter.format(context)

        assertEquals("src/App.kt:42", result)
    }

    @Test
    fun `PathLineFormatter formats line range`() {
        val formatter = PathLineFormatter()
        val context = FormatContext(path = "src/App.kt", startLine = 42, endLine = 53)

        val result = formatter.format(context)

        assertEquals("src/App.kt:42-53", result)
    }

    @Test
    fun `Factory returns ClaudeCodeFormatter for claude key`() {
        val formatter = OutputFormatterFactory.getFormatter("claude")

        assertIs<ClaudeCodeFormatter>(formatter)
    }

    @Test
    fun `Factory returns PathLineFormatter for pathline key`() {
        val formatter = OutputFormatterFactory.getFormatter("pathline")

        assertIs<PathLineFormatter>(formatter)
    }

    @Test
    fun `Factory defaults to ClaudeCodeFormatter for unknown key`() {
        val formatter = OutputFormatterFactory.getFormatter("unknown")

        assertIs<ClaudeCodeFormatter>(formatter)
    }

    @Test
    fun `ClaudeCodeFormatter handles code with triple backticks`() {
        val formatter = ClaudeCodeFormatter()
        val codeWithBackticks = "```\nsome code\n```"
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = codeWithBackticks,
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Should use 4+ backticks to avoid breaking markdown
        assertTrue(result.contains("````kotlin"))
        assertTrue(result.contains("````"))
    }

    @Test
    fun `ClaudeCodeFormatter handles empty code string`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = "",
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Empty code should not produce code block
        assertEquals(" @src/App.kt#L42-53 ", result)
    }

    @Test
    fun `ClaudeCodeFormatter handles whitespace-only code`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = "   \n  \n  ",
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Whitespace-only code should not produce code block
        assertEquals(" @src/App.kt#L42-53 ", result)
    }

    @Test
    fun `ClaudeCodeFormatter handles path with hash character`() {
        val formatter = ClaudeCodeFormatter()
        val context = FormatContext(
            path = "src/App#v2.kt",
            startLine = 42,
            endLine = 42
        )

        val result = formatter.format(context)

        // Path with # should pass through unchanged
        assertEquals(" @src/App#v2.kt#L42 ", result)
    }

    @Test
    fun `PathLineFormatter handles code with triple backticks`() {
        val formatter = PathLineFormatter()
        val codeWithBackticks = "```\nsome code\n```"
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = codeWithBackticks,
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Should use 4+ backticks to avoid breaking markdown
        assertTrue(result.contains("````kotlin"))
        assertTrue(result.contains("````"))
    }

    @Test
    fun `PathLineFormatter handles empty code string`() {
        val formatter = PathLineFormatter()
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = "",
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Empty code should not produce code block
        assertEquals("src/App.kt:42-53", result)
    }

    @Test
    fun `PathLineFormatter handles whitespace-only code`() {
        val formatter = PathLineFormatter()
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 42,
            endLine = 53,
            code = "   \n  \n  ",
            language = "kotlin"
        )

        val result = formatter.format(context)

        // Whitespace-only code should not produce code block
        assertEquals("src/App.kt:42-53", result)
    }

    @Test
    fun `PathLineFormatter handles path with hash character`() {
        val formatter = PathLineFormatter()
        val context = FormatContext(
            path = "src/App#v2.kt",
            startLine = 42,
            endLine = 42
        )

        val result = formatter.format(context)

        // Path with # should pass through unchanged
        assertEquals("src/App#v2.kt:42", result)
    }

    @Test
    fun `format switching - claude format produces at-path-hash-L output`() {
        val formatter = OutputFormatterFactory.getFormatter("claude")
        val context = FormatContext(path = "src/App.kt", startLine = 10, endLine = 20)

        val result = formatter.format(context)

        assertEquals(" @src/App.kt#L10-20 ", result)
    }

    @Test
    fun `format switching - claude format with code block`() {
        val formatter = OutputFormatterFactory.getFormatter("claude")
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 10,
            endLine = 12,
            code = "val x = 1",
            language = "kotlin"
        )

        val result = formatter.format(context)

        assertTrue(result.startsWith(" @src/App.kt#L10-12 "))
        assertTrue(result.contains("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun `format switching - pathline format produces colon-separated output`() {
        val formatter = OutputFormatterFactory.getFormatter("pathline")
        val context = FormatContext(path = "src/App.kt", startLine = 10, endLine = 20)

        val result = formatter.format(context)

        assertEquals("src/App.kt:10-20", result)
    }

    @Test
    fun `format switching - pathline format with code block`() {
        val formatter = OutputFormatterFactory.getFormatter("pathline")
        val context = FormatContext(
            path = "src/App.kt",
            startLine = 10,
            endLine = 12,
            code = "val x = 1",
            language = "kotlin"
        )

        val result = formatter.format(context)

        assertTrue(result.startsWith("src/App.kt:10-12"))
        assertTrue(result.contains("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun `format switching - unknown format defaults to claude output`() {
        val formatter = OutputFormatterFactory.getFormatter("nonexistent")
        val context = FormatContext(path = "src/App.kt", startLine = 5, endLine = 5)

        val result = formatter.format(context)

        assertEquals(" @src/App.kt#L5 ", result)
    }

    @Test
    fun `format switching - empty key defaults to claude output`() {
        val formatter = OutputFormatterFactory.getFormatter("")
        val context = FormatContext(path = "src/App.kt", startLine = 5, endLine = 5)

        val result = formatter.format(context)

        assertEquals(" @src/App.kt#L5 ", result)
    }
}
