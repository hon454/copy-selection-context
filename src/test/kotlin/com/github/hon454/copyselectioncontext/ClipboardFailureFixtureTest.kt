package com.github.hon454.copyselectioncontext

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Consumer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.util.concurrent.FutureTask

/** Real entry points and platform owners; writes fail before touching the real clipboard. */
class ClipboardFailureFixtureTest : BasePlatformTestCase() {
    private enum class Route { STANDARD, GIT, COLLECTION, HISTORY, STATUS }
    private data class Notice(val project: Project?, val text: String, val type: NotificationType)
    private lateinit var originalSettings: CopySelectionSettings.State
    private lateinit var realClipboard: CopyPasteManager
    private var originalClipboard: Transferable? = null
    private val notices = mutableListOf<Notice>()
    private var failWrite = true
    private var attempts = 0
    private var attemptedContent = ""
    private var beforeNotification: () -> Unit = {}

    override fun setUp() {
        super.setUp()
        originalSettings = CopySelectionSettings.getInstance().state.copy()
        CopySelectionSettings.getInstance().loadState(CopySelectionSettings.State(
            enableNotification = false, analyticsEnabled = true, outputFormat = "pathline"))
        myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("private-source.kt", "private code credential").virtualFile)
        realClipboard = CopyPasteManager.getInstance()
        originalClipboard = realClipboard.contents
        val clipboard = mockk<CopyPasteManager>(relaxed = true)
        every { clipboard.setContents(any()) } answers {
            write(firstArg<Transferable>().getTransferData(DataFlavor.stringFlavor) as String)
        }
        mockkStatic(CopyPasteManager::class)
        every { CopyPasteManager.getInstance() } returns clipboard

