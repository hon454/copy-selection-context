package com.github.hon454.copyselectioncontext

import java.time.Instant

/** Session identity survives rename; location records where this particular capture was taken. */
data class ContextCollectionSourceLocation(val sourceToken: Long, val url: String)

data class ContextCollectionItem(
    val id: Long,
    val sourceLocation: ContextCollectionSourceLocation,
    val absolutePath: String,
    val relativePath: String?,
    val displayPath: String,
    val filename: String,
    val language: String,
    val startLine: Int,
    val endLine: Int,
    val code: String,
    val captureNumber: Long,
    val capturedAt: Instant,
    val codeBytes: Int,
)

/** Lists returned by the collection are defensive, unmodifiable copies. */
data class ContextCollectionSnapshot(
    val items: List<ContextCollectionItem>,
    val rawCodeBytes: Long,
    val includeCode: Boolean,
    val revision: Long,
)

enum class ContextCollectionLimit { ITEM_COUNT, ITEM_BYTES, TOTAL_BYTES }

sealed interface ContextCollectionAddResult {
    data class Added(val added: Int, val duplicates: Int) : ContextCollectionAddResult
    data class Rejected(val limit: ContextCollectionLimit) : ContextCollectionAddResult
    data object InvalidContext : ContextCollectionAddResult
}

/** Ephemeral read view. Never retained by the store, including on rejection. */
internal data class ContextCollectionCandidate(
    val sourceLocation: ContextCollectionSourceLocation,
    val absolutePath: String,
    val relativePath: String?,
    val displayPath: String,
    val filename: String,
    val language: String,
    val startLine: Int,
    val endLine: Int,
    val text: CharSequence,
    val startOffset: Int = 0,
    val endOffset: Int = text.length,
)
