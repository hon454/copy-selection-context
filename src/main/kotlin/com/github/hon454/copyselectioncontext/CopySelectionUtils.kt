package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

object CopySelectionUtils {
    private val LANGUAGE_MAP = mapOf(
        "kotlin" to "kotlin",
        "java" to "java",
        "c#" to "csharp",
        "javascript" to "javascript",
        "typescript" to "typescript",
        "python" to "python",
        "xml" to "xml",
        "yaml" to "yaml",
        "json" to "json",
        "markdown" to "markdown",
        "html" to "html",
        "css" to "css",
        "sql" to "sql",
        "shell script" to "bash",
        "text" to "text",
        "go" to "go",
        "rust" to "rust",
        "c" to "c",
        "c++" to "cpp",
        "swift" to "swift",
        "dart" to "dart",
        "ruby" to "ruby",
        "php" to "php",
        "groovy" to "groovy",
        "scala" to "scala",
        "perl" to "perl",
        "lua" to "lua",
        "r" to "r",
        "jsx harmony" to "jsx",
        "typescript jsx" to "tsx",
        "vue.js" to "vue",
        "svelte" to "svelte",
        "scss" to "scss",
        "less" to "less",
        "toml" to "toml",
        "dockerfile" to "dockerfile",
        "hcl" to "hcl",
        "graphql" to "graphql",
        "protocol buffer" to "protobuf"
    )

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

    fun resolveLineNumbers(editor: Editor, caret: Caret): Pair<Int, Int> {
        val document = editor.document
        return if (caret.hasSelection()) {
            val startLine = document.getLineNumber(caret.selectionStart) + 1
            val endLine = document.getLineNumber(caret.selectionEnd) + 1
            Pair(startLine, endLine)
        } else {
            val currentLine = caret.logicalPosition.line + 1
            Pair(currentLine, currentLine)
        }
    }

    fun resolveLineRange(editor: Editor, caret: Caret): String {
        val (startLine, endLine) = resolveLineNumbers(editor, caret)
        return toLineRange(startLine, endLine)
    }

    fun resolveLineRanges(editor: Editor): List<String> {
        val caretModel = editor.caretModel
        if (caretModel.caretCount <= 1) {
            return listOf(resolveLineRange(editor))
        }

        val ranges = mutableListOf<String>()
        caretModel.runForEachCaret { caret ->
            ranges.add(resolveLineRange(editor, caret))
        }
        return ranges
    }

    fun toLineRange(startLine: Int, endLine: Int): String {
        return if (startLine == endLine) "$startLine" else "$startLine-$endLine"
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
        val fileTypeName = file.fileType.name.lowercase()
        return LANGUAGE_MAP[fileTypeName] ?: file.extension?.lowercase().orEmpty()
    }

    fun formatOutput(path: String, lineRange: String, code: String? = null, language: String? = null): String {
        val startLine = lineRange.substringBefore("-").toIntOrNull() ?: 0
        val endLine = lineRange.substringAfter("-", lineRange).toIntOrNull() ?: startLine
        val context = FormatContext(
            path = path,
            startLine = startLine,
            endLine = endLine,
            code = code,
            language = language ?: "null"
        )
        return ClaudeCodeFormatter().format(context)
    }
}
