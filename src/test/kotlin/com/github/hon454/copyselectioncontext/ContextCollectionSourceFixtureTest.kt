package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.time.Instant

class ContextCollectionSourceFixtureTest : BasePlatformTestCase() {
    fun testUnrelatedRealDocumentsVisitAllocateAndScheduleNothingAtEveryCapacityAndProjectCount() {
        val otherProjectPath = Files.createTempDirectory("copy-selection-context-source-other-")
        val otherProject = requireNotNull(ProjectManager.getInstance().createProject("source-other", otherProjectPath.toString()))
        val otherFile = LightVirtualFile("unrelated.txt", "other")
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(otherFile))
        val editor = EditorFactory.getInstance().createEditor(document, otherProject)
        try {
            for (projectCount in listOf(1, 2)) for (count in listOf(0, 1, 100)) {
                val owners = List(projectCount) { index ->
                    Disposer.newDisposable().also { Disposer.register(if (index == 0) project else otherProject, it) }
                }
                val probes = owners.map { Probe() }
                val trackers = owners.mapIndexed { index, owner -> ContextCollectionSourceTracker(owner, probes[index]) }
                try {
                    trackers.forEach { tracker ->
                        tracker.synchronize(List(count) { index -> item(tracker, LightVirtualFile("capture$index.txt"), index + 1L) })
                    }
                    val before = trackers.map { it.snapshot() }
                    val events = IntArray(projectCount)
                    trackers.forEachIndexed { index, tracker -> tracker.subscribe(owners[index]) { events[index]++ } }
                    probes.forEach { it.reset() }
                    repeat(10) {
                        WriteCommandAction.runWriteCommandAction(otherProject) { document.insertString(document.textLength, "x") }
                    }
                    ApplicationManager.getApplication().executeOnPooledThread {
                        trackers.forEach { it.observe(setOf(otherFile)) }
                    }.get()
                    trackers.forEachIndexed { index, tracker ->
                        assertSame(before[index], tracker.snapshot())
                        assertEquals(0, events[index])
                        assertEquals(listOf(0, 0, 0, 0), probes[index].counts())
                    }
                } finally {
                    owners.forEach(Disposer::dispose)
                }
            }
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
            ApplicationManager.getApplication().runWriteAction { Disposer.dispose(otherProject) }
            otherProjectPath.toFile().deleteRecursively()
        }
    }

    fun testQueuedEventFreezesIdsAndRepeatedChangeDoesNotPublish() {
        val owner = Disposer.newDisposable(testRootDisposable)
        val probe = Probe()
        val queue = mutableListOf<() -> Unit>()
        val tracker = ContextCollectionSourceTracker(owner, probe, queue::add)
        val file = LightVirtualFile("same.txt")
        val first = item(tracker, file, 1)
        val second = item(tracker, file, 2)
        tracker.synchronize(listOf(first, second))
        ApplicationManager.getApplication().executeOnPooledThread { tracker.observe(setOf(file)) }.get()
        val third = item(tracker, file, 3)
        tracker.synchronize(listOf(first, second, third))
        queue.removeAt(0).invoke()
        assertTrue(tracker.snapshot().statuses.getValue(1).changed)
        assertTrue(tracker.snapshot().statuses.getValue(2).changed)
        assertFalse(tracker.snapshot().statuses.getValue(3).changed)
        tracker.observe(setOf(file))
        val frozen = tracker.snapshot()
        var events = 0
        tracker.subscribe(owner) { events++ }
        probe.reset()
        tracker.observe(setOf(file))
        assertSame(frozen, tracker.snapshot())
        assertEquals(0, events)
        assertEquals(listOf(3, 0, 0, 0), probe.counts())
    }

    fun testQueuedWorkCannotReviveRemovedClearedOrDisposedCapturesOrRetentions() {
        for (dispose in listOf(false, true)) {
            val owner = Disposer.newDisposable(testRootDisposable)
            val queue = mutableListOf<() -> Unit>()
            val tracker = ContextCollectionSourceTracker(owner, enqueue = queue::add)
            val file = LightVirtualFile("retained.txt")
            val first = item(tracker, file, 1)
            val second = item(tracker, file, 2)
            tracker.synchronize(listOf(first, second))
            assertEquals(1, tracker.retainedSourceCount())
            tracker.synchronize(listOf(second))
            assertEquals(1, tracker.indexedSourceCount())
            ApplicationManager.getApplication().executeOnPooledThread { tracker.observe(setOf(file)) }.get()
            var events = 0
            tracker.subscribe(owner) { events++ }
            if (dispose) Disposer.dispose(owner) else tracker.synchronize(emptyList())
            assertEquals(0, tracker.retainedSourceCount())
            assertEquals(0, tracker.indexedSourceCount())
            if (!dispose) tracker.synchronize(listOf(item(tracker, file, 3)))
            val before = tracker.snapshot()
            val beforeEvents = events
            queue.removeAt(0).invoke()
            assertSame(before, tracker.snapshot())
            assertEquals(beforeEvents, events)
            if (!dispose) assertFalse(tracker.snapshot().statuses.getValue(3).changed)
        }
    }

    fun testServiceRemoveClearAndUiCloseReleaseOnlyOwnedReferences() {
        val service = ContextCollectionService.getInstance(project)
        service.clear()
        val file = myFixture.addFileToProject("retention.txt", "one\ntwo").virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        myFixture.editor.caretModel.moveToOffset(4)
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        val ids = service.snapshot().items.map { it.id }
        val ui = Disposer.newDisposable()
        service.sourceTracker.subscribe(ui) { }
        Disposer.dispose(ui)
        assertEquals(2, service.snapshot().items.size)
        assertEquals(1, service.sourceTracker.retainedSourceCount())
        service.remove(ids.first())
        assertEquals(1, service.sourceTracker.indexedSourceCount())
        service.remove(ids.last())
        assertEquals(0, service.sourceTracker.retainedSourceCount())
        assertEquals(0, service.sourceTracker.indexedSourceCount())
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        service.clear()
        assertEquals(0, service.sourceTracker.retainedSourceCount())
        assertEquals(0, service.sourceTracker.indexedSourceCount())
        val empty = service.sourceTracker.snapshot()
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, "x") }
        assertSame(empty, service.sourceTracker.snapshot())
    }

    fun testAncestorRenameMoveDeleteAndUnknownStructurePreserveFrozenContent() {
        val service = ContextCollectionService.getInstance(project)
        service.clear()
        val file = myFixture.addFileToProject("original/parent/source.txt", "frozen code").virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        val frozen = service.snapshot()
        val id = frozen.items.single().id
        val destination = myFixture.tempDirFixture.findOrCreateDir("destination")
        WriteCommandAction.runWriteCommandAction(project) { file.parent.rename(this, "renamed") }
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(id).relocated)
        // A new capture at the renamed path must itself observe the subsequent ancestor move.
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        val afterRename = service.snapshot()
        val renamedId = afterRename.items.last().id
        assertFalse(service.sourceTracker.snapshot().statuses.getValue(renamedId).relocated)
        WriteCommandAction.runWriteCommandAction(project) { file.parent.move(this, destination) }
        assertTrue(service.sourceTracker.snapshot().statuses.getValue(renamedId).relocated)
        WriteCommandAction.runWriteCommandAction(project) { file.parent.delete(this) }
        assertTrue(service.sourceTracker.snapshot().statuses.values.all { it.unavailable })
        assertSame(afterRename, service.snapshot())
        assertEquals(frozen.items.single(), service.snapshot().items.first())
        service.clear()
    }

    fun testNullFileStructureAndContentOnlyBatchesTakeSeparatePaths() {
        val owner = Disposer.newDisposable(testRootDisposable)
        val probe = Probe()
        val tracker = ContextCollectionSourceTracker(owner, probe)
        val file = LightVirtualFile("source.txt")
        tracker.synchronize(listOf(item(tracker, file, 1)))
        val before = tracker.snapshot()
        probe.reset()
        tracker.observeVfs(listOf(VFileContentChangeEvent(this, LightVirtualFile("other.txt"), 1, 2, false)))
        assertEquals(listOf(0, 0, 0, 0), probe.counts())
        val unknown = object : VFileEvent(this) {
            override fun getFile() = null
            override fun getFileSystem() = file.fileSystem
            override fun isValid() = true
            override fun computePath() = "synthetic"
            override fun equals(other: Any?) = this === other
            override fun hashCode() = System.identityHashCode(this)
        }
        tracker.observeVfs(listOf(unknown))
        assertEquals(listOf(1, 1, 0, 0), probe.counts())
        assertSame(before, tracker.snapshot())
        tracker.observeVfs(listOf(VFileContentChangeEvent(this, file, 1, 2, false)))
        assertTrue(tracker.snapshot().statuses.getValue(1).changed)
    }

    fun testActualDocumentChangeAndUndoKeepLatchedStatusAndContentOutput() {
        val service = ContextCollectionService.getInstance(project)
        service.clear()
        val file = myFixture.addFileToProject("undo.txt", "frozen").virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        service.capture(myFixture.editor, file, PathType.RELATIVE)
        val frozen = service.snapshot()
        val options = ContextCollectionOutputOptions("claude", "", false)
        val payload = ContextCollectionFormatter.format(frozen, options)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("edited") }
        val changed = service.sourceTracker.snapshot()
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals("frozen", myFixture.editor.document.text)
        assertSame(changed, service.sourceTracker.snapshot())
        assertTrue(changed.statuses.values.single().changed)
        assertSame(frozen, service.snapshot())
        assertEquals(payload, ContextCollectionFormatter.format(service.snapshot(), options))
        service.clear()
    }

    private fun item(tracker: ContextCollectionSourceTracker, file: VirtualFile, id: Long) = ContextCollectionItem(
        id, ContextCollectionSourceLocation(tracker.token(file), file.url), file.path, null, file.path,
        file.name, "text", 1, 1, "frozen", id, Instant.EPOCH, 6,
    )

    private class Probe : ContextCollectionSourceWorkObserver {
        private var visits = 0
        private var maps = 0
        private var snapshots = 0
        private var scheduled = 0
        override fun visited() { visits++ }
        override fun mapCreated() { maps++ }
        override fun snapshotCreated() { snapshots++ }
        override fun scheduled() { scheduled++ }
        fun counts() = listOf(visits, maps, snapshots, scheduled)
        fun reset() { visits = 0; maps = 0; snapshots = 0; scheduled = 0 }
    }
}
