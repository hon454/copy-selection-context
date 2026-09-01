package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SelectionModel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CopyGitPermalinkActionTest {
    private val action = CopyGitPermalinkAction()

    @Test
    fun `single caret keeps the editor selection range contract`() {
        val editor = mockk<Editor>()
        val caretModel = mockk<CaretModel>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.caretModel } returns caretModel
        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { caretModel.caretCount } returns 1
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 10
        every { selectionModel.selectionEnd } returns 30
        every { document.getLineNumber(10) } returns 1
        every { document.getLineNumber(29) } returns 2

        val result = action.resolveLineRanges(editor)

        assertEquals(listOf(Pair(2, 3)), result)
    }

    @Test
    fun `multi-caret ranges are copied in document order with current-line fallback`() {
        val editor = mockk<Editor>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()
        val earlySelection = selectedCaret(selectionStart = 10, selectionEnd = 30)
        val currentLineCaret = mockk<Caret>()
        val lateSelection = selectedCaret(selectionStart = 80, selectionEnd = 110)

        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { caretModel.caretCount } returns 3
        every { caretModel.allCarets } returns listOf(lateSelection, currentLineCaret, earlySelection)
        every { currentLineCaret.selectionStart } returns 50
        every { currentLineCaret.hasSelection() } returns false
        every { currentLineCaret.logicalPosition } returns LogicalPosition(4, 0)
        every { document.getLineNumber(10) } returns 1
        every { document.getLineNumber(29) } returns 2
        every { document.getLineNumber(80) } returns 7
        every { document.getLineNumber(109) } returns 9

        val result = action.resolveLineRanges(editor)

        assertEquals(listOf(Pair(2, 3), Pair(5, 5), Pair(8, 10)), result)
    }

    @Test
    fun `multi-caret permalinks preserve line fragments and block separator`() {
        val lineRanges = listOf(Pair(2, 3), Pair(5, 5), Pair(8, 10))

        val result = action.buildPermalinkContent(lineRanges) { startLine, endLine ->
            GitPermalinkGenerator.buildPermalink(
                repoUrl = "https://github.com/owner/repo",
                host = "github.com",
                sha = "abc123",
                filePath = "src/Main.kt",
                startLine = startLine,
                endLine = endLine,
            )
        }

        assertEquals(
            "https://github.com/owner/repo/blob/abc123/src/Main.kt#L2-L3\n\n" +
                "https://github.com/owner/repo/blob/abc123/src/Main.kt#L5\n\n" +
                "https://github.com/owner/repo/blob/abc123/src/Main.kt#L8-L10",
            result,
        )
    }

    @Test
    fun `multi-caret permalink content fails when any range cannot be resolved`() {
        val result = action.buildPermalinkContent(listOf(Pair(2, 3), Pair(5, 5))) { startLine, _ ->
            if (startLine == 2) "first-permalink" else null
        }

        assertNull(result)
    }

    private fun selectedCaret(selectionStart: Int, selectionEnd: Int): Caret = mockk<Caret>().also { caret ->
        every { caret.selectionStart } returns selectionStart
        every { caret.selectionEnd } returns selectionEnd
        every { caret.hasSelection() } returns true
    }
}
