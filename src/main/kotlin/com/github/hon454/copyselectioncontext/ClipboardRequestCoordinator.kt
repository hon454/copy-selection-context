package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

internal class CopyResultRequest internal constructor(internal val id: Long)

enum class CopyNotPublishedReason { STALE, INVALIDATED, DISPOSED, CLIPBOARD_FAILURE, ALREADY_ATTEMPTED }
enum class CopyFeedbackEffect { ANALYTICS, GUTTER, HISTORY, NOTIFICATION, STATUS, REVIEW }
data class CopyFeedbackFailure(val effect: CopyFeedbackEffect, val exceptionType: String)
sealed interface CopyPublicationOutcome {
    data class NotPublished(val reason: CopyNotPublishedReason) : CopyPublicationOutcome
    data class Published(val feedbackFailures: List<CopyFeedbackFailure>) : CopyPublicationOutcome
}

/** Application lifetime ordering only: never retains callbacks, projects or copied data. */
@Service
internal class ClipboardRequestCoordinator {
    private var sequence = 0L
    private var attempted = false

    @Synchronized fun beginRequest(): CopyResultRequest {
        sequence = Math.incrementExact(sequence)
        attempted = false
        return CopyResultRequest(sequence)
    }

    @Synchronized fun isCurrent(request: CopyResultRequest): Boolean = request.id == sequence

    @Synchronized fun writeIfCurrent(
        request: CopyResultRequest,
        validate: () -> CopyNotPublishedReason?,
        write: () -> Unit,
    ): CopyNotPublishedReason? {
        if (!isCurrent(request)) return CopyNotPublishedReason.STALE
        if (attempted) return CopyNotPublishedReason.ALREADY_ATTEMPTED
        attempted = true
        validate()?.let { return it }
        if (!isCurrent(request)) return CopyNotPublishedReason.STALE
        return try { write(); null } catch (_: Exception) { CopyNotPublishedReason.CLIPBOARD_FAILURE }
    }

    companion object {
        fun getInstance(): ClipboardRequestCoordinator =
            ApplicationManager.getApplication().getService(ClipboardRequestCoordinator::class.java)

        /** Managed re-copy is clipboard-only and takes its place in the same application sequence. */
        fun recopy(content: String, isAlive: () -> Boolean = { true }): CopyPublicationOutcome {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val coordinator = getInstance()
            val request = coordinator.beginRequest()
            val failure = coordinator.writeIfCurrent(request,
                { if (isAlive()) null else CopyNotPublishedReason.DISPOSED },
                { CopyPasteManager.getInstance().setContents(StringSelection(content)) })
            return if (failure == null) CopyPublicationOutcome.Published(emptyList())
                else CopyPublicationOutcome.NotPublished(failure)
        }
    }
}
