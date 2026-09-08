package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** Project-owned error boundary. Queued reports capture request/lifetime checks without a copied payload. */
@Service(Service.Level.PROJECT)
internal class CopyFailureReporter private constructor(
    private val coordinator: ClipboardRequestCoordinator,
    private val isAlive: () -> Boolean,
    private val dispatch: (() -> Unit) -> Unit,
    private val showError: (() -> Boolean) -> Unit,
) {
    constructor(project: Project) : this(
        ClipboardRequestCoordinator.getInstance(),
        { !project.isDisposed },
        { ApplicationManager.getApplication().invokeLater(it) },
        { isCurrent -> CopySelectionNotifier.notifyClipboardFailure(project, isCurrent) },
    )

    fun report(request: CopyResultRequest, outcome: CopyPublicationOutcome, isOwnerAlive: () -> Boolean = { true }) {
        if (outcome != CopyPublicationOutcome.NotPublished(CopyNotPublishedReason.CLIPBOARD_FAILURE)) return
        fun isCurrent() = isAlive() && isOwnerAlive() && coordinator.isCurrent(request)
        if (!isCurrent() || !coordinator.claimFailureReport(request)) return
        dispatch {
            if (isCurrent()) showError(::isCurrent)
        }
    }

    /** A single clipboard-only attempt; no success feedback, retry, or payload retention. */
    fun recopy(content: String, isOwnerAlive: () -> Boolean = { true }): CopyPublicationOutcome {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val request = coordinator.beginRequest()
        val outcome = ClipboardRequestCoordinator.recopy(request, content) { isAlive() && isOwnerAlive() }
        report(request, outcome, isOwnerAlive)
        return outcome
    }

    companion object {
        fun getInstance(project: Project): CopyFailureReporter = project.getService(CopyFailureReporter::class.java)

        internal fun createForTest(
            coordinator: ClipboardRequestCoordinator,
            isAlive: () -> Boolean = { true },
            dispatch: (() -> Unit) -> Unit,
            showError: (() -> Boolean) -> Unit,
        ): CopyFailureReporter = CopyFailureReporter(coordinator, isAlive, dispatch, showError)
    }
}
