package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Real documents/VFS and local commit objects; modal responses and publication effects are controlled. */
class GitPermalinkFixtureTest : BasePlatformTestCase() {
    private lateinit var directory: Path
    private lateinit var repository: LocalGitRepository
    private lateinit var baseSha: String
    private lateinit var oldSettings: CopySelectionSettings.State
    private var oldClipboard: Transferable? = null
    private val effects = mutableListOf<CopyFeedbackEffect>()
    private var writes = 0
    private val actions = mutableListOf<Harness>()

    override fun setUp() {
        super.setUp()
        oldSettings = CopySelectionSettings.getInstance().state.copy()
        oldClipboard = CopyPasteManager.getInstance().contents
        CopySelectionSettings.getInstance().loadState(CopySelectionSettings.State(enableNotification = false, analyticsEnabled = true))
        CopySelectionAnalytics.getInstance().reset()
        CopyHistoryService.getInstance(project).clear()
        directory = Files.createTempDirectory("copy-selection-head-fixture-").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        repository = LocalGitRepository(directory)
        baseSha = repository.commit("source.txt", ORIGINAL)
        openFile("source.txt")
        CopyPasteManager.getInstance().setContents(StringSelection(SENTINEL))
        writes = 0
        effects.clear()
    }

    override fun tearDown() {
        try {
            actions.forEach { it.lifetime?.close() }
            CopySelectionSettings.getInstance().loadState(oldSettings)
            oldClipboard?.let { CopyPasteManager.getInstance().setContents(it) }
        } finally {
            // The platform clears fixture fields during super.tearDown().
            val repositoryDirectory = directory
            try { super.tearDown() } finally { com.intellij.openapi.util.io.FileUtil.delete(repositoryDirectory.toFile()) }
        }
    }

    fun testCleanHeadCopiesOnceWithoutConfirmationAndPreservesPermalinkPolicy() {
        val action = harness()
        action.start()
        action.background()
        assertUntouched()
        action.ui()
        assertEquals(0, action.confirmations)
        assertEquals(1, writes)
        assertEquals("https://github.com/owner/repo/blob/$baseSha/source.txt#L1", copied())
        assertEquals(listOf(CopyFeedbackEffect.GUTTER, CopyFeedbackEffect.HISTORY, CopyFeedbackEffect.NOTIFICATION, CopyFeedbackEffect.STATUS), effects)
        assertEquals(0, CopySelectionAnalytics.getInstance().getTotalCopyCount())
        assertFalse(action.lifetime!!.isAlive())
        assertNull(action.lifetime!!.editor())
    }

    fun testDirtyCancelIsOneConfirmationWithoutClipboardOrSuccessEffects() {
        edit("inserted\n$ORIGINAL")
        val action = harness().apply { allowConfirm = false }
        action.start(); action.background(); action.ui()
        assertEquals(1, action.confirmations)
        assertEquals(0, action.revalidations)
        assertTrue(action.work.isEmpty())
        assertUntouched()
        assertFalse(action.lifetime!!.isAlive())
    }

    fun testDirtyApprovalKeepsCapturedShaAndCurrentOrderedCaretRangesAfterBackgroundRevalidation() {
        edit("inserted\n$ORIGINAL")
        myFixture.editor.caretModel.moveToLogicalPosition(LogicalPosition(3, 0))
        myFixture.editor.caretModel.addCaret(myFixture.editor.logicalToVisualPosition(LogicalPosition(1, 0)))
        val action = harness()
        action.start(); action.background(); action.ui()
        assertEquals(1, action.confirmations)
        assertUntouched()
        action.background(); action.ui()
        assertEquals(1, action.revalidations)
        assertEquals(1, action.confirmations)
        assertEquals("https://github.com/owner/repo/blob/$baseSha/source.txt#L2\n\n" +
            "https://github.com/owner/repo/blob/$baseSha/source.txt#L4", copied())
        assertEquals(1, writes)
    }

    fun testUntrackedAndRenamedNewPathsFailBeforeConfirmation() {
        repository.write("new.txt", "new file\n")
        openFile("new.txt")
        val untracked = harness()
        untracked.start(); untracked.background(); untracked.ui()
        assertEquals(listOf(GitPermalinkFailureReason.HEAD_PATH_ABSENT), untracked.failures)
        assertEquals(0, untracked.confirmations)
        assertUntouched()
        Files.move(directory.resolve("source.txt"), directory.resolve("renamed.txt"))
        repository.command("add", "--all")
        openFile("renamed.txt")
        val renamed = harness()
        renamed.start(); renamed.background(); renamed.ui()
        assertEquals(listOf(GitPermalinkFailureReason.HEAD_PATH_ABSENT), renamed.failures)
        assertUntouched()
    }

