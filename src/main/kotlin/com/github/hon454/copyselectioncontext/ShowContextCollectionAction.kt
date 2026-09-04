package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

class ShowContextCollectionAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project?.isDisposed == false }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project?.takeUnless { it.isDisposed } ?: return
        ToolWindowManager.getInstance(project).getToolWindow(ContextCollectionToolWindowFactory.ID)?.activate(null)
    }
}