        val manager = mockk<NotificationGroupManager>()
        val group = mockk<NotificationGroup>()
        every { manager.getNotificationGroup("CopySelectionContext") } returns group
        every { group.createNotification(any<String>(), any<NotificationType>()) } answers {
            val text = firstArg<String>()
            val type = secondArg<NotificationType>()
            beforeNotification()
            mockk<Notification>().also { notification ->
                every { notification.notify(any()) } answers { notices += Notice(firstArg(), text, type) }
            }
        }
        mockkStatic(NotificationGroupManager::class)
        every { NotificationGroupManager.getInstance() } returns manager
    }

    override fun tearDown() {
        try {
            unmockkStatic(CopyPasteManager::class)
            unmockkStatic(NotificationGroupManager::class)
            realClipboard.setContents(originalClipboard ?: StringSelection(""))
            CopySelectionSettings.getInstance().loadState(originalSettings)
        } finally { super.tearDown() }
    }

    fun testFiveEntryPointsReportOnceWithEitherSuccessPreferenceAndZeroSuccessEffects() {
        for (route in Route.entries) for (enabled in listOf(false, true)) {
            reset(enabled)
            Harness(project).use { harness ->
                val history = CopyHistoryService.getInstance(project).state
                val captured = harness.collection.snapshot()
                harness.start(route)
                assertEquals("$route / $enabled", 1, attempts)
                assertEquals("sentinel", copied())
                assertTrue(harness.successEffects.isEmpty())
                assertEquals(history, CopyHistoryService.getInstance(project).state)
                assertSame(captured, harness.collection.snapshot())
                assertEquals(1, harness.errors.size)
                assertTrue(notices.isEmpty())
                harness.flushErrors()
                harness.flushErrors()
                assertEquals(listOf(Notice(project, CopySelectionBundle.message("notification.clipboard.failed"), NotificationType.ERROR)), notices)
                assertTrue(harness.collectionErrors.isEmpty())
                assertEquals(1, attempts)
                assertFalse(notices.single().text.contains("private"))
                assertFalse(notices.single().text.contains("credential"))
            }
        }
    }

    fun testFiveQueuedErrorsLoseToOtherProjectCopyAndIdenticalContentAba() {
        withOtherProject { other ->
            for (route in Route.entries) {
                reset()
                Harness(project).use { harness ->
                    harness.start(route)
                    val a = attemptedContent
                    failWrite = false
                    CopyHistoryPopup.recopy(other, "B")
                    CopyHistoryPopup.recopy(project, a)
                    harness.flushErrors()
                    assertEquals(a, copied())
                    assertTrue("$route stale error", notices.isEmpty())
                    assertTrue(harness.successEffects.isEmpty())
                    assertEquals(3, attempts)
                }
            }
        }
    }

    fun testNewCancelledOrFailedRequestNeverRevivesAnyQueuedError() {
        withOtherProject { other ->
            for (route in Route.entries) for (newFailure in listOf(false, true)) {
                reset()
                Harness(project).use { a -> Harness(other).use { b ->
                    a.start(route)
                    if (newFailure) b.start(Route.HISTORY) else b.publisher.beginRequest()
                    a.flushErrors()
                    assertTrue(notices.isEmpty())
                    b.flushErrors()
                    assertEquals(if (newFailure) 1 else 0, notices.size)
                    assertTrue(notices.all { it.project === other })
                    assertEquals("sentinel", copied())
                } }
            }
        }
    }

    fun testProjectDisposalSuppressesQueuedErrorFromEveryEntryPoint() {
        for (route in Route.entries) {
            reset()
            withOtherProject { other ->
                Harness(other).use { harness ->
                    harness.start(route)
                    disposeProject(other)
                    harness.flushErrors()
                    assertTrue("$route after project disposal", notices.isEmpty())
                    assertEquals("sentinel", copied())
                }
            }
        }
    }

    fun testWidgetDisposalSuppressesQueuedErrorAndReleasesProjectWhilePopupCloseDoesNot() {
        reset()
        Harness(project).use { harness ->
            harness.start(Route.STATUS)
            harness.widget.dispose()
            harness.widget.update("must not revive")
            harness.clickWidget()
            harness.flushErrors()
            assertTrue(notices.isEmpty())
            assertEquals(1, attempts)
            val projectField = CopySelectionStatusBarWidget::class.java.getDeclaredField("project").apply { isAccessible = true }
            assertNull(projectField.get(harness.widget))
        }
        reset()
        Harness(project).use { harness ->
            harness.start(Route.HISTORY)
            assertTrue(harness.historyPopupClosed)
            harness.flushErrors()
            assertEquals(1, notices.size)
        }
    }

    fun testNotificationConstructionCannotShowAnErrorAfterReentrantNewCopy() {
        withOtherProject { other ->
            for (route in Route.entries) {
                reset()
                Harness(project).use { harness ->
                    harness.start(route)
                    beforeNotification = {
                        beforeNotification = {}
                        failWrite = false
                        CopyHistoryPopup.recopy(other, "newest")
                    }
                    harness.flushErrors()
                    assertTrue(notices.isEmpty())
                    assertEquals("newest", copied())
                }
            }
        }
    }

    fun testSuccessfulHistoryAndWidgetCopiesRemainClipboardOnly() {
        for (route in listOf(Route.HISTORY, Route.STATUS)) for (enabled in listOf(false, true)) {
            reset(enabled)
            failWrite = false
            Harness(project).use { harness ->
                val history = CopyHistoryService.getInstance(project).state
                val analytics = CopySelectionAnalytics.getInstance().snapshot()
                val gutter = myFixture.editor.markupModel.allHighlighters.toList()
                harness.start(route)
                assertEquals("history/status payload", copied())
                assertEquals(history, CopyHistoryService.getInstance(project).state)
                assertEquals(analytics, CopySelectionAnalytics.getInstance().snapshot())
                assertEquals(gutter, myFixture.editor.markupModel.allHighlighters.toList())
                assertTrue(harness.successEffects.isEmpty())
                assertTrue(harness.errors.isEmpty())
                assertTrue(notices.isEmpty())
                assertEquals(1, attempts)
            }
        }
    }

    fun testPublishedWithFeedbackFailureKeepsSuccessPreferenceAndNeverReportsOrRetries() {
        for (route in listOf(Route.STANDARD, Route.GIT, Route.COLLECTION)) for (enabled in listOf(false, true)) {
            reset(enabled)
            failWrite = false
            Harness(project).use { harness ->
                harness.failFeedback = true
                harness.start(route)
                assertFalse("sentinel" == copied())
                assertEquals(1, attempts)
                assertTrue(harness.errors.isEmpty())
                assertTrue(harness.collectionErrors.isEmpty())
                assertEquals(if (enabled) 1 else 0, notices.size)
                assertTrue(notices.all { it.type == NotificationType.INFORMATION })
                assertEquals(1, harness.successEffects.count { it == CopyFeedbackEffect.STATUS })
            }
        }
    }

    fun testDelayedGitAndCollectionCompletionCannotWriteAfterAnotherProjectCopy() {
        withOtherProject { other ->
            for (route in listOf(Route.GIT, Route.COLLECTION)) {
                reset()
                Harness(project).use { harness ->
                    harness.start(route, finish = false)
                    failWrite = false
                    CopyHistoryPopup.recopy(other, "newest")
                    harness.flushWork()
                    harness.flushErrors()
                    assertEquals("newest", copied())
                    assertEquals(1, attempts)
                    assertTrue(notices.isEmpty())
                    assertTrue(harness.successEffects.isEmpty())
                }
            }
        }
    }

    fun testCollectionOverflowInvalidationAndConfirmationCancelKeepTheirExistingBoundary() {
        reset()
        Harness(project).use { harness ->
            WriteCommandAction.runWriteCommandAction(project) { harness.editor.document.setText("x".repeat(262144)) }
            harness.collection.clear()
            harness.collection.capture(harness.editor, harness.file, PathType.RELATIVE)
            CopySelectionSettings.getInstance().state.apply { outputFormat = "template"; customFormatTemplate = "{code}".repeat(17) }
            harness.start(Route.COLLECTION)
            assertEquals(listOf(CopySelectionBundle.message("collection.copy.overflow")), harness.collectionErrors)
            assertTrue(harness.errors.isEmpty())
            assertEquals(0, attempts)
        }
        reset()
        Harness(project).use { harness ->
            WriteCommandAction.runWriteCommandAction(project) { harness.editor.document.setText("a second snapshot") }
            harness.collection.capture(harness.editor, harness.file, PathType.RELATIVE)
            assertEquals(2, harness.collection.snapshot().items.size)
            harness.collection.setIncludeCode(false)
            harness.allowConfirm = false
            harness.start(Route.COLLECTION)
            assertEquals(1, harness.confirmations)
            assertTrue(harness.collectionErrors.isEmpty())
            assertTrue(harness.errors.isEmpty())
            assertEquals(0, attempts)
            harness.allowConfirm = true
            harness.duringConfirmation = { harness.collection.setIncludeCode(true) }
            harness.start(Route.COLLECTION)
            assertEquals(listOf(CopySelectionBundle.message("collection.copy.invalidated")), harness.collectionErrors)
            assertTrue(harness.errors.isEmpty())
            assertEquals(0, attempts)
        }
    }

    private fun reset(enabled: Boolean = false) {
        notices.clear()
        attempts = 0
        failWrite = true
        beforeNotification = {}
        realClipboard.setContents(StringSelection("sentinel"))
        CopySelectionSettings.getInstance().loadState(CopySelectionSettings.State(
            enableNotification = enabled, analyticsEnabled = true, outputFormat = "pathline"))
    }

    private fun write(content: String) {
        attempts++
        attemptedContent = content
        if (failWrite) throw IllegalStateException("private code /private-source.kt https://user:credential@host")
        realClipboard.setContents(StringSelection(content))
    }

    private fun copied(): String? = realClipboard.getContents(DataFlavor.stringFlavor)

    private fun withOtherProject(action: (Project) -> Unit) {
        val other = requireNotNull(ProjectManager.getInstance().createProject("clipboard-B", myFixture.tempDirPath + "/clipboard-B"))
        try { action(other) } finally { if (!other.isDisposed) disposeProject(other) }
    }

    private fun disposeProject(owner: Project) = ApplicationManager.getApplication().runWriteAction { Disposer.dispose(owner) }

    private inner class Harness(val owner: Project) : AutoCloseable {
        private val scope = Disposer.newDisposable("Clipboard failure fixture")
        private val work = ArrayDeque<() -> Unit>()
        private val jobs = ArrayDeque<FutureTask<Unit>>()
        val errors = ArrayDeque<() -> Unit>()
        val collectionErrors = mutableListOf<String>()
        val successEffects = mutableListOf<CopyFeedbackEffect>()
        var failFeedback = false
        var allowConfirm = true
        var confirmations = 0
        var duringConfirmation: () -> Unit = {}
        var historyPopupClosed = false
        val file: VirtualFile = if (owner === project) myFixture.file.virtualFile else
            LightVirtualFile("private-source-B.txt", PlainTextFileType.INSTANCE, "private code credential")
        val editor: Editor = if (owner === project) myFixture.editor else
            EditorFactory.getInstance().createEditor(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), owner)
        val collection = ContextCollectionService.getInstance(owner)
        private val coordinator = ClipboardRequestCoordinator.getInstance()
        private val reporter = CopyFailureReporter.createForTest(coordinator, { !owner.isDisposed }, errors::addLast,
            { current -> CopySelectionNotifier.notifyClipboardFailure(owner, current) })
        val publisher = CopyResultPublisher.createForTest(object : CopyResultSideEffects {
            override fun writeClipboard(content: String) = write(content)
            override fun recordAnalytics(format: String, language: String) { successEffects += CopyFeedbackEffect.ANALYTICS }
            override fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>) { successEffects += CopyFeedbackEffect.GUTTER }
            override fun addToHistory(content: String, maxSize: Int) { successEffects += CopyFeedbackEffect.HISTORY }
            override fun showNotification(content: String, isCurrent: () -> Boolean) {
                successEffects += CopyFeedbackEffect.NOTIFICATION
                if (isCurrent()) CopySelectionNotifier.notify(owner, content)
            }
            override fun updateStatusBar(content: String, isCurrent: () -> Boolean) {
                successEffects += CopyFeedbackEffect.STATUS
                if (failFeedback) throw IllegalStateException("private optional feedback")
            }
            override fun recordReviewEligibleCopy() { successEffects += CopyFeedbackEffect.REVIEW }
        }, coordinator, { !owner.isDisposed }) { CopyResultSettings(true, "pathline", 10) }
        private val output = ContextCollectionOutputService.createForTest(owner, collection, CopySelectionSettings.getInstance(),
            { action -> FutureTask<Unit> { action() }.also(jobs::addLast) }, work::addLast)
        private val command = ContextCollectionCopyCommand.createForTest(owner, output, publisher,
            { confirmations++; duringConfirmation(); allowConfirm }, collectionErrors::add, work::addLast, reporter)
        val widget = CopySelectionStatusBarWidgetFactory().createWidget(owner) as CopySelectionStatusBarWidget

        init {
            Disposer.register(owner, scope)
            if (owner !== project) Disposer.register(scope, Disposable { EditorFactory.getInstance().releaseEditor(editor) })
            Disposer.register(scope, widget)
            Disposer.register(scope, output)
            Disposer.register(scope, command)
            owner.replaceService(CopyResultPublisher::class.java, publisher, scope)
            owner.replaceService(CopyFailureReporter::class.java, reporter, scope)
            owner.replaceService(ContextCollectionCopyCommand::class.java, command, scope)
            collection.clear()
            collection.setIncludeCode(true)
            assertTrue(collection.capture(editor, file, PathType.RELATIVE) is ContextCollectionAddResult.Added)
            CopyHistoryService.getInstance(owner).clear()
            CopyHistoryService.getInstance(owner).addEntry("history/status payload", 10)
            widget.update("history/status payload")
            flushWork()
        }

        fun start(route: Route, finish: Boolean = true) {
            when (route) {
                Route.STANDARD -> perform(CopySelectionContextAction())
                Route.GIT -> perform(object : CopyGitPermalinkAction() {
                    override fun resolveGitRootPath(project: Project, file: VirtualFile) = "/fixture"
                    override fun executeInBackground(action: () -> Unit) { work.addLast(action) }
                    override fun invokeOnUiThread(action: () -> Unit) { work.addLast(action) }
                    override fun tryBuildPermalink(rootPath: String, filePath: String, lineRanges: List<Pair<Int, Int>>) =
                        GitPermalinkResult.Success("https://github.com/owner/repo/blob/abcdef/private-source.kt#L1")
                })
                Route.COLLECTION -> perform(CopyAllContextCollectionAction(), editorAvailable = false)
                Route.HISTORY -> chooseHistory()
                Route.STATUS -> clickWidget()
            }
            if (finish) flushWork()
        }

        fun clickWidget() {
            val component = widget.component
            val event = MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false)
            component.mouseListeners.forEach { it.mouseClicked(event) }
        }

        private fun perform(action: AnAction, editorAvailable: Boolean = true) {
            action.actionPerformed(TestActionEvent.createTestEvent(action, DataContext {
                when (it) {
                    CommonDataKeys.PROJECT.name -> owner
                    CommonDataKeys.EDITOR.name -> editor.takeIf { editorAvailable }
                    CommonDataKeys.VIRTUAL_FILE.name -> file.takeIf { editorAvailable }
                    else -> null
                }
            }))
        }

        private fun chooseHistory() {
            val factory = mockk<JBPopupFactory>()
            val builder = mockk<IPopupChooserBuilder<CopyHistoryPopup.PopupItem>>()
            val popup = mockk<JBPopup>(relaxed = true)
            val chosen = slot<Consumer<in CopyHistoryPopup.PopupItem>>()
            val entries = slot<List<CopyHistoryPopup.PopupItem>>()
            every { factory.createPopupChooserBuilder(capture(entries)) } returns builder
            every { builder.setTitle(any()) } returns builder
            every { builder.setItemChosenCallback(capture(chosen)) } returns builder
            every { builder.createPopup() } returns popup
            every { popup.cancel() } answers { historyPopupClosed = true }
            mockkStatic(JBPopupFactory::class)
            try {
                every { JBPopupFactory.getInstance() } returns factory
                CopyHistoryPopup.show(owner)
                chosen.captured.consume(entries.captured.first())
                popup.cancel()
            } finally { unmockkStatic(JBPopupFactory::class) }
        }

        fun flushWork() {
            while (jobs.isNotEmpty() || work.isNotEmpty()) {
                if (jobs.isNotEmpty()) jobs.removeFirst().run() else work.removeFirst().invoke()
            }
        }

        fun flushErrors() { while (errors.isNotEmpty()) errors.removeFirst().invoke() }

        override fun close() {
            if (!Disposer.isDisposed(scope)) Disposer.dispose(scope)
            if (!owner.isDisposed) collection.clear()
        }
    }
}
