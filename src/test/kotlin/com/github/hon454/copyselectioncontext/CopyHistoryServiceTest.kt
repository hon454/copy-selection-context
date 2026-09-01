package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StoragePathMacros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `zero size disables history and removes existing entries`() {
        val service = CopyHistoryService()
        service.addEntry("existing")

        service.addEntry("must not persist", maxSize = 0)

        assertTrue(service.getEntries().isEmpty())
    }

    @Test
    fun `trimToSize immediately keeps the newest entries deterministically`() {
        val service = CopyHistoryService()
        listOf("first", "second", "third", "fourth").forEach { content ->
            service.addEntry(content, maxSize = 10)
        }

        service.trimToSize(2)

        assertEquals(listOf("fourth", "third"), service.getEntries().map { it.content })
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

    @Test
    fun `history persists only in the local workspace storage`() {
        val state = CopyHistoryService::class.java.getAnnotation(State::class.java)
        val workspaceStorage = state.storages.first()

        assertEquals(StoragePathMacros.WORKSPACE_FILE, workspaceStorage.value)
        assertEquals(RoamingType.DISABLED, workspaceStorage.roamingType)
        assertFalse(workspaceStorage.deprecated)
    }

    @Test
    fun `legacy project history storage is migrated and cleaned up`() {
        val state = CopyHistoryService::class.java.getAnnotation(State::class.java)
        val legacyStorage = state.storages.single { it.value == "copySelectionHistory.xml" }

        assertTrue(legacyStorage.deprecated)
    }

    @Test
    fun `popup clear-all action removes every entry`() {
        val service = CopyHistoryService()
        service.addEntry("first")
        service.addEntry("second")
        val clearAll = CopyHistoryPopup.createItems(service.getEntries()).last()

        CopyHistoryPopup.handleSelection(service, clearAll) { error("Clear must not copy content") }

        assertEquals(CopyHistoryPopup.PopupItem.ClearAll, clearAll)
        assertTrue(service.getEntries().isEmpty())
    }

    @Test
    fun `history preserves complete copied content`() {
        val service = CopyHistoryService()
        val content = "src/App.kt:10-20\n<script>😀 ${"x".repeat(10_000)}</script>"

        service.addEntry(content)

        assertEquals(content, service.getEntries().single().content)
    }
}
