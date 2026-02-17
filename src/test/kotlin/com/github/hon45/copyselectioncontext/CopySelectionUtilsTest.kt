package com.github.hon45.copyselectioncontext

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class CopySelectionUtilsTest {
    @Test
    fun `resolvePath returns absolute path`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { file.path } returns "C:/repo/src/App.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.ABSOLUTE)

        assertEquals("C:/repo/src/App.kt", result)
    }

    @Test
    fun `resolvePath returns relative path when inside project`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { project.basePath } returns "C:/repo"
        every { file.path } returns "C:/repo/src/App.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("src/App.kt", result)
    }

    @Test
    fun `resolvePath falls back to absolute when outside project`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { project.basePath } returns "C:/repo"
        every { file.path } returns "D:/other/App.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("D:/other/App.kt", result)
    }

    @Test
    fun `resolveLineRange returns selection range`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 10
        every { selectionModel.selectionEnd } returns 20
        every { document.getLineNumber(10) } returns 1
        every { document.getLineNumber(20) } returns 3

        val result = CopySelectionUtils.resolveLineRange(editor)

        assertEquals("2-4", result)
    }

    @Test
    fun `resolveLineRange returns current line when no selection`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns false
        every { caretModel.logicalPosition } returns LogicalPosition(9, 0)

        val result = CopySelectionUtils.resolveLineRange(editor)

        assertEquals("10", result)
    }

    @Test
    fun `getCodeContent returns selected text`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()

        every { editor.selectionModel } returns selectionModel
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectedText } returns "val x = 1"

        val result = CopySelectionUtils.getCodeContent(editor)

        assertEquals("val x = 1", result)
    }

    @Test
    fun `getCodeContent returns current line text when no selection`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns false
        every { caretModel.logicalPosition } returns LogicalPosition(2, 0)
        every { document.getLineStartOffset(2) } returns 5
        every { document.getLineEndOffset(2) } returns 12
        every { document.getText(any()) } answers {
            val range = firstArg<TextRange>()
            if (range.startOffset == 5 && range.endOffset == 12) "line text" else ""
        }

        val result = CopySelectionUtils.getCodeContent(editor)

        assertEquals("line text", result)
    }

    @Test
    fun `detectLanguage maps known file type`() {
        val file = mockVirtualFile(fileTypeName = "Kotlin", extension = "kt")

        val result = CopySelectionUtils.detectLanguage(file)

        assertEquals("kotlin", result)
    }

    @Test
    fun `detectLanguage falls back to lowercase extension`() {
        val file = mockVirtualFile(fileTypeName = "SomeUnknownType", extension = "TF")

        val result = CopySelectionUtils.detectLanguage(file)

        assertEquals("tf", result)
    }

    @Test
    fun `formatOutput renders plain output`() {
        val result = CopySelectionUtils.formatOutput("C:\\repo\\src\\App.kt", "3-5")

        assertEquals(" @C:/repo/src/App.kt#L3-5 ", result)
    }

    @Test
    fun `formatOutput renders markdown output`() {
        val result = CopySelectionUtils.formatOutput("src/App.kt", "7", "println(1)", "kotlin")

        assertEquals(" @src/App.kt#L7 \n```kotlin\nprintln(1)\n```", result)
    }

    private fun mockVirtualFile(fileTypeName: String, extension: String?): VirtualFile {
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()
        every { file.fileType } returns fileType
        every { fileType.name } returns fileTypeName
        every { file.extension } returns extension
        return file
    }
}