    fun testDocumentChangesDuringLookupInvalidateEvenAfterSameTextAndStampAreRestored() {
        val action = harness()
        action.start(); action.background()
        val document = myFixture.editor.document
        val stamp = document.modificationStamp
        edit("changed\n")
        edit(ORIGINAL)
        // The event latch is authoritative even if an undo-like operation restores the old stamp.
        (document as DocumentEx).setModificationStamp(stamp)
        action.ui()
        assertEquals(0, action.confirmations)
        assertTrue(action.failures.isEmpty())
        assertUntouched()
    }

    fun testDocumentMutationInsideConfirmationAndBeforeFinalUiInvalidatesApproval() {
        edit("dirty\n$ORIGINAL")
        val during = harness().apply { onConfirm = { edit("another edit\n") } }
        during.start(); during.background(); during.ui()
        assertEquals(1, during.confirmations)
        assertTrue(during.work.isEmpty())
        assertUntouched()
        edit("dirty\n$ORIGINAL")
        val after = harness()
        after.start(); after.background(); after.ui(); after.background()
        edit("changed after final HEAD check\n")
        after.ui()
        assertUntouched()
    }

    fun testRenameAndRenameBackDuringConfirmationInvalidatesTheOriginalFileIdentityCapture() {
        edit("dirty\n$ORIGINAL")
        val file = myFixture.file.virtualFile
        val action = harness().apply { onConfirm = {
            WriteCommandAction.runWriteCommandAction(project) { file.rename(this, "changed.txt"); file.rename(this, "source.txt") }
        } }
        action.start(); action.background(); action.ui()
        assertEquals(1, action.confirmations)
        assertEquals("source.txt", file.name)
        assertTrue(action.work.isEmpty())
        assertUntouched()
    }

    fun testHeadChangeInsideConfirmationIsRejectedByTheFinalAuthoritativeBackgroundRead() {
        val otherSha = repository.commit("other.txt", "another commit\n")
        repository.command("update-ref", "refs/heads/main", baseSha)
        edit("dirty\n$ORIGINAL")
        val action = harness().apply { onConfirm = { repository.command("update-ref", "refs/heads/main", otherSha) } }
        action.start(); action.background(); action.ui(); action.background(); action.ui()
        assertEquals(1, action.confirmations)
        assertEquals(1, action.revalidations)
        assertEquals(listOf(GitPermalinkFailureReason.HEAD_CHANGED), action.failures)
        assertUntouched()
    }

    fun testHeadAbaInsideConfirmationDoesNotReuseApproval() {
        val otherSha = repository.commit("other.txt", "another commit\n")
        repository.command("update-ref", "refs/heads/main", baseSha)
        edit("dirty\n$ORIGINAL")
        val action = harness().apply { onConfirm = {
            repository.command("update-ref", "refs/heads/main", otherSha)
            repository.command("update-ref", "refs/heads/main", baseSha)
        } }
        action.start(); action.background(); action.ui(); action.background(); action.ui()
        assertEquals(listOf(GitPermalinkFailureReason.HEAD_CHANGED), action.failures)
        assertUntouched()
    }

    fun testExternalDeleteDuringConfirmationWithoutVfsRefreshInvalidatesApproval() =
        assertExternalSourceChangeRejected { Files.delete(it) }

    fun testExternalRenameDuringConfirmationWithoutVfsRefreshInvalidatesApproval() =
        assertExternalSourceChangeRejected { Files.move(it, it.resolveSibling("moved.txt")) }

