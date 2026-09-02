package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
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
        val caretCount = editor.caretModel.caretCount

        val copyResult = if (caretCount > 1) {
            buildMultiCaretContent(path, file, editor, project)
        } else {
            val (startLine, endLine) = resolveLineNumbers(editor)
            val lineRange = CopySelectionUtils.toLineRange(startLine, endLine)
            CaretCopyResult(
                content = buildContent(path, lineRange, file, editor, project),
                lineRanges = listOf(Pair(startLine, endLine)),
            )
        }
        val result = copyResult.content

        copyToClipboard(result)

        val appSettings = CopySelectionSettings.getInstance().state
        if (appSettings.analyticsEnabled) {
            CopySelectionAnalytics.getInstance().recordCopy(
                format = appSettings.outputFormat,
                language = detectLanguage(file),
            )
        }

        CopySelectionHighlighter.update(editor, copyResult.lineRanges)

        val historyService = project.getService(CopyHistoryService::class.java)
        val maxSize = CopySelectionSettings.getInstance().state.copyHistorySize
        historyService?.addEntry(result, maxSize)

        showNotification(project, result)
        updateStatusBar(project, result)
    }

    protected open fun showNotification(project: Project, content: String) {
        CopySelectionNotifier.notify(project, content)
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

    protected open fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor, project: Project? = null): String {
        val (startLine, endLine) = resolveLineNumbers(editor)
        return formatWithSettings(path, startLine, endLine, file)
    }

    protected open fun buildContentForCaret(
        path: String,
        lineRange: String,
        startLine: Int,
        endLine: Int,
        file: VirtualFile,
        editor: Editor,
        caret: Caret,
        project: Project? = null,
    ): String {
        return formatWithSettings(path, startLine, endLine, file)
    }

    internal fun buildMultiCaretContent(
        path: String,
        file: VirtualFile,
        editor: Editor,
        project: Project? = null,
    ): CaretCopyResult {
        val blocks = mutableListOf<String>()
        val lineRanges = mutableListOf<Pair<Int, Int>>()
        editor.caretModel.runForEachCaret { caret ->
            val (startLine, endLine) = CopySelectionUtils.resolveLineNumbers(editor, caret)
            val lineRange = CopySelectionUtils.toLineRange(startLine, endLine)
            lineRanges.add(Pair(startLine, endLine))
            blocks.add(buildContentForCaret(path, lineRange, startLine, endLine, file, editor, caret, project))
        }
        return CaretCopyResult(CopySelectionUtils.joinCaretBlocks(blocks), lineRanges)
    }

    protected fun resolveLineNumbers(editor: Editor): Pair<Int, Int> {
        return CopySelectionUtils.resolveLineNumbers(editor)
    }

    protected fun formatWithSettings(
        path: String,
        startLine: Int,
        endLine: Int,
        file: VirtualFile,
        code: String? = null,
        language: String = ""
    ): String {
        val settings = CopySelectionSettings.getInstance().state
        val formatter = OutputFormatterFactory.getFormatterForSettings(settings)
        val context = FormatContext(
            path = path,
            startLine = startLine,
            endLine = endLine,
            code = code,
            language = language,
            filename = file.name
        )
        return formatter.format(context)
    }

    protected fun getCodeContent(editor: Editor): String {
        return CopySelectionUtils.getCodeContent(editor)
    }

    protected fun getCodeContent(editor: Editor, caret: Caret): String {
        return CopySelectionUtils.getCodeContent(editor, caret)
    }

    protected fun detectLanguage(file: VirtualFile): String {
        return CopySelectionUtils.detectLanguage(file)
    }

    protected fun applyCodeTrimming(code: String): String {
        val settings = CopySelectionSettings.getInstance().state
        return if (settings.codeTrimming) code.trim() else code
    }
    
    private fun copyToClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
}

internal data class CaretCopyResult(
    val content: String,
    val lineRanges: List<Pair<Int, Int>>,
)
