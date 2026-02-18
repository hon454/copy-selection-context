package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CopySelectionProjectSettings", storages = [Storage("copySelectionContext.xml")])
class CopySelectionProjectSettings : PersistentStateComponent<CopySelectionProjectSettings.State> {

    data class State(
        var useProjectSettings: Boolean = false,
        var outputFormat: String = "claude",
        var pathType: String = "relative"
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(project: Project): CopySelectionProjectSettings =
            project.getService(CopySelectionProjectSettings::class.java)
    }
}
