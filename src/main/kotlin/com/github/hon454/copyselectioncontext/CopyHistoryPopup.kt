package com.github.hon454.copyselectioncontext

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

object CopyHistoryPopup {
    internal sealed interface PopupItem {
        data class Entry(val preview: String, val content: String) : PopupItem {
            override fun toString(): String = preview
        }

        data object ClearAll : PopupItem {
            override fun toString(): String = CopySelectionBundle.message("history.popup.clear.all")
        }
    }

    fun show(project: Project, component: JComponent? = null) {
        val service = CopyHistoryService.getInstance(project)
        val entries = service.getEntries()
        if (entries.isEmpty()) return

        val items = createItems(entries)

        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(items)
            .setTitle(CopySelectionBundle.message("history.popup.title"))
            .setItemChosenCallback { selected ->
                handleSelection(service, selected) { content ->
                    CopyPasteManager.getInstance().setContents(StringSelection(content))
                }
            }
            .createPopup()

        if (component != null) {
            popup.show(RelativePoint(component, Point(0, 0)))
        } else {
            popup.showInFocusCenter()
        }
    }

    internal fun createItems(entries: List<CopyHistoryService.HistoryEntry>): List<PopupItem> =
        entries.map { entry ->
            PopupItem.Entry(
                preview = entry.content.take(80).replace("\n", " "),
                content = entry.content
            )
        } + PopupItem.ClearAll

    internal fun handleSelection(
        service: CopyHistoryService,
        selected: PopupItem,
        copyContent: (String) -> Unit
    ) {
        when (selected) {
            is PopupItem.Entry -> copyContent(selected.content)
            PopupItem.ClearAll -> service.clear()
        }
    }
}
