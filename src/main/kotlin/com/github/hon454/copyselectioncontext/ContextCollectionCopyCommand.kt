package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer

/** Shared Find Action/tool-window command; confirmations always refer to one immutable prepared result. */
@Service(Service.Level.PROJECT)
class ContextCollectionCopyCommand private constructor(
    private val project: Project,
    private val output: ContextCollectionOutputService,
    private val publisher: CopyResultPublisher,
    private val confirm: (ContextCollectionOutputResult.Ready) -> Boolean,
    private val report: (String) -> Unit,
    private val dispatch: (() -> Unit) -> Unit,
) : Disposable {
    private var pending: Disposable? = null
    private var disposed = false

    constructor(project: Project) : this(project, ContextCollectionOutputService.getInstance(project), CopyResultPublisher.getInstance(project),
        { ready -> confirmCopy(project, ready) },
        { message -> Messages.showWarningDialog(project, message, CopySelectionBundle.message("collection.copy.title")) },
        { ApplicationManager.getApplication().invokeLater(it) })

    fun execute() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (disposed || project.isDisposed) return
        pending?.let(Disposer::dispose)
        val request = publisher.beginRequest()
        val initial = output.refresh()
        val key = initial.key
        val owner = Disposer.newDisposable("Pending collection copy")
        pending = owner
        var scheduled = false
        fun accept(state: ContextCollectionOutputState) {
            if (scheduled || state.key == key && state is ContextCollectionOutputState.Calculating) return
            scheduled = true
            // Never open a modal loop or mutate while a service is notifying subscribers.
            dispatch {
                if (pending !== owner) return@dispatch
                pending = null
                Disposer.dispose(owner)
                if (disposed || !publisher.isCurrent(request)) return@dispatch
                if (!output.isCurrent(key) || state.key != key) {
                    report(CopySelectionBundle.message("collection.copy.invalidated"))
                    return@dispatch
                }
                val result = (state as ContextCollectionOutputState.Computed).result
                if (result !is ContextCollectionOutputResult.Ready) {
                    report(errorMessage(result))
                    return@dispatch
                }
                if (result.warnings.isNotEmpty() && !confirm(result)) return@dispatch
                val outcome = publisher.publishOutcomeIfCurrent(request,
                    CopyResult(result.payload, language = result.language, actualFormat = result.actualFormat),
                    CopyResultPolicy.COLLECTION, output::serializePublication, { !disposed && output.isCurrent(key) })
                if (outcome is CopyPublicationOutcome.NotPublished && publisher.isCurrent(request)) {
                    when (outcome.reason) {
                        CopyNotPublishedReason.INVALIDATED -> report(CopySelectionBundle.message("collection.copy.invalidated"))
                        CopyNotPublishedReason.CLIPBOARD_FAILURE -> report(CopySelectionBundle.message("collection.copy.failed"))
                        else -> Unit
                    }
                }
            }
        }
        output.subscribe(owner, ::accept)
        accept(initial)
    }

    override fun dispose() {
        disposed = true
        pending?.let(Disposer::dispose)
        pending = null
    }

    companion object {
        fun getInstance(project: Project): ContextCollectionCopyCommand = project.getService(ContextCollectionCopyCommand::class.java)

        fun confirmationMessage(ready: ContextCollectionOutputResult.Ready): String = buildList {
            add(CopySelectionBundle.message("collection.copy.confirm.summary", ready.bytes, ready.itemCount))
            ready.warnings.sortedBy { it.ordinal }.forEach {
                add(CopySelectionBundle.message("collection.copy.warning.${it.name.lowercase(java.util.Locale.ROOT)}"))
            }
        }.joinToString("\n\n")

        fun errorMessage(result: ContextCollectionOutputResult): String = when (result) {
            ContextCollectionOutputResult.Empty -> CopySelectionBundle.message("collection.copy.empty")
            is ContextCollectionOutputResult.BlankItem -> CopySelectionBundle.message("collection.copy.blank", result.captureNumber, result.actualFormat)
            ContextCollectionOutputResult.AboveHardLimit -> CopySelectionBundle.message("collection.copy.overflow")
            is ContextCollectionOutputResult.Ready -> ""
        }

        private fun confirmCopy(project: Project, ready: ContextCollectionOutputResult.Ready): Boolean =
            Messages.showDialog(project, confirmationMessage(ready), CopySelectionBundle.message("collection.copy.title"),
                arrayOf(CopySelectionBundle.message("collection.copy.anyway"), CopySelectionBundle.message("collection.copy.cancel")),
                1, Messages.getWarningIcon()) == 0

        internal fun createForTest(project: Project, output: ContextCollectionOutputService, publisher: CopyResultPublisher,
            confirm: (ContextCollectionOutputResult.Ready) -> Boolean, report: (String) -> Unit,
            dispatch: (() -> Unit) -> Unit): ContextCollectionCopyCommand =
            ContextCollectionCopyCommand(project, output, publisher, confirm, report, dispatch)
    }
}
