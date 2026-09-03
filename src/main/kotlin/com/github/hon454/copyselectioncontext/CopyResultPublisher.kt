package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.datatransfer.StringSelection

internal data class CopyResult(
    val content: String,
    val editor: Editor,
    val lineRanges: List<Pair<Int, Int>>,
    val language: String = "",
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
}

internal class CopyResultRequest internal constructor(internal val id: Long)

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

    fun showNotification(content: String)

    fun updateStatusBar(content: String)

    fun recordReviewEligibleCopy()
}

@Service(Service.Level.PROJECT)
internal class CopyResultPublisher private constructor(
    private val sideEffects: CopyResultSideEffects,
    private val settingsProvider: () -> CopyResultSettings,
) {
    private val requestLock = Any()
    private var latestRequestId = 0L

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
    )

    fun beginRequest(): CopyResultRequest = synchronized(requestLock) {
        CopyResultRequest(nextRequestId())
    }

    fun publish(result: CopyResult, policy: CopyResultPolicy) {
        synchronized(requestLock) {
            nextRequestId()
            publishSideEffects(result, policy)
        }
    }

    fun publishIfCurrent(
        request: CopyResultRequest,
        result: CopyResult,
        policy: CopyResultPolicy,
    ): Boolean = synchronized(requestLock) {
        if (request.id != latestRequestId) return@synchronized false
        publishSideEffects(result, policy)
        true
    }

    fun runIfCurrent(request: CopyResultRequest, action: () -> Unit): Boolean = synchronized(requestLock) {
        if (request.id != latestRequestId) return@synchronized false
        action()
        true
    }

    private fun nextRequestId(): Long {
        latestRequestId++
        return latestRequestId
    }

    private fun publishSideEffects(result: CopyResult, policy: CopyResultPolicy) {
        val settings = settingsProvider()
        if (policy.clipboard) sideEffects.writeClipboard(result.content)
        if (policy.analytics && settings.analyticsEnabled) {
            sideEffects.recordAnalytics(settings.outputFormat, result.language)
        }
        if (policy.gutterHighlight) {
            sideEffects.updateGutterHighlight(result.editor, result.lineRanges)
        }
        if (policy.history) sideEffects.addToHistory(result.content, settings.historySize)
        if (policy.notification) sideEffects.showNotification(result.content)
        if (policy.statusBar) sideEffects.updateStatusBar(result.content)
        if (policy.reviewAccounting) sideEffects.recordReviewEligibleCopy()
    }

    companion object {
        fun getInstance(project: Project): CopyResultPublisher =
            project.getService(CopyResultPublisher::class.java)

        internal fun createForTest(
            sideEffects: CopyResultSideEffects,
            settingsProvider: () -> CopyResultSettings,
        ): CopyResultPublisher = CopyResultPublisher(sideEffects, settingsProvider)
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

    override fun showNotification(content: String) {
        CopySelectionNotifier.notify(project, content)
    }

    override fun updateStatusBar(content: String) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        (statusBar?.getWidget(CopySelectionStatusBarWidget.ID) as? CopySelectionStatusBarWidget)?.update(content)
    }

    override fun recordReviewEligibleCopy() {
        CopySelectionReviewService.getInstance().recordSuccessfulCopy(project)
    }
}
