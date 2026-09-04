package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.Collections
import java.util.WeakHashMap

/** Observed since capture, never a claim that the current text equals the captured text. */
data class ContextCollectionSourceStatus(
    val changed: Boolean = false,
    val relocated: Boolean = false,
    val unavailable: Boolean = false,
)

data class ContextCollectionSourceSnapshot(
    val statuses: Map<Long, ContextCollectionSourceStatus>,
    val revision: Long,
)

/** Retains only captured VirtualFiles strongly; stores no Editor or Document references. Owned by the collection service. */
class ContextCollectionSourceTracker internal constructor(private val lifetime: Disposable) : Disposable {
    private val sources = WeakHashMap<VirtualFile, Long>()
    private var nextToken = 1L
    private var items = emptyList<ContextCollectionItem>()
    private var retainedSources = emptyMap<Long, VirtualFile>()
    @Volatile private var observedSourcesByCapture = emptyMap<Long, VirtualFile>()
    private val subscriptions = ContextCollectionSubscriptions<ContextCollectionSourceSnapshot>()
    @Volatile private var disposed = false
    @Volatile private var current = ContextCollectionSourceSnapshot(emptyMap(), 0)

    init {
        Disposer.register(lifetime, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                observe(setOf(file))
            }
        }, this)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    observe(events.filterIsInstance<VFileContentChangeEvent>().mapNotNull { it.file }.toSet())
                }
            })
    }

    internal fun token(file: VirtualFile): Long {
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(!disposed)
        return sources.getOrPut(file) { nextToken++ }
    }

    fun snapshot(): ContextCollectionSourceSnapshot = current

    fun subscribe(parent: Disposable, listener: (ContextCollectionSourceSnapshot) -> Unit) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(!disposed)
        subscriptions.subscribe(parent, listener)
    }

    internal fun synchronize(captures: List<ContextCollectionItem>) {
        items = captures
        val available = sources.entries.associate { it.value to it.key }
        retainedSources = captures.mapNotNull { item ->
            available[item.sourceLocation.sourceToken]?.let { item.sourceLocation.sourceToken to it }
        }.toMap()
        observedSourcesByCapture = captures.mapNotNull { item ->
            retainedSources[item.sourceLocation.sourceToken]?.let { item.id to it }
        }.toMap()
        update(emptySet())
    }

    internal fun observe(changedFiles: Set<VirtualFile>) {
        // Freeze the affected capture IDs before dispatch: a delayed old event cannot mark a new capture.
        val changedIds = observedSourcesByCapture.filterValues { it in changedFiles }.keys.toSet()
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            if (!disposed) update(changedIds)
        } else {
            application.invokeLater({ if (!disposed) update(changedIds) }, { disposed })
        }
    }

    private fun update(changedIds: Set<Long>) {
        val byToken = retainedSources
        val statuses = items.associate { item ->
            val file = byToken[item.sourceLocation.sourceToken]
            val previous = current.statuses[item.id] ?: ContextCollectionSourceStatus()
            item.id to previous.copy(
                changed = previous.changed || item.id in changedIds,
                relocated = previous.relocated || (file != null && file.url != item.sourceLocation.url),
                unavailable = previous.unavailable || file == null || !file.isValid,
            )
        }
        if (statuses == current.statuses) return
        current = ContextCollectionSourceSnapshot(Collections.unmodifiableMap(statuses), current.revision + 1)
        subscriptions.publish(current)
    }

    override fun dispose() {
        disposed = true
        subscriptions.dispose()
        sources.clear()
        retainedSources = emptyMap()
        observedSourcesByCapture = emptyMap()
        items = emptyList()
        current = ContextCollectionSourceSnapshot(emptyMap(), current.revision + 1)
    }
}
