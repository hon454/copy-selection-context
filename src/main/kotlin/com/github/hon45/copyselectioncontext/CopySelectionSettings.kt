package com.github.hon45.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
    name = "CopySelectionSettings",
    storages = [Storage("CopySelectionPlugin.xml")]
)
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State> {

    data class State(
        var defaultPathType: PathType = PathType.RELATIVE
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): CopySelectionSettings {
            return ApplicationManager.getApplication().getService(CopySelectionSettings::class.java)
        }
    }
}

enum class PathType {
    RELATIVE,
    ABSOLUTE
}
