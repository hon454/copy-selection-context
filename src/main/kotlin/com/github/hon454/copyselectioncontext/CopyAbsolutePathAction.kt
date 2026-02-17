package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopyAbsolutePathAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        return CopySelectionUtils.resolvePath(project, file, PathType.ABSOLUTE)
    }
}
