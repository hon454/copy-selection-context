package com.github.hon454.copyselectioncontext

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object CopySelectionNotifier {
    internal fun notifyClipboardFailure(project: Project, isCurrent: () -> Boolean) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("CopySelectionContext")
            .createNotification(CopySelectionBundle.message("notification.clipboard.failed"), NotificationType.ERROR)
        // Notification construction may initialize services; check again at the visible boundary.
        if (isCurrent()) notification.notify(project)
    }

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

    fun notifyPermalinkFailure(project: Project?, reason: GitPermalinkFailureReason, isCurrent: () -> Boolean = { true }) {
        if (project == null || project.isDisposed || !isCurrent()) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("CopySelectionContext")
            .createNotification(
                permalinkFailureText(reason),
                NotificationType.ERROR
            )
        if (!project.isDisposed && isCurrent()) notification.notify(project)
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
            GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE -> "notification.permalink.failed.git.unavailable"
            GitPermalinkFailureReason.GIT_EXECUTION_FAILED -> "notification.permalink.failed.git.execution"
            GitPermalinkFailureReason.GIT_TIMEOUT -> "notification.permalink.failed.git.timeout"
            GitPermalinkFailureReason.GIT_OUTPUT_LIMIT -> "notification.permalink.failed.git.output.limit"
            GitPermalinkFailureReason.HEAD_PATH_ABSENT -> "notification.permalink.failed.head.absent"
            GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT -> "notification.permalink.failed.head.unsupported"
            GitPermalinkFailureReason.TARGET_UNAVAILABLE -> "notification.permalink.failed.target.unavailable"
            GitPermalinkFailureReason.HEAD_CHANGED -> "notification.permalink.failed.head.changed"
            GitPermalinkFailureReason.IO_FAILURE -> "notification.permalink.failed.io"
            GitPermalinkFailureReason.UNEXPECTED_FAILURE -> "notification.permalink.failed.unexpected"
        }
    )
}
