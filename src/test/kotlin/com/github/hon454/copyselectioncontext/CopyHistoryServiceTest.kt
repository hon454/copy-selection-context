package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyHistoryServiceTest {
    @Test
    fun `addEntry stores entry`() {
        val service = CopyHistoryService()

        service.addEntry("first")

        val entries = service.getEntries()
        assertEquals(1, entries.size)
        assertEquals("first", entries.first().content)
    }

    @Test
    fun `addEntry respects max size`() {
        val service = CopyHistoryService()

        service.addEntry("first", maxSize = 2)
        service.addEntry("second", maxSize = 2)
        service.addEntry("third", maxSize = 2)

        val entries = service.getEntries()
        assertEquals(2, entries.size)
        assertEquals("third", entries[0].content)
        assertEquals("second", entries[1].content)
        assertTrue(entries.none { it.content == "first" })
    }

    @Test
    fun `clear removes all entries`() {
        val service = CopyHistoryService()

        service.addEntry("first")
        service.addEntry("second")
        service.clear()

        assertTrue(service.getEntries().isEmpty())
    }

    @Test
    fun `getEntries returns newest first`() {
        val service = CopyHistoryService()

        service.addEntry("older")
        service.addEntry("newer")

        val entries = service.getEntries()
        assertEquals("newer", entries[0].content)
        assertEquals("older", entries[1].content)
    }
}
