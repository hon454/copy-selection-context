package com.github.hon454.copyselectioncontext

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

class AddToContextCollectionAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = project != null && !project.isDisposed && editor != null &&
            !editor.isDisposed && editor.project === project && file != null && file.isValid &&
            !file.isDirectory && !file.fileType.isBinary
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project?.takeUnless { it.isDisposed } ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)?.takeUnless { it.isDisposed } ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val settings = CopySelectionSettings.getInstance().state
        val result = ContextCollectionService.getInstance(project).capture(editor, file, settings.defaultPathType)
        if (result == ContextCollectionAddResult.InvalidContext) return
        val rejected = result is ContextCollectionAddResult.Rejected
        if (!rejected && !settings.enableNotification) return
        NotificationGroupManager.getInstance().getNotificationGroup("CopySelectionContext")
            .createNotification(feedback(result), if (rejected) NotificationType.WARNING else NotificationType.INFORMATION)
            .notify(project)
    }

    internal fun feedback(result: ContextCollectionAddResult): String = when (result) {
        is ContextCollectionAddResult.Added -> CopySelectionBundle.message(
            "collection.add.result", result.added, result.duplicates,
        )
        is ContextCollectionAddResult.Rejected -> CopySelectionBundle.message(when (result.limit) {
            ContextCollectionLimit.ITEM_COUNT -> "collection.limit.items"
            ContextCollectionLimit.ITEM_BYTES -> "collection.limit.item.bytes"
            ContextCollectionLimit.TOTAL_BYTES -> "collection.limit.total.bytes"
        })
        ContextCollectionAddResult.InvalidContext -> CopySelectionBundle.message("collection.invalid.context")
    }
}
