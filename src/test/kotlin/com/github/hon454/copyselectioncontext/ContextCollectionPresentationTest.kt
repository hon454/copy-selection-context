package com.github.hon454.copyselectioncontext

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextCollectionPresentationTest {
    private fun item(id: Long, code: String = "val x = 1") = ContextCollectionItem(id,
        ContextCollectionSourceLocation(1, "file:///A.kt"), "/A.kt", "A.kt", "<A&>.kt", "A.kt", "kotlin",
        3, 4, code, id, Instant.parse("2026-09-04T09:00:00.123Z"), code.toByteArray().size)

    @Test fun `selection follows stable identity and removal chooses nearest survivor`() {
        val items = listOf(item(3), item(1), item(2))
        assertEquals(1, ContextCollectionPresentation.selectedIndex(items, 1, 0))
        assertEquals(2, ContextCollectionPresentation.selectedIndex(items, 9, 5))
        assertEquals(0, ContextCollectionPresentation.selectedIndex(items, 9, 0))
        assertEquals(-1, ContextCollectionPresentation.selectedIndex(items, null, -1))
        assertEquals(-1, ContextCollectionPresentation.selectedIndex(emptyList(), 1, 0))
    }

    @Test fun `row escapes markup bounds Unicode preview and retains capture identity`() {
        val capture = item(42, "<script>🙂\n".repeat(200))
        val row = ContextCollectionPresentation.row(capture, ContextCollectionSourceStatus(true, true, true))
        assertTrue(row.contains("#42"))
        assertTrue(row.contains("&lt;A&amp;&gt;.kt:3–4"))
        assertTrue(row.contains("&lt;script&gt;"))
        assertFalse(row.contains("<script>"))
        assertTrue(row.length < 600)
        val details = ContextCollectionPresentation.details(capture, null)
        assertTrue(details.contains(".123"))
        assertTrue(details.contains("<A&>.kt"))
        assertTrue(details.contains("No source changes observed"))
    }
}
