package com.github.hon454.copyselectioncontext

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

object CopyHistoryPopup {
    private data class PopupItem(val preview: String, val content: String) {
        override fun toString(): String = preview
    }

    fun show(project: Project, component: JComponent? = null) {
        val service = CopyHistoryService.getInstance(project)
        val entries = service.getEntries()
        if (entries.isEmpty()) return

        val items = entries.map { entry ->
            PopupItem(
                preview = entry.content.take(80).replace("\n", " "),
                content = entry.content
            )
        }

        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(items)
            .setTitle(CopySelectionBundle.message("history.popup.title"))
            .setItemChosenCallback { selected ->
                CopyPasteManager.getInstance().setContents(StringSelection(selected.content))
            }
            .createPopup()

        if (component != null) {
            popup.show(RelativePoint(component, Point(0, 0)))
        } else {
            popup.showInFocusCenter()
        }
    }
}
