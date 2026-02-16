package com.github.hon45.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopyRelativePathAction : CopySelectionBaseAction() {
    override fun getPath(project: Project, file: VirtualFile): String {
        val projectBasePath = project.basePath ?: return file.path
        val filePath = file.path
        
        return if (filePath.startsWith(projectBasePath)) {
            val relativePath = filePath.substring(projectBasePath.length)
            if (relativePath.startsWith("/") || relativePath.startsWith("\\"))
                relativePath.substring(1)
            else relativePath
        } else {
            filePath
        }
    }
}
