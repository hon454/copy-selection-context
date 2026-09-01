package com.github.hon454.copyselectioncontext

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Integration tests for CopySelectionAction using mock fixtures.
 * This test class demonstrates the test infrastructure setup for the plugin.
 */
class CopySelectionActionTest {

    private lateinit var settings: CopySelectionSettings

    @BeforeTest
    fun setUp() {
        settings = mockk()
        mockkObject(CopySelectionSettings.Companion)
        every { CopySelectionSettings.getInstance() } returns settings
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CopySelectionSettings.Companion)
    }

    @Test
    fun testResolveLineRangeWithMockEditor() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 0
        every { selectionModel.selectionEnd } returns 20
        every { document.getLineNumber(0) } returns 0
        every { document.getLineNumber(20) } returns 2

        val result = CopySelectionUtils.resolveLineRange(editor)

        assertNotNull(result)
        assertTrue(result.contains("-"))
        assertEquals("1-3", result)
    }

    @Test
    fun testDetectLanguageForKotlinFile() {
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()
        every { file.fileType } returns fileType
        every { fileType.name } returns "Kotlin"
        every { file.extension } returns "kt"

        val language = CopySelectionUtils.detectLanguage(file)

        assertEquals("kotlin", language)
    }

    @Test
    fun testFormatOutputWithCode() {
        val result = CopySelectionUtils.formatOutput("src/App.kt", "10-15", "fun main() {}", "kotlin")

        assertNotNull(result)
        assertTrue(result.contains("@src/App.kt#L10-15"))
        assertTrue(result.contains("```kotlin"))
        assertTrue(result.contains("fun main() {"))
    }

    @Test
    fun `custom template includes filename for single caret output`() {
        useFilenameTemplate()
        val file = mockk<VirtualFile>()
        every { file.name } returns "Example.kt"

        val result = TestCopySelectionAction().buildSingleCaretContent(file, mockSingleCaretEditor())

        assertEquals("File: Example.kt", result)
    }

    @Test
    fun `custom template includes filename for multi caret output`() {
        useFilenameTemplate()
        val file = mockk<VirtualFile>()
        val caret = mockk<Caret>()
        every { file.name } returns "Example.kt"

        val result = TestCopySelectionAction().buildMultiCaretContent(file, mockSingleCaretEditor(), caret)

        assertEquals("File: Example.kt", result)
    }

    private fun useFilenameTemplate() {
        every { settings.state } returns CopySelectionSettings.State(
            outputFormat = "template",
            customFormatTemplate = "File: {filename}"
        )
    }

    private fun mockSingleCaretEditor(): Editor {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()
        every { editor.selectionModel } returns selectionModel
        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns false
        every { caretModel.logicalPosition } returns LogicalPosition(4, 0)
        return editor
    }

    private class TestCopySelectionAction : CopySelectionBaseAction() {
        override fun getPath(project: Project, file: VirtualFile): String = file.path

        fun buildSingleCaretContent(file: VirtualFile, editor: Editor): String {
            return buildContent("src/Example.kt", "5", file, editor)
        }

        fun buildMultiCaretContent(file: VirtualFile, editor: Editor, caret: Caret): String {
            return buildContentForCaret("src/Example.kt", "5", 5, 5, file, editor, caret)
        }
    }
}
