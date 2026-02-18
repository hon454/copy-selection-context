package com.github.hon454.copyselectioncontext

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
        
        val path = getPath(project, file)
        val lineRange = CopySelectionUtils.resolveLineRange(editor)
        val result = buildContent(path, lineRange, file, editor)
        copyToClipboard(result)
        
        CopySelectionNotifier.notify(project, result)
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    protected abstract fun getPath(project: Project, file: VirtualFile): String

    protected open fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor): String {
        return CopySelectionUtils.formatOutput(path, lineRange)
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
