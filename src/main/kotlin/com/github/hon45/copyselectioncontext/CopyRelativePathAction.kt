package com.github.hon45.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopyRelativePathAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        return CopySelectionUtils.resolvePath(project, file, PathType.RELATIVE)
    }
}
