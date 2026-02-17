package com.github.hon454.copyselectioncontext

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

    @Test
    fun `resolvePath returns absolute path when basePath is null`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { project.basePath } returns null
        every { file.path } returns "C:/repo/src/App.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("C:/repo/src/App.kt", result)
    }

    @Test
    fun `resolvePath handles Korean characters in path`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { project.basePath } returns "C:/repo"
        every { file.path } returns "C:/repo/한글/App.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("한글/App.kt", result)
    }

    @Test
    fun `resolvePath handles special characters in path`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile>()
        every { project.basePath } returns "C:/repo"
        every { file.path } returns "C:/repo/src-v2.0/App@test.kt"

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("src-v2.0/App@test.kt", result)
    }

    @Test
    fun `resolveLineRange returns single line number when startLine equals endLine`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 10
        every { selectionModel.selectionEnd } returns 15
        every { document.getLineNumber(10) } returns 5
        every { document.getLineNumber(15) } returns 5

        val result = CopySelectionUtils.resolveLineRange(editor)

        assertEquals("6", result)
    }

    @Test
    fun `getCodeContent returns empty string when selectedText is null`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()

        every { editor.selectionModel } returns selectionModel
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectedText } returns null

        val result = CopySelectionUtils.getCodeContent(editor)

        assertEquals("", result)
    }

    @Test
    fun `detectLanguage returns empty string when extension is null`() {
        val file = mockVirtualFile(fileTypeName = "SomeUnknownType", extension = null)

        val result = CopySelectionUtils.detectLanguage(file)

        assertEquals("", result)
    }

    @Test
    fun `formatOutput handles code with triple backticks`() {
        val code = "val markdown = \"\"\"\n```\ncode block\n```\n\"\"\""
        val result = CopySelectionUtils.formatOutput("src/App.kt", "10-15", code, "kotlin")

        assertEquals(" @src/App.kt#L10-15 \n```kotlin\n$code\n```", result)
    }

    @Test
    fun `formatOutput handles path with hash character`() {
        val result = CopySelectionUtils.formatOutput("src/App#v2.kt", "5", null, null)

        assertEquals(" @src/App#v2.kt#L5 ", result)
    }

    @Test
    fun `formatOutput renders code block even with empty code string`() {
        val result = CopySelectionUtils.formatOutput("src/App.kt", "3", "", "kotlin")

        assertEquals(" @src/App.kt#L3 \n```kotlin\n\n```", result)
    }

    @Test
    fun `detectLanguage handles case-insensitive file type names`() {
        val file = mockVirtualFile(fileTypeName = "KOTLIN", extension = "kt")

        val result = CopySelectionUtils.detectLanguage(file)

        assertEquals("kotlin", result)
    }

    @Test
    fun `getCodeContent handles multiline selected text with special characters`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val multilineText = "fun test() {\n    println(\"한글\")\n}"

        every { editor.selectionModel } returns selectionModel
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectedText } returns multilineText

        val result = CopySelectionUtils.getCodeContent(editor)

        assertEquals(multilineText, result)
    }

    @Test
    fun `detectLanguage maps Go`() {
        val file = mockVirtualFile(fileTypeName = "Go", extension = "go")
        assertEquals("go", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Rust`() {
        val file = mockVirtualFile(fileTypeName = "Rust", extension = "rs")
        assertEquals("rust", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps C`() {
        val file = mockVirtualFile(fileTypeName = "C", extension = "c")
        assertEquals("c", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps C++`() {
        val file = mockVirtualFile(fileTypeName = "C++", extension = "cpp")
        assertEquals("cpp", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Swift`() {
        val file = mockVirtualFile(fileTypeName = "Swift", extension = "swift")
        assertEquals("swift", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Dart`() {
        val file = mockVirtualFile(fileTypeName = "Dart", extension = "dart")
        assertEquals("dart", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Ruby`() {
        val file = mockVirtualFile(fileTypeName = "Ruby", extension = "rb")
        assertEquals("ruby", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps PHP`() {
        val file = mockVirtualFile(fileTypeName = "PHP", extension = "php")
        assertEquals("php", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Groovy`() {
        val file = mockVirtualFile(fileTypeName = "Groovy", extension = "groovy")
        assertEquals("groovy", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Scala`() {
        val file = mockVirtualFile(fileTypeName = "Scala", extension = "scala")
        assertEquals("scala", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Perl`() {
        val file = mockVirtualFile(fileTypeName = "Perl", extension = "pl")
        assertEquals("perl", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Lua`() {
        val file = mockVirtualFile(fileTypeName = "Lua", extension = "lua")
        assertEquals("lua", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps R`() {
        val file = mockVirtualFile(fileTypeName = "R", extension = "r")
        assertEquals("r", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps JSX Harmony`() {
        val file = mockVirtualFile(fileTypeName = "JSX Harmony", extension = "jsx")
        assertEquals("jsx", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps TypeScript JSX`() {
        val file = mockVirtualFile(fileTypeName = "TypeScript JSX", extension = "tsx")
        assertEquals("tsx", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Vue`() {
        val file = mockVirtualFile(fileTypeName = "Vue.js", extension = "vue")
        assertEquals("vue", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Svelte`() {
        val file = mockVirtualFile(fileTypeName = "Svelte", extension = "svelte")
        assertEquals("svelte", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps SCSS`() {
        val file = mockVirtualFile(fileTypeName = "SCSS", extension = "scss")
        assertEquals("scss", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps LESS`() {
        val file = mockVirtualFile(fileTypeName = "LESS", extension = "less")
        assertEquals("less", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps TOML`() {
        val file = mockVirtualFile(fileTypeName = "TOML", extension = "toml")
        assertEquals("toml", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Dockerfile`() {
        val file = mockVirtualFile(fileTypeName = "Dockerfile", extension = "dockerfile")
        assertEquals("dockerfile", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps HCL`() {
        val file = mockVirtualFile(fileTypeName = "HCL", extension = "hcl")
        assertEquals("hcl", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps GraphQL`() {
        val file = mockVirtualFile(fileTypeName = "GraphQL", extension = "graphql")
        assertEquals("graphql", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `detectLanguage maps Protocol Buffer`() {
        val file = mockVirtualFile(fileTypeName = "Protocol Buffer", extension = "proto")
        assertEquals("protobuf", CopySelectionUtils.detectLanguage(file))
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
