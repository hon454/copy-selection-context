package com.github.hon454.copyselectioncontext

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
        var defaultPathType: PathType = PathType.ABSOLUTE,
        var includeCodeContent: Boolean = false,
        var enableNotification: Boolean = true,
        var outputFormat: String = "claude",
        var codeTrimming: Boolean = false,
        var copyHistorySize: Int = 10,
        var customFormatTemplate: String = "",
        var analyticsEnabled: Boolean = false
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
