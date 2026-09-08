package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.concurrent.CancellationException

internal data class GitPermalinkInput(
    val rootPath: String,
    val filePath: String,
    val documentText: String,
    val charsetName: String,
    val lineRanges: List<Pair<Int, Int>>,
)

internal enum class GitHeadContentState { CLEAN, DIRTY }

internal data class GitPreparedPermalink(
    val content: String,
    val state: GitHeadContentState,
    val head: GitHeadSnapshot,
    val source: GitSourceSnapshot,
)

/** Reads the original commit tree and raw blob, never the index, diff/textconv or working-tree filters. */
internal class GitHeadTargetValidator(private val runner: GitProcessRunner = GitProcessRunner()) {
    fun prepare(
        input: GitPermalinkInput,
        checkCanceled: () -> Unit = ProgressManager::checkCanceled,
    ): GitPermalinkResult<GitPreparedPermalink> = gitLookupBoundary(GitPermalinkOperation.READ_HEAD_TARGET) {
        checkCanceled()
        val root = Path.of(input.rootPath).toAbsolutePath().normalize()
        val file = Path.of(input.filePath).toAbsolutePath().normalize()
        if (!file.startsWith(root) || file == root) return@gitLookupBoundary failure(GitPermalinkFailureReason.OUT_OF_ROOT_FILE)
        val source = when (val captured = GitSourceSnapshot.capture(file, checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary captured
            is GitPermalinkResult.Success -> captured.value
        }
        if (input.documentText.length > MAX_DOCUMENT_CHARACTERS) {
            return@gitLookupBoundary failure(GitPermalinkFailureReason.GIT_OUTPUT_LIMIT)
        }
        val relative = root.relativize(file).joinToString("/") { it.toString() }
        val head = when (val captured = GitHeadSnapshot.capture(root, checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary captured
            is GitPermalinkResult.Success -> captured.value
        }
        val metadata = head.metadata
        val remote = when (val parsed = GitPermalinkGenerator.parseRemoteUrl(metadata.remoteUrl)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary parsed
            is GitPermalinkResult.Success -> parsed.value
        }
        val entry = when (val tree = query(root, listOf("ls-tree", "-z", "--full-tree", metadata.commitSha, "--", relative), checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary tree
            is GitPermalinkResult.Success -> tree.value
        }
        if (entry.isEmpty()) return@gitLookupBoundary failure(GitPermalinkFailureReason.HEAD_PATH_ABSENT)
        if (!isRegularBlobEntry(entry, relative)) {
            return@gitLookupBoundary failure(GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        }
        val bytes = when (val blob = query(root, listOf("cat-file", "blob", "${metadata.commitSha}:$relative"), checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary blob
            is GitPermalinkResult.Success -> blob.value
        }
        // Git stores working-tree-encoding paths as UTF-8. Query attributes without running a filter.
        val attributes = when (val result = query(root,
            listOf("check-attr", "--source=${metadata.commitSha}", "-z", "working-tree-encoding", "--", relative), checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary result
            is GitPermalinkResult.Success -> result.value.toString(Charsets.UTF_8).split('\u0000')
        }
        if (attributes.size != 4 || attributes[0] != relative || attributes[1] != "working-tree-encoding") {
            return@gitLookupBoundary failure(GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        }
        val charset = if (attributes[2] in listOf("unspecified", "unset")) Charset.forName(input.charsetName) else Charsets.UTF_8
        val headText = decodeText(bytes, charset)
            ?: return@gitLookupBoundary failure(GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        if ('\u0000' in input.documentText) return@gitLookupBoundary failure(GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        checkCanceled()
        when (val current = head.revalidate(checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary current
            is GitPermalinkResult.Success -> Unit
        }
        when (val current = source.revalidate(checkCanceled)) {
            is GitPermalinkResult.Failure -> return@gitLookupBoundary current
            is GitPermalinkResult.Success -> Unit
        }
        GitPermalinkResult.Success(GitPreparedPermalink(
            content = CopySelectionUtils.joinCaretBlocks(input.lineRanges.map { (start, end) ->
                GitPermalinkGenerator.buildPermalink(remote.repositoryUrl, remote.host, metadata.commitSha, relative, start, end)
            }),
            state = if (headText == input.documentText) GitHeadContentState.CLEAN else GitHeadContentState.DIRTY,
            head = head,
            source = source,
        ))
    }

    private fun query(root: Path, args: List<String>, checkCanceled: () -> Unit): GitPermalinkResult<ByteArray> =
        when (val result = runner.run(root, args, checkCanceled)) {
            is GitProcessResult.Success -> GitPermalinkResult.Success(result.stdout)
            is GitProcessResult.Failure -> failure(result.reason)
        }

    private fun isRegularBlobEntry(bytes: ByteArray, relative: String): Boolean {
        val entry = bytes.toString(Charsets.UTF_8)
        val tab = entry.indexOf('\t')
        if (tab < 0 || entry.substring(tab + 1) != "$relative\u0000") return false
        return Regex("100(?:644|755) blob [0-9a-f]{40}(?:[0-9a-f]{24})?").matches(entry.substring(0, tab))
    }

    private fun decodeText(bytes: ByteArray, preferred: Charset): String? {
        val (charset, skip) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> preferred to 0
        }
        return try {
            val decoded = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString()
            decoded.takeUnless { '\u0000' in it }?.replace("\r\n", "\n")?.replace('\r', '\n')
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean = size >= prefix.size &&
        prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private fun failure(reason: GitPermalinkFailureReason) = GitPermalinkResult.Failure(
        reason, GitPermalinkDiagnostic(GitPermalinkOperation.READ_HEAD_TARGET),
    )

    companion object {
        const val MAX_DOCUMENT_CHARACTERS = 4 * 1024 * 1024
    }
}

/** Cancellation is control flow at every pre-publication boundary, including metadata reader failures. */
internal inline fun <T> gitLookupBoundary(
    operation: GitPermalinkOperation,
    action: () -> GitPermalinkResult<T>,
): GitPermalinkResult<T> = try {
    action()
} catch (canceled: ProcessCanceledException) {
    throw canceled
} catch (canceled: CancellationException) {
    throw canceled
} catch (exception: IOException) {
    GitPermalinkResult.Failure(GitPermalinkFailureReason.IO_FAILURE, GitPermalinkDiagnostic(operation, exceptionType = exception.javaClass.name))
} catch (exception: Exception) {
    GitPermalinkResult.Failure(GitPermalinkFailureReason.UNEXPECTED_FAILURE, GitPermalinkDiagnostic(operation, exceptionType = exception.javaClass.name))
}
