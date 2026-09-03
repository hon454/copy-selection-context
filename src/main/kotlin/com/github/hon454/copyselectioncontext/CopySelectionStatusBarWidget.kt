package com.github.hon454.copyselectioncontext

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.wm.StatusBar
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel

class CopySelectionStatusBarWidget : CustomStatusBarWidgetAdapter() {

    companion object {
        const val ID = "CopySelectionStatusBarWidget"
        private const val STATUS_PREFIX = "📋 "
    }

    private val lastCopied = AtomicReference("")
    private var statusBar: StatusBar? = null
    private val label: JLabel by lazy {
        JLabel().apply {
            text = getText()
            toolTipText = getTooltipText()
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        copyLastValue()
                    }
                },
            )
        }
    }

    override fun ID() = ID
    override fun getComponent(): JComponent = label

    fun getText() = lastCopied.get().let { content ->
        if (content.isBlank()) {
            ""
        } else {
            STATUS_PREFIX + CopyPreview.status(content, CopyPreview.STATUS_MAX_LENGTH - STATUS_PREFIX.length)
        }
    }

    fun getTooltipText() = lastCopied.get().let { content ->
        if (content.isBlank()) CopySelectionBundle.message("widget.tooltip") else CopyPreview.tooltip(content)
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    fun update(content: String) {
        lastCopied.set(content)
        label.text = getText()
        label.toolTipText = getTooltipText()
        statusBar?.updateWidget(ID)
    }

    private fun copyLastValue() {
        val content = lastCopied.get()
        if (content.isNotBlank()) {
            CopyPasteManager.getInstance().setContents(StringSelection(content))
        }
    }
}
