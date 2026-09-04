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

    private val outputLock = Any()
    @Volatile private var myState = State()
    private var outputOptions = readOutputOptions()
    private var outputRevision = 0L
    private val outputSubscriptions = ContextCollectionSubscriptions<Long>()
    @Volatile private var disposed = false

    private fun readOutputOptions(): ContextCollectionOutputOptions = myState.let {
        ContextCollectionOutputOptions(it.outputFormat, it.customFormatTemplate, it.codeTrimming)
    }

    fun currentOutputOptions(): ContextCollectionOutputOptions = withOutputLock { readOutputOptions() }
    fun outputSettingsRevision(): Long = withOutputLock { outputRevision }
    fun outputSettingsSnapshot(): Pair<Long, ContextCollectionOutputOptions> = withOutputLock { outputRevision to readOutputOptions() }

    internal fun <T> withOutputLock(action: () -> T): T = synchronized(outputLock, action)

    fun subscribeOutputSettings(parent: Disposable, listener: (Long) -> Unit) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        outputSubscriptions.subscribe(parent, listener)
    }

    /** Call only after all committed fields have been applied; UI Reset has no signal. */
    fun outputSettingsCommitted() {
        val revision = withOutputLock { updateOutputRevision() } ?: return
        notifyOutputSettings(revision)
    }

    private fun updateOutputRevision(): Long? {
        val options = readOutputOptions()
        if (options == outputOptions) return null
        outputOptions = options
        outputRevision++
        return outputRevision
    }

    private fun notifyOutputSettings(revision: Long) {
        val application = ApplicationManager.getApplication()
        val notify = { if (!disposed) outputSubscriptions.publish(revision) }
        if (application != null && !application.isDispatchThread) application.invokeLater(notify)
        else notify()
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        val restored = state.copy()
        restored.copyHistorySize = restored.copyHistorySize.coerceIn(0, 100)
        if (restored.outputFormat == LEGACY_GITHUB_FORMAT) {
            restored.outputFormat = DEFAULT_OUTPUT_FORMAT
        }
        restored.outputFormat = OutputFormatOption.fromKey(restored.outputFormat).key
        // loadState may run during service initialization under a background read action.
        // Restore synchronously without waiting for EDT; only observer delivery is dispatched.
        val revision = withOutputLock {
            myState = restored
            updateOutputRevision()
        } ?: return
        notifyOutputSettings(revision)
    }

    override fun dispose() {
        disposed = true
        outputSubscriptions.dispose()
    }

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
