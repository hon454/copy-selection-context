package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

open class CopySelectionContextAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        return CopySelectionUtils.resolvePath(project, file, CopySelectionSettings.getInstance().state.defaultPathType)
    }

    override fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor, project: Project?): String {
        val settings = CopySelectionSettings.getInstance().state
        val (startLine, endLine) = resolveLineNumbers(editor)
        return if (settings.includeCodeContent) {
            var code = getCodeContent(editor)
            code = applyCodeTrimming(code)
            val language = detectLanguage(file)
            formatWithSettings(path, startLine, endLine, file, code, language)
        } else {
            formatWithSettings(path, startLine, endLine, file)
        }
    }

    protected override fun buildContentForCaret(
        path: String,
        lineRange: String,
        startLine: Int,
        endLine: Int,
        file: VirtualFile,
        editor: Editor,
        caret: Caret,
        project: Project?,
    ): String {
        val settings = CopySelectionSettings.getInstance().state
        return if (settings.includeCodeContent) {
            var code = getCodeContent(editor, caret)
            code = applyCodeTrimming(code)
            val language = detectLanguage(file)
            formatWithSettings(path, startLine, endLine, file, code, language)
        } else {
            formatWithSettings(path, startLine, endLine, file)
        }
    }
}
