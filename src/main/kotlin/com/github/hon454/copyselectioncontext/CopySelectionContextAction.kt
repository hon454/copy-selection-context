package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

open class CopySelectionContextAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        return CopySelectionUtils.resolvePath(project, file, CopySelectionSettings.getInstance().state.defaultPathType)
    }

    override fun buildContent(context: SelectionContext): String {
        val settings = CopySelectionSettings.getInstance().state
        return formatWithSettings(context, includeCode = settings.includeCodeContent)
    }
}
