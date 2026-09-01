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
class CopyHistoryService : PersistentStateComponent<CopyHistoryService.State> {

    data class HistoryEntry(val content: String = "", val timestamp: Long = 0L)

    data class State(val entries: MutableList<HistoryEntry> = mutableListOf())

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun addEntry(content: String, maxSize: Int = 50) {
        val limit = maxSize.coerceAtLeast(0)
        if (limit == 0) {
            clear()
            return
        }

        myState.entries.add(0, HistoryEntry(content = content, timestamp = System.currentTimeMillis()))
        trimToSize(limit)
    }

    fun getEntries(): List<HistoryEntry> = myState.entries.toList()

    fun clear() {
        myState.entries.clear()
    }

    fun trimToSize(maxSize: Int) {
        val limit = maxSize.coerceAtLeast(0)
        if (myState.entries.size > limit) {
            myState.entries.subList(limit, myState.entries.size).clear()
        }
    }

    companion object {
        fun getInstance(project: Project): CopyHistoryService =
            project.getService(CopyHistoryService::class.java)

        fun trimOpenProjects(maxSize: Int) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                project.getService(CopyHistoryService::class.java)?.trimToSize(maxSize)
            }
        }
    }
}
