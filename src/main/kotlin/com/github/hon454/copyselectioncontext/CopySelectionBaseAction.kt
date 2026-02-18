package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

abstract class CopySelectionBaseAction : AnAction() {
    companion object {
        private var lastHighlighter: RangeHighlighter? = null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val path = getPath(project, file)
        val caretCount = editor.caretModel.caretCount

        val result = if (caretCount > 1) {
            val blocks = mutableListOf<String>()
            editor.caretModel.runForEachCaret { caret ->
                val (startLine, endLine) = CopySelectionUtils.resolveLineNumbers(editor, caret)
                val lineRange = CopySelectionUtils.toLineRange(startLine, endLine)
                val block = buildContentForCaret(path, lineRange, startLine, endLine, file, editor, caret, project)
                blocks.add(block)
            }
            CopySelectionUtils.joinCaretBlocks(blocks)
        } else {
            val lineRange = CopySelectionUtils.resolveLineRange(editor)
            buildContent(path, lineRange, file, editor, project)
        }

        copyToClipboard(result)

        val settings = CopySelectionSettings.getInstance().state
        if (settings.analyticsEnabled) {
            CopySelectionAnalytics.getInstance()?.recordCopy(settings.outputFormat)
        }

        lastHighlighter?.let { highlighter ->
            editor.markupModel.removeHighlighter(highlighter)
        }
        lastHighlighter = null

        val (gutterStartLine, gutterEndLine) = resolveLineNumbers(editor)
        val startOffset = editor.document.getLineStartOffset(gutterStartLine - 1)
        val endOffset = editor.document.getLineEndOffset(gutterEndLine - 1)
        lastHighlighter = editor.markupModel.addRangeHighlighter(
            startOffset, endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.LINES_IN_RANGE
        ).also { it.gutterIconRenderer = CopySelectionGutterIconRenderer() }

        val historyService = project.getService(CopyHistoryService::class.java)
        val maxSize = CopySelectionSettings.getInstance().state.copyHistorySize
        historyService?.addEntry(result, maxSize)

        CopySelectionNotifier.notify(project, result)
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        (statusBar?.getWidget(CopySelectionStatusBarWidget.ID) as? CopySelectionStatusBarWidget)?.update(result)
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    protected abstract fun getPath(project: Project, file: VirtualFile): String

    protected open fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor, project: Project? = null): String {
        val (startLine, endLine) = resolveLineNumbers(editor)
        return formatWithSettings(path, startLine, endLine, project = project)
    }

    protected open fun buildContentForCaret(
        path: String,
        lineRange: String,
        startLine: Int,
        endLine: Int,
        file: VirtualFile,
        editor: Editor,
        caret: Caret,
        project: Project? = null
    ): String {
        return buildContent(path, lineRange, file, editor, project)
    }

    protected fun resolveLineNumbers(editor: Editor): Pair<Int, Int> {
        val selectionModel = editor.selectionModel
        val document = editor.document
        return if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            Pair(startLine, endLine)
        } else {
            val currentLine = editor.caretModel.logicalPosition.line + 1
            Pair(currentLine, currentLine)
        }
    }

    protected fun formatWithSettings(path: String, startLine: Int, endLine: Int, code: String? = null, language: String = "", project: Project? = null): String {
        val appSettings = CopySelectionSettings.getInstance().state
        val outputFormat = if (project != null) {
            val projSettings = CopySelectionProjectSettings.getInstance(project).state
            if (projSettings.useProjectSettings) projSettings.outputFormat else appSettings.outputFormat
        } else {
            appSettings.outputFormat
        }
        val formatter = OutputFormatterFactory.getFormatter(outputFormat)
        val context = FormatContext(path = path, startLine = startLine, endLine = endLine, code = code, language = language)
        return formatter.format(context)
    }

    protected fun getCodeContent(editor: Editor): String {
        return CopySelectionUtils.getCodeContent(editor)
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
