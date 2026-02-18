package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionProjectSettingsTest {
    @Test
    fun `default state has useProjectSettings false`() {
        val settings = CopySelectionProjectSettings()
        assertFalse(settings.state.useProjectSettings)
    }

    @Test
    fun `default outputFormat is claude`() {
        val settings = CopySelectionProjectSettings()
        assertEquals("claude", settings.state.outputFormat)
    }

    @Test
    fun `default pathType is relative`() {
        val settings = CopySelectionProjectSettings()
        assertEquals("relative", settings.state.pathType)
    }

    @Test
    fun `loadState restores values`() {
        val settings = CopySelectionProjectSettings()
        settings.loadState(
            CopySelectionProjectSettings.State(
                useProjectSettings = true,
                outputFormat = "pathline",
                pathType = "absolute"
            )
        )
        assertTrue(settings.state.useProjectSettings)
        assertEquals("pathline", settings.state.outputFormat)
        assertEquals("absolute", settings.state.pathType)
    }

    @Test
    fun `getState returns current state`() {
        val settings = CopySelectionProjectSettings()
        val state = settings.state
        state.useProjectSettings = true
        state.outputFormat = "template"
        assertTrue(settings.state.useProjectSettings)
        assertEquals("template", settings.state.outputFormat)
    }

    @Test
    fun `State data class defaults are correct`() {
        val state = CopySelectionProjectSettings.State()
        assertFalse(state.useProjectSettings)
        assertEquals("claude", state.outputFormat)
        assertEquals("relative", state.pathType)
    }
}
