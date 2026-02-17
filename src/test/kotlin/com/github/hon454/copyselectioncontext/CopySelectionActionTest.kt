package com.github.hon454.copyselectioncontext

import io.mockk.every
import io.mockk.mockk
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileTypes.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Integration tests for CopySelectionAction using mock fixtures.
 * This test class demonstrates the test infrastructure setup for the plugin.
 */
class CopySelectionActionTest {

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
}
