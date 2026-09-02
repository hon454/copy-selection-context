package com.github.hon454.copyselectioncontext

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `detectLanguage returns Kotlin language tag`() {
        val file = mockk<VirtualFile>()
        val fileType = mockk<FileType>()
        every { file.fileType } returns fileType
        every { fileType.name } returns "Kotlin"
        every { file.extension } returns "kt"

        assertEquals("kotlin", CopySelectionUtils.detectLanguage(file))
    }

    @Test
    fun `Claude output with code remains byte-for-byte stable`() {
        val result = ClaudeCodeFormatter().format(
            FormatContext(
                path = "src/App.kt",
                startLine = 10,
                endLine = 15,
                code = "fun main() {}",
                language = "kotlin",
            ),
        )

        assertEquals(" @src/App.kt#L10-15 \n```kotlin\nfun main() {}\n```", result)
    }

    @Test
    fun `custom template uses filename from captured context`() {
        every { settings.state } returns CopySelectionSettings.State(
            outputFormat = "template",
            customFormatTemplate = "File: {filename}",
        )
        val file = mockk<VirtualFile>()
        val context = SelectionContext(
            path = "src/Example.kt",
            file = file,
            startLine = 5,
            endLine = 5,
            code = "example()",
            language = "kotlin",
            filename = "Example.kt",
        )

        val result = TestCopySelectionAction().buildCapturedContent(listOf(context, context))

        assertEquals("File: Example.kt\n\nFile: Example.kt", result.content)
        assertEquals(listOf(Pair(5, 5), Pair(5, 5)), result.lineRanges)
    }

    private class TestCopySelectionAction : CopySelectionBaseAction() {
        override fun getPath(project: Project, file: VirtualFile): String = file.path
    }
}
