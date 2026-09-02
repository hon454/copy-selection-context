package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopySelectionUtilsTest {
    private val virtualFileSystem = mockk<VirtualFileSystem>()

    @Test
    fun `joinCaretBlocks joins blocks with double newlines`() {
        val result = CopySelectionUtils.joinCaretBlocks(listOf(" @src/App.kt#L1 ", " @src/App.kt#L3-5 "))

        assertEquals(" @src/App.kt#L1 \n\n @src/App.kt#L3-5 ", result)
    }

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
        val projectDir = mockPath("C:/repo", "repo")
        val sourceDir = mockPath("C:/repo/src", "src", projectDir)
        val file = mockPath("C:/repo/src/App.kt", "App.kt", sourceDir)
        every { project.basePath } returns "C:/repo"
        every { virtualFileSystem.findFileByPath("C:/repo") } returns projectDir

        val result = CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)

        assertEquals("src/App.kt", result)
    }

    @Test
    fun `resolvePath falls back to absolute when outside project`() {
        val projectDir = mockPath("C:/repo", "repo")
        val otherDir = mockPath("D:/other", "other")
        val file = mockPath("D:/other/App.kt", "App.kt", otherDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("D:/other/App.kt", result)
    }

    @Test
    fun `resolveLineNumbers returns selection range`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 10
        every { selectionModel.selectionEnd } returns 20
        every { document.getLineNumber(10) } returns 1
        every { document.getLineNumber(19) } returns 3

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(2, 4), result)
    }

    @Test
    fun `resolveLineNumbers excludes the line at the selection end offset`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 0
        every { selectionModel.selectionEnd } returns 6
        every { document.getLineNumber(0) } returns 0
        every { document.getLineNumber(5) } returns 0

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(1, 1), result)
        verify(exactly = 0) { document.getLineNumber(6) }
    }

    @Test
    fun `resolveLineNumbers handles a selection ending at EOF`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 6
        every { selectionModel.selectionEnd } returns 12
        every { document.getLineNumber(6) } returns 1
        every { document.getLineNumber(11) } returns 1

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(2, 2), result)
    }

    @Test
    fun `resolveLineNumbers excludes the empty line after a trailing newline`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 0
        every { selectionModel.selectionEnd } returns 6
        every { document.getLineNumber(0) } returns 0
        every { document.getLineNumber(5) } returns 0

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(1, 1), result)
    }

    @Test
    fun `resolveLineNumbers uses the current line for an empty selection`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val caretModel = mockk<CaretModel>()

        every { editor.selectionModel } returns selectionModel
        every { editor.caretModel } returns caretModel
        every { selectionModel.hasSelection() } returns false
        every { selectionModel.selectionStart } returns 8
        every { selectionModel.selectionEnd } returns 8
        every { caretModel.logicalPosition } returns LogicalPosition(2, 4)

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(3, 3), result)
    }

    @Test
    fun `resolveLineNumbers returns current line when no selection`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val caretModel = mockk<CaretModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.caretModel } returns caretModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns false
        every { caretModel.logicalPosition } returns LogicalPosition(9, 0)

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(10, 10), result)
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
        val result = ClaudeCodeFormatter().format(FormatContext("C:\\repo\\src\\App.kt", 3, 5))

        assertEquals(" @C:/repo/src/App.kt#L3-5 ", result)
    }

    @Test
    fun `formatOutput renders markdown output`() {
        val result = ClaudeCodeFormatter().format(FormatContext("src/App.kt", 7, 7, "println(1)", "kotlin"))

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
        val projectDir = mockPath("C:/repo", "repo")
        val unicodeDir = mockPath("C:/repo/한글", "한글", projectDir)
        val file = mockPath("C:/repo/한글/App.kt", "App.kt", unicodeDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("한글/App.kt", result)
    }

    @Test
    fun `resolvePath handles special characters in path`() {
        val projectDir = mockPath("C:/repo", "repo")
        val sourceDir = mockPath("C:/repo/src-v2.0", "src-v2.0", projectDir)
        val file = mockPath("C:/repo/src-v2.0/App@test.kt", "App@test.kt", sourceDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("src-v2.0/App@test.kt", result)
    }

    @Test
    fun `resolvePath rejects a sibling whose name shares the project prefix`() {
        val projectDir = mockPath("/repo/app", "app")
        val siblingDir = mockPath("/repo/application", "application")
        val file = mockPath("/repo/application/File.kt", "File.kt", siblingDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("/repo/application/File.kt", result)
    }

    @Test
    fun `resolvePath uses VFS ancestry for Windows separators and case rules`() {
        val projectDir = mockPath("C:\\Repo", "Repo")
        val sourceDir = mockPath("c:\\repo\\src", "src", projectDir)
        val file = mockPath("c:\\repo\\src\\App.kt", "App.kt", sourceDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("src/App.kt", result)
    }

    @Test
    fun `resolvePath preserves an absolute Windows path on another drive`() {
        val projectDir = mockPath("C:\\repo", "repo")
        val otherDir = mockPath("D:\\other", "other")
        val file = mockPath("D:\\other\\App.kt", "App.kt", otherDir)

        val result = resolveRelativePath(projectDir, file)

        assertEquals("D:\\other\\App.kt", result)
    }

    @Test
    fun `resolveLineNumbers returns equal line numbers for one selected line`() {
        val editor = mockk<Editor>()
        val selectionModel = mockk<SelectionModel>()
        val document = mockk<Document>()

        every { editor.selectionModel } returns selectionModel
        every { editor.document } returns document
        every { selectionModel.hasSelection() } returns true
        every { selectionModel.selectionStart } returns 10
        every { selectionModel.selectionEnd } returns 15
        every { document.getLineNumber(10) } returns 5
        every { document.getLineNumber(14) } returns 5

        val result = CopySelectionUtils.resolveLineNumbers(editor)

        assertEquals(Pair(6, 6), result)
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
        val result = ClaudeCodeFormatter().format(FormatContext("src/App.kt", 10, 15, code, "kotlin"))

        // Should use 4+ backticks to avoid breaking markdown
        assertTrue(result.contains("````kotlin"))
        assertTrue(result.contains("````"))
    }

    @Test
    fun `formatOutput handles path with hash character`() {
        val result = ClaudeCodeFormatter().format(FormatContext("src/App#v2.kt", 5, 5))

        assertEquals(" @src/App#v2.kt#L5 ", result)
    }

    @Test
    fun `formatOutput does not render code block with empty code string`() {
        val result = ClaudeCodeFormatter().format(FormatContext("src/App.kt", 3, 3, "", "kotlin"))

        // Empty code should not produce code block
        assertEquals(" @src/App.kt#L3 ", result)
    }

    @Test
    fun `detectLanguage handles case-insensitive file type names`() {
        val file = mockVirtualFile(fileTypeName = "KOTLIN", extension = "kt")

        val result = CopySelectionUtils.detectLanguage(file)

        assertEquals("kotlin", result)
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

    @Test
    fun `formatOutput with trimmed code removes whitespace from code block`() {
        val trimmedCode = "val x = 1"
        val result = ClaudeCodeFormatter().format(FormatContext("src/App.kt", 5, 5, trimmedCode, "kotlin"))

        assertEquals(" @src/App.kt#L5 \n```kotlin\nval x = 1\n```", result)
    }

    @Test
    fun `formatOutput with untrimmed code preserves whitespace in code block`() {
        val untrimmedCode = "  val x = 1  "
        val result = ClaudeCodeFormatter().format(FormatContext("src/App.kt", 5, 5, untrimmedCode, "kotlin"))

        assertEquals(" @src/App.kt#L5 \n```kotlin\n  val x = 1  \n```", result)
    }

    @Test
    fun `trimming only affects code content, not path or line range`() {
        val trimmedCode = "  code  ".trim()

        // Path and line range should not be affected
        val result = ClaudeCodeFormatter().format(FormatContext("src/  App  .kt", 5, 5, trimmedCode, "kotlin"))

        // Path should preserve its whitespace, only code is trimmed
        assertTrue(result.contains("src/  App  .kt"))
        assertTrue(result.contains("code"))
        assertTrue(!result.contains("  code  "))
    }

    private fun mockVirtualFile(fileTypeName: String, extension: String?): VirtualFile {
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()
        every { file.fileType } returns fileType
        every { fileType.name } returns fileTypeName
        every { file.extension } returns extension
        return file
    }

    private fun mockPath(path: String, name: String, parent: VirtualFile? = null): VirtualFile {
        val file = mockk<VirtualFile>()
        every { file.path } returns path
        every { file.name } returns name
        every { file.nameSequence } returns name
        every { file.parent } returns parent
        every { file.fileSystem } returns virtualFileSystem
        return file
    }

    private fun resolveRelativePath(projectDir: VirtualFile, file: VirtualFile): String {
        val project = mockk<Project>()
        val projectPath = projectDir.path
        every { project.basePath } returns projectPath
        every { virtualFileSystem.findFileByPath(projectPath) } returns projectDir
        return CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)
    }
}
