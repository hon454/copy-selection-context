package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

object CopySelectionUtils {
    fun resolvePath(project: Project, file: VirtualFile, pathType: PathType): String {
        return when (pathType) {
            PathType.ABSOLUTE -> file.path
            PathType.RELATIVE -> {
                val projectBasePath = project.basePath ?: return file.path
                val filePath = file.path
                if (filePath.startsWith(projectBasePath)) {
                    val relativePath = filePath.substring(projectBasePath.length)
                    if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                        relativePath.substring(1)
                    } else {
                        relativePath
                    }
                } else {
                    filePath
                }
            }
        }
    }

    fun resolveLineRange(editor: Editor): String {
        val selectionModel = editor.selectionModel
        val document = editor.document
        return if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            if (startLine == endLine) "$startLine" else "$startLine-$endLine"
        } else {
            val currentLine = editor.caretModel.logicalPosition.line + 1
            "$currentLine"
        }
    }

    fun getCodeContent(editor: Editor): String {
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

    fun detectLanguage(file: VirtualFile): String {
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
            else -> file.extension?.lowercase().orEmpty()
        }
    }

    fun formatOutput(path: String, lineRange: String, code: String? = null, language: String? = null): String {
        val normalizedPath = path.replace("\\", "/")
        return if (code == null) {
            " @$normalizedPath#L$lineRange "
        } else {
            " @$normalizedPath#L$lineRange \n```$language\n$code\n```"
        }
    }
}
