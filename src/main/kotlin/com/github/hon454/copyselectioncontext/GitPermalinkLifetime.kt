package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Request-scoped listeners latch ABA changes. No live editor/document is stored in prepared data. */
internal class GitPermalinkLifetime(project: Project, editor: Editor, file: VirtualFile) : Disposable {
    private val projectRef = WeakReference(project)
    private val editorRef = WeakReference(editor)
    private val documentRef = WeakReference(editor.document)
    private val fileRef = WeakReference(file)
    private val capturedPath = file.path
    private val capturedUrl = file.url
    private val capturedStamp = editor.document.modificationStamp
    private val invalidated = AtomicBoolean()
    private val closed = AtomicBoolean()
    @Volatile private var indicator: ProgressIndicator? = null

    init {
        Disposer.register(project, this)
        editor.document.addDocumentListener(object : DocumentListener {
            override fun beforeDocumentChange(event: DocumentEvent) {
                invalidated.set(true)
            }
        }, this)
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun before(events: List<VFileEvent>) {
                val source = fileRef.get() ?: return
                if (events.any { event ->
                    val structural = event is VFileMoveEvent || event is VFileDeleteEvent ||
                        (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME)
                    structural && event.file?.let { it == source || VfsUtilCore.isAncestor(it, source, true) } == true
                }) invalidated.set(true)
            }
        })
    }

    fun observeCancellation(indicator: ProgressIndicator?) { this.indicator = indicator }

    fun isAlive(): Boolean = !closed.get() && indicator?.isCanceled != true &&
        projectRef.get()?.isDisposed == false && editorRef.get()?.isDisposed == false

    /** Called on EDT, with no filesystem or Git command. The stamp check is supplemented by the latch. */
    fun matchesCapture(): Boolean {
        if (!isAlive() || invalidated.get()) return false
        val editor = editorRef.get() ?: return false
        val document = documentRef.get() ?: return false
        val file = fileRef.get() ?: return false
        return editor.document === document && document.modificationStamp == capturedStamp &&
            file.isValid && file.path == capturedPath && file.url == capturedUrl &&
            // The VFS may supply distinct equal wrappers for the same file.
            FileDocumentManager.getInstance().getFile(document) == file
    }

    fun editor(): Editor? = editorRef.get()

    fun close() = Disposer.dispose(this)

    override fun dispose() {
        closed.set(true)
        indicator = null
        projectRef.clear()
        editorRef.clear()
        documentRef.clear()
        fileRef.clear()
    }
}
