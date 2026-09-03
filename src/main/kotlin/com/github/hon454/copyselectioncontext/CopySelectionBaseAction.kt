package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class CopySelectionBaseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val path = getPath(project, file)
        val contexts = CopySelectionUtils.captureSelectionContexts(path, file, editor)
        if (contexts.isEmpty()) return
        val capturedContent = buildCapturedContent(contexts)

        copyResultPublisher(project).publish(
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
}

internal data class CaretCopyResult(
    val content: String,
    val lineRanges: List<Pair<Int, Int>>,
)
