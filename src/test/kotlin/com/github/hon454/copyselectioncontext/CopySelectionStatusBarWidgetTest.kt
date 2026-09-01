package com.github.hon454.copyselectioncontext

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionStatusBarWidgetTest {
    @Test
    fun `status and tooltip use safe bounded previews`() {
        val widget = CopySelectionStatusBarWidget(mockk<Project>(relaxed = true))
        val content = "src/App.kt:10-20\n<script>😀 ${"x".repeat(1_000)}</script>"

        widget.update(content)

        assertTrue(widget.getText().length <= CopyPreview.STATUS_MAX_LENGTH)
        assertTrue(widget.getTooltipText().length <= CopyPreview.TOOLTIP_MAX_LENGTH)
        assertFalse(widget.getText().any { it == '\n' || it == '\r' })
        assertFalse(widget.getTooltipText().any { it == '\n' || it == '\r' })
        assertFalse(widget.getText().contains("<script>"))
        assertFalse(widget.getTooltipText().contains("<script>"))
    }

    @Test
    fun `re-copy writes the complete original value`() {
        val manager = mockk<CopyPasteManager>()
        val copied = slot<Transferable>()
        mockkStatic(CopyPasteManager::class)
        try {
            every { CopyPasteManager.getInstance() } returns manager
            every { manager.setContents(capture(copied)) } just runs

            val widget = CopySelectionStatusBarWidget(mockk<Project>(relaxed = true))
            val content = "src/App.kt:10-20\n<script>😀 ${"x".repeat(1_000)}</script>"
            widget.update(content)

            widget.getClickConsumer().consume(mockk(relaxed = true))

            assertEquals(content, copied.captured.getTransferData(DataFlavor.stringFlavor))
        } finally {
            unmockkStatic(CopyPasteManager::class)
        }
    }
}
