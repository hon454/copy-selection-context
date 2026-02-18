package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

class CopyGitPermalinkAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val (startLine, endLine) = resolveLineNumbers(editor)
        val permalink = tryBuildPermalink(project, file, startLine, endLine)
            ?: fallbackPath(project, file, startLine, endLine)

        CopyPasteManager.getInstance().setContents(StringSelection(permalink))
        CopySelectionNotifier.notify(project, permalink)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PROJECT) != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }

    private fun resolveLineNumbers(editor: Editor): Pair<Int, Int> {
        val selectionModel = editor.selectionModel
        val document = editor.document
        return if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            Pair(startLine, endLine)
        } else {
            val currentLine = editor.caretModel.logicalPosition.line + 1
            Pair(currentLine, currentLine)
        }
    }

    private fun tryBuildPermalink(
        project: Project,
        file: VirtualFile,
        startLine: Int,
        endLine: Int
    ): String? {
        return try {
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            vcsManager.getVcsFor(file) ?: return null
            val root = vcsManager.getVcsRootFor(file) ?: return null
            val relativePath = file.path
                .removePrefix(root.path)
                .trimStart('/', '\\')
                .replace('\\', '/')

            val gitDir = resolveGitDir(root) ?: return null
            val configFile = gitDir.findFileByRelativePath("config") ?: return null
            val remoteUrl = extractRemoteUrl(String(configFile.contentsToByteArray())) ?: return null

            val sha = resolveHeadSha(gitDir) ?: return null
            GitPermalinkGenerator.buildPermalinkFromRemote(remoteUrl, sha, relativePath, startLine, endLine)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGitDir(root: VirtualFile): VirtualFile? {
        val dotGit = root.findChild(".git") ?: return null
        if (dotGit.isDirectory) {
            return dotGit
        }

        val marker = String(dotGit.contentsToByteArray())
            .lineSequence()
            .firstOrNull { it.trim().startsWith("gitdir:") }
            ?.substringAfter("gitdir:")
            ?.trim()
            ?: return null

        val resolvedPath = if (marker.startsWith("/") || Regex("""^[A-Za-z]:[/\\].*""").matches(marker)) {
            marker
        } else {
            "${root.path}/$marker"
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(resolvedPath.replace('\\', '/'))
    }

    private fun resolveHeadSha(gitDir: VirtualFile): String? {
        val headFile = gitDir.findFileByRelativePath("HEAD") ?: return null
        val headContent = String(headFile.contentsToByteArray()).trim()
        if (!headContent.startsWith("ref: ")) {
            return headContent
        }

        val refPath = headContent.removePrefix("ref: ").trim()
        val refFile = gitDir.findFileByRelativePath(refPath) ?: return null
        return String(refFile.contentsToByteArray()).trim()
    }

    private fun extractRemoteUrl(gitConfig: String): String? {
        val lines = gitConfig.lines()
        var inRemoteOrigin = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "[remote \"origin\"]") {
                inRemoteOrigin = true
                continue
            }
            if (inRemoteOrigin && trimmed.startsWith("[")) {
                inRemoteOrigin = false
            }
            if (inRemoteOrigin && trimmed.startsWith("url =")) {
                return trimmed.removePrefix("url =").trim()
            }
        }
        return null
    }

    private fun fallbackPath(
        project: Project,
        file: VirtualFile,
        startLine: Int,
        endLine: Int
    ): String {
        val basePath = project.basePath ?: ""
        val relativePath = file.path.removePrefix(basePath).trimStart('/', '\\').replace('\\', '/')
        val lineFragment = if (startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
        return "@$relativePath#$lineFragment"
    }
}
