package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopyWithCodeContentAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        val settings = CopySelectionSettings.getInstance().state
        return CopySelectionUtils.resolvePath(project, file, settings.defaultPathType)
    }

    override fun buildContent(path: String, lineRange: String, file: VirtualFile, editor: Editor): String {
        val code = getCodeContent(editor)
        val language = detectLanguage(file)
        return CopySelectionUtils.formatOutput(path, lineRange, code, language)
    }
}
