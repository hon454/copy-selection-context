package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

class CopyGitPermalinkAction : AnAction() {
    private val requests = LatestPermalinkRequestTracker()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val requestId = requests.begin()

        val (startLine, endLine) = CopySelectionUtils.resolveLineNumbers(editor)
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        if (vcsManager.getVcsFor(file) == null) {
            CopySelectionNotifier.notifyPermalinkFailure(project)
            return
        }
        val rootPath = vcsManager.getVcsRootFor(file)?.path
        if (rootPath == null) {
            CopySelectionNotifier.notifyPermalinkFailure(project)
            return
        }
        val filePath = file.path
        val application = ApplicationManager.getApplication()

        application.executeOnPooledThread {
            val permalink = tryBuildPermalink(rootPath, filePath, startLine, endLine)
            application.invokeLater {
                if (project.isDisposed) return@invokeLater
                requests.runIfCurrent(requestId) {
                    if (permalink == null) {
                        CopySelectionNotifier.notifyPermalinkFailure(project)
                    } else {
                        CopyPasteManager.getInstance().setContents(StringSelection(permalink))
                        CopySelectionNotifier.notify(project, permalink)
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PROJECT) != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }

    private fun tryBuildPermalink(
        rootPath: String,
        filePath: String,
        startLine: Int,
        endLine: Int
    ): String? {
        return try {
            val root = Path.of(rootPath).toAbsolutePath().normalize()
            val file = Path.of(filePath).toAbsolutePath().normalize()
            if (!file.startsWith(root)) return null
            val relativePath = root.relativize(file).toString().replace('\\', '/')
            val metadata = GitRepositoryMetadataResolver.resolve(root) ?: return null
            GitPermalinkGenerator.buildPermalinkFromRemote(
                metadata.remoteUrl,
                metadata.commitSha,
                relativePath,
                startLine,
                endLine
            )
        } catch (_: Exception) {
            null
        }
    }
}

internal class LatestPermalinkRequestTracker {
    private val latestRequestId = AtomicLong()

    fun begin(): Long = latestRequestId.incrementAndGet()

    fun runIfCurrent(requestId: Long, action: () -> Unit): Boolean {
        if (latestRequestId.get() != requestId) return false
        action()
        return true
    }
}
