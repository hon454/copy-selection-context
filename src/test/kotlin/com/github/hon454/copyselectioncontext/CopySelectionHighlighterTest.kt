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
    fun `multi-caret update adds a gutter marker for every copied range`() {
        val firstHighlighter = mockHighlighter()
        val secondHighlighter = mockHighlighter()
        val editor = mockEditor(firstHighlighter, secondHighlighter)

        CopySelectionHighlighter.update(editor.editor, listOf(Pair(1, 1), Pair(3, 4)))

        verify(exactly = 1) {
            editor.markupModel.addRangeHighlighter(
                0,
                9,
                HighlighterLayer.ADDITIONAL_SYNTAX,
                null,
                HighlighterTargetArea.LINES_IN_RANGE,
            )
            editor.markupModel.addRangeHighlighter(
                20,
                39,
                HighlighterLayer.ADDITIONAL_SYNTAX,
                null,
                HighlighterTargetArea.LINES_IN_RANGE,
            )
        }
    }

    @Test
    fun `previous multi-caret highlighters are replaced within the same editor`() {
        val firstPreviousHighlighter = mockHighlighter()
        val secondPreviousHighlighter = mockHighlighter()
        val replacementHighlighter = mockHighlighter()
        val editor = mockEditor(firstPreviousHighlighter, secondPreviousHighlighter, replacementHighlighter)

        CopySelectionHighlighter.update(editor.editor, listOf(Pair(1, 1), Pair(3, 4)))
        CopySelectionHighlighter.update(editor.editor, 1, 1)

        verify(exactly = 1) {
            editor.markupModel.removeHighlighter(firstPreviousHighlighter)
            editor.markupModel.removeHighlighter(secondPreviousHighlighter)
        }
    }

    private fun mockEditor(vararg highlighters: RangeHighlighter): EditorFixture {
        val editor = mockk<Editor>()
        val document = mockk<Document>()
        val markupModel = mockk<MarkupModel>()
        var storedHighlighters: List<RangeHighlighter>? = null

        every { editor.document } returns document
        every { editor.markupModel } returns markupModel
        every { document.getLineStartOffset(any()) } answers { firstArg<Int>() * 10 }
        every { document.getLineEndOffset(any()) } answers { firstArg<Int>() * 10 + 9 }
        every { markupModel.addRangeHighlighter(
            any(),
            any(),
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.LINES_IN_RANGE,
        ) } returnsMany highlighters.toList()
        every { editor.getUserData(any<Key<List<RangeHighlighter>>>()) } answers {
            storedHighlighters
        }
        every {
            editor.putUserData(any<Key<List<RangeHighlighter>>>(), any<List<RangeHighlighter>>())
        } answers {
            storedHighlighters = secondArg()
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
