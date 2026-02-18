package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TemplateFormatterTest {
    private val context = FormatContext(
        path = "src/App.kt",
        startLine = 15,
        endLine = 23,
        code = "fun main() = Unit",
        language = "kotlin",
        filename = "App.kt"
    )

    @Test
    fun `TemplateFormatter replaces path variable`() {
        val formatter = TemplateFormatter("{path}")

        assertEquals("src/App.kt", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter replaces line variable`() {
        val formatter = TemplateFormatter("{line}")

        assertEquals("15", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter replaces range variable`() {
        val formatter = TemplateFormatter("{range}")

        assertEquals("15-23", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter replaces code variable`() {
        val formatter = TemplateFormatter("{code}")

        assertEquals("fun main() = Unit", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter replaces lang variable`() {
        val formatter = TemplateFormatter("{lang}")

        assertEquals("kotlin", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter replaces filename variable`() {
        val formatter = TemplateFormatter("{filename}")

        assertEquals("App.kt", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter supports combined template`() {
        val formatter = TemplateFormatter("File: {path}, Lines: {range}")

        assertEquals("File: src/App.kt, Lines: 15-23", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter leaves unknown variable unchanged`() {
        val formatter = TemplateFormatter("{unknown}")

        assertEquals("{unknown}", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter returns empty string for empty template`() {
        val formatter = TemplateFormatter("")

        assertEquals("", formatter.format(context))
    }

    @Test
    fun `TemplateFormatter normalizes windows style paths`() {
        val formatter = TemplateFormatter("{path}")
        val windowsContext = context.copy(path = "src\\nested\\App.kt")

        assertEquals("src/nested/App.kt", formatter.format(windowsContext))
    }

    @Test
    fun `TemplateFormatter exposes preset templates`() {
        assertEquals("{path}:{range}", TemplateFormatter.PRESET_PATH_AND_RANGE)
        assertEquals(" @{path}#L{range} ", TemplateFormatter.PRESET_CLAUDE_REFERENCE)
        assertEquals("{path}:{range}\n```{lang}\n{code}\n```", TemplateFormatter.PRESET_WITH_CODE_BLOCK)
    }

    @Test
    fun `OutputFormatterFactory returns template formatter for template key`() {
        val formatter = OutputFormatterFactory.getFormatter("template")

        assertIs<TemplateFormatter>(formatter)
    }

    @Test
    fun `OutputFormatterFactory creates template formatter from settings`() {
        val settings = CopySelectionSettings.State(
            outputFormat = "template",
            customFormatTemplate = "File: {filename}"
        )

        val formatter = OutputFormatterFactory.getFormatterForSettings(settings)

        assertIs<TemplateFormatter>(formatter)
        assertEquals("File: App.kt", formatter.format(context))
    }

    @Test
    fun `OutputFormatterFactory falls back to registered template formatter when template is blank`() {
        val settings = CopySelectionSettings.State(
            outputFormat = "template",
            customFormatTemplate = ""
        )

        val formatter = OutputFormatterFactory.getFormatterForSettings(settings)

        assertIs<TemplateFormatter>(formatter)
    }
}
