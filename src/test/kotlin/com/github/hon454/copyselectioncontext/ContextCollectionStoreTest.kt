package com.github.hon454.copyselectioncontext

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContextCollectionStoreTest {
    private val time = Instant.parse("2026-09-04T07:30:00Z")
    private val store = ContextCollectionStore { time }

    @Test fun `exact duplicates preserve revision order bytes and frozen display path`() {
        add(candidate("timeout = 10"), candidate("next", line = 2))
        val before = store.snapshot
        val duplicate = candidate("timeout = 10").copy(displayPath = "/project/A.kt")
        assertEquals(ContextCollectionAddResult.Added(0, 1), add(duplicate))
        assertSame(before, store.snapshot)
        assertEquals("A.kt", store.snapshot.items.first().displayPath)
        add(duplicate.copy(text = "timeout = 30"))
        assertEquals(listOf("timeout = 10", "next", "timeout = 30"), store.snapshot.items.map { it.code })
        assertEquals("/project/A.kt", store.snapshot.items.last().displayPath)
        assertEquals(store.snapshot.items.first().sourceLocation, store.snapshot.items.last().sourceLocation)
    }

    @Test fun `capture identity includes location filename language range and exact code`() {
        val original = candidate(" text ")
        val variations = listOf(
            original.copy(text = "text", endOffset = 4),
            original.copy(filename = "B.kt"),
            original.copy(language = "java"),
            original.copy(endLine = 2),
            original.copy(sourceLocation = ContextCollectionSourceLocation(1, "file:///B.kt")),
            original.copy(sourceLocation = ContextCollectionSourceLocation(2, "file:///A.kt")),
        )
        add(original, *variations.toTypedArray())
        assertEquals(7, store.snapshot.items.size)
    }

    @Test fun `overlapping ranges remain independent and within-batch duplicates skip`() {
        assertEquals(ContextCollectionAddResult.Added(2, 1), add(
            candidate("first\nsecond").copy(endLine = 2), candidate("second", line = 2),
            candidate("first\nsecond").copy(endLine = 2),
        ))
        assertEquals(listOf(1, 2), store.snapshot.items.map { it.startLine })
    }

    @Test fun `numbers and times survive moves and no numbers recycle after removal clear or rejection`() {
        add(candidate("a"), candidate("b"))
        val first = store.snapshot.items.first()
        assertTrue(store.move(first.id, 1))
        assertEquals(first, store.snapshot.items.last())
        assertEquals(listOf(2L, 1L), store.snapshot.items.map { it.captureNumber })
        assertTrue(store.remove(first.id))
        store.clear()
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES),
            add(candidate("x".repeat(ContextCollectionStore.MAX_ITEM_BYTES + 1))))
        add(candidate("c"))
        assertEquals(3L, store.snapshot.items.single().captureNumber)
        assertEquals(time, store.snapshot.items.single().capturedAt)
    }

    @Test fun `immutable snapshots and invalid mutations do not expose mutable lists`() {
        add(candidate("a"), candidate("b"))
        val before = store.snapshot
        assertFailsWith<UnsupportedOperationException> {
            (before.items as MutableList<ContextCollectionItem>).clear()
        }
        assertFalse(store.remove(123))
        assertFalse(store.move(1, -1))
        assertFalse(store.move(2, 1))
        assertFalse(store.move(1, 5))
        assertFalse(store.setIncludeCode(true))
        assertSame(before, store.snapshot)
        assertTrue(store.setIncludeCode(false))
        assertEquals(before.revision + 1, store.snapshot.revision)
        assertTrue(before.includeCode)
        assertTrue(store.clear())
        assertFalse(store.clear())
        assertEquals(2, before.items.size)
    }

    @Test fun `item UTF-8 threshold permits below and equality rejects above`() {
        val limit = ContextCollectionStore.MAX_ITEM_BYTES
        for (size in listOf(limit - 1, limit, limit + 1)) {
            val subject = ContextCollectionStore()
            val result = subject.add(sequenceOf(candidate("x".repeat(size))))
            if (size <= limit) {
                assertEquals(ContextCollectionAddResult.Added(1, 0), result)
                assertEquals(size.toLong(), subject.snapshot.rawCodeBytes)
            } else assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES), result)
        }
    }

    @Test fun `UTF-8 count matches JVM encoding for BMP supplementary and malformed text`() {
        listOf("abc", "한글", "é", "😀", "\uD800", "\uDC00", "\uD800x", "a😀한\uD800").forEach { text ->
            assertEquals(text.toByteArray(Charsets.UTF_8).size,
                ContextCollectionStore.boundedUtf8Bytes(text, 0, text.length))
        }
        val exact = "😀".repeat(ContextCollectionStore.MAX_ITEM_BYTES / 4)
        assertEquals(ContextCollectionAddResult.Added(1, 0), add(candidate(exact)))
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES), add(candidate(exact + "é")))
        assertEquals(ContextCollectionStore.MAX_ITEM_BYTES.toLong(), store.snapshot.rawCodeBytes)
    }

    @Test fun `total UTF-8 budget is atomic below at and above boundary after deduplication`() {
        val block = "x".repeat(ContextCollectionStore.MAX_ITEM_BYTES)
        repeat(7) { add(candidate(block, line = it + 1)) }
        add(candidate(block.dropLast(1), line = 8))
        assertEquals(ContextCollectionStore.MAX_TOTAL_BYTES - 1, store.snapshot.rawCodeBytes)
        add(candidate("a", line = 9))
        assertEquals(ContextCollectionStore.MAX_TOTAL_BYTES, store.snapshot.rawCodeBytes)
        val before = store.snapshot
        assertEquals(ContextCollectionAddResult.Added(0, 1), add(candidate(block, line = 1)))
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.TOTAL_BYTES), add(candidate("b", line = 10)))
        assertSame(before, store.snapshot)
        store.remove(before.items.last().id)
        val beforeBatch = store.snapshot
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.TOTAL_BYTES),
            add(candidate("a", line = 9), candidate("b", line = 10)))
        assertSame(beforeBatch, store.snapshot)
    }

    @Test fun `100 items allow duplicates but reject new entries and whole multi-candidate batch`() {
        repeat(99) { add(candidate("$it", line = it + 1)) }
        val before = store.snapshot
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_COUNT),
            add(candidate("99", line = 100), candidate("100", line = 101)))
        assertSame(before, store.snapshot)
        add(candidate("99", line = 100))
        assertEquals(ContextCollectionAddResult.Added(0, 2), add(candidate("0"), candidate("99", line = 100)))
        assertEquals(100, store.snapshot.items.size)
    }

    @Test fun `oversized text rejects before reading or materializing and stops lazy batch`() {
        val unreadable = object : CharSequence {
            override val length = ContextCollectionStore.MAX_ITEM_BYTES + 1
            override fun get(index: Int): Char = error("must not read obvious overflow")
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("must not materialize")
        }
        val batch = sequence {
            yield(candidate("" ).copy(text = unreadable, endOffset = unreadable.length))
            error("must not evaluate rest of rejected batch")
        }
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES), store.add(batch))
        assertEquals(0L, store.snapshot.revision)
    }

    @Test fun `encoding overflow stops before materialization and empty text stays valid`() {
        var reads = 0
        val text = object : CharSequence {
            override val length = ContextCollectionStore.MAX_ITEM_BYTES
            override fun get(index: Int): Char { reads++; return '한' }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("must not materialize")
        }
        assertEquals(ContextCollectionAddResult.Rejected(ContextCollectionLimit.ITEM_BYTES),
            add(candidate("").copy(text = text, endOffset = text.length)))
        assertTrue(reads < text.length)
        add(candidate(""))
        assertEquals(0L, store.snapshot.rawCodeBytes)
    }

    private fun add(vararg candidates: ContextCollectionCandidate) = store.add(candidates.asSequence())
    private fun candidate(code: String, line: Int = 1) = ContextCollectionCandidate(
        ContextCollectionSourceLocation(1, "file:///A.kt"), "/project/A.kt", "A.kt", "A.kt",
        "A.kt", "kotlin", line, line, code,
    )
}
