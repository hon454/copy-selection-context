package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import javax.swing.event.CaretListener
import javax.swing.text.PlainDocument

/** Keeps native text selection/copy while preparing all paragraph geometry away from Swing's EDT. */
internal class ContextCollectionTextViewer private constructor(
    private val project: Project,
    name: String,
    private val background: (() -> Unit) -> Future<*>,
    private val dispatch: (() -> Unit) -> Unit,
    private val prepare: (String, Font, FontRenderContext, () -> Unit) -> PlainDocument,
) : Disposable {
    val component = ContextCollectionTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        accessibleContext.accessibleName = name
        focusTraversalKeysEnabled = true
    }
    private var generation = 0L
    @Volatile private var disposed = false
    internal var pendingRequest: Request? = null
        private set
    private var shownText: String? = null
    private var renderFont: Font? = null
    private var renderContext: FontRenderContext? = null
    private var renderDirection: Boolean? = null
    private val selectionListener = CaretListener { component.repaint() }
    private val displayListener = PropertyChangeListener { event ->
        if (!disposed && event.propertyName in setOf("font", "graphicsConfiguration", "UI")) {
            shownText?.let(::show)
        }
    }
    // A project closing before content installation must also release pending payloads.
    private val projectLifetime = Disposable { dispose() }

    constructor(project: Project, name: String) : this(project, name,
        { ApplicationManager.getApplication().executeOnPooledThread(it) },
        { ToolWindowManager.getInstance(project).invokeLater(it) }, ::prepareDocument)

    init {
        ApplicationManager.getApplication().assertIsDispatchThread()
        component.addCaretListener(selectionListener)
        component.addPropertyChangeListener(displayListener)
        Disposer.register(project, projectLifetime)
    }

    fun show(text: String) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (disposed || project.isDisposed) return
        val font = component.font
        val context = component.getFontMetrics(font).fontRenderContext
        val direction = if (component.componentOrientation.isLeftToRight) TextAttribute.RUN_DIRECTION_LTR else TextAttribute.RUN_DIRECTION_RTL
        if (shownText == text && renderFont == font && renderContext == context && renderDirection == direction) return
        shownText = text
        renderFont = font
        renderContext = context
        renderDirection = direction
        val ticket = ++generation
        pendingRequest?.cancel()
        pendingRequest = null
        component.document = PlainDocument()
        if (text.isEmpty()) return
        val work = Request(text)
        pendingRequest = work
        val future = background {
            val value = work.text() ?: return@background
            val document = try {
                prepare(value, font, context, work::checkCancelled).also {
                    if (it.getProperty(TextAttribute.RUN_DIRECTION) != direction) it.putProperty(TextAttribute.RUN_DIRECTION, direction)
                    work.checkCancelled()
                }
            }
                catch (_: CancellationException) { return@background }
            if (!work.complete(document)) return@background
            // The queue holds a cancellable request, never a captured document or text payload.
            dispatch {
                val ready = work.takeDocument()
                if (!disposed && !project.isDisposed && ticket == generation && pendingRequest === work && ready != null) {
                    component.document = ready
                    component.caretPosition = 0
                    work.releaseFuture()
                    pendingRequest = null
                }
            }
        }
        work.attachFuture(future)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        pendingRequest?.cancel()
        pendingRequest = null
        shownText = null
        renderFont = null
        renderContext = null
        renderDirection = null
        component.removeCaretListener(selectionListener)
        component.removePropertyChangeListener(displayListener)
        component.document = PlainDocument()
        Disposer.dispose(projectLifetime)
    }

    internal class Request(private var value: String?) {
        private var cancelled = false
        private var document: PlainDocument? = null
        private var future: Future<*>? = null
        @Synchronized fun attachFuture(next: Future<*>) {
            if (cancelled) next.cancel(true) else future = next
        }
        @Synchronized fun releaseFuture() { future = null }
        @Synchronized fun retainedCharacters(): Int = value?.length ?: document?.length ?: 0
        @Synchronized fun text(): String? = value
        @Synchronized fun checkCancelled() {
            if (cancelled || Thread.currentThread().isInterrupted) throw CancellationException()
        }
        @Synchronized fun complete(prepared: PlainDocument): Boolean {
            if (cancelled) return false
            document = prepared
            value = null
            return true
        }
        @Synchronized fun takeDocument(): PlainDocument? = document.also { document = null }
        @Synchronized fun cancel() {
            cancelled = true
            value = null
            document = null
            future?.cancel(true)
            future = null
        }
    }

    companion object {
        internal fun prepareDocument(text: String, font: Font, context: FontRenderContext, checkCancelled: () -> Unit): PlainDocument {
            val geometry = ContextCollectionTextLayout.prepare(text, font, context, checkCancelled)
            val document = PlainDocument()
            // JTextComponent.setDocument otherwise changes this on EDT, reanalysing all bidi text.
            document.putProperty(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_LTR)
            checkCancelled()
            // One detached insertion avoids repeatedly reanalysing the growing bidi paragraph.
            document.insertString(0, text, null)
            checkCancelled()
            document.putProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY, geometry)
            return document
        }

        internal fun createForTest(project: Project, name: String, background: (() -> Unit) -> Future<*>,
            dispatch: (() -> Unit) -> Unit,
            prepare: (String, Font, FontRenderContext, () -> Unit) -> PlainDocument = ::prepareDocument): ContextCollectionTextViewer =
            ContextCollectionTextViewer(project, name, background, dispatch, prepare)
    }
}
