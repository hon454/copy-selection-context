package com.github.hon454.copyselectioncontext

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.intellij.DynamicBundle

/** Bounded row text only; full, unescaped captured data belongs in the plain-text details. */
internal object ContextCollectionPresentation {
    fun status(status: ContextCollectionSourceStatus?): String = buildList {
        if (status?.changed == true) add(message("changed"))
        if (status?.relocated == true) add(message("relocated"))
        if (status?.unavailable == true) add(message("unavailable"))
        if (isEmpty()) add(message("unobserved"))
    }.joinToString(" · ")

    fun time(item: ContextCollectionItem, full: Boolean = false): String {
        val formatter = if (full) DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS VV", DynamicBundle.getLocale())
            else DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(DynamicBundle.getLocale())
        return formatter.withZone(ZoneId.systemDefault()).format(item.capturedAt)
    }

    fun range(item: ContextCollectionItem): String = if (item.startLine == item.endLine) "${item.startLine}"
        else "${item.startLine}–${item.endLine}"

    fun row(item: ContextCollectionItem, source: ContextCollectionSourceStatus?): String =
        "<html><b>#${item.captureNumber} &nbsp; ${CopyPreview.create(item.displayPath, 160)}:${range(item)}</b>" +
            "<br><span style=\"font-family:monospace\">${CopyPreview.create(item.code, 100)}</span>" +
            "<br><small>${time(item)} · ${status(source)}</small></html>"

    fun details(item: ContextCollectionItem, source: ContextCollectionSourceStatus?): String =
        CopySelectionBundle.message("collection.ui.metadata", item.captureNumber, time(item, true), item.displayPath,
            range(item), item.codeBytes, status(source))

    fun selectedIndex(items: List<ContextCollectionItem>, previousId: Long?, previousIndex: Int): Int {
        if (items.isEmpty()) return -1
        val retained = items.indexOfFirst { it.id == previousId }
        return if (retained >= 0) retained else if (previousIndex >= 0) previousIndex.coerceAtMost(items.lastIndex) else -1
    }

    private fun message(suffix: String) = CopySelectionBundle.message("collection.ui.source.$suffix")
}
