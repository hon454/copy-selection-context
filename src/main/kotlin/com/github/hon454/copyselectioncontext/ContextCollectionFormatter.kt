package com.github.hon454.copyselectioncontext

import java.time.format.DateTimeFormatterBuilder
import java.util.Collections
import java.util.concurrent.CancellationException

data class ContextCollectionOutputOptions(
    val format: String,
    val template: String,
    val trimCode: Boolean,
)

data class ContextCollectionOutputKey(
    val contentRevision: Long,
    val settingsRevision: Long,
    val options: ContextCollectionOutputOptions,
    val includeCode: Boolean,
)

enum class ContextCollectionWarning { SNAPSHOT_LABELS_ABSENT, HISTORICAL_CODE_ABSENT, SIZE }

sealed interface ContextCollectionOutputResult {
    data class Ready(
        val payload: String,
        val bytes: Int,
        val itemCount: Int,
        val actualFormat: String,
        val language: String,
        val warnings: Set<ContextCollectionWarning>,
    ) : ContextCollectionOutputResult
    data object Empty : ContextCollectionOutputResult
    data class BlankItem(val captureNumber: Long, val actualFormat: String) : ContextCollectionOutputResult
    data object AboveHardLimit : ContextCollectionOutputResult
}

/** Pure, bounded formatter. No source lookup, mutable settings, UI, or copy side effects. */
object ContextCollectionFormatter {
    const val WARNING_BYTES = 256 * 1024
    const val MAX_BYTES = 4 * 1024 * 1024
    private val timestamp = DateTimeFormatterBuilder().appendInstant(3).toFormatter()

    fun format(
        snapshot: ContextCollectionSnapshot,
        options: ContextCollectionOutputOptions,
        checkCancelled: () -> Unit = { if (Thread.currentThread().isInterrupted) throw CancellationException() },
    ): ContextCollectionOutputResult {
        if (snapshot.items.isEmpty()) return ContextCollectionOutputResult.Empty
        val formatter = if (options.format == "template") OutputFormatterFactory.getTemplateFormatter(options.template)
            else OutputFormatterFactory.getFormatter(options.format)
        val conflicts = snapshot.items.groupingBy { Triple(it.sourceLocation, it.startLine, it.endLine) }.eachCount()
        val warnings = mutableSetOf<ContextCollectionWarning>()
        val output = BoundedOutput(checkCancelled)
        try {
            snapshot.items.forEachIndexed { index, item ->
                checkCancelled()
                if (index != 0) output.append("\n\n")
                val conflict = conflicts.getValue(Triple(item.sourceLocation, item.startLine, item.endLine)) > 1
                val code = if (!snapshot.includeCode) null else if (options.trimCode) item.code.trim() else item.code
                if (conflict) {
                    if (formatter is TemplateFormatter) warnings += ContextCollectionWarning.SNAPSHOT_LABELS_ABSENT
                    else output.append("[Snapshot #${item.captureNumber} · ${timestamp.format(item.capturedAt)}]\n")
                    if (code.isNullOrBlank() || formatter is TemplateFormatter &&
                        !options.template.ifBlank { TemplateFormatter.PRESET_PATH_AND_RANGE }.contains("{code}")) {
                        warnings += ContextCollectionWarning.HISTORICAL_CODE_ABSENT
                    }
                }
                output.beginItem()
                val context = FormatContext(item.displayPath, item.startLine, item.endLine, code, item.language, item.filename)
                if (formatter is TemplateFormatter) formatter.appendTo(context, output::append)
                else output.append(formatter.format(context))
                if (!output.itemHasText) return ContextCollectionOutputResult.BlankItem(item.captureNumber, formatter.key)
            }
        } catch (_: OutputOverflow) {
            return ContextCollectionOutputResult.AboveHardLimit
        }
        if (output.bytes > WARNING_BYTES) warnings += ContextCollectionWarning.SIZE
        val languages = snapshot.items.map { it.language }.filter { it.isNotBlank() }.distinct()
        return ContextCollectionOutputResult.Ready(
            output.toString(), output.bytes, snapshot.items.size, formatter.key,
            when (languages.size) { 0 -> ""; 1 -> languages.single(); else -> "mixed" },
            Collections.unmodifiableSet(warnings.toSet()),
        )
    }

    private class OutputOverflow : RuntimeException(null, null, false, false)

    /** Stateful UTF-8 accounting also joins surrogate pairs across template fragments. */
    private class BoundedOutput(private val checkCancelled: () -> Unit) {
        private val builder = StringBuilder()
        private var highSurrogate = false
        var bytes = 0
            private set
        var itemHasText = false
            private set
        fun beginItem() { itemHasText = false }
        fun append(text: String) = append(text, 0, text.length)
        fun append(text: String, start: Int, end: Int) {
            for (index in start until end) {
                if (index % 4096 == 0) checkCancelled()
                val char = text[index]
                bytes += when {
                    highSurrogate && char.isLowSurrogate() -> 3 // prior high counted as replacement byte
                    char.isSurrogate() -> 1 // JVM UTF-8 replacement for malformed UTF-16
                    char.code < 0x80 -> 1
                    char.code < 0x800 -> 2
                    else -> 3
                }
                highSurrogate = char.isHighSurrogate()
                if (bytes > MAX_BYTES) throw OutputOverflow()
                if (!char.isWhitespace()) itemHasText = true
                builder.append(char)
            }
        }
        override fun toString(): String = builder.toString()
    }
}
