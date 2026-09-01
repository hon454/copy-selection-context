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
    fun `English and Korean bundles localize every output format option`() {
        val control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        val english = ResourceBundle.getBundle("messages.CopySelectionBundle", Locale.ENGLISH, control)
        val korean = ResourceBundle.getBundle("messages.CopySelectionBundle", Locale.KOREAN, control)

        OutputFormatOption.entries.forEach { option ->
            assertTrue(english.getString(option.messageKey).isNotBlank())
            assertTrue(korean.getString(option.messageKey).isNotBlank())
            assertEquals(CopySelectionBundle.message(option.messageKey), option.toString())
        }

        assertEquals("Path:Line (path:line)", english.getString(OutputFormatOption.PATH_LINE.messageKey))
        assertEquals("경로:줄 (path:line)", korean.getString(OutputFormatOption.PATH_LINE.messageKey))
        assertEquals("Custom Template", english.getString(OutputFormatOption.TEMPLATE.messageKey))
        assertEquals("사용자 정의 템플릿", korean.getString(OutputFormatOption.TEMPLATE.messageKey))
    }
}
