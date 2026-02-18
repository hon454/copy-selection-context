package com.github.hon454.copyselectioncontext

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object CopySelectionNotifier {
    fun notify(project: Project?, message: String) {
        if (project == null) return
        if (!CopySelectionSettings.getInstance().state.enableNotification) return
        
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CopySelectionContext")
            .createNotification("✓ Copied: $message", NotificationType.INFORMATION)
            .notify(project)
    }
}
