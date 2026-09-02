package com.github.hon454.copyselectioncontext

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object CopySelectionReviewNotifier {
    fun notify(
        project: Project,
        onReview: () -> Unit,
        onNeverAskAgain: () -> Unit,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("CopySelectionContext")
            .createNotification(
                promptTitle(),
                promptContent(),
                NotificationType.INFORMATION,
            )

        notification.addAction(
            NotificationAction.createSimpleExpiring(actionText(ReviewPromptAction.REVIEW)) {
                onReview()
            },
        )
        notification.addAction(
            NotificationAction.createSimpleExpiring(actionText(ReviewPromptAction.LATER)) {},
        )
        notification.addAction(
            NotificationAction.createSimpleExpiring(actionText(ReviewPromptAction.NEVER_ASK_AGAIN)) {
                onNeverAskAgain()
            },
        )
        notification.notify(project)
    }

    internal fun promptTitle(): String = CopySelectionBundle.message("review.prompt.title")

    internal fun promptContent(): String = CopySelectionBundle.message("review.prompt.content")

    internal fun actionText(action: ReviewPromptAction): String =
        CopySelectionBundle.message(action.messageKey)
}

internal enum class ReviewPromptAction(val messageKey: String) {
    REVIEW("review.prompt.action.review"),
    LATER("review.prompt.action.later"),
    NEVER_ASK_AGAIN("review.prompt.action.never"),
}
