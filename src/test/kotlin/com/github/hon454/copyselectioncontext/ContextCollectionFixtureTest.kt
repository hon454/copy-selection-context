package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class ContextCollectionFixtureTest : BasePlatformTestCase() {
    private lateinit var service: ContextCollectionService
    private lateinit var originalSettings: CopySelectionSettings.State

    override fun setUp() {
        super.setUp()
        originalSettings = CopySelectionSettings.getInstance().state.copy()
        CopySelectionSettings.getInstance().state.enableNotification = false
        service = ContextCollectionService.getInstance(project)
        service.clear()
        service.setIncludeCode(true)
    }

    override fun tearDown() {
        try {
            service.clear()
            CopySelectionSettings.getInstance().loadState(originalSettings)
        } finally {
            super.tearDown()
        }
    }

    fun testUnsavedSnapshotRecaptureAndSeparateSourceRevision() {
        val file = open("Timeout.kt", "val timeout = 10\n")
        capture(file)
        val frozen = service.snapshot()
        val statusBefore = service.sourceTracker.snapshot().revision
        var contentEvents = 0
        var sourceEvents = 0
        service.subscribe(testRootDisposable) { contentEvents++ }
        service.sourceTracker.subscribe(testRootDisposable) { sourceEvents++ }
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("val timeout = 30\n") }
        assertSame(frozen, service.snapshot())
        assertEquals("val timeout = 10", frozen.items.single().code)
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(frozen.items.single().id).changed)
        assertTrue(service.sourceTracker.snapshot().revision > statusBefore)
        assertEquals(0, contentEvents)
        assertEquals(1, sourceEvents)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(myFixture.editor.document))
        capture(file)
        assertEquals(listOf("val timeout = 10", "val timeout = 30"), service.snapshot().items.map { it.code })
        assertEquals(1, contentEvents)
        assertFalse(service.sourceTracker.snapshot().statuses.getValue(service.snapshot().items.last().id).changed)
    }

    fun testMultipleEditorsCaretsCurrentLineAndExclusiveEnd() {
        val first = open("First.txt", "first\nsecond\nthird\nfourth")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(2))
        requireNotNull(editor.caretModel.addCaret(editor.logicalToVisualPosition(LogicalPosition(0, 0))))
            .setSelection(0, editor.document.getLineStartOffset(1))
        capture(first)
        val second = open("Second.txt", "other file")
        capture(second)
        assertEquals(listOf("first\n", "third", "other file"), service.snapshot().items.map { it.code })
        assertEquals(listOf(1 to 1, 3 to 3, 1 to 1), service.snapshot().items.map { it.startLine to it.endLine })
        assertEquals(2, service.snapshot().items.map { it.sourceLocation.sourceToken }.distinct().size)
    }

    fun testPathPreferenceDuplicatesBothDirectionsAndChangedCaptureKeepsLocation() {
        val path = java.nio.file.Path.of(requireNotNull(project.basePath), "path-preference-example.kt")
        java.nio.file.Files.createDirectories(path.parent)
        java.nio.file.Files.writeString(path, "val timeout = 10")
        val file = requireNotNull(com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
        myFixture.configureFromExistingVirtualFile(file)
        for ((first, second) in listOf(PathType.RELATIVE to PathType.ABSOLUTE, PathType.ABSOLUTE to PathType.RELATIVE)) {
            service.clear()
            assertEquals(ContextCollectionAddResult.Added(1, 0), service.capture(myFixture.editor, file, first))
            val before = service.snapshot()
            assertEquals(ContextCollectionAddResult.Added(0, 1), service.capture(myFixture.editor, file, second))
            assertSame(before, service.snapshot())
        }
        service.clear()
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        val original = service.snapshot().items.single()
        assertEquals("path-preference-example.kt", original.relativePath)
        assertEquals(original.relativePath, original.displayPath)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("val timeout = 30") }
        service.capture(myFixture.editor, file, PathType.ABSOLUTE)
        val changed = service.snapshot().items.last()
        assertEquals(original.sourceLocation, changed.sourceLocation)
        assertEquals(file.path, changed.displayPath)
        assertFalse(original.displayPath == changed.displayPath)
        assertEquals(original, service.snapshot().items.first())
    }

    fun testRenameDeleteAndRecreatedPathKeepSeparateSourceIdentity() {
        val file = open("source/Before.kt", "val timeout = 10")
        capture(file)
        val original = service.snapshot().items.single()
        val frozenRevision = service.snapshot().revision
        WriteCommandAction.runWriteCommandAction(project) { file.rename(this, "After.kt") }
        assertEquals(frozenRevision, service.snapshot().revision)
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(original.id).relocated)
        capture(file)
        val renamed = service.snapshot().items.last()
        assertEquals(original.sourceLocation.sourceToken, renamed.sourceLocation.sourceToken)
        assertFalse(original.sourceLocation.url == renamed.sourceLocation.url)
        WriteCommandAction.runWriteCommandAction(project) { file.delete(this) }
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(original.id).unavailable)
        assertEquals(original, service.snapshot().items.first())
        val recreated = open("source/Before.kt", "val timeout = 10")
        capture(recreated)
        val latest = service.snapshot().items.last()
        assertFalse(original.sourceLocation.sourceToken == latest.sourceLocation.sourceToken)
        assertEquals(3, service.snapshot().items.size)
    }

    fun testMutationsNotifyOnceAndUiSubscriptionsReleaseWithoutClearingSession() {
        val parent = Disposer.newDisposable()
        val revisions = mutableListOf<Long>()
        service.subscribe(parent) { revisions.add(it.revision) }
        val file = open("mutations.txt", "one\ntwo")
        capture(file)
        val first = service.snapshot().items.single()
        capture(file)
        assertEquals(1, revisions.size)
        assertFalse(service.moveUp(first.id))
        assertFalse(service.remove(-1))
        myFixture.editor.caretModel.primaryCaret.moveToLogicalPosition(LogicalPosition(1, 0))
        capture(file)
        assertTrue(service.moveDown(first.id))
        assertTrue(service.remove(first.id))
        assertTrue(service.setIncludeCode(false))
        val confirmedRevision = service.snapshot().revision
        service.setIncludeCode(true)
        assertFalse(service.clear(confirmedRevision))
        assertEquals(6, revisions.size)
        assertEquals(revisions.sorted().distinct(), revisions)
        Disposer.dispose(parent)
        assertEquals(1, service.snapshot().items.size)
        service.clear()
        assertEquals(6, revisions.size)
    }

    fun testActionLeavesClipboardHistoryAnalyticsReviewAndMarkersUnchanged() {
        open("action.txt", "collection-private-marker")
        val action = AddToContextCollectionAction()
        val clipboard = CopyPasteManager.getInstance().contents
        val history = CopyHistoryService.getInstance(project).state
        val analytics = CopySelectionAnalytics.getInstance().snapshot()
        val review = CopySelectionReviewService.getInstance().state
        val reviewCount = CopySelectionReviewService.getInstance().sessionCopyCount()
        val markers = myFixture.editor.markupModel.allHighlighters.toList()
        try {
            CopyPasteManager.getInstance().setContents(StringSelection("sentinel"))
            action.actionPerformed(TestActionEvent.createTestEvent(action, context()))
            action.actionPerformed(TestActionEvent.createTestEvent(action, context()))
            assertEquals(1, service.snapshot().items.size)
            assertEquals("sentinel", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
            assertEquals(history, CopyHistoryService.getInstance(project).state)
            assertEquals(analytics, CopySelectionAnalytics.getInstance().snapshot())
            assertEquals(review, CopySelectionReviewService.getInstance().state)
            assertEquals(reviewCount, CopySelectionReviewService.getInstance().sessionCopyCount())
            assertEquals(markers, myFixture.editor.markupModel.allHighlighters.toList())
            assertTrue(action.feedback(ContextCollectionAddResult.Added(0, 1)).contains("1"))
        } finally {
            CopyPasteManager.getInstance().setContents(clipboard ?: StringSelection(""))
        }
    }

    fun testMissingContextAndOversizedMultiCaretAddLeaveExistingCollectionUntouched() {
        val file = open("capacity.txt", "small\n" + "한".repeat(100000))
        capture(file)
        val before = service.snapshot()
        requireNotNull(myFixture.editor.caretModel.addCaret(
            myFixture.editor.logicalToVisualPosition(LogicalPosition(1, 0))))
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES),
            service.capture(myFixture.editor, file, PathType.RELATIVE))
        assertSame(before, service.snapshot())
        val action = AddToContextCollectionAction()
        for (missing in listOf(CommonDataKeys.PROJECT.name, CommonDataKeys.EDITOR.name, CommonDataKeys.VIRTUAL_FILE.name)) {
            val data = context()
            action.actionPerformed(TestActionEvent.createTestEvent(action, DataContext { if (it == missing) null else data.getData(it) }))
            assertSame(before, service.snapshot())
        }
    }

    fun testIndependentProjectLifetimeAndSessionReset() {
        val otherLifetime = Disposer.newDisposable()
        val otherProject = requireNotNull(ProjectManager.getInstance().createProject("collection-other", myFixture.tempDirPath + "/other-project"))
        Disposer.register(otherLifetime, otherProject)
        val otherService = ContextCollectionService(otherProject)
        Disposer.register(otherLifetime, otherService)
        val file = open("isolation.txt", "project-one")
        capture(file)
        val otherFile = com.intellij.testFramework.LightVirtualFile("other.txt", "project-two")
        val otherDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(otherFile))
        val otherEditor = EditorFactory.getInstance().createEditor(otherDocument, otherProject)
        try {
            otherService.capture(otherEditor, otherFile, PathType.ABSOLUTE)
            otherService.setIncludeCode(false)
            assertEquals(1, otherService.snapshot().items.size)
            assertTrue(service.snapshot().includeCode)
            ApplicationManager.getApplication().runWriteAction { Disposer.dispose(otherLifetime) }
            assertTrue(otherService.snapshot().items.isEmpty())
            assertTrue(otherService.sourceTracker.snapshot().statuses.isEmpty())
            assertEquals(1, service.snapshot().items.size)
            val reopened = ContextCollectionService(project)
            try {
                assertTrue(reopened.snapshot().items.isEmpty())
                assertTrue(reopened.snapshot().includeCode)
            } finally { Disposer.dispose(reopened) }
        } finally {
            EditorFactory.getInstance().releaseEditor(otherEditor)
            ApplicationManager.getApplication().runWriteAction { Disposer.dispose(otherLifetime) }
        }
    }

    fun testDelayedSourceEventDoesNotMarkNewCapturesAndEditorsCanClose() {
        val file = open("delayed.txt", "old\nnew")
        capture(file)
        val first = service.snapshot().items.single()
        ApplicationManager.getApplication().executeOnPooledThread {
            service.sourceTracker.observe(setOf(file))
        }.get()
        myFixture.editor.caretModel.primaryCaret.moveToLogicalPosition(LogicalPosition(1, 0))
        capture(file)
        val second = service.snapshot().items.last()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(first.id).changed)
        assertFalse(service.sourceTracker.snapshot().statuses.getValue(second.id).changed)
        open("unrelated.txt", "other")
        // The original editor no longer owns the capture. A new source event still resolves its token.
        service.sourceTracker.observe(setOf(file))
        assertFalse(service.sourceTracker.snapshot().statuses.getValue(first.id).unavailable)
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.caretModel.primaryCaret.moveToOffset(0)
        assertEquals(ContextCollectionAddResult.Added(0, 1), capture(file))
    }

    fun testCollectionIsAbsentFromSerializablePluginState() {
        val file = open("private-marker.kt", "private-collection-source-marker")
        capture(file)
        service.setIncludeCode(false)
        assertFalse(PersistentStateComponent::class.java.isAssignableFrom(ContextCollectionService::class.java))
        assertNull(ContextCollectionService::class.java.getAnnotation(State::class.java))
        val states = listOf(
            CopySelectionSettings.getInstance().state,
            CopyHistoryService.getInstance(project).state,
            CopySelectionAnalytics.getInstance().state,
            CopySelectionReviewService.getInstance().state,
        )
        states.forEach { state ->
            val serialized = com.intellij.openapi.util.JDOMUtil.writeElement(XmlSerializer.serialize(state))
            assertFalse(serialized.contains("private-collection-source-marker"))
            assertFalse(serialized.contains("private-marker.kt"))
            assertFalse(serialized.contains("includeCode="))
        }
    }

    private fun open(name: String, text: String): VirtualFile {
        val file = myFixture.addFileToProject(name, text).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        return file
    }
    private fun capture(file: VirtualFile) = service.capture(myFixture.editor, file, PathType.RELATIVE)
    private fun context() = DataContext { key -> when (key) {
        CommonDataKeys.PROJECT.name -> project
        CommonDataKeys.EDITOR.name -> myFixture.editor
        CommonDataKeys.VIRTUAL_FILE.name -> myFixture.file.virtualFile
        else -> null
    } }
}
