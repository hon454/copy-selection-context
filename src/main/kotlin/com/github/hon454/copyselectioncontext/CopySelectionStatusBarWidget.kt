package com.github.hon454.copyselectioncontext

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

class CopySelectionStatusBarWidget(project: Project) :
    EditorBasedWidget(project), TextPresentation {

    companion object {
        const val ID = "CopySelectionStatusBarWidget"
        private const val STATUS_PREFIX = "📋 "
    }

    private val lastCopied = AtomicReference("")

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getText() = lastCopied.get().let { content ->
        if (content.isBlank()) {
            ""
        } else {
            STATUS_PREFIX + CopyPreview.status(content, CopyPreview.STATUS_MAX_LENGTH - STATUS_PREFIX.length)
        }
    }

    override fun getTooltipText() = lastCopied.get().let { content ->
        if (content.isBlank()) CopySelectionBundle.message("widget.tooltip") else CopyPreview.tooltip(content)
    }
    override fun getAlignment() = 0f

    override fun getClickConsumer() = Consumer<MouseEvent> {
        copyLastValue()
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
    }

    fun update(content: String) {
        lastCopied.set(content)
        myStatusBar?.updateWidget(ID)
    }

    private fun copyLastValue() {
        val content = lastCopied.get()
        if (content.isNotBlank()) {
            CopyPasteManager.getInstance().setContents(StringSelection(content))
        }
    }
}
