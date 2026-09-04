package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.swing.JComponent

object CopyHistoryPopup {
    internal sealed interface PopupItem {
        data class Entry(val preview: String, val timestamp: String, val content: String) : PopupItem {
            override fun toString(): String =
                CopySelectionBundle.message("history.popup.entry", preview, timestamp)
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
                handleSelection(
                    service = service,
                    selected = selected,
                    copyContent = { content ->
                        recopy(project, content)
                    },
                    confirmClear = { confirmClear(project) },
                )
            }
            .createPopup()

        if (component != null) {
            popup.show(RelativePoint(component, Point(0, 0)))
        } else {
            popup.showInFocusCenter()
        }
    }

    internal fun createItems(
        entries: List<CopyHistoryService.HistoryEntry>,
        formatTimestamp: (Long) -> String = ::formatTimestamp,
    ): List<PopupItem> =
        entries.map { entry ->
            PopupItem.Entry(
                preview = CopyPreview.history(entry.content),
                timestamp = formatTimestamp(entry.timestamp),
                content = entry.content,
            )
        } + PopupItem.ClearAll

    internal fun recopy(project: Project, content: String): CopyPublicationOutcome =
        ClipboardRequestCoordinator.recopy(content) { !project.isDisposed }

    internal fun handleSelection(
        service: CopyHistoryService,
        selected: PopupItem,
        copyContent: (String) -> Unit,
        confirmClear: () -> Boolean,
    ) {
        when (selected) {
            is PopupItem.Entry -> copyContent(selected.content)
            PopupItem.ClearAll -> if (confirmClear()) service.clear()
        }
    }

    internal fun formatTimestamp(
        timestamp: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        formatter.timeZone = timeZone
        return formatter.format(Date(timestamp))
    }

    private fun confirmClear(project: Project): Boolean =
        Messages.showYesNoDialog(
            project,
            CopySelectionBundle.message("history.popup.clear.confirm.message"),
            CopySelectionBundle.message("history.popup.clear.confirm.title"),
            Messages.getWarningIcon(),
        ) == Messages.YES
}
