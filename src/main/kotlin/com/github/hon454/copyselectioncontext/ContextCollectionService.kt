package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/** Session-only; deliberately has no PersistentStateComponent, State, or Storage. */
@Service(Service.Level.PROJECT)
class ContextCollectionService(private val project: Project) : Disposable {
    private val store = ContextCollectionStore()
    private val lifetime = Disposer.newDisposable("Context collection session")
    private val subscriptions = ContextCollectionSubscriptions<ContextCollectionSnapshot>()
    val sourceTracker = ContextCollectionSourceTracker(lifetime)
    @Volatile private var disposed = false
    private var publishing = false

    fun snapshot(): ContextCollectionSnapshot = store.snapshot

    fun subscribe(parent: Disposable, listener: (ContextCollectionSnapshot) -> Unit) {
        assertMutable()
        subscriptions.subscribe(parent, listener)
    }

    fun capture(editor: Editor, file: VirtualFile, pathType: PathType): ContextCollectionAddResult {
        assertMutable()
        if (project.isDisposed || editor.isDisposed || editor.project !== project || !file.isValid ||
            file.isDirectory || file.fileType.isBinary || FileDocumentManager.getInstance().getFile(editor.document) != file
        ) return ContextCollectionAddResult.InvalidContext
        val document = editor.document
        val ranges = editor.caretModel.allCarets.map { caret ->
            if (caret.hasSelection()) caret.selectionStart to caret.selectionEnd else {
                val line = caret.logicalPosition.line
                document.getLineStartOffset(line) to document.getLineEndOffset(line)
            }
        }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        val base = project.basePath?.let(file.fileSystem::findFileByPath)
        val relative = base?.let { VfsUtilCore.getRelativePath(file, it) }
        val absolute = file.path.replace('\\', '/')
        val location = ContextCollectionSourceLocation(sourceTracker.token(file), file.url)
        val filename = file.name
        val language = CopySelectionUtils.detectLanguage(file)
        val revision = snapshot().revision
        val result = store.add(ranges.asSequence().map { (start, end) ->
            ContextCollectionCandidate(
                sourceLocation = location,
                absolutePath = absolute,
                relativePath = relative,
                displayPath = if (pathType == PathType.RELATIVE) relative ?: absolute else absolute,
                filename = filename,
                language = language,
                startLine = document.getLineNumber(start) + 1,
                endLine = document.getLineNumber(if (end > start) end - 1 else start) + 1,
                text = document.charsSequence,
                startOffset = start,
                endOffset = end,
            )
        })
        if (snapshot().revision != revision) publish()
        return result
    }

    fun remove(id: Long): Boolean = mutate { store.remove(id) }
    fun moveUp(id: Long): Boolean = mutate { store.move(id, -1) }
    fun moveDown(id: Long): Boolean = mutate { store.move(id, 1) }

    /** UI owner confirms first and supplies its confirmed revision to avoid clearing newly added items. */
    fun clear(expectedRevision: Long = snapshot().revision): Boolean = mutate {
        snapshot().revision == expectedRevision && store.clear()
    }

    fun setIncludeCode(value: Boolean): Boolean = mutate { store.setIncludeCode(value) }

    private fun mutate(mutation: () -> Boolean): Boolean {
        assertMutable()
        if (project.isDisposed) return false
        val changed = mutation()
        if (changed) publish()
        return changed
    }

    private fun publish() {
        publishing = true
        try {
            sourceTracker.synchronize(snapshot().items)
            subscriptions.publish(snapshot())
        } finally {
            publishing = false
        }
    }

    private fun assertMutable() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(!disposed) { "Collection session is disposed" }
        check(!publishing) { "Schedule collection mutations after the notification callback returns" }
    }

    override fun dispose() {
        disposed = true
        subscriptions.dispose()
        Disposer.dispose(lifetime)
        store.dispose()
    }

    companion object {
        const val MAX_ITEMS = ContextCollectionStore.MAX_ITEMS
        const val MAX_ITEM_BYTES = ContextCollectionStore.MAX_ITEM_BYTES
        const val MAX_TOTAL_BYTES = ContextCollectionStore.MAX_TOTAL_BYTES
        fun getInstance(project: Project): ContextCollectionService =
            project.getService(ContextCollectionService::class.java)
    }
}
