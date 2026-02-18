package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.markup.GutterIconRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CopySelectionGutterIconRendererTest {
    @Test
    fun `renderer has tooltip text`() {
        val renderer = CopySelectionGutterIconRenderer()
        assertEquals("Copied to clipboard", renderer.tooltipText)
    }

    @Test
    fun `renderer equality is by class`() {
        val r1 = CopySelectionGutterIconRenderer()
        val r2 = CopySelectionGutterIconRenderer()
        assertEquals(r1, r2)
    }

    @Test
    fun `renderer hashCode is consistent`() {
        val r1 = CopySelectionGutterIconRenderer()
        val r2 = CopySelectionGutterIconRenderer()
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `renderer alignment is LEFT`() {
        val renderer = CopySelectionGutterIconRenderer()
        assertEquals(GutterIconRenderer.Alignment.LEFT, renderer.alignment)
    }

    @Test
    fun `renderer is not equal to non-renderer object`() {
        val renderer = CopySelectionGutterIconRenderer()
        assertFalse(renderer.equals("not a renderer"))
    }

    @Test
    fun `renderer is not equal to null`() {
        val renderer = CopySelectionGutterIconRenderer()
        assertFalse(renderer.equals(null))
    }
}
