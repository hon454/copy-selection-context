package com.github.hon45.copyselectioncontext

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
        return if (settings.includeCodeContent) {
            val code = getCodeContent(editor)
            val language = detectLanguage(file)
            CopySelectionUtils.formatOutput(path, lineRange, code, language)
        } else {
            CopySelectionUtils.formatOutput(path, lineRange)
        }
    }
}
