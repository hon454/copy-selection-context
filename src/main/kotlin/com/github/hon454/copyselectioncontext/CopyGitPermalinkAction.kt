package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.file.Path

open class CopyGitPermalinkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val publisher = copyResultPublisher(project)
        val request = publisher.beginRequest()

        val lineRanges = resolveLineRanges(editor)
        val rootPath = resolveGitRootPath(project, file)
        if (rootPath == null) {
            handlePermalinkFailure(
                project,
                GitPermalinkResult.Failure(
                    reason = GitPermalinkFailureReason.MISSING_VCS_ROOT,
                    diagnostic = GitPermalinkDiagnostic(GitPermalinkOperation.LOCATE_VCS_ROOT),
                ),
            )
            return
        }
        val filePath = file.path

        executeInBackground {
            val result = tryBuildPermalink(rootPath, filePath, lineRanges)
            invokeOnUiThread {
                if (project.isDisposed) return@invokeOnUiThread
                when (result) {
                    is GitPermalinkResult.Failure -> {
                        publisher.runIfCurrent(request) {
                            handlePermalinkFailure(project, result)
                        }
                    }
                    is GitPermalinkResult.Success -> {
                        publisher.publishIfCurrent(
                            request = request,
                            result = CopyResult(
                                content = result.value,
                                editor = editor,
                                lineRanges = lineRanges,
                            ),
                            policy = CopyResultPolicy.GIT_PERMALINK,
                        )
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

    protected open fun showPermalinkFailure(project: Project, reason: GitPermalinkFailureReason) {
        CopySelectionNotifier.notifyPermalinkFailure(project, reason)
    }

    internal open fun copyResultPublisher(project: Project): CopyResultPublisher =
        CopyResultPublisher.getInstance(project)

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
        buildBlock: (startLine: Int, endLine: Int) -> String,
    ): String {
        val blocks = lineRanges.map { (startLine, endLine) ->
            buildBlock(startLine, endLine)
        }
        return CopySelectionUtils.joinCaretBlocks(blocks)
    }

    internal open fun tryBuildPermalink(
        rootPath: String,
        filePath: String,
        lineRanges: List<Pair<Int, Int>>
    ): GitPermalinkResult<String> {
        return try {
            val root = Path.of(rootPath).toAbsolutePath().normalize()
            val file = Path.of(filePath).toAbsolutePath().normalize()
            if (!file.startsWith(root)) {
                return GitPermalinkResult.Failure(
                    reason = GitPermalinkFailureReason.OUT_OF_ROOT_FILE,
                    diagnostic = GitPermalinkDiagnostic(GitPermalinkOperation.RELATIVIZE_FILE),
                )
            }
            val relativePath = root.relativize(file).toString().replace('\\', '/')
            val metadata = when (val result = GitRepositoryMetadataResolver.resolve(root)) {
                is GitPermalinkResult.Failure -> return result
                is GitPermalinkResult.Success -> result.value
            }
            val remote = when (val result = GitPermalinkGenerator.parseRemoteUrl(metadata.remoteUrl)) {
                is GitPermalinkResult.Failure -> return result
                is GitPermalinkResult.Success -> result.value
            }
            GitPermalinkResult.Success(
                buildPermalinkContent(lineRanges) { startLine, endLine ->
                    GitPermalinkGenerator.buildPermalink(
                        remote.repositoryUrl,
                        remote.host,
                        metadata.commitSha,
                        relativePath,
                        startLine,
                        endLine,
                    )
                },
            )
        } catch (exception: IOException) {
            GitPermalinkResult.Failure(
                reason = GitPermalinkFailureReason.IO_FAILURE,
                diagnostic = GitPermalinkDiagnostic(
                    operation = GitPermalinkOperation.BUILD_PERMALINK,
                    exceptionType = exception.javaClass.name,
                ),
            )
        } catch (exception: Exception) {
            GitPermalinkResult.Failure(
                reason = GitPermalinkFailureReason.UNEXPECTED_FAILURE,
                diagnostic = GitPermalinkDiagnostic(
                    operation = GitPermalinkOperation.BUILD_PERMALINK,
                    exceptionType = exception.javaClass.name,
                ),
            )
        }
    }

    private fun handlePermalinkFailure(project: Project, failure: GitPermalinkResult.Failure) {
        logPermalinkFailure(failure)
        showPermalinkFailure(project, failure.reason)
    }

    protected open fun logPermalinkFailure(failure: GitPermalinkResult.Failure) {
        LOG.warn(failure.safeLogMessage())
    }

    private companion object {
        val LOG = Logger.getInstance(CopyGitPermalinkAction::class.java)
    }
}
