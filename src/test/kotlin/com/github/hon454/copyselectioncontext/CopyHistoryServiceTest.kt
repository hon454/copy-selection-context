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

    @Test
    fun `entry byte budget accepts the exact boundary and rejects content above it`() {
        val service = CopyHistoryService()
        val belowBoundary = "a".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES - 1)
        val atBoundary = "b".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES)
        val aboveBoundary = "c".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES + 1)

        service.addEntry(belowBoundary, maxSize = 10)
        service.addEntry(atBoundary, maxSize = 10)
        service.addEntry(aboveBoundary, maxSize = 10)

        assertEquals(listOf(atBoundary, belowBoundary), service.getEntries().map { it.content })
    }

    @Test
    fun `entry byte budget uses UTF-8 bytes for Korean and emoji content`() {
        val prefix = "한😀"
        assertEquals(7, prefix.toByteArray(Charsets.UTF_8).size)
        val atBoundary = prefix + "a".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES - 7)
        val aboveBoundary = atBoundary + "한"
        val service = CopyHistoryService()

        service.addEntry(atBoundary, maxSize = 10)
        service.addEntry(aboveBoundary, maxSize = 10)

        assertEquals(listOf(atBoundary), service.getEntries().map { it.content })
    }

    @Test
    fun `total byte budget evicts the oldest entries deterministically`() {
        val service = CopyHistoryService()
        val entries = (1..9).map { index ->
            index.toString() + "x".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES - 1)
        }

        entries.forEach { service.addEntry(it, maxSize = 20) }

        assertEquals(entries.takeLast(8).reversed(), service.getEntries().map { it.content })
        assertEquals(
            CopyHistoryService.MAX_TOTAL_CONTENT_BYTES,
            service.getEntries().sumOf { it.content.toByteArray(Charsets.UTF_8).size },
        )
    }

    @Test
    fun `loadState discards oversized entries and enforces configured entry count`() {
        val retained = (1..9).map { index ->
            CopyHistoryService.HistoryEntry(
                content = index.toString() + "x".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES - 1),
                timestamp = index.toLong(),
            )
        }
        val oversized = CopyHistoryService.HistoryEntry(
            content = "z".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES + 1),
            timestamp = 10L,
        )
        val service = CopyHistoryService { 6 }

        service.loadState(CopyHistoryService.State((listOf(oversized) + retained).toMutableList()))

        assertEquals(retained.take(6), service.getEntries())
    }

    @Test
    fun `loadState clears existing entries when configured history size is zero`() {
        val service = CopyHistoryService { 0 }

        service.loadState(
            CopyHistoryService.State(
                mutableListOf(CopyHistoryService.HistoryEntry(content = "must-not-persist")),
            ),
        )

        assertTrue(service.getEntries().isEmpty())
    }

    @Test
    fun `loadState evicts oldest entries beyond the total byte budget`() {
        val entries = (1..9).map { index ->
            CopyHistoryService.HistoryEntry(
                content = index.toString() + "x".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES - 1),
                timestamp = index.toLong(),
            )
        }
        val service = CopyHistoryService { 20 }

        service.loadState(CopyHistoryService.State(entries.toMutableList()))

        assertEquals(entries.take(8), service.getEntries())
    }
}
