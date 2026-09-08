package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference

open class CopyGitPermalinkAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val publisher = copyResultPublisher(project)
        val request = publisher.beginRequest()
        val failureReporter = copyFailureReporter(project)
        if (project.isDisposed || editor.isDisposed || !file.isValid) return
        val lifetime = requestLifetime(project, editor, file)
        try {
            val input = when (val captured = captureInput(project, editor, file)) {
                is GitPermalinkResult.Failure -> { completeFailure(project, publisher, request, lifetime, captured); return }
                is GitPermalinkResult.Success -> captured.value
            }
            val lineRanges = input.lineRanges
            runLookup(project, publisher, request, lifetime, { preparePermalink(input, it) }) { result ->
                when (result) {
                    is GitPermalinkResult.Failure -> completeFailure(project, publisher, request, lifetime, result)
                    is GitPermalinkResult.Success -> confirmAndPublish(project, publisher, failureReporter, request, lifetime, lineRanges, result.value)
                }
            }
        } catch (exception: Exception) {
            lifetime.close()
            throw exception
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

    protected open fun executeInBackground(project: Project, action: () -> Unit, onCanceled: () -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, CopySelectionBundle.message("permalink.progress"), true) {
            override fun run(indicator: ProgressIndicator) = action()
            override fun onCancel() = onCanceled()
        })
    }

    protected open fun invokeOnUiThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }

    protected open fun showPermalinkFailure(project: Project, reason: GitPermalinkFailureReason, isCurrent: () -> Boolean) {
        CopySelectionNotifier.notifyPermalinkFailure(project, reason, isCurrent)
    }

    internal open fun copyResultPublisher(project: Project): CopyResultPublisher =
        CopyResultPublisher.getInstance(project)

    internal open fun copyFailureReporter(project: Project): CopyFailureReporter = CopyFailureReporter.getInstance(project)

    internal open fun requestLifetime(project: Project, editor: Editor, file: VirtualFile): GitPermalinkLifetime =
        GitPermalinkLifetime(project, editor, file)

    private fun captureInput(project: Project, editor: Editor, file: VirtualFile): GitPermalinkResult<GitPermalinkInput> =
        gitLookupBoundary(GitPermalinkOperation.READ_HEAD_TARGET) {
            val rootPath = resolveGitRootPath(project, file) ?: return@gitLookupBoundary GitPermalinkResult.Failure(
                GitPermalinkFailureReason.MISSING_VCS_ROOT, GitPermalinkDiagnostic(GitPermalinkOperation.LOCATE_VCS_ROOT))
            if (file.fileType.isBinary || editor.document.textLength > GitHeadTargetValidator.MAX_DOCUMENT_CHARACTERS) {
                return@gitLookupBoundary GitPermalinkResult.Failure(
                    if (file.fileType.isBinary) GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT else GitPermalinkFailureReason.GIT_OUTPUT_LIMIT,
                    GitPermalinkDiagnostic(GitPermalinkOperation.READ_HEAD_TARGET))
            }
            GitPermalinkResult.Success(GitPermalinkInput(rootPath, file.path, editor.document.immutableCharSequence.toString(),
                file.charset.name(), resolveLineRanges(editor)))
        }

    internal open fun preparePermalink(input: GitPermalinkInput, checkCanceled: () -> Unit): GitPermalinkResult<GitPreparedPermalink> =
        GitHeadTargetValidator().prepare(input, checkCanceled)

    internal open fun revalidateHead(prepared: GitPreparedPermalink, checkCanceled: () -> Unit): GitPermalinkResult<Unit> =
        when (val head = prepared.head.revalidate(checkCanceled)) {
            is GitPermalinkResult.Failure -> head
            is GitPermalinkResult.Success -> prepared.source.revalidate(checkCanceled)
        }

    protected open fun confirmHeadDifference(project: Project): Boolean = Messages.showDialog(
        project,
        CopySelectionBundle.message("permalink.confirm.message"),
        CopySelectionBundle.message("permalink.confirm.title"),
        arrayOf(CopySelectionBundle.message("permalink.confirm.copy"), Messages.getCancelButton()),
        1,
        Messages.getWarningIcon(),
    ) == 0

    private fun confirmAndPublish(
        project: Project,
        publisher: CopyResultPublisher,
        failureReporter: CopyFailureReporter,
        request: CopyResultRequest,
        lifetime: GitPermalinkLifetime,
        lineRanges: List<Pair<Int, Int>>,
        prepared: GitPreparedPermalink,
    ) {
        if (!publisher.isCurrent(request) || !lifetime.matchesCapture()) { lifetime.close(); return }
        if (prepared.state == GitHeadContentState.CLEAN) {
            publishPrepared(publisher, failureReporter, request, lifetime, lineRanges, prepared)
            return
        }
        val confirmed = try { confirmHeadDifference(project) } catch (exception: Exception) { lifetime.close(); throw exception }
        // A modal dialog can process edits, undo, VFS changes, project close and any project's new copy.
        if (!confirmed || !publisher.isCurrent(request) || !lifetime.matchesCapture()) { lifetime.close(); return }
        runLookup(project, publisher, request, lifetime, { revalidateHead(prepared, it) }) { result ->
            when (result) {
                is GitPermalinkResult.Failure -> completeFailure(project, publisher, request, lifetime, result)
                is GitPermalinkResult.Success -> publishPrepared(publisher, failureReporter, request, lifetime, lineRanges, prepared)
            }
        }
    }

    private fun publishPrepared(
        publisher: CopyResultPublisher,
        failureReporter: CopyFailureReporter,
        request: CopyResultRequest,
        lifetime: GitPermalinkLifetime,
        lineRanges: List<Pair<Int, Int>>,
        prepared: GitPreparedPermalink,
    ) {
        try {
            val editorRef = WeakReference(lifetime.editor())
            val outcome = publisher.publishOutcomeIfCurrent(request, CopyResult(prepared.content, lifetime.editor(), lineRanges),
                CopyResultPolicy.GIT_PERMALINK, validate = lifetime::matchesCapture)
            // Request cleanup must not invalidate a queued clipboard error for its still-live editor.
            failureReporter.report(request, outcome) { editorRef.get()?.isDisposed == false }
        } finally {
            lifetime.close()
        }
    }

    private fun <T> runLookup(
        project: Project,
        publisher: CopyResultPublisher,
        request: CopyResultRequest,
        lifetime: GitPermalinkLifetime,
        lookup: (() -> Unit) -> GitPermalinkResult<T>,
        complete: (GitPermalinkResult<T>) -> Unit,
    ) {
        try {
            executeInBackground(project, action = {
                var queued = false
                try {
                    lifetime.observeCancellation(ProgressManager.getInstance().progressIndicator)
                    val checkCanceled = {
                        ProgressManager.checkCanceled()
                        if (!lifetime.isAlive() || !publisher.isCurrent(request)) throw ProcessCanceledException()
                    }
                    val result = gitLookupBoundary(GitPermalinkOperation.READ_HEAD_TARGET) {
                        checkCanceled()
                        lookup(checkCanceled).also { checkCanceled() }
                    }
                    invokeOnUiThread {
                        if (!publisher.isCurrent(request) || !lifetime.matchesCapture()) lifetime.close()
                        else complete(result)
                    }
                    queued = true
                } finally {
                    if (!queued) lifetime.close()
                }
            }, onCanceled = lifetime::close)
        } catch (exception: Exception) {
            lifetime.close()
            throw exception
        }
    }

    private fun completeFailure(project: Project, publisher: CopyResultPublisher, request: CopyResultRequest,
        lifetime: GitPermalinkLifetime, failure: GitPermalinkResult.Failure) {
        try {
            publisher.runIfCurrent(request) {
                if (lifetime.matchesCapture()) {
                    logPermalinkFailure(failure)
                    showPermalinkFailure(project, failure.reason) { publisher.isCurrent(request) && lifetime.matchesCapture() }
                }
            }
        } finally {
            lifetime.close()
        }
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
        buildBlock: (startLine: Int, endLine: Int) -> String,
    ): String {
        val blocks = lineRanges.map { (startLine, endLine) ->
            buildBlock(startLine, endLine)
        }
        return CopySelectionUtils.joinCaretBlocks(blocks)
    }

    protected open fun logPermalinkFailure(failure: GitPermalinkResult.Failure) {
        LOG.warn(failure.safeLogMessage())
    }

    private companion object {
        val LOG = Logger.getInstance(CopyGitPermalinkAction::class.java)
    }
}
