package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

open class CopySelectionContextAction : CopySelectionBaseAction() {
    override fun getPath(
        project: Project,
        file: VirtualFile,
        settings: CopySelectionSettings.State,
    ): String {
        return CopySelectionUtils.resolvePath(project, file, settings.defaultPathType)
    }

    override fun includeCode(settings: CopySelectionSettings.State): Boolean = settings.includeCodeContent
}
