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
            .createNotification(
                notificationText(message),
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    fun notifyPermalinkFailure(project: Project?, reason: GitPermalinkFailureReason) {
        if (project == null) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CopySelectionContext")
            .createNotification(
                permalinkFailureText(reason),
                NotificationType.ERROR
            )
            .notify(project)
    }

    internal fun notificationText(message: String): String =
        CopySelectionBundle.message("notification.copied", CopyPreview.notification(message))

    internal fun permalinkFailureText(reason: GitPermalinkFailureReason): String = CopySelectionBundle.message(
        when (reason) {
            GitPermalinkFailureReason.MISSING_VCS_ROOT -> "notification.permalink.failed.missing.vcs.root"
            GitPermalinkFailureReason.UNRESOLVED_GIT_METADATA -> "notification.permalink.failed.git.metadata"
            GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE ->
                "notification.permalink.failed.git.config.include.io"
            GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_CYCLE ->
                "notification.permalink.failed.git.config.include.cycle"
            GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_DEPTH_EXCEEDED ->
                "notification.permalink.failed.git.config.include.depth"
            GitPermalinkFailureReason.UNSUPPORTED_REMOTE_HOST -> "notification.permalink.failed.remote.host"
            GitPermalinkFailureReason.OUT_OF_ROOT_FILE -> "notification.permalink.failed.out.of.root"
            GitPermalinkFailureReason.IO_FAILURE -> "notification.permalink.failed.io"
            GitPermalinkFailureReason.UNEXPECTED_FAILURE -> "notification.permalink.failed.unexpected"
        }
    )
}
