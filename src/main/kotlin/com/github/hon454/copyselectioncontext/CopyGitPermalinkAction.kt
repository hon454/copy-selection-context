package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

open class CopyGitPermalinkAction : AnAction() {
    private val requests = LatestPermalinkRequestTracker()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val requestId = requests.begin()

        val lineRanges = resolveLineRanges(editor)
        val rootPath = resolveGitRootPath(project, file)
        if (rootPath == null) {
            showPermalinkFailure(project)
            return
        }
        val filePath = file.path

        executeInBackground {
            val permalink = tryBuildPermalink(rootPath, filePath, lineRanges)
            invokeOnUiThread {
                if (project.isDisposed) return@invokeOnUiThread
                requests.runIfCurrent(requestId) {
                    if (permalink == null) {
                        showPermalinkFailure(project)
                    } else {
                        CopyPasteManager.getInstance().setContents(StringSelection(permalink))
                        showPermalinkSuccess(project, permalink)
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PROJECT) != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }

    protected open fun resolveGitRootPath(project: Project, file: VirtualFile): String? {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        if (vcsManager.getVcsFor(file) == null) return null
        return vcsManager.getVcsRootFor(file)?.path
    }

    protected open fun executeInBackground(action: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(action)
    }

    protected open fun invokeOnUiThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }

    protected open fun showPermalinkFailure(project: Project) {
        CopySelectionNotifier.notifyPermalinkFailure(project)
    }

    protected open fun showPermalinkSuccess(project: Project, permalink: String) {
        CopySelectionNotifier.notify(project, permalink)
    }

    internal fun resolveLineRanges(editor: Editor): List<Pair<Int, Int>> {
        if (editor.caretModel.caretCount <= 1) {
            return listOf(CopySelectionUtils.resolveLineNumbers(editor))
        }

        return editor.caretModel.allCarets
            .sortedBy { it.selectionStart }
            .map { caret -> CopySelectionUtils.resolveLineNumbers(editor, caret) }
    }

    internal fun buildPermalinkContent(
        lineRanges: List<Pair<Int, Int>>,
        buildBlock: (startLine: Int, endLine: Int) -> String?,
    ): String? {
        val blocks = lineRanges.map { (startLine, endLine) ->
            buildBlock(startLine, endLine) ?: return null
        }
        return CopySelectionUtils.joinCaretBlocks(blocks)
    }

    protected open fun tryBuildPermalink(
        rootPath: String,
        filePath: String,
        lineRanges: List<Pair<Int, Int>>
    ): String? {
        return try {
            val root = Path.of(rootPath).toAbsolutePath().normalize()
            val file = Path.of(filePath).toAbsolutePath().normalize()
            if (!file.startsWith(root)) return null
            val relativePath = root.relativize(file).toString().replace('\\', '/')
            val metadata = GitRepositoryMetadataResolver.resolve(root) ?: return null
            buildPermalinkContent(lineRanges) { startLine, endLine ->
                GitPermalinkGenerator.buildPermalinkFromRemote(
                    metadata.remoteUrl,
                    metadata.commitSha,
                    relativePath,
                    startLine,
                    endLine
                )
            }
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
