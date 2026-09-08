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
class ContextCollectionSourceTracker internal constructor(
    lifetime: Disposable,
    private val workObserver: ContextCollectionSourceWorkObserver? = null,
    private val enqueue: ((() -> Unit) -> Unit)? = null,
) : Disposable {
    private val sources = WeakHashMap<VirtualFile, Long>()
    private var nextToken = 1L
    private var items = emptyList<ContextCollectionItem>()
    private var retainedSources = emptyMap<Long, VirtualFile>()
    @Volatile private var capturesBySource = emptyMap<VirtualFile, Set<Long>>()
    private val subscriptions = ContextCollectionSubscriptions<ContextCollectionSourceSnapshot>()
    @Volatile private var disposed = false
    @Volatile private var current = ContextCollectionSourceSnapshot(emptyMap(), 0)

    init {
        Disposer.register(lifetime, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (disposed || capturesBySource.isEmpty()) return
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                observe(setOf(file))
            }
        }, this)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    observeVfs(events)
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
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(!disposed)
        items = captures
        val available = sources.entries.associate { it.value to it.key }
        retainedSources = captures.mapNotNull { item ->
            available[item.sourceLocation.sourceToken]?.let { item.sourceLocation.sourceToken to it }
        }.toMap()
        val index = mutableMapOf<VirtualFile, MutableSet<Long>>()
        captures.forEach { item ->
            retainedSources[item.sourceLocation.sourceToken]?.let { file ->
                index.getOrPut(file) { mutableSetOf() }.add(item.id)
            }
        }
        // Background readers see one complete, immutable index. Only retained captures own files.
        capturesBySource = Collections.unmodifiableMap(index.mapValues { (_, ids) ->
            Collections.unmodifiableSet(ids)
        })
        updateStructure(emptySet())
    }

    internal fun observe(changedFiles: Set<VirtualFile>) {
        if (disposed) return
        val changedIds = affectedCaptures(changedFiles)
        if (changedIds.isEmpty()) return
        dispatch(changedIds, structural = false)
    }

    internal fun observeVfs(events: List<VFileEvent>) {
        if (disposed || capturesBySource.isEmpty() || events.isEmpty()) return
        val changedIds = affectedCaptures(events.filterIsInstance<VFileContentChangeEvent>().map { it.file })
        // Unknown events (including null-file events) conservatively check location/lifetime.
        // A directory event can affect descendants without mentioning any captured file.
        val structural = events.any { it !is VFileContentChangeEvent }
        if (changedIds.isEmpty() && !structural) return
        dispatch(changedIds, structural)
    }

    private fun affectedCaptures(files: Iterable<VirtualFile>): Set<Long> {
        val index = capturesBySource
        if (index.isEmpty()) return emptySet()
        val ids = mutableSetOf<Long>()
        files.forEach { file -> index[file]?.let(ids::addAll) }
        return ids
    }

    private fun dispatch(changedIds: Set<Long>, structural: Boolean) {
        // Freeze IDs before scheduling; queued work retains neither files nor an old index.
        val application = ApplicationManager.getApplication()
        val task = {
            if (!disposed) {
                if (structural) updateStructure(changedIds) else updateContent(changedIds)
            }
        }
        if (application.isDispatchThread) {
            task()
        } else {
            workObserver?.scheduled()
            if (enqueue != null) enqueue.invoke(task) else application.invokeLater(task, { disposed })
        }
    }

    private fun updateContent(changedIds: Set<Long>) {
        var statuses: MutableMap<Long, ContextCollectionSourceStatus>? = null
        changedIds.forEach { id ->
            val previous = current.statuses[id] ?: return@forEach
            workObserver?.visited()
            if (!previous.changed) {
                if (statuses == null) {
                    workObserver?.mapCreated()
                    statuses = current.statuses.toMutableMap()
                }
                statuses[id] = previous.copy(changed = true)
            }
        }
        statuses?.let(::publish)
    }

    private fun updateStructure(changedIds: Set<Long>) {
        workObserver?.mapCreated()
        val statuses = items.associate { item ->
            workObserver?.visited()
            val file = retainedSources[item.sourceLocation.sourceToken]
            val previous = current.statuses[item.id] ?: ContextCollectionSourceStatus()
            item.id to previous.copy(
                changed = previous.changed || item.id in changedIds,
                relocated = previous.relocated || (file != null && file.url != item.sourceLocation.url),
                unavailable = previous.unavailable || file == null || !file.isValid,
            )
        }
        if (statuses != current.statuses) publish(statuses)
    }

    private fun publish(statuses: Map<Long, ContextCollectionSourceStatus>) {
        workObserver?.snapshotCreated()
        current = ContextCollectionSourceSnapshot(Collections.unmodifiableMap(statuses), current.revision + 1)
        subscriptions.publish(current)
    }

    /** Counts only; never exposes a mutable registry or a retained source to callers. */
    internal fun retainedSourceCount(): Int = retainedSources.size
    internal fun indexedSourceCount(): Int = capturesBySource.size

    override fun dispose() {
        disposed = true
        subscriptions.dispose()
        sources.clear()
        retainedSources = emptyMap()
        capturesBySource = emptyMap()
        items = emptyList()
        current = ContextCollectionSourceSnapshot(emptyMap(), current.revision + 1)
    }
}

/** Optional deterministic test instrumentation; production has no counters or telemetry. */
internal interface ContextCollectionSourceWorkObserver {
    fun visited()
    fun mapCreated()
    fun snapshotCreated()
    fun scheduled()
}
