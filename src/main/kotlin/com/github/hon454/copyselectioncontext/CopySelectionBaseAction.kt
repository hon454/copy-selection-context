package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

abstract class CopySelectionBaseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val path = getPath(project, file)
        val contexts = CopySelectionUtils.captureSelectionContexts(path, file, editor)
        if (contexts.isEmpty()) return
        val copyResult = buildCapturedContent(contexts)
        val result = copyResult.content

        copyToClipboard(result)

        val appSettings = CopySelectionSettings.getInstance().state
        if (appSettings.analyticsEnabled) {
            CopySelectionAnalytics.getInstance().recordCopy(
                format = appSettings.outputFormat,
                language = contexts.first().language,
            )
        }

        CopySelectionHighlighter.update(editor, copyResult.lineRanges)

        val historyService = project.getService(CopyHistoryService::class.java)
        val maxSize = CopySelectionSettings.getInstance().state.copyHistorySize
        historyService?.addEntry(result, maxSize)

        showNotification(project, result)
        updateStatusBar(project, result)
        recordSuccessfulCopy(project)
    }

    protected open fun showNotification(project: Project, content: String) {
        CopySelectionNotifier.notify(project, content)
    }

    protected open fun recordSuccessfulCopy(project: Project) {
        CopySelectionReviewService.getInstance().recordSuccessfulCopy(project)
    }

    protected open fun updateStatusBar(project: Project, content: String) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        (statusBar?.getWidget(CopySelectionStatusBarWidget.ID) as? CopySelectionStatusBarWidget)?.update(content)
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    protected abstract fun getPath(project: Project, file: VirtualFile): String

    protected open fun buildContent(context: SelectionContext): String = formatWithSettings(context)

    internal fun buildCapturedContent(contexts: List<SelectionContext>): CaretCopyResult = CaretCopyResult(
        content = CopySelectionUtils.joinCaretBlocks(contexts.map(::buildContent)),
        lineRanges = contexts.map(SelectionContext::lineNumbers),
    )

    protected fun formatWithSettings(
        context: SelectionContext,
        includeCode: Boolean = false,
    ): String {
        val settings = CopySelectionSettings.getInstance().state
        val formatter = OutputFormatterFactory.getFormatterForSettings(settings)
        val code = if (includeCode) {
            if (settings.codeTrimming) context.code.trim() else context.code
        } else {
            null
        }
        return formatter.format(context.toFormatContext(code))
    }
    
    private fun copyToClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
}

internal data class CaretCopyResult(
    val content: String,
    val lineRanges: List<Pair<Int, Int>>,
)
