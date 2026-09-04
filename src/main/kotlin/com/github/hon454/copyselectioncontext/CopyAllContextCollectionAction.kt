package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class CopyAllContextCollectionAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isDisposed) ContextCollectionCopyCommand.getInstance(project).execute()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed &&
            ContextCollectionService.getInstance(project).snapshot().items.isNotEmpty()
    }
}
