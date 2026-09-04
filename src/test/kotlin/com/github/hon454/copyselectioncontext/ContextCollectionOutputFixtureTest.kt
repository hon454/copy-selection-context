package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.util.concurrent.FutureTask

class ContextCollectionOutputFixtureTest : BasePlatformTestCase() {
    private lateinit var settings: CopySelectionSettings
    private lateinit var original: CopySelectionSettings.State
    private lateinit var collection: ContextCollectionService
    private var clipboard: java.awt.datatransfer.Transferable? = null

    override fun setUp() {
        super.setUp()
        settings = CopySelectionSettings.getInstance()
        original = settings.state.copy()
        settings.loadState(CopySelectionSettings.State(enableNotification = false, outputFormat = "pathline"))
        collection = ContextCollectionService.getInstance(project)
        collection.clear()
        collection.setIncludeCode(true)
        clipboard = CopyPasteManager.getInstance().contents
        CopyPasteManager.getInstance().setContents(StringSelection("sentinel"))
    }

    override fun tearDown() {
        try {
            collection.clear()
            settings.loadState(original)
            CopyPasteManager.getInstance().setContents(clipboard ?: StringSelection(""))
        } finally { super.tearDown() }
    }

    fun testCommittedSettingsInvalidationDiscardsOldResultsAndSourceOnlyDoesNotCalculate() {
        val file = capture("A.kt", "old")
        val harness = Harness()
        val states = mutableListOf<ContextCollectionOutputState>()
        harness.output.subscribe(testRootDisposable) { states += it }
        harness.compute() // old result awaiting EDT dispatch
        val firstKey = harness.output.snapshot().key
        settings.state.outputFormat = "claude"
        settings.outputSettingsCommitted()
        assertTrue(harness.output.snapshot() is ContextCollectionOutputState.Calculating)
        harness.flush()
        assertTrue(harness.output.snapshot() is ContextCollectionOutputState.Calculating)
        harness.computeAndFlush()
        val current = harness.output.snapshot()
        assertFalse(current.key == firstKey)
        assertTrue((current as ContextCollectionOutputState.Computed).result is ContextCollectionOutputResult.Ready)
        assertEquals(2, states.size)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("changed") }
        WriteCommandAction.runWriteCommandAction(project) { file.rename(this, "renamed.kt") }
        assertSame(current, harness.output.snapshot())
        assertEquals(0, harness.jobs.size)
        settings.state.defaultPathType = PathType.ABSOLUTE
        settings.state.includeCodeContent = true
        settings.outputSettingsCommitted()
        assertSame(current, harness.output.refresh())
        val revision = settings.outputSettingsRevision()
        settings.loadState(settings.state.copy(codeTrimming = true))
        assertTrue(settings.outputSettingsRevision() > revision)
        assertTrue(harness.output.snapshot() is ContextCollectionOutputState.Calculating)
    }

    fun testNoEditorCopyRetainsCapturedDeletedSourceAndExistingHistoryAndGutter() {
        val file = capture("A.kt", "old")
        CopySelectionHighlighter.update(myFixture.editor, listOf(1 to 1))
        val markers = myFixture.editor.markupModel.allHighlighters.toList()
        val history = CopyHistoryService.getInstance(project)
        history.addEntry("previous", 10)
        val beforeHistory = history.state
        val before = collection.snapshot()
        val harness = Harness()
        harness.computeAndFlush()
        val ready = harness.ready()
        WriteCommandAction.runWriteCommandAction(project) { file.delete(this) }
        harness.command.execute()
        harness.flush()
        assertEquals(ready.payload, copied())
        assertSame(before, collection.snapshot())
        assertEquals(beforeHistory, history.state)
        assertEquals(markers, myFixture.editor.markupModel.allHighlighters.toList())
        assertTrue(harness.errors.isEmpty())
        assertEquals(0, harness.confirmations)
        val action = CopyAllContextCollectionAction()
        val event = TestActionEvent.createTestEvent(action, DataContext { if (it == CommonDataKeys.PROJECT.name) project else null })
        action.update(event)
        assertTrue(event.presentation.isEnabled)
    }

    fun testWarningCancelAndConfirmedPayloadRetainCollectionAndCombineReasonsOnce() {
        capture("large.kt", "x".repeat(131073))
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("y".repeat(131073)) }
        collection.capture(myFixture.editor, myFixture.file.virtualFile, PathType.RELATIVE)
        settings.state.outputFormat = "template"
        settings.state.customFormatTemplate = "{code}"
        settings.outputSettingsCommitted()
        val harness = Harness()
        harness.computeAndFlush()
        val ready = harness.ready()
        assertTrue(ContextCollectionWarning.SIZE in ready.warnings)
        assertTrue(ContextCollectionWarning.SNAPSHOT_LABELS_ABSENT in ready.warnings)
        val before = collection.snapshot()
        harness.allow = false
        harness.command.execute(); harness.flush()
        assertEquals("sentinel", copied())
        assertSame(before, collection.snapshot())
        assertEquals(1, harness.confirmations)
        harness.allow = true
        harness.command.execute(); harness.flush()
        assertEquals(ready.payload, copied())
        assertEquals(2, harness.confirmations)
        assertSame(before, collection.snapshot())
        assertTrue(ContextCollectionCopyCommand.confirmationMessage(ready).contains(java.text.NumberFormat.getIntegerInstance().format(ready.bytes)))
    }

    fun testEveryFinalPathRejectsChangedContentCommittedSettingsAndDefensiveActualTuple() {
        capture("small.kt", "old")
        for (change in listOf("content", "committed", "direct")) {
            val harness = Harness()
            harness.computeAndFlush()
            harness.command.execute() // computed but queued final dispatch, below warning
            when (change) {
                "content" -> collection.setIncludeCode(!collection.snapshot().includeCode)
                "committed" -> settings.loadState(settings.state.copy(codeTrimming = !settings.state.codeTrimming))
                else -> settings.state.customFormatTemplate += " changed"
            }
            harness.flush()
            assertEquals("sentinel", copied())
            assertEquals(1, harness.errors.size)
            Disposer.dispose(harness.command)
            Disposer.dispose(harness.output)
        }
    }

    fun testSmallConflictStillConfirmsAndMutationInsideConfirmationCannotPublish() {
        capture("A.kt", "old")
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("new") }
        collection.capture(myFixture.editor, myFixture.file.virtualFile, PathType.RELATIVE)
        collection.setIncludeCode(false)
        val harness = Harness()
        harness.computeAndFlush()
        assertTrue(harness.ready().bytes < ContextCollectionFormatter.WARNING_BYTES)
        harness.duringConfirmation = { collection.moveDown(collection.snapshot().items.first().id) }
        harness.command.execute(); harness.flush()
        assertEquals(1, harness.confirmations)
        assertEquals(1, harness.errors.size)
        assertEquals("sentinel", copied())
        assertEquals(2, collection.snapshot().items.size)
    }

    fun testBlankAndOverflowCannotBeConfirmedAndPendingOutputChangesCancelOldCopy() {
        capture("A.kt", "x".repeat(262144))
        settings.state.outputFormat = "template"
        settings.state.customFormatTemplate = "{code}".repeat(17)
        settings.outputSettingsCommitted()
        val harness = Harness()
        harness.command.execute()
        harness.computeAndFlush()
        assertEquals(ContextCollectionOutputResult.AboveHardLimit, (harness.output.snapshot() as ContextCollectionOutputState.Computed).result)
        assertEquals("sentinel", copied())
        assertEquals(0, harness.confirmations)
        settings.state.customFormatTemplate = "{code}"
        settings.outputSettingsCommitted()
        collection.setIncludeCode(false)
        harness.command.execute()
        harness.computeAllAndFlush()
        assertEquals("sentinel", copied())
        assertTrue(harness.errors.size >= 2)
    }

    fun testTwoProjectsRecopyAndDelayedPublisherPathsUseOneSequenceWithoutCrossProjectEffects() {
        capture("A.kt", "A")
        val other = requireNotNull(ProjectManager.getInstance().createProject("copy-order-B", myFixture.tempDirPath + "/copy-order-B"))
        val a = CopyResultPublisher.getInstance(project)
        val b = CopyResultPublisher.getInstance(other)
        val aHistory = CopyHistoryService.getInstance(project).state
        try {
            for (kind in listOf("standard", "permalink", "collection", "history", "status", "cancel", "dispose")) {
                val harness = Harness()
                harness.computeAndFlush()
                val delayedPermalink = a.beginRequest()
                harness.command.execute()
                when (kind) {
                    "standard" -> b.publish(CopyResult("B-standard"), CopyResultPolicy.STANDARD)
                    "permalink" -> b.publishIfCurrent(b.beginRequest(), CopyResult("B-permalink"), CopyResultPolicy.GIT_PERMALINK)
                    "collection" -> b.publish(CopyResult("B-collection", actualFormat = "pathline"), CopyResultPolicy.COLLECTION)
                    "history" -> CopyHistoryPopup.recopy(other, "B-history")
                    "status" -> {
                        val widget = CopySelectionStatusBarWidget()
                        widget.update("B-status")
                        val component = widget.component
                        component.mouseListeners.forEach { it.mouseClicked(MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false)) }
                        widget.dispose()
                    }
                    "cancel" -> b.beginRequest()
                    "dispose" -> { b.beginRequest(); ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) } }
                }
                val expected = copied()
                harness.flush()
                assertEquals(expected, copied())
                assertTrue(harness.errors.isEmpty())
                assertFalse(a.publishIfCurrent(delayedPermalink, CopyResult("stale"), CopyResultPolicy.GIT_PERMALINK))
                assertFalse(a.runIfCurrent(delayedPermalink) { fail("stale failure feedback") })
                assertEquals(aHistory, CopyHistoryService.getInstance(project).state)
                Disposer.dispose(harness.command)
                Disposer.dispose(harness.output)
            }
        } finally { if (!other.isDisposed) ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) } }
    }

    fun testOutputDisposalUnsubscribesAndDropsPayloadAndCommand() {
        capture("A.kt", "secret")
        val harness = Harness()
        harness.computeAndFlush()
        val parent = Disposer.newDisposable()
        var events = 0
        harness.output.subscribe(parent) { events++ }
        Disposer.dispose(parent)
        harness.command.execute()
        Disposer.dispose(harness.command)
        Disposer.dispose(harness.output)
        harness.flush()
        assertEquals("sentinel", copied())
        assertFalse(harness.output.snapshot() is ContextCollectionOutputState.Computed)
        settings.loadState(settings.state.copy(codeTrimming = true))
        collection.setIncludeCode(false)
        assertEquals(0, events)
    }

    fun testSettingsReplacementInvalidatesBothLiveProjectOutputsAndDisposalSuppressesQueuedWork() {
        capture("A.kt", "A")
        val first = Harness()
        first.computeAndFlush()
        val other = requireNotNull(ProjectManager.getInstance().createProject("output-B", myFixture.tempDirPath + "/output-B"))
        val jobs = ArrayDeque<FutureTask<Unit>>()
        val ui = ArrayDeque<() -> Unit>()
        val second = ContextCollectionOutputService.createForTest(other, ContextCollectionService.getInstance(other), settings,
            { work -> FutureTask<Unit> { work() }.also(jobs::addLast) }, { ui.addLast(it) })
        Disposer.register(other, second)
        try {
            jobs.removeFirst().run(); ui.removeFirst().invoke()
            settings.loadState(settings.state.copy(codeTrimming = true))
            assertTrue(first.output.snapshot() is ContextCollectionOutputState.Calculating)
            assertTrue(second.snapshot() is ContextCollectionOutputState.Calculating)
            assertEquals(first.output.snapshot().key.settingsRevision, second.snapshot().key.settingsRevision)
            jobs.removeFirst().run()
            ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) }
            while (ui.isNotEmpty()) ui.removeFirst().invoke()
            assertTrue(second.snapshot() is ContextCollectionOutputState.Calculating)
            first.computeAndFlush()
            assertTrue(first.output.snapshot() is ContextCollectionOutputState.Computed)
        } finally { if (!other.isDisposed) ApplicationManager.getApplication().runWriteAction { Disposer.dispose(other) } }
    }

    private inner class Harness {
        val jobs = ArrayDeque<FutureTask<Unit>>()
        val ui = ArrayDeque<() -> Unit>()
        val errors = mutableListOf<String>()
        var confirmations = 0
        var allow = true
        var duringConfirmation: () -> Unit = {}
        val output = ContextCollectionOutputService.createForTest(project, collection, settings,
            { work -> FutureTask<Unit> { work() }.also(jobs::addLast) }, { ui.addLast(it) })
        val command = ContextCollectionCopyCommand.createForTest(project, output, CopyResultPublisher.getInstance(project),
            { confirmations++; duringConfirmation(); allow }, { errors += it }, { ui.addLast(it) })
        init { Disposer.register(testRootDisposable, output); Disposer.register(testRootDisposable, command) }
        fun compute() { jobs.removeFirst().run() }
        fun flush() { while (ui.isNotEmpty()) ui.removeFirst().invoke() }
        fun computeAndFlush() { compute(); flush() }
        fun computeAllAndFlush() { while (jobs.isNotEmpty()) compute(); flush() }
        fun ready() = (output.snapshot() as ContextCollectionOutputState.Computed).result as ContextCollectionOutputResult.Ready
    }

    private fun capture(name: String, code: String): com.intellij.openapi.vfs.VirtualFile {
        val file = myFixture.addFileToProject(name, code).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.selectionModel.setSelection(0, code.length)
        collection.capture(myFixture.editor, file, PathType.RELATIVE)
        return file
    }
    private fun copied(): String? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
}
