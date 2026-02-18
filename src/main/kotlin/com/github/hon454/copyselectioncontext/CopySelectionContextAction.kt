package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopySelectionContextAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        val settings = CopySelectionSettings.getInstance().state
        return CopySelectionUtils.resolvePath(project, file, settings.defaultPathType)
    }

    override fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor): String {
        val settings = CopySelectionSettings.getInstance().state
        val (startLine, endLine) = resolveLineNumbers(editor)
        return if (settings.includeCodeContent) {
            var code = getCodeContent(editor)
            code = applyCodeTrimming(code)
            val language = detectLanguage(file)
            formatWithSettings(path, startLine, endLine, code, language)
        } else {
            formatWithSettings(path, startLine, endLine)
        }
    }
}
