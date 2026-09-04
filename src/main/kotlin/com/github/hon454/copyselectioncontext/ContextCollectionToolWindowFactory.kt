package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class ContextCollectionToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount != 0) return
        val panel = ContextCollectionPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.isCloseable = false
        content.setDisposer(panel)
        content.preferredFocusableComponent = panel.itemList
        toolWindow.contentManager.addContent(content)
    }

    companion object { const val ID = "Context Collection" }
}
