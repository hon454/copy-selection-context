package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretAction
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class CopySelectionCapturePolicyTest {
    @Test
    fun `path-only actionPerformed paths never acquire selected or current-line payloads`() {
        val settings = CopySelectionSettings.State(
            defaultPathType = PathType.ABSOLUTE,
            includeCodeContent = false,
            outputFormat = "pathline",
        )

        withSettings(settings) {
            listOf(
                CopySelectionContextAction(),
                CopyRelativePathAction(),
                CopyAbsolutePathAction(),
            ).forEach { action ->
                val fixture = actionFixture()

                action.actionPerformed(fixture.event)

                assertEquals(
                    "/project/src/App.kt:1-2\n\n/project/src/App.kt:5",
                    fixture.published.captured.content,
                )
                verify(exactly = 0) { fixture.selectedCaret.selectedText }
                verify(exactly = 0) { fixture.document.getLineStartOffset(any()) }
                verify(exactly = 0) { fixture.document.getLineEndOffset(any()) }
                verify(exactly = 0) { fixture.document.getText(any<TextRange>()) }
            }
        }
    }

    @Test
    fun `code actionPerformed paths acquire each caret payload exactly once`() {
        listOf(
            CopySelectionSettings.State(
                defaultPathType = PathType.ABSOLUTE,
                includeCodeContent = true,
                outputFormat = "pathline",
            ) to CopySelectionContextAction(),
            CopySelectionSettings.State(
                defaultPathType = PathType.ABSOLUTE,
                includeCodeContent = false,
                outputFormat = "pathline",
            ) to CopyWithCodeContentAction(),
        ).forEach { (settings, action) ->
            withSettings(settings) {
                val fixture = actionFixture()

                action.actionPerformed(fixture.event)

                assertEquals(
                    "/project/src/App.kt:1-2\n```kotlin\nfirst()\n```\n\n" +
                        "/project/src/App.kt:5\n```kotlin\nsecond()\n```",
                    fixture.published.captured.content,
                )
                verify(exactly = 1) { fixture.selectedCaret.selectedText }
                verify(exactly = 1) { fixture.document.getLineStartOffset(4) }
                verify(exactly = 1) { fixture.document.getLineEndOffset(4) }
                verify(exactly = 1) { fixture.document.getText(TextRange(40, 48)) }
            }
        }
    }

    @Test
    fun `custom code variable follows the captured main-action include setting`() {
        listOf(
            Triple(false, false, "" to ""),
            Triple(false, true, "" to ""),
            Triple(true, false, "  first()  " to "  second()  "),
            Triple(true, true, "first()" to "second()"),
        ).forEach { (includeCode, trimCode, expected) ->
            val settings = CopySelectionSettings.State(
                defaultPathType = PathType.ABSOLUTE,
                includeCodeContent = includeCode,
                outputFormat = "template",
                customFormatTemplate = "{filename}:{range}:{code}",
                codeTrimming = trimCode,
            )
            withSettings(settings) {
                val fixture = actionFixture(
                    selectedCode = "  first()  ",
                    currentLineCode = "  second()  ",
                )

                CopySelectionContextAction().actionPerformed(fixture.event)

                assertEquals(
                    "App.kt:1-2:${expected.first}\n\nApp.kt:5:${expected.second}",
                    fixture.published.captured.content,
                )
            }
        }
    }

    @Test
    fun `custom template without code does not suppress an enabled code snapshot`() {
        val settings = CopySelectionSettings.State(
            defaultPathType = PathType.ABSOLUTE,
            includeCodeContent = true,
            outputFormat = "template",
            customFormatTemplate = "{path}:{range}",
        )
        withSettings(settings) {
            val fixture = actionFixture()

            CopySelectionContextAction().actionPerformed(fixture.event)

            assertEquals(
                "/project/src/App.kt:1-2\n\n/project/src/App.kt:5",
                fixture.published.captured.content,
            )
            verify(exactly = 1) { fixture.selectedCaret.selectedText }
            verify(exactly = 1) { fixture.document.getText(TextRange(40, 48)) }
        }
    }

    private fun actionFixture(
        selectedCode: String = "first()",
        currentLineCode: String = "second()",
    ): ActionFixture {
        val event = mockk<AnActionEvent>()
        val project = mockk<Project>()
        val editor = mockk<Editor>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()
        val selectedCaret = mockk<Caret>()
        val currentLineCaret = mockk<Caret>()
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()
        val publisher = mockk<CopyResultPublisher>()
        val request = CopyResultRequest(1)
        val published = slot<CopyResult>()

        every { event.getData(CommonDataKeys.PROJECT) } returns project
        every { event.getData(CommonDataKeys.EDITOR) } returns editor
        every { event.getData(CommonDataKeys.VIRTUAL_FILE) } returns file
        every { project.basePath } returns null
        every { project.getService(CopyResultPublisher::class.java) } returns publisher
        every { publisher.beginRequest() } returns request
        every { publisher.publishIfCurrent(request, capture(published), CopyResultPolicy.STANDARD) } returns true
        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { selectedCaret.hasSelection() } returns true
        every { selectedCaret.selectionStart } returns 0
        every { selectedCaret.selectionEnd } returns 12
        every { selectedCaret.selectedText } returns selectedCode
        every { currentLineCaret.hasSelection() } returns false
        every { currentLineCaret.logicalPosition } returns LogicalPosition(4, 0)
        every { document.getLineNumber(0) } returns 0
        every { document.getLineNumber(11) } returns 1
        every { document.getLineStartOffset(4) } returns 40
        every { document.getLineEndOffset(4) } returns 48
        every { document.getText(TextRange(40, 48)) } returns currentLineCode
        every { caretModel.runForEachCaret(any<CaretAction>()) } answers {
            val action = firstArg<CaretAction>()
            action.perform(selectedCaret)
            action.perform(currentLineCaret)
        }
        every { file.path } returns "/project/src/App.kt"
        every { file.fileType } returns fileType
        every { fileType.name } returns "Kotlin"
        every { file.extension } returns "kt"
        every { file.name } returns "App.kt"

        return ActionFixture(event, document, selectedCaret, published)
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

    private data class ActionFixture(
        val event: AnActionEvent,
        val document: Document,
        val selectedCaret: Caret,
        val published: CapturingSlot<CopyResult>,
    )
}
