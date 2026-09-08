package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class CopySelectionBaseAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val publisher = copyResultPublisher(project)
        val request = publisher.beginRequest()

        val settings = CopySelectionSettings.getInstance().state
        val includeCode = includeCode(settings)
        val path = getPath(project, file, settings)
        val contexts = CopySelectionUtils.captureSelectionContexts(path, file, editor, includeCode)
        if (contexts.isEmpty()) return
        val capturedContent = buildCapturedContent(contexts, settings, includeCode)

        publisher.publishIfCurrent(
            request = request,
            result = CopyResult(
                content = capturedContent.content,
                editor = editor,
                lineRanges = capturedContent.lineRanges,
                language = contexts.first().language,
            ),
            policy = CopyResultPolicy.STANDARD,
        )
    }

    internal open fun copyResultPublisher(project: Project): CopyResultPublisher =
        CopyResultPublisher.getInstance(project)
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    protected abstract fun getPath(
        project: Project,
        file: VirtualFile,
        settings: CopySelectionSettings.State,
    ): String

    protected open fun includeCode(settings: CopySelectionSettings.State): Boolean = false

    internal fun buildCapturedContent(contexts: List<SelectionContext>): CaretCopyResult {
        val settings = CopySelectionSettings.getInstance().state
        return buildCapturedContent(contexts, settings, includeCode(settings))
    }

    private fun buildCapturedContent(
        contexts: List<SelectionContext>,
        settings: CopySelectionSettings.State,
        includeCode: Boolean,
    ): CaretCopyResult = CaretCopyResult(
        content = CopySelectionUtils.joinCaretBlocks(contexts.map { context ->
            formatWithSettings(context, settings, includeCode)
        }),
        lineRanges = contexts.map(SelectionContext::lineNumbers),
    )

    private fun formatWithSettings(
        context: SelectionContext,
        settings: CopySelectionSettings.State,
        includeCode: Boolean,
    ): String {
        val formatter = OutputFormatterFactory.getFormatterForSettings(settings)
        val code = if (includeCode) {
            if (settings.codeTrimming) context.code.trim() else context.code
        } else {
            null
        }
        return formatter.format(context.toFormatContext(code))
    }
}

internal data class CaretCopyResult(
    val content: String,
    val lineRanges: List<Pair<Int, Int>>,
)
