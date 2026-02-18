package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CopySelectionHistory", storages = [Storage("copySelectionHistory.xml")])
class CopyHistoryService : PersistentStateComponent<CopyHistoryService.State> {

    data class HistoryEntry(val content: String = "", val timestamp: Long = 0L)

    data class State(val entries: MutableList<HistoryEntry> = mutableListOf())

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun addEntry(content: String, maxSize: Int = 50) {
        myState.entries.add(0, HistoryEntry(content = content, timestamp = System.currentTimeMillis()))
        if (myState.entries.size > maxSize) {
            myState.entries.removeAt(myState.entries.size - 1)
        }
    }

    fun getEntries(): List<HistoryEntry> = myState.entries.toList()

    fun clear() {
        myState.entries.clear()
    }

    companion object {
        fun getInstance(project: Project): CopyHistoryService =
            project.getService(CopyHistoryService::class.java)
    }
}
