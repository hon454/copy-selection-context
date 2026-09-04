package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.Disposable

@Service
@State(
    name = "CopySelectionSettings",
    storages = [Storage("CopySelectionPlugin.xml")]
)
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>, Disposable {

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
    private var outputOptions = currentOutputOptions()
    private var outputRevision = 0L
    private val outputSubscriptions = ContextCollectionSubscriptions<Long>()

    fun currentOutputOptions(): ContextCollectionOutputOptions = myState.let {
        ContextCollectionOutputOptions(it.outputFormat, it.customFormatTemplate, it.codeTrimming)
    }

    fun outputSettingsRevision(): Long = outputRevision

    fun subscribeOutputSettings(parent: Disposable, listener: (Long) -> Unit) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        outputSubscriptions.subscribe(parent, listener)
    }

    /** Call only after all committed fields have been applied; UI Reset has no signal. */
    fun outputSettingsCommitted() {
        val options = currentOutputOptions()
        if (options == outputOptions) return
        outputOptions = options
        outputRevision++
        outputSubscriptions.publish(outputRevision)
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        state.copyHistorySize = state.copyHistorySize.coerceIn(0, 100)
        if (state.outputFormat == LEGACY_GITHUB_FORMAT) {
            state.outputFormat = DEFAULT_OUTPUT_FORMAT
        }
        state.outputFormat = OutputFormatOption.fromKey(state.outputFormat).key
        val commit = {
            myState = state
            outputSettingsCommitted()
        }
        val application = ApplicationManager.getApplication()
        if (application != null && !application.isDispatchThread) application.invokeAndWait(commit)
        else commit()
    }

    override fun dispose() = outputSubscriptions.dispose()

    companion object {
        private const val LEGACY_GITHUB_FORMAT = "github"
        private const val DEFAULT_OUTPUT_FORMAT = "claude"

        fun getInstance(): CopySelectionSettings {
            return ApplicationManager.getApplication().getService(CopySelectionSettings::class.java)
        }
    }
}

enum class PathType {
    RELATIVE,
    ABSOLUTE
}
