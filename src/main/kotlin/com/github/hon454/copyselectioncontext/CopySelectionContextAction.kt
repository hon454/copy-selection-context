package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopySelectionContextAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        val appSettings = CopySelectionSettings.getInstance().state
        val projSettings = CopySelectionProjectSettings.getInstance(project).state
        val pathType = if (projSettings.useProjectSettings) {
            if (projSettings.pathType == "relative") PathType.RELATIVE else PathType.ABSOLUTE
        } else {
            appSettings.defaultPathType
        }
        return CopySelectionUtils.resolvePath(project, file, pathType)
    }

    override fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor, project: Project?): String {
        val settings = CopySelectionSettings.getInstance().state
        val (startLine, endLine) = resolveLineNumbers(editor)
        return if (settings.includeCodeContent) {
            var code = getCodeContent(editor)
            code = applyCodeTrimming(code)
            val language = detectLanguage(file)
            formatWithSettings(path, startLine, endLine, code, language, project)
        } else {
            formatWithSettings(path, startLine, endLine, project = project)
        }
    }
}
