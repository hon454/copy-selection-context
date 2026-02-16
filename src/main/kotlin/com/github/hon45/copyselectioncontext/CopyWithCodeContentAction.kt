package com.github.hon45.copyselectioncontext

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

class CopyWithCodeContentAction : CopySelectionBaseAction() {
    
    override fun getPath(project: Project, file: VirtualFile): String {
        val settings = CopySelectionSettings.getInstance()
        return when (settings.state.defaultPathType) {
            PathType.RELATIVE -> {
                val projectBasePath = project.basePath ?: return file.path
                val filePath = file.path
                if (filePath.startsWith(projectBasePath)) {
                    val relativePath = filePath.substring(projectBasePath.length)
                    if (relativePath.startsWith("/") || relativePath.startsWith("\\"))
                        relativePath.substring(1)
                    else relativePath
                } else filePath
            }
            PathType.ABSOLUTE -> file.path
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val result = buildMarkdownCodeBlock(project, file, editor)
        copyToClipboard(result)
        
        CopySelectionNotifier.notify(project, result)
        CopySelectionStatusBarWidget.update(result)
    }
    
    private fun buildMarkdownCodeBlock(project: Project, file: VirtualFile, editor: Editor): String {
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
        
        val code = getCodeContent(editor)
        val language = detectLanguage(file)
        val normalizedPath = path.replace("\\", "/")
        
        return "$normalizedPath:$lineRange\n```$language\n$code\n```"
    }
    
    private fun getCodeContent(editor: Editor): String {
        val selectionModel = editor.selectionModel
        
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText ?: ""
        } else {
            val document = editor.document
            val caretLine = editor.caretModel.logicalPosition.line
            val lineStart = document.getLineStartOffset(caretLine)
            val lineEnd = document.getLineEndOffset(caretLine)
            document.getText(TextRange(lineStart, lineEnd))
        }
    }
    
    private fun detectLanguage(file: VirtualFile): String {
        val fileTypeName = file.fileType.name
        return when (fileTypeName.lowercase()) {
            "kotlin" -> "kotlin"
            "java" -> "java"
            "c#" -> "csharp"
            "javascript" -> "javascript"
            "typescript" -> "typescript"
            "python" -> "python"
            "xml" -> "xml"
            "yaml" -> "yaml"
            "json" -> "json"
            "markdown" -> "markdown"
            "html" -> "html"
            "css" -> "css"
            "sql" -> "sql"
            "shell script" -> "bash"
            "text" -> "text"
            else -> ""
        }
    }
    
    private fun copyToClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
}
