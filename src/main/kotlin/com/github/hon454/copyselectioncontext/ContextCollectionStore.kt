package com.github.hon454.copyselectioncontext

import java.time.Instant
import java.util.Collections

/** Pure transaction engine. Its owner serializes mutations; readers use the volatile snapshot. */
internal class ContextCollectionStore(private val clock: () -> Instant = Instant::now) {
    @Volatile
    var snapshot = ContextCollectionSnapshot(emptyList(), 0, true, 0)
        private set
    private var nextNumber = 1L

    fun add(candidates: Sequence<ContextCollectionCandidate>): ContextCollectionAddResult {
        val pending = mutableListOf<ContextCollectionItem>()
        var bytes = snapshot.rawCodeBytes
        var duplicates = 0
        for (candidate in candidates) {
            if (!candidate.valid()) return ContextCollectionAddResult.InvalidContext
            // Every UTF-16 code unit requires at least one UTF-8 byte, including replacement.
            if (candidate.endOffset - candidate.startOffset > MAX_ITEM_BYTES) {
                return ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES)
            }
            if ((snapshot.items.asSequence() + pending.asSequence()).any { candidate.matches(it) }) {
                duplicates++
                continue
            }
            if (snapshot.items.size + pending.size >= MAX_ITEMS) {
                return ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_COUNT)
            }
            val codeBytes = boundedUtf8Bytes(candidate.text, candidate.startOffset, candidate.endOffset)
            if (codeBytes > MAX_ITEM_BYTES) {
                return ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES)
            }
            if (bytes + codeBytes > MAX_TOTAL_BYTES) {
                return ContextCollectionAddResult.Rejected(ContextCollectionLimit.TOTAL_BYTES)
            }
            bytes += codeBytes
            // Materialization only happens after all individual and running batch budgets pass.
            pending.add(ContextCollectionItem(
                id = 0,
                sourceLocation = candidate.sourceLocation,
                absolutePath = candidate.absolutePath,
                relativePath = candidate.relativePath,
                displayPath = candidate.displayPath,
                filename = candidate.filename,
                language = candidate.language,
                startLine = candidate.startLine,
                endLine = candidate.endLine,
                code = candidate.text.subSequence(candidate.startOffset, candidate.endOffset).toString(),
                captureNumber = 0,
                capturedAt = Instant.EPOCH,
                codeBytes = codeBytes,
            ))
        }
        if (pending.isNotEmpty()) {
            val accepted = pending.map { item ->
                val number = nextNumber++
                item.copy(id = number, captureNumber = number, capturedAt = clock())
            }
            publish(snapshot.items + accepted, bytes)
        }
        return ContextCollectionAddResult.Added(pending.size, duplicates)
    }

    fun remove(id: Long): Boolean {
        val item = snapshot.items.find { it.id == id } ?: return false
        publish(snapshot.items.filterNot { it.id == id }, snapshot.rawCodeBytes - item.codeBytes)
        return true
    }

    fun move(id: Long, direction: Int): Boolean {
        if (direction != -1 && direction != 1) return false
        val from = snapshot.items.indexOfFirst { it.id == id }
        val to = from + direction
        if (from < 0 || to !in snapshot.items.indices) return false
        val items = snapshot.items.toMutableList()
        Collections.swap(items, from, to)
        publish(items)
        return true
    }

    fun clear(): Boolean {
        if (snapshot.items.isEmpty()) return false
        publish(emptyList(), 0)
        return true
    }

    fun setIncludeCode(value: Boolean): Boolean {
        if (snapshot.includeCode == value) return false
        snapshot = snapshot.copy(includeCode = value, revision = snapshot.revision + 1)
        return true
    }

    fun dispose() {
        snapshot = ContextCollectionSnapshot(emptyList(), 0, true, snapshot.revision + 1)
    }

    private fun publish(items: List<ContextCollectionItem>, bytes: Long = snapshot.rawCodeBytes) {
        snapshot = snapshot.copy(
            items = Collections.unmodifiableList(ArrayList(items)),
            rawCodeBytes = bytes,
            revision = snapshot.revision + 1,
        )
    }

    private fun ContextCollectionCandidate.valid(): Boolean =
        startOffset >= 0 && endOffset >= startOffset && endOffset <= text.length &&
            startLine > 0 && endLine >= startLine

    private fun ContextCollectionCandidate.matches(item: ContextCollectionItem): Boolean =
        sourceLocation == item.sourceLocation && filename == item.filename && language == item.language &&
            startLine == item.startLine && endLine == item.endLine &&
            endOffset - startOffset == item.code.length &&
            item.code.indices.all { text[startOffset + it] == item.code[it] }

    companion object {
        const val MAX_ITEMS = 100
        const val MAX_ITEM_BYTES = 256 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024

        /** Matches JVM UTF-8: an unpaired surrogate is replaced with the one-byte question mark. */
        internal fun boundedUtf8Bytes(text: CharSequence, start: Int, end: Int): Int {
            var bytes = 0
            var index = start
            while (index < end && bytes <= MAX_ITEM_BYTES) {
                val char = text[index++]
                bytes += when {
                    char.code < 0x80 -> 1
                    char.code < 0x800 -> 2
                    char.isHighSurrogate() && index < end && text[index].isLowSurrogate() -> {
                        index++
                        4
                    }
                    char.isSurrogate() -> 1
                    else -> 3
                }
            }
            return bytes
        }
    }
}
