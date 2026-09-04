package com.github.hon454.copyselectioncontext

import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionSettingsTest {
    @Test fun `background reload cannot change settings inside a final publication transaction`() {
        val settings = CopySelectionSettings()
        val started = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            lateinit var reload: java.util.concurrent.Future<*>
            settings.withOutputLock {
                reload = executor.submit {
                    started.countDown()
                    settings.loadState(CopySelectionSettings.State(outputFormat = "pathline"))
                }
                assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals("claude", settings.outputSettingsSnapshot().second.format)
                // A copy performs validation and the coordinator write before this lock is released.
                assertEquals(0L, settings.outputSettingsRevision())
            }
            reload.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("pathline", settings.outputSettingsSnapshot().second.format)
            assertEquals(1L, settings.outputSettingsRevision())
        } finally { executor.shutdownNow(); settings.dispose() }
    }

    @Test
    fun `State has correct default for defaultPathType`() {
        val state = CopySelectionSettings.State()
        assertEquals(PathType.ABSOLUTE, state.defaultPathType)
    }

    @Test
    fun `State has correct default for includeCodeContent`() {
        val state = CopySelectionSettings.State()
        assertFalse(state.includeCodeContent)
    }

    @Test
    fun `State has correct default for enableNotification`() {
        val state = CopySelectionSettings.State()
        assertTrue(state.enableNotification)
    }

    @Test
    fun `State has correct default for outputFormat`() {
        val state = CopySelectionSettings.State()
        assertEquals("claude", state.outputFormat)
    }

    @Test
    fun `State has correct default for codeTrimming`() {
        val state = CopySelectionSettings.State()
        assertFalse(state.codeTrimming)
    }

    @Test
    fun `State has correct default for copyHistorySize`() {
        val state = CopySelectionSettings.State()
        assertEquals(10, state.copyHistorySize)
    }

    @Test
    fun `State has correct default for customFormatTemplate`() {
        val state = CopySelectionSettings.State()
        assertEquals("", state.customFormatTemplate)
    }

    @Test
    fun `State backward compatibility with old constructor`() {
        val state = CopySelectionSettings.State(
            defaultPathType = PathType.RELATIVE,
            includeCodeContent = true
        )
        assertEquals(PathType.RELATIVE, state.defaultPathType)
        assertTrue(state.includeCodeContent)
        // New fields should use defaults
        assertTrue(state.enableNotification)
        assertEquals("claude", state.outputFormat)
        assertFalse(state.codeTrimming)
        assertEquals(10, state.copyHistorySize)
        assertEquals("", state.customFormatTemplate)
    }

    @Test
    fun `State can set all fields individually`() {
        val state = CopySelectionSettings.State(
            defaultPathType = PathType.RELATIVE,
            includeCodeContent = true,
            enableNotification = false,
            outputFormat = "custom",
            codeTrimming = true,
            copyHistorySize = 20,
            customFormatTemplate = "@{path}#{line}"
        )
        assertEquals(PathType.RELATIVE, state.defaultPathType)
        assertTrue(state.includeCodeContent)
        assertFalse(state.enableNotification)
        assertEquals("custom", state.outputFormat)
        assertTrue(state.codeTrimming)
        assertEquals(20, state.copyHistorySize)
        assertEquals("@{path}#{line}", state.customFormatTemplate)
    }

    @Test
    fun `State can be modified after creation`() {
        val state = CopySelectionSettings.State()
        state.enableNotification = false
        state.outputFormat = "verbose"
        state.copyHistorySize = 50

        assertFalse(state.enableNotification)
        assertEquals("verbose", state.outputFormat)
        assertEquals(50, state.copyHistorySize)
    }

    @Test
    fun `loadState accepts zero to disable history`() {
        val settings = CopySelectionSettings()

        settings.loadState(CopySelectionSettings.State(copyHistorySize = 0))

        assertEquals(0, settings.state.copyHistorySize)
    }

    @Test
    fun `loadState clamps invalid history sizes`() {
        val settings = CopySelectionSettings()

        settings.loadState(CopySelectionSettings.State(copyHistorySize = -1))
        assertEquals(0, settings.state.copyHistorySize)

        settings.loadState(CopySelectionSettings.State(copyHistorySize = 101))
        assertEquals(100, settings.state.copyHistorySize)
    }

    @Test
    fun `loadState migrates legacy github format to claude`() {
        val settings = CopySelectionSettings()

        settings.loadState(CopySelectionSettings.State(outputFormat = "github"))

        assertEquals("claude", settings.state.outputFormat)
    }

    @Test
    fun `loadState preserves supported output format`() {
        val settings = CopySelectionSettings()

        settings.loadState(CopySelectionSettings.State(outputFormat = "pathline"))

        assertEquals("pathline", settings.state.outputFormat)
    }

    @Test
    fun `loadState preserves supported output format keys`() {
        OutputFormatOption.entries.forEach { option ->
            val settings = CopySelectionSettings()

            settings.loadState(CopySelectionSettings.State(outputFormat = option.key))

            assertEquals(option.key, settings.state.outputFormat)
        }
    }

    @Test
    fun `localized preset labels do not alter persisted format or template content`() {
        val control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        val english = ResourceBundle.getBundle("messages.CopySelectionBundle", Locale.ENGLISH, control)
        val korean = ResourceBundle.getBundle("messages.CopySelectionBundle", Locale.KOREAN, control)
        val preset = TemplatePreset.WITH_CODE_BLOCK
        val template = requireNotNull(preset.template)

        assertFalse(english.getString(preset.messageKey) == korean.getString(preset.messageKey))

        val settings = CopySelectionSettings()
        settings.loadState(
            CopySelectionSettings.State(
                outputFormat = OutputFormatOption.TEMPLATE.key,
                customFormatTemplate = template,
            ),
        )

        assertEquals("template", settings.state.outputFormat)
        assertEquals(template, settings.state.customFormatTemplate)
        assertEquals("with-code-block", preset.key)
    }

    @Test
    fun `loadState replaces unknown persisted output format with default key`() {
        val settings = CopySelectionSettings()

        settings.loadState(CopySelectionSettings.State(outputFormat = "legacy-format"))

        assertEquals("claude", settings.state.outputFormat)
    }
}
