package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

class CopySelectionActionFixtureTest : BasePlatformTestCase() {
    private lateinit var originalSettings: CopySelectionSettings.State
    private var originalClipboard: Transferable? = null

    override fun setUp() {
        super.setUp()
        originalSettings = CopySelectionSettings.getInstance().state.copy()
        originalClipboard = CopyPasteManager.getInstance().contents
        resetActionState()
    }

    override fun tearDown() {
        try {
            CopySelectionSettings.getInstance().loadState(originalSettings)
            CopyPasteManager.getInstance().setContents(originalClipboard ?: StringSelection(""))
        } finally {
            super.tearDown()
        }
    }

    fun testMainActionCopiesSingleSelectionAndUpdatesLocalSideEffects() {
        myFixture.configureByText(
            "single.txt",
            "<selection>first line\nsecond</selection> line\nthird line",
        )

        perform(CopySelectionContextAction())

        val expected = "${relativePath()}:1-2"
        assertEquals(expected, clipboardText())
        assertEquals(listOf(expected), historyContents())
        assertEquals(1, myFixture.editor.markupModel.allHighlighters.size)
        assertTrue(
            myFixture.editor.markupModel.allHighlighters.single().gutterIconRenderer is
                CopySelectionGutterIconRenderer,
        )
    }

    fun testMainActionFallsBackToCurrentLineWithoutSelection() {
        myFixture.configureByText("fallback.txt", "first line\nsecond<caret> line\nthird line")

        perform(CopySelectionContextAction())

        assertEquals("${relativePath()}:2", clipboardText())
    }

    fun testMainActionFormatsEveryCaretIndependently() {
        myFixture.configureByText("multiple.txt", "first line\nsecond line\nthird line\nfourth line")
        val document = myFixture.editor.document
        val primaryCaret = myFixture.editor.caretModel.primaryCaret
        primaryCaret.moveToLogicalPosition(LogicalPosition(0, 0))
        primaryCaret.setSelection(document.getLineStartOffset(0), document.getLineEndOffset(0))
        val secondCaret = requireNotNull(
            myFixture.editor.caretModel.addCaret(
                myFixture.editor.logicalToVisualPosition(LogicalPosition(2, 0)),
            ),
        )
        secondCaret.setSelection(document.getLineStartOffset(2), document.getLineEndOffset(2))

        perform(CopySelectionContextAction())

        val path = relativePath()
        assertEquals("$path:1\n\n$path:3", clipboardText())
        assertEquals(listOf("$path:1\n\n$path:3"), historyContents())
    }

    fun testMainActionIncludesTrimmedCodeWithCustomTemplate() {
        myFixture.configureByText("custom.txt", "<selection>  selected code  </selection>\nignored")
        settings().apply {
            includeCodeContent = true
            codeTrimming = true
            outputFormat = "template"
            customFormatTemplate =
                "[{path}] file={filename}; lines={range}; lang={lang}; code={code}"
        }

        perform(CopySelectionContextAction())

        assertEquals(
            "[${relativePath()}] file=custom.txt; lines=1; lang=txt; code=selected code",
            clipboardText(),
        )
    }

    fun testExplicitActionsExecuteAgainstFixtureEditor() {
        myFixture.configureByText("explicit.txt", "alpha\n<selection>beta\ngamma</selection>\ndelta")
        val relativePath = relativePath()
        val absolutePath = myFixture.file.virtualFile.path

        perform(CopyRelativePathAction())
        assertEquals("$relativePath:2-3", clipboardText())

        perform(CopyAbsolutePathAction())
        assertEquals("$absolutePath:2-3", clipboardText())

        perform(CopyWithCodeContentAction())
        assertEquals(
            "$relativePath:2-3\n```txt\nbeta\ngamma\n```",
            clipboardText(),
        )
    }

    fun testActionReturnsWithoutSideEffectsWhenRequiredDataKeysAreUnavailable() {
        myFixture.configureByText("missing.txt", "content<caret>")
        val contexts = listOf(
            actionContext(includeProject = false),
            actionContext(includeEditor = false),
            actionContext(includeFile = false),
        )

        contexts.forEachIndexed { index, context ->
            val sentinel = "unchanged-$index"
            CopyPasteManager.getInstance().setContents(StringSelection(sentinel))
            CopyHistoryService.getInstance(project).clear()

            perform(CopySelectionContextAction(), context)

            assertEquals(sentinel, clipboardText())
            assertTrue(historyContents().isEmpty())
        }
    }

    private fun resetActionState() {
        CopySelectionSettings.getInstance().loadState(
            CopySelectionSettings.State(
                defaultPathType = PathType.RELATIVE,
                includeCodeContent = false,
                enableNotification = false,
                outputFormat = "pathline",
                codeTrimming = false,
                copyHistorySize = 10,
                customFormatTemplate = "",
                analyticsEnabled = false,
            ),
        )
        CopyHistoryService.getInstance(project).clear()
        CopyPasteManager.getInstance().setContents(StringSelection("fixture-initial"))
    }

    private fun settings(): CopySelectionSettings.State = CopySelectionSettings.getInstance().state

    private fun relativePath(): String =
        CopySelectionUtils.resolvePath(project, myFixture.file.virtualFile, PathType.RELATIVE)

    private fun historyContents(): List<String> =
        CopyHistoryService.getInstance(project).getEntries().map { it.content }

    private fun clipboardText(): String? =
        CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)

    private fun perform(action: AnAction, context: DataContext = actionContext()) {
        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
    }

    private fun actionContext(
        includeProject: Boolean = true,
        includeEditor: Boolean = true,
        includeFile: Boolean = true,
    ): DataContext = DataContext { dataId ->
        when (dataId) {
            CommonDataKeys.PROJECT.name -> project.takeIf { includeProject }
            CommonDataKeys.EDITOR.name -> myFixture.editor.takeIf { includeEditor }
            CommonDataKeys.VIRTUAL_FILE.name -> myFixture.file.virtualFile.takeIf { includeFile }
            else -> null
        }
    }
}
