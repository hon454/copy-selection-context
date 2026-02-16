package com.github.hon45.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

abstract class CopySelectionBaseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val result = buildPathString(project, file, editor)
        copyToClipboard(result)
        
        CopySelectionNotifier.notify(project, result)
        CopySelectionStatusBarWidget.update(result)
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }
    
    protected abstract fun getPath(project: Project, file: VirtualFile): String
    
    private fun buildPathString(project: Project, file: VirtualFile, editor: Editor): String {
        val selectionModel = editor.selectionModel
        val document = editor.document
        val path = getPath(project, file)
        
        val lineRange = if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            if (startLine == endLine) "$startLine" else "$startLine-$endLine"
        } else {
            val currentLine = editor.caretModel.logicalPosition.line + 1
            "$currentLine"
        }
        
        val normalizedPath = path.replace("\\", "/")
        return "$normalizedPath:$lineRange"
    }
    
    private fun copyToClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
}
