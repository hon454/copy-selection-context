package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

class CopySelectionActionFixtureTest : BasePlatformTestCase() {
    private lateinit var originalSettings: CopySelectionSettings.State
    private lateinit var originalAnalyticsState: CopySelectionAnalytics.State
    private var originalClipboard: Transferable? = null

    override fun setUp() {
        super.setUp()
        originalSettings = CopySelectionSettings.getInstance().state.copy()
        originalAnalyticsState = CopySelectionAnalytics.getInstance().state
        originalClipboard = CopyPasteManager.getInstance().contents
        resetActionState()
    }

    override fun tearDown() {
        try {
            CopySelectionSettings.getInstance().loadState(originalSettings)
            CopySelectionAnalytics.getInstance().loadState(originalAnalyticsState)
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
        settings().enableNotification = true

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

    fun testEnabledAnalyticsRecordsSingleCaretFormatAndLanguageOnce() {
        myFixture.configureByText("analytics.py", "<selection>first line</selection>\nsecond line")
        settings().analyticsEnabled = true

        perform(CopySelectionContextAction())

        assertEquals(
            CopySelectionAnalytics.Snapshot(
                totalCopyCount = 1,
                formatUsage = mapOf("pathline" to 1),
                languageUsage = mapOf(CopySelectionUtils.detectLanguage(myFixture.file.virtualFile) to 1),
            ),
            CopySelectionAnalytics.getInstance().snapshot(),
        )
    }

    fun testEnabledAnalyticsRecordsMultiCaretActionOnlyOnce() {
        myFixture.configureByText("analytics.py", "first line\nsecond line\nthird line")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToLogicalPosition(LogicalPosition(0, 0))
        requireNotNull(
            editor.caretModel.addCaret(editor.logicalToVisualPosition(LogicalPosition(2, 0))),
        )
        settings().analyticsEnabled = true

        perform(CopySelectionContextAction())

        assertEquals(2, editor.caretModel.caretCount)
        assertEquals(
            CopySelectionAnalytics.Snapshot(
                totalCopyCount = 1,
                formatUsage = mapOf("pathline" to 1),
                languageUsage = mapOf(CopySelectionUtils.detectLanguage(myFixture.file.virtualFile) to 1),
            ),
            CopySelectionAnalytics.getInstance().snapshot(),
        )
    }

    fun testDisabledAnalyticsDoesNotWriteAnyCounters() {
        myFixture.configureByText("disabled.py", "content<caret>")
        val analytics = CopySelectionAnalytics.getInstance()
        analytics.recordCopy("existing", "kotlin")
        val before = analytics.snapshot()
        settings().analyticsEnabled = false

        perform(CopySelectionContextAction())

        assertEquals(before, analytics.snapshot())
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

    fun testOversizedResultStillCopiesWithoutEnteringHistory() {
        val oversizedContent = "x".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES + 1)
        myFixture.configureByText("oversized.txt", "<selection>$oversizedContent</selection>")
        settings().apply {
            includeCodeContent = true
            outputFormat = "template"
            customFormatTemplate = "{code}"
        }

        perform(CopySelectionContextAction())

        assertEquals(oversizedContent, clipboardText())
        assertTrue(historyContents().isEmpty())
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

    fun testGitPermalinkActionCoversAsyncMultiCaretSuccessAndFailure() {
        myFixture.configureByText(
            "permalink.txt",
            "first line\nsecond line\nthird line\nfourth line",
        )
        val document = myFixture.editor.document
        val primaryCaret = myFixture.editor.caretModel.primaryCaret
        primaryCaret.moveToLogicalPosition(LogicalPosition(1, 0))
        primaryCaret.setSelection(document.getLineStartOffset(1), document.getLineEndOffset(1))
        val secondCaret = requireNotNull(
            myFixture.editor.caretModel.addCaret(
                myFixture.editor.logicalToVisualPosition(LogicalPosition(3, 0)),
            ),
        )
        secondCaret.setSelection(document.getLineStartOffset(3), document.getLineEndOffset(3))
        val clipboardSentinel = "clipboard-before-permalink"
        CopyPasteManager.getInstance().setContents(StringSelection(clipboardSentinel))
        settings().analyticsEnabled = true
        val resolvedPermalink =
            "https://github.com/owner/repo/blob/abc123/src/permalink.txt#L2\n\n" +
                "https://github.com/owner/repo/blob/abc123/src/permalink.txt#L4"

        val resolvedAction = StubCopyGitPermalinkAction(GitPermalinkResult.Success(resolvedPermalink))
        perform(resolvedAction)

        assertEquals(clipboardSentinel, clipboardText())
        assertEquals(1, resolvedAction.backgroundActions.size)
        assertTrue(resolvedAction.uiActions.isEmpty())

        resolvedAction.runBackgroundAction()

        assertEquals(clipboardSentinel, clipboardText())
        assertEquals(1, resolvedAction.uiActions.size)
        assertEquals(myFixture.file.virtualFile.path, resolvedAction.requestedFilePath)
        assertEquals(listOf(Pair(2, 2), Pair(4, 4)), resolvedAction.requestedLineRanges)

        resolvedAction.runUiAction()

        assertEquals(resolvedPermalink, clipboardText())
        assertEquals(listOf(resolvedPermalink), historyContents())
        assertEquals(2, myFixture.editor.markupModel.allHighlighters.size)
        assertEquals(0, CopySelectionAnalytics.getInstance().getTotalCopyCount())
        assertTrue(resolvedAction.failureReasons.isEmpty())

        myFixture.configureByText("failed-permalink.txt", "first line\nsecond<caret> line")
        CopyHistoryService.getInstance(project).clear()
        val failureSentinel = "clipboard-before-failure"
        CopyPasteManager.getInstance().setContents(StringSelection(failureSentinel))
        val failedAction = StubCopyGitPermalinkAction(
            GitPermalinkResult.Failure(
                reason = GitPermalinkFailureReason.UNRESOLVED_GIT_METADATA,
                diagnostic = GitPermalinkDiagnostic(GitPermalinkOperation.RESOLVE_GIT_METADATA),
            )
        )

        perform(failedAction)
        failedAction.runBackgroundAction()
        failedAction.runUiAction()

        assertEquals(failureSentinel, clipboardText())
        assertTrue(historyContents().isEmpty())
        assertTrue(myFixture.editor.markupModel.allHighlighters.isEmpty())
        assertEquals(0, CopySelectionAnalytics.getInstance().getTotalCopyCount())
        assertEquals(listOf(GitPermalinkFailureReason.UNRESOLVED_GIT_METADATA), failedAction.failureReasons)
        assertEquals(1, failedAction.loggedFailures.size)

        val missingRootSentinel = "clipboard-before-missing-root"
        CopyPasteManager.getInstance().setContents(StringSelection(missingRootSentinel))
        val missingRootAction = StubCopyGitPermalinkAction(
            result = GitPermalinkResult.Success("unused"),
            rootPath = null,
        )

        perform(missingRootAction)

        assertEquals(missingRootSentinel, clipboardText())
        assertTrue(historyContents().isEmpty())
        assertTrue(myFixture.editor.markupModel.allHighlighters.isEmpty())
        assertEquals(0, CopySelectionAnalytics.getInstance().getTotalCopyCount())
        assertTrue(missingRootAction.backgroundActions.isEmpty())
        assertEquals(listOf(GitPermalinkFailureReason.MISSING_VCS_ROOT), missingRootAction.failureReasons)
        assertEquals(1, missingRootAction.loggedFailures.size)
    }

    fun testActionReturnsWithoutSideEffectsWhenRequiredDataKeysAreUnavailable() {
        myFixture.configureByText("missing.txt", "content<caret>")
        settings().enableNotification = true
        val action = CopySelectionContextAction()
        val contexts = listOf(
            actionContext(includeProject = false),
            actionContext(includeEditor = false),
            actionContext(includeFile = false),
        )

        contexts.forEachIndexed { index, context ->
            val sentinel = "unchanged-$index"
            CopyPasteManager.getInstance().setContents(StringSelection(sentinel))
            CopyHistoryService.getInstance(project).clear()

            perform(action, context)

            assertEquals(sentinel, clipboardText())
            assertTrue(historyContents().isEmpty())
        }
    }

    fun testOlderPermalinkCompletionCannotOverwriteNewerStandardCopy() {
        myFixture.configureByText("standard-wins.txt", "first line\nsecond<caret> line")
        val stalePermalink = "https://github.com/owner/repo/blob/old/standard-wins.txt#L2"
        val permalinkAction = StubCopyGitPermalinkAction(GitPermalinkResult.Success(stalePermalink))

        perform(permalinkAction)
        perform(CopySelectionContextAction())
        val standardResult = "${relativePath()}:2"

        permalinkAction.runBackgroundAction()
        permalinkAction.runUiAction()

        assertEquals(standardResult, clipboardText())
        assertEquals(listOf(standardResult), historyContents())
        assertEquals(1, myFixture.editor.markupModel.allHighlighters.size)
    }

    fun testOlderPermalinkCompletionCannotOverwriteNewerPermalinkAcrossActions() {
        myFixture.configureByText("permalink-race.txt", "first<caret> line\nsecond line")
        settings().analyticsEnabled = true
        val firstValue = "https://github.com/owner/repo/blob/first/permalink-race.txt#L1"
        val secondValue = "https://github.com/owner/repo/blob/second/permalink-race.txt#L1"
        val firstAction = StubCopyGitPermalinkAction(GitPermalinkResult.Success(firstValue))
        val secondAction = StubCopyGitPermalinkAction(GitPermalinkResult.Success(secondValue))

        perform(firstAction)
        perform(secondAction)
        firstAction.runBackgroundAction()
        secondAction.runBackgroundAction()
        secondAction.runUiAction()
        firstAction.runUiAction()

        assertEquals(secondValue, clipboardText())
        assertEquals(listOf(secondValue), historyContents())
        assertEquals(1, myFixture.editor.markupModel.allHighlighters.size)
        assertEquals(0, CopySelectionAnalytics.getInstance().getTotalCopyCount())
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
        CopySelectionAnalytics.getInstance().reset()
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

    private class StubCopyGitPermalinkAction(
        private val result: GitPermalinkResult<String>,
        private val rootPath: String? = "/fixture-repo",
    ) : CopyGitPermalinkAction() {
        val backgroundActions = mutableListOf<() -> Unit>()
        val uiActions = mutableListOf<() -> Unit>()
        val failureReasons = mutableListOf<GitPermalinkFailureReason>()
        val loggedFailures = mutableListOf<GitPermalinkResult.Failure>()
        var requestedFilePath: String? = null
        var requestedLineRanges: List<Pair<Int, Int>>? = null

        override fun resolveGitRootPath(project: Project, file: VirtualFile): String? = rootPath

        override fun executeInBackground(action: () -> Unit) {
            backgroundActions.add(action)
        }

        override fun invokeOnUiThread(action: () -> Unit) {
            uiActions.add(action)
        }

        override fun tryBuildPermalink(
            rootPath: String,
            filePath: String,
            lineRanges: List<Pair<Int, Int>>,
        ): GitPermalinkResult<String> {
            requestedFilePath = filePath
            requestedLineRanges = lineRanges
            return result
        }

        override fun showPermalinkFailure(project: Project, reason: GitPermalinkFailureReason) {
            failureReasons.add(reason)
        }

        override fun logPermalinkFailure(failure: GitPermalinkResult.Failure) {
            loggedFailures.add(failure)
        }

        fun runBackgroundAction() {
            backgroundActions.removeAt(0).invoke()
        }

        fun runUiAction() {
            uiActions.removeAt(0).invoke()
        }
    }
}
