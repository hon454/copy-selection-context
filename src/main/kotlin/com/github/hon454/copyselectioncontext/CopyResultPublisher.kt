package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.datatransfer.StringSelection

internal data class CopyResult(
    val content: String,
    val editor: Editor? = null,
    val lineRanges: List<Pair<Int, Int>> = emptyList(),
    val language: String = "",
    val actualFormat: String? = null,
)

internal enum class CopyResultPolicy(
    val clipboard: Boolean,
    val analytics: Boolean,
    val gutterHighlight: Boolean,
    val history: Boolean,
    val notification: Boolean,
    val statusBar: Boolean,
    val reviewAccounting: Boolean,
) {
    STANDARD(
        clipboard = true,
        analytics = true,
        gutterHighlight = true,
        history = true,
        notification = true,
        statusBar = true,
        reviewAccounting = true,
    ),
    GIT_PERMALINK(
        clipboard = true,
        analytics = false,
        gutterHighlight = true,
        history = true,
        notification = true,
        statusBar = true,
        reviewAccounting = false,
    ),
    COLLECTION(true, true, false, false, true, true, true),
}

internal data class CopyResultSettings(
    val analyticsEnabled: Boolean,
    val outputFormat: String,
    val historySize: Int,
)

internal interface CopyResultSideEffects {
    fun writeClipboard(content: String)

    fun recordAnalytics(format: String, language: String)

    fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>)

    fun addToHistory(content: String, maxSize: Int)

    fun showNotification(content: String, isCurrent: () -> Boolean)

    fun updateStatusBar(content: String, isCurrent: () -> Boolean)

    fun recordReviewEligibleCopy()
}

@Service(Service.Level.PROJECT)
internal class CopyResultPublisher private constructor(
    private val sideEffects: CopyResultSideEffects,
    private val settingsProvider: () -> CopyResultSettings,
    private val coordinator: ClipboardRequestCoordinator,
    private val isAlive: () -> Boolean,
    private val assertDispatchThread: () -> Unit,
) {

    constructor(project: Project) : this(
        sideEffects = IntelliJCopyResultSideEffects(project),
        settingsProvider = {
            CopySelectionSettings.getInstance().state.let { settings ->
                CopyResultSettings(
                    analyticsEnabled = settings.analyticsEnabled,
                    outputFormat = settings.outputFormat,
                    historySize = settings.copyHistorySize,
                )
            }
        },
        coordinator = ClipboardRequestCoordinator.getInstance(),
        isAlive = { !project.isDisposed },
        assertDispatchThread = { ApplicationManager.getApplication().assertIsDispatchThread() },
    )

    fun beginRequest(): CopyResultRequest {
        assertDispatchThread()
        return coordinator.beginRequest()
    }

    fun publish(result: CopyResult, policy: CopyResultPolicy): CopyPublicationOutcome =
        publishOutcomeIfCurrent(beginRequest(), result, policy)

    fun publishIfCurrent(
        request: CopyResultRequest,
        result: CopyResult,
        policy: CopyResultPolicy,
    ): Boolean = publishOutcomeIfCurrent(request, result, policy) is CopyPublicationOutcome.Published

    fun isCurrent(request: CopyResultRequest): Boolean = isAlive() && coordinator.isCurrent(request)

    fun runIfCurrent(request: CopyResultRequest, action: () -> Unit): Boolean {
        assertDispatchThread()
        if (!isCurrent(request)) return false
        action()
        return true
    }

    fun publishOutcomeIfCurrent(
        request: CopyResultRequest,
        result: CopyResult,
        policy: CopyResultPolicy,
        validate: () -> Boolean = { true },
    ): CopyPublicationOutcome {
        assertDispatchThread()
        val settings = settingsProvider()
        val failure = coordinator.writeIfCurrent(request, {
            when {
                !isAlive() -> CopyNotPublishedReason.DISPOSED
                !validate() -> CopyNotPublishedReason.INVALIDATED
                else -> null
            }
        }, { if (policy.clipboard) sideEffects.writeClipboard(result.content) })
        if (failure != null) return CopyPublicationOutcome.NotPublished(failure)
        val failures = mutableListOf<CopyFeedbackFailure>()
        fun effect(kind: CopyFeedbackEffect, action: () -> Unit) {
            // A successful collection write owns its accounting even if an effect starts another copy.
            if (policy != CopyResultPolicy.COLLECTION && !isCurrent(request)) return
            try { action() } catch (failure: Exception) {
                failures += CopyFeedbackFailure(kind, failure.javaClass.simpleName)
            }
        }
        if (policy.analytics && settings.analyticsEnabled) {
            effect(CopyFeedbackEffect.ANALYTICS) { sideEffects.recordAnalytics(result.actualFormat ?: settings.outputFormat, result.language) }
        }
        if (policy.gutterHighlight && result.editor != null) {
            effect(CopyFeedbackEffect.GUTTER) { sideEffects.updateGutterHighlight(result.editor, result.lineRanges) }
        }
        if (policy.history) effect(CopyFeedbackEffect.HISTORY) { sideEffects.addToHistory(result.content, settings.historySize) }
        if (policy.notification) effect(CopyFeedbackEffect.NOTIFICATION) { sideEffects.showNotification(result.content) { isCurrent(request) } }
        if (policy.statusBar) effect(CopyFeedbackEffect.STATUS) { sideEffects.updateStatusBar(result.content) { isCurrent(request) } }
        if (policy.reviewAccounting) effect(CopyFeedbackEffect.REVIEW) { sideEffects.recordReviewEligibleCopy() }
        return CopyPublicationOutcome.Published(java.util.Collections.unmodifiableList(failures))
    }

    companion object {
        fun getInstance(project: Project): CopyResultPublisher =
            project.getService(CopyResultPublisher::class.java)

        internal fun createForTest(
            sideEffects: CopyResultSideEffects,
            coordinator: ClipboardRequestCoordinator = ClipboardRequestCoordinator(),
            isAlive: () -> Boolean = { true },
            settingsProvider: () -> CopyResultSettings,
        ): CopyResultPublisher = CopyResultPublisher(sideEffects, settingsProvider, coordinator, isAlive, {})
    }
}

private class IntelliJCopyResultSideEffects(private val project: Project) : CopyResultSideEffects {
    override fun writeClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }

    override fun recordAnalytics(format: String, language: String) {
        CopySelectionAnalytics.getInstance().recordCopy(format, language)
    }

    override fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>) {
        CopySelectionHighlighter.update(editor, lineRanges)
    }

    override fun addToHistory(content: String, maxSize: Int) {
        CopyHistoryService.getInstance(project).addEntry(content, maxSize)
    }

    override fun showNotification(content: String, isCurrent: () -> Boolean) {
        if (!isCurrent()) return
        CopySelectionNotifier.notify(project, content)
    }

    override fun updateStatusBar(content: String, isCurrent: () -> Boolean) {
        if (!isCurrent()) return
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        if (!isCurrent()) return
        (statusBar?.getWidget(CopySelectionStatusBarWidget.ID) as? CopySelectionStatusBarWidget)?.update(content)
    }

    override fun recordReviewEligibleCopy() {
        if (project.isDisposed) return
        CopySelectionReviewService.getInstance().recordSuccessfulCopy(project)
    }
}
