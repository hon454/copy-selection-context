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
}