    fun testExternalReplacementDuringConfirmationWithoutVfsRefreshInvalidatesApproval() =
        assertExternalSourceChangeRejected {
            val replacement = it.resolveSibling("replacement.txt")
            Files.writeString(replacement, ORIGINAL)
            Files.setLastModifiedTime(replacement, Files.getLastModifiedTime(it))
            Files.move(replacement, it, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }

    private fun assertExternalSourceChangeRejected(change: (Path) -> Unit) {
        edit("dirty\n$ORIGINAL")
        val file = myFixture.file.virtualFile
        val capturedVfsPath = file.path
        val action = harness().apply { onConfirm = {
            change(directory.resolve("source.txt"))
            // Cached VFS identity is still valid; the final BGT source check must detect this.
            assertTrue(file.isValid)
            assertEquals(capturedVfsPath, file.path)
        } }
        action.start(); action.background(); action.ui(); action.background(); action.ui()
        assertEquals(1, action.confirmations)
        assertEquals(1, action.revalidations)
        assertEquals(listOf(GitPermalinkFailureReason.TARGET_UNAVAILABLE), action.failures)
        assertUntouched()
    }

    fun testAnotherProjectsCopyInsideConfirmationSupersedesThePreparedGitRequest() {
        withOtherProject { other ->
            val editor = EditorFactory.getInstance().createEditor(myFixture.editor.document, other)
            try {
                for (route in listOf("standard", "collection", "history", "status")) {
                    edit("dirty\n$ORIGINAL")
                    var newer: String? = null
                    val action = harness().apply { onConfirm = {
                        copyOtherRoute(route, other, editor, myFixture.file.virtualFile)
                        newer = copied()
                    } }
                    action.start(); action.background(); action.ui()
                    assertEquals(newer, copied())
                    assertEquals(1, action.confirmations)
                    assertTrue(action.work.isEmpty())
                    assertTrue(action.failures.isEmpty())
                    assertEquals(0, writes)
                }
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    fun testInjectedMissingGitOnlyFailsPermalinkAndAllFourOtherCopyEntrypointsStillWork() {
        var discoveries = 0
        val action = harness().apply {
            validator = GitHeadTargetValidator(GitProcessRunner(environment = { emptyMap() }, executable = { discoveries++; null }))
        }
        action.start(); action.background(); action.ui()
        assertEquals(listOf(GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE), action.failures)
        assertUntouched()
        for (route in listOf("standard", "collection", "history", "status")) {
            CopyPasteManager.getInstance().setContents(StringSelection(SENTINEL))
            copyOtherRoute(route, project, myFixture.editor, myFixture.file.virtualFile)
            assertFalse(copied().isNullOrBlank())
            assertFalse(copied() == SENTINEL)
        }
        assertEquals(1, discoveries)
    }

    fun testCanceledNewestPermalinkDoesNotReviveAnOlderPreparedRequest() {
        val old = harness()
        old.start(); old.background()
        edit("dirty\n$ORIGINAL")
        val latest = harness().apply { allowConfirm = false }
        latest.start(); latest.background(); latest.ui()
        old.ui()
        assertEquals(1, latest.confirmations)
        assertUntouched()
    }

    fun testNewCopySuppressesAQueuedGitFailureAndCancelsAnUnstartedOldLookup() {
        val failed = harness().apply { prepareFailure = GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE }
        failed.start(); failed.background()
        CopyHistoryPopup.recopy(project, "newer")
        failed.ui()
        assertTrue(failed.failures.isEmpty())
        assertTrue(failed.logs.isEmpty())
        val unstarted = harness()
        unstarted.start()
        CopyHistoryPopup.recopy(project, "newer again")
        assertFailsWith<ProcessCanceledException> { unstarted.background() }
        assertTrue(unstarted.uiWork.isEmpty())
        assertFalse(unstarted.lifetime!!.isAlive())
        assertEquals("newer again", copied())
        assertEquals(0, writes)
    }

    fun testPrepublicationCancellationPropagatesWithoutLoggingFailureOrKeepingTheEditor() {
        for (canceled in listOf(ProcessCanceledException(), CancellationException("injected"))) {
            val action = harness().apply { prepareException = canceled }
            action.start()
            val thrown = assertFailsWith<RuntimeException> { action.background() }
            assertSame(canceled, thrown)
            assertNull(action.lifetime!!.editor())
            assertTrue(action.uiWork.isEmpty())
            assertTrue(action.failures.isEmpty())
            assertTrue(action.logs.isEmpty())
            assertUntouched()
        }
    }

    fun testProgressCancellationWhileUiIsQueuedPreventsPublication() {
        val action = harness()
        action.start(); action.background()
        val indicator = EmptyProgressIndicator()
        action.lifetime!!.observeCancellation(indicator)
        indicator.cancel()
        action.ui()
        assertTrue(action.failures.isEmpty())
        assertTrue(action.logs.isEmpty())
        assertUntouched()
    }

    fun testEditorReleaseInsideConfirmationStopsFollowupAndPublication() {
        val file = myFixture.file.virtualFile
        val editor = EditorFactory.getInstance().createEditor(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), project)
        try {
            edit("dirty\n$ORIGINAL")
            val action = harness().apply { onConfirm = { EditorFactory.getInstance().releaseEditor(editor) } }
            action.start(editor = editor, file = file)
            action.background(); action.ui()
            assertTrue(action.work.isEmpty())
            assertUntouched()
        } finally {
            if (!editor.isDisposed) EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    fun testProjectDisposalInsideConfirmationStopsFollowupAndClearsWeakOwners() {
        withOtherProject { other ->
            edit("dirty\n$ORIGINAL")
            val file = myFixture.file.virtualFile
            val editor = EditorFactory.getInstance().createEditor(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), other)
            try {
                val action = harness(other).apply { onConfirm = {
                    ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) }
                } }
                action.start(other, editor, file); action.background(); action.ui()
                assertNull(action.lifetime!!.editor())
                assertTrue(action.work.isEmpty())
                assertUntouched()
            } finally {
                if (!editor.isDisposed) EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    private fun harness(owner: Project = project) = Harness(owner).also(actions::add)

    private inner class Harness(owner: Project) : CopyGitPermalinkAction() {
        val work = ArrayDeque<() -> Unit>()
        val uiWork = ArrayDeque<() -> Unit>()
        val failures = mutableListOf<GitPermalinkFailureReason>()
        val logs = mutableListOf<GitPermalinkResult.Failure>()
        var lifetime: GitPermalinkLifetime? = null
        var confirmations = 0
        var revalidations = 0
        var allowConfirm = true
        var onConfirm: () -> Unit = {}
        var prepareFailure: GitPermalinkFailureReason? = null
        var prepareException: RuntimeException? = null
        var validator: GitHeadTargetValidator? = null
        private val publisher = CopyResultPublisher.createForTest(object : CopyResultSideEffects {
            override fun writeClipboard(content: String) { writes++; CopyPasteManager.getInstance().setContents(StringSelection(content)) }
            override fun recordAnalytics(format: String, language: String) { effects += CopyFeedbackEffect.ANALYTICS }
            override fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>) {
                effects += CopyFeedbackEffect.GUTTER; CopySelectionHighlighter.update(editor, lineRanges)
            }
            override fun addToHistory(content: String, maxSize: Int) { effects += CopyFeedbackEffect.HISTORY; CopyHistoryService.getInstance(owner).addEntry(content, maxSize) }
            override fun showNotification(content: String, isCurrent: () -> Boolean) { effects += CopyFeedbackEffect.NOTIFICATION }
            override fun updateStatusBar(content: String, isCurrent: () -> Boolean) { effects += CopyFeedbackEffect.STATUS }
            override fun recordReviewEligibleCopy() { effects += CopyFeedbackEffect.REVIEW }
        }, ClipboardRequestCoordinator.getInstance(), { !owner.isDisposed }) { CopyResultSettings(true, "pathline", 10) }

        override fun copyResultPublisher(project: Project) = publisher
        override fun resolveGitRootPath(project: Project, file: VirtualFile): String = directory.toString()
        override fun requestLifetime(project: Project, editor: Editor, file: VirtualFile): GitPermalinkLifetime =
            super.requestLifetime(project, editor, file).also { lifetime = it }
        override fun executeInBackground(project: Project, action: () -> Unit, onCanceled: () -> Unit) { work.addLast(action) }
        override fun invokeOnUiThread(action: () -> Unit) { uiWork.addLast(action) }
        override fun preparePermalink(input: GitPermalinkInput, checkCanceled: () -> Unit): GitPermalinkResult<GitPreparedPermalink> {
            assertFalse(ApplicationManager.getApplication().isDispatchThread)
            prepareException?.let { throw it }
            prepareFailure?.let { return GitPermalinkResult.Failure(it, GitPermalinkDiagnostic(GitPermalinkOperation.READ_HEAD_TARGET)) }
            validator?.let { return it.prepare(input, checkCanceled) }
            return super.preparePermalink(input, checkCanceled)
        }
        override fun revalidateHead(prepared: GitPreparedPermalink, checkCanceled: () -> Unit): GitPermalinkResult<Unit> {
            assertFalse(ApplicationManager.getApplication().isDispatchThread)
            revalidations++
            return super.revalidateHead(prepared, checkCanceled)
        }
        override fun confirmHeadDifference(project: Project): Boolean { confirmations++; onConfirm(); return allowConfirm }
        override fun showPermalinkFailure(project: Project, reason: GitPermalinkFailureReason, isCurrent: () -> Boolean) {
            if (isCurrent()) failures += reason
        }
        override fun logPermalinkFailure(failure: GitPermalinkResult.Failure) { logs += failure }

        fun start(owner: Project = project, editor: Editor = myFixture.editor, file: VirtualFile = myFixture.file.virtualFile) {
            val context = DataContext { key -> when (key) {
                CommonDataKeys.PROJECT.name -> owner
                CommonDataKeys.EDITOR.name -> editor
                CommonDataKeys.VIRTUAL_FILE.name -> file
                else -> null
            } }
            actionPerformed(TestActionEvent.createTestEvent(this, context))
        }

        // Preserve the exact platform cancellation instance across Future.get's wrapper.
        @Suppress("SwallowedException")
        fun background() {
            val action = work.removeFirst()
            // The platform Runnable adapter consumes PCE as normal cancellation. Own the Future
            // inside that adapter so the test can assert what the production lookup actually threw.
            val result = FutureTask<Unit> { action() }
            ApplicationManager.getApplication().executeOnPooledThread(result)
            try { result.get(30, TimeUnit.SECONDS) }
            catch (failed: ExecutionException) { throw requireNotNull(failed.cause) }
        }
        fun ui() = uiWork.removeFirst().invoke()
    }

    private fun openFile(relative: String) {
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory.resolve(relative)))
        myFixture.configureFromExistingVirtualFile(file)
    }

    private fun copyOtherRoute(route: String, owner: Project, editor: Editor, file: VirtualFile) {
        when (route) {
            "standard" -> perform(CopySelectionContextAction(), owner, editor, file)
            "collection" -> {
                val collection = ContextCollectionService.getInstance(owner)
                collection.clear()
                assertIs<ContextCollectionAddResult.Added>(collection.capture(editor, file, PathType.RELATIVE))
                val ui = ArrayDeque<() -> Unit>()
                val output = ContextCollectionOutputService.createForTest(owner, collection, CopySelectionSettings.getInstance(),
                    { action -> FutureTask<Unit> { action() }.also { it.run() } }, ui::addLast)
                val command = ContextCollectionCopyCommand.createForTest(owner, output, CopyResultPublisher.getInstance(owner),
                    { true }, { fail(it) }, ui::addLast)
                try {
                    command.execute()
                    while (ui.isNotEmpty()) ui.removeFirst().invoke()
                } finally { Disposer.dispose(command); Disposer.dispose(output) }
            }
            "history" -> assertIs<CopyPublicationOutcome.Published>(CopyHistoryPopup.recopy(owner, "newer history"))
            "status" -> {
                val widget = CopySelectionStatusBarWidgetFactory().createWidget(owner) as CopySelectionStatusBarWidget
                try {
                    widget.update("newer status")
                    val component = widget.component
                    component.dispatchEvent(MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false))
                } finally { widget.dispose() }
            }
            else -> error("Unknown fixture route")
        }
    }

    private fun perform(action: AnAction, owner: Project, editor: Editor, file: VirtualFile) {
        val context = DataContext { key -> when (key) {
            CommonDataKeys.PROJECT.name -> owner
            CommonDataKeys.EDITOR.name -> editor
            CommonDataKeys.VIRTUAL_FILE.name -> file
            else -> null
        } }
        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
    }
    private fun edit(text: String) = WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(text) }
    private fun copied(): String? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
    private fun assertUntouched() {
        assertEquals(SENTINEL, copied())
        assertEquals(0, writes)
        assertTrue(effects.isEmpty())
        assertTrue(CopyHistoryService.getInstance(project).getEntries().isEmpty())
    }
    private fun withOtherProject(action: (Project) -> Unit) {
        val path = Files.createTempDirectory("copy-selection-head-other-")
        val other = requireNotNull(ProjectManager.getInstance().createProject("head-other", path.toString()))
        try { action(other) } finally {
            if (!other.isDisposed) ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) }
            path.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ORIGINAL = "first\nsecond\nthird\n"
        const val SENTINEL = "clipboard before permalink"
    }
}
