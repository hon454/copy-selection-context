package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

@Service(Service.Level.PROJECT)
@State(
    name = "CopySelectionHistory",
    storages = [
        Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED),
        Storage(value = "copySelectionHistory.xml", deprecated = true)
    ]
)
class CopyHistoryService internal constructor(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val historySizeProvider: () -> Int,
) : PersistentStateComponent<CopyHistoryService.State> {

    constructor() : this(historySizeProvider = { CopySelectionSettings.getInstance().state.copyHistorySize })

    data class HistoryEntry(var content: String = "", var timestamp: Long = 0L)

    data class State(var entries: MutableList<HistoryEntry> = mutableListOf())

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = State(normalizeEntries(state.entries, historySizeProvider()).toMutableList())
    }

    fun addEntry(content: String, maxSize: Int = 50) {
        val limit = maxSize.coerceAtLeast(0)
        if (limit == 0) {
            clear()
            return
        }

        if (content.utf8Size() > MAX_ENTRY_CONTENT_BYTES) {
            trimToSize(limit)
            return
        }

        val newestEntry = myState.entries.firstOrNull()
        if (newestEntry?.content == content) {
            newestEntry.timestamp = currentTimeMillis()
            trimToSize(limit)
            return
        }

        myState.entries.add(0, HistoryEntry(content = content, timestamp = currentTimeMillis()))
        trimToSize(limit)
    }

    fun getEntries(): List<HistoryEntry> = myState.entries.toList()

    fun clear() {
        myState.entries.clear()
    }

    fun trimToSize(maxSize: Int) {
        myState.entries = normalizeEntries(myState.entries, maxSize).toMutableList()
    }

    private fun normalizeEntries(entries: List<HistoryEntry>, maxSize: Int): List<HistoryEntry> {
        val limit = maxSize.coerceAtLeast(0)
        if (limit == 0) return emptyList()

        val normalized = ArrayList<HistoryEntry>(minOf(entries.size, limit))
        var totalBytes = 0
        var previousContent: String? = null
        for (entry in entries) {
            if (normalized.size == limit) break

            val entryBytes = entry.content.utf8Size()
            val isConsecutiveDuplicate = entry.content == previousContent
            previousContent = entry.content
            if (isConsecutiveDuplicate) continue
            if (entryBytes > MAX_ENTRY_CONTENT_BYTES) continue
            if (totalBytes + entryBytes > MAX_TOTAL_CONTENT_BYTES) break

            normalized.add(entry)
            totalBytes += entryBytes
        }
        return normalized
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    companion object {
        internal const val MAX_ENTRY_CONTENT_BYTES = 256 * 1024
        internal const val MAX_TOTAL_CONTENT_BYTES = 2 * 1024 * 1024

        fun getInstance(project: Project): CopyHistoryService =
            project.getService(CopyHistoryService::class.java)

        fun trimOpenProjects(maxSize: Int) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                project.getService(CopyHistoryService::class.java)?.trimToSize(maxSize)
            }
        }
    }
}
