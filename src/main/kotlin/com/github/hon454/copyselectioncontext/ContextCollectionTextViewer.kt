package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Font
import java.util.concurrent.Future
import javax.swing.JTextArea
import javax.swing.text.PlainDocument

/** Owns detached Swing documents; prepares large payloads off EDT without retaining old previews. */
internal class ContextCollectionTextViewer(private val project: Project, name: String) : Disposable {
    val component = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        accessibleContext.accessibleName = name
        focusTraversalKeysEnabled = true
    }
    private var generation = 0L
    @Volatile private var disposed = false
    private var job: Future<*>? = null

    fun show(text: String) {
        val ticket = ++generation
        job?.cancel(true)
        component.document = PlainDocument()
        if (text.isEmpty()) return
        job = ApplicationManager.getApplication().executeOnPooledThread {
            val document = PlainDocument()
            // Chunked preparation permits a superseded large preview to stop promptly.
            for (start in text.indices step 8192) {
                if (disposed || Thread.currentThread().isInterrupted) return@executeOnPooledThread
                document.insertString(document.length, text.substring(start, minOf(start + 8192, text.length)), null)
            }
            if (disposed || project.isDisposed) return@executeOnPooledThread
            ToolWindowManager.getInstance(project).invokeLater {
                if (!disposed && ticket == generation && !project.isDisposed) {
                    component.document = document
                    component.caretPosition = 0
                    job = null
                }
            }
        }
    }

    override fun dispose() {
        disposed = true
        generation++
        job?.cancel(true)
        job = null
        component.document = PlainDocument()
    }
}
