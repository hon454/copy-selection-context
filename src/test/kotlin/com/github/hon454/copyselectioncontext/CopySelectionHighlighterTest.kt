package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

class CopySelectionHighlighterTest {
    @Test
    fun `highlighters remain scoped to their owning editor`() {
        val firstHighlighter = mockHighlighter()
        val secondHighlighter = mockHighlighter()
        val firstEditor = mockEditor(firstHighlighter)
        val secondEditor = mockEditor(secondHighlighter)

        CopySelectionHighlighter.update(firstEditor.editor, 1, 1)
        CopySelectionHighlighter.update(secondEditor.editor, 1, 1)

        verify(exactly = 0) {
            secondEditor.markupModel.removeHighlighter(firstHighlighter)
            firstEditor.markupModel.removeHighlighter(secondHighlighter)
        }
    }

    @Test
    fun `previous highlighter is replaced within the same editor`() {
        val previousHighlighter = mockHighlighter()
        val replacementHighlighter = mockHighlighter()
        val editor = mockEditor(previousHighlighter, replacementHighlighter)

        CopySelectionHighlighter.update(editor.editor, 1, 1)
        CopySelectionHighlighter.update(editor.editor, 1, 1)

        verify(exactly = 1) {
            editor.markupModel.removeHighlighter(previousHighlighter)
        }
    }

    private fun mockEditor(vararg highlighters: RangeHighlighter): EditorFixture {
        val editor = mockk<Editor>()
        val document = mockk<Document>()
        val markupModel = mockk<MarkupModel>()
        var storedHighlighter: RangeHighlighter? = null

        every { editor.document } returns document
        every { editor.markupModel } returns markupModel
        every { document.getLineStartOffset(0) } returns 0
        every { document.getLineEndOffset(0) } returns 10
        every { markupModel.addRangeHighlighter(
            0,
            10,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.LINES_IN_RANGE,
        ) } returnsMany highlighters.toList()
        every { editor.getUserData(any<Key<RangeHighlighter>>()) } answers {
            storedHighlighter
        }
        every {
            editor.putUserData(any<Key<RangeHighlighter>>(), any<RangeHighlighter>())
        } answers {
            storedHighlighter = secondArg()
        }
        every { markupModel.removeHighlighter(any()) } just Runs

        return EditorFixture(editor, markupModel)
    }

    private fun mockHighlighter() = mockk<RangeHighlighter>().also { highlighter ->
        every { highlighter.isValid } returns true
        every { highlighter.gutterIconRenderer = any() } just Runs
    }

    private data class EditorFixture(
        val editor: Editor,
        val markupModel: MarkupModel,
    )
}
