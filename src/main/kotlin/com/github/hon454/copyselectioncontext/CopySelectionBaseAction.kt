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

        val result = if (caretCount > 1) {
            val blocks = mutableListOf<String>()
            editor.caretModel.runForEachCaret { caret ->
                val (startLine, endLine) = CopySelectionUtils.resolveLineNumbers(editor, caret)
                val lineRange = CopySelectionUtils.toLineRange(startLine, endLine)
                val block = buildContentForCaret(path, lineRange, startLine, endLine, file, editor, caret)
                blocks.add(block)
            }
            blocks.joinToString("\n\n")
        } else {
            val lineRange = CopySelectionUtils.resolveLineRange(editor)
            buildContent(path, lineRange, file, editor)
        }

        copyToClipboard(result)

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

    protected open fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor): String {
        val (startLine, endLine) = resolveLineNumbers(editor)
        return formatWithSettings(path, startLine, endLine)
    }

    protected open fun buildContentForCaret(
        path: String,
        lineRange: String,
        startLine: Int,
        endLine: Int,
        file: VirtualFile,
        editor: Editor,
        caret: Caret
    ): String {
        return buildContent(path, lineRange, file, editor)
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

    protected fun formatWithSettings(path: String, startLine: Int, endLine: Int, code: String? = null, language: String = ""): String {
        val settings = CopySelectionSettings.getInstance().state
        val formatter = OutputFormatterFactory.getFormatter(settings.outputFormat)
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
