package com.github.hon454.copyselectioncontext

import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputFormatOptionTest {
    @Test
    fun `known persisted keys map to stable output format options`() {
        assertEquals(OutputFormatOption.CLAUDE, OutputFormatOption.fromKey("claude"))
        assertEquals(OutputFormatOption.PATH_LINE, OutputFormatOption.fromKey("pathline"))
        assertEquals(OutputFormatOption.TEMPLATE, OutputFormatOption.fromKey("template"))

        assertEquals("claude", OutputFormatOption.CLAUDE.key)
        assertEquals("pathline", OutputFormatOption.PATH_LINE.key)
        assertEquals("template", OutputFormatOption.TEMPLATE.key)
    }

    @Test
    fun `unknown and legacy persisted keys fall back to claude`() {
        assertEquals(OutputFormatOption.CLAUDE, OutputFormatOption.fromKey(""))
        assertEquals(OutputFormatOption.CLAUDE, OutputFormatOption.fromKey("custom"))
        assertEquals(OutputFormatOption.CLAUDE, OutputFormatOption.fromKey("Claude Code (@path#L)"))
    }

    @Test
    fun `every shipped locale localizes every output format option`() {
        val control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        val expectedLabels = linkedMapOf(
            Locale.ENGLISH to listOf("Claude Code (@path#L)", "Path:Line (path:line)", "Custom Template"),
            Locale.KOREAN to listOf("Claude Code (@path#L)", "경로:줄 (path:line)", "사용자 정의 템플릿"),
            Locale.JAPANESE to listOf(
                "Claude Code (@path#L)",
                "パス:行 (path:line)",
                "カスタムテンプレート",
            ),
            Locale.SIMPLIFIED_CHINESE to listOf("Claude Code (@path#L)", "路径:行 (path:line)", "自定义模板"),
            Locale.TRADITIONAL_CHINESE to listOf("Claude Code (@path#L)", "路徑:行 (path:line)", "自訂範本"),
        )

        expectedLabels.forEach { (locale, expected) ->
            val bundle = ResourceBundle.getBundle("messages.CopySelectionBundle", locale, control)
            OutputFormatOption.entries.forEachIndexed { index, option ->
                assertTrue(bundle.getString(option.messageKey).isNotBlank())
                assertEquals(expected[index], bundle.getString(option.messageKey))
            }
        }

        OutputFormatOption.entries.forEach { option ->
            assertEquals(CopySelectionBundle.message(option.messageKey), option.toString())
        }
    }
}
