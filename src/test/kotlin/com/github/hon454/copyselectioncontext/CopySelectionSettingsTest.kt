package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionSettingsTest {
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
}
