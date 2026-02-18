package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class CopySelectionStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = CopySelectionStatusBarWidget.ID
    override fun getDisplayName() = "Copy Selection Context"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = CopySelectionStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}
