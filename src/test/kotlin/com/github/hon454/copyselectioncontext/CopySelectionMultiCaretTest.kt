package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretAction
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class CopySelectionMultiCaretTest {
    @Test
    fun `settings action formats each caret with its own range and code`() = withSettings(
        CopySelectionSettings.State(includeCodeContent = true),
    ) {
        val fixture = multiCaretFixture()
        val action = CopySelectionContextAction()

        val contexts = CopySelectionUtils.captureSelectionContexts("src/App.kt", fixture.file, fixture.editor)
        val result = action.buildCapturedContent(contexts)

        assertEquals(
            " @src/App.kt#L1-2 \n```kotlin\nfirst()\n```\n\n" +
                " @src/App.kt#L5 \n```kotlin\nsecond()\n```",
            result.content,
        )
        assertEquals(listOf(Pair(1, 2), Pair(5, 5)), result.lineRanges)
    }

    @Test
    fun `explicit code action formats each caret with its own range and code`() = withSettings(
        CopySelectionSettings.State(),
    ) {
        val fixture = multiCaretFixture()
        val action = CopyWithCodeContentAction()

        val contexts = CopySelectionUtils.captureSelectionContexts("src/App.kt", fixture.file, fixture.editor)
        val result = action.buildCapturedContent(contexts)

        assertEquals(
            " @src/App.kt#L1-2 \n```kotlin\nfirst()\n```\n\n" +
                " @src/App.kt#L5 \n```kotlin\nsecond()\n```",
            result.content,
        )
        assertEquals(listOf(Pair(1, 2), Pair(5, 5)), result.lineRanges)
    }

    @Test
    fun `explicit path actions format each caret with its own range`() = withSettings(
        CopySelectionSettings.State(),
    ) {
        val fixture = multiCaretFixture()
        val actions = listOf(CopyAbsolutePathAction(), CopyRelativePathAction())
        val contexts = CopySelectionUtils.captureSelectionContexts("src/App.kt", fixture.file, fixture.editor)

        actions.forEach { action ->
            val result = action.buildCapturedContent(contexts)

            assertEquals(
                " @src/App.kt#L1-2 \n\n @src/App.kt#L5 ",
                result.content,
            )
            assertEquals(listOf(Pair(1, 2), Pair(5, 5)), result.lineRanges)
        }
        verify(exactly = 1) { fixture.document.getLineNumber(0) }
        verify(exactly = 1) { fixture.document.getLineNumber(11) }
    }

    @Test
    fun `captured contexts remain deterministic after selections change`() = withSettings(
        CopySelectionSettings.State(includeCodeContent = true),
    ) {
        val fixture = multiCaretFixture()
        val contexts = CopySelectionUtils.captureSelectionContexts("src/App.kt", fixture.file, fixture.editor)
        every { fixture.selectedCaret.selectionStart } returns 40
        every { fixture.selectedCaret.selectionEnd } returns 48
        every { fixture.selectedCaret.selectedText } returns "changed()"

        val result = CopySelectionContextAction().buildCapturedContent(contexts)

        assertEquals(
            " @src/App.kt#L1-2 \n```kotlin\nfirst()\n```\n\n" +
                " @src/App.kt#L5 \n```kotlin\nsecond()\n```",
            result.content,
        )
    }

    private fun multiCaretFixture(): MultiCaretFixture {
        val editor = mockk<Editor>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()
        val selectedCaret = mockk<Caret>()
        val currentLineCaret = mockk<Caret>()
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()

        every { editor.document } returns document
        every { editor.caretModel } returns caretModel
        every { selectedCaret.hasSelection() } returns true
        every { selectedCaret.selectionStart } returns 0
        every { selectedCaret.selectionEnd } returns 12
        every { selectedCaret.selectedText } returns "first()"
        every { currentLineCaret.hasSelection() } returns false
        every { currentLineCaret.logicalPosition } returns LogicalPosition(4, 0)
        every { document.getLineNumber(0) } returns 0
        every { document.getLineNumber(11) } returns 1
        every { document.getLineStartOffset(4) } returns 40
        every { document.getLineEndOffset(4) } returns 48
        every { document.getText(TextRange(40, 48)) } returns "second()"
        every { caretModel.runForEachCaret(any<CaretAction>()) } answers {
            val action = firstArg<CaretAction>()
            action.perform(selectedCaret)
            action.perform(currentLineCaret)
        }
        every { file.fileType } returns fileType
        every { fileType.name } returns "Kotlin"
        every { file.extension } returns "kt"
        every { file.name } returns "App.kt"

        return MultiCaretFixture(editor, document, selectedCaret, currentLineCaret, file)
    }

    private fun withSettings(state: CopySelectionSettings.State, test: () -> Unit) {
        val settings = mockk<CopySelectionSettings>()
        mockkObject(CopySelectionSettings.Companion)
        every { CopySelectionSettings.getInstance() } returns settings
        every { settings.state } returns state

        try {
            test()
        } finally {
            unmockkObject(CopySelectionSettings.Companion)
        }
    }

    private data class MultiCaretFixture(
        val editor: Editor,
        val document: Document,
        val selectedCaret: Caret,
        val currentLineCaret: Caret,
        val file: VirtualFile,
    )
}
