package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Future
import java.util.concurrent.CancellationException

sealed interface ContextCollectionOutputState {
    val key: ContextCollectionOutputKey
    data class Calculating(override val key: ContextCollectionOutputKey) : ContextCollectionOutputState
    data class Computed(override val key: ContextCollectionOutputKey, val result: ContextCollectionOutputResult) : ContextCollectionOutputState
}

/** One current payload per project. Content/settings invalidate it; source status deliberately does not. */
@Service(Service.Level.PROJECT)
class ContextCollectionOutputService private constructor(
    private val project: Project,
    private val collection: ContextCollectionService,
    private val settings: CopySelectionSettings,
    private val background: (() -> Unit) -> Future<*>,
    private val dispatch: (() -> Unit) -> Unit,
) : Disposable {
    private val lifetime = Disposer.newDisposable("Collection output")
    private val subscriptions = ContextCollectionSubscriptions<ContextCollectionOutputState>()
    @Volatile private var state: ContextCollectionOutputState = ContextCollectionOutputState.Calculating(currentKey())
    @Volatile private var disposed = false
    private var computation: Future<*>? = null

    constructor(project: Project) : this(project, ContextCollectionService.getInstance(project), CopySelectionSettings.getInstance(),
        { ApplicationManager.getApplication().executeOnPooledThread(it) },
        { ApplicationManager.getApplication().invokeLater(it) })

    init {
        ApplicationManager.getApplication().assertIsDispatchThread()
        collection.subscribe(lifetime) { refresh() }
        settings.subscribeOutputSettings(lifetime) { refresh() }
        calculate(collection.snapshot(), state.key)
    }

    fun snapshot(): ContextCollectionOutputState = state

    fun subscribe(parent: Disposable, listener: (ContextCollectionOutputState) -> Unit) {
        assertLive()
        subscriptions.subscribe(parent, listener)
    }

    /** Defensive tuple comparison also detects direct state edits that omitted the committed signal. */
    fun refresh(): ContextCollectionOutputState {
        assertLive()
        val key = currentKey()
        if (state.key != key) {
            computation?.cancel(true)
            state = ContextCollectionOutputState.Calculating(key)
            subscriptions.publish(state)
            calculate(collection.snapshot(), key)
        }
        return state
    }

    fun isCurrent(key: ContextCollectionOutputKey): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return !disposed && !project.isDisposed && currentKey() == key
    }

    private fun currentKey(): ContextCollectionOutputKey = collection.snapshot().let {
        val (revision, options) = settings.outputSettingsSnapshot()
        ContextCollectionOutputKey(it.revision, revision, options, it.includeCode)
    }

    /** Serializes background loadState replacement with the final validation/write, never feedback. */
    internal fun serializePublication(transaction: () -> CopyNotPublishedReason?): CopyNotPublishedReason? =
        settings.withOutputLock(transaction)

    private fun calculate(snapshot: ContextCollectionSnapshot, key: ContextCollectionOutputKey) {
        computation = background {
            val result = try { ContextCollectionFormatter.format(snapshot, key.options) }
                catch (_: CancellationException) { return@background }
            if (disposed) return@background
            dispatch {
                if (isCurrent(key)) {
                    computation = null
                    state = ContextCollectionOutputState.Computed(key, result)
                    subscriptions.publish(state)
                }
            }
        }
    }

    private fun assertLive() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(!disposed)
    }

    override fun dispose() {
        disposed = true
        computation?.cancel(true)
        computation = null
        state = ContextCollectionOutputState.Calculating(state.key)
        subscriptions.dispose()
        Disposer.dispose(lifetime)
    }

    companion object {
        fun getInstance(project: Project): ContextCollectionOutputService = project.getService(ContextCollectionOutputService::class.java)
        internal fun createForTest(project: Project, collection: ContextCollectionService, settings: CopySelectionSettings,
            background: (() -> Unit) -> Future<*>, dispatch: (() -> Unit) -> Unit): ContextCollectionOutputService =
            ContextCollectionOutputService(project, collection, settings, background, dispatch)
    }
}
