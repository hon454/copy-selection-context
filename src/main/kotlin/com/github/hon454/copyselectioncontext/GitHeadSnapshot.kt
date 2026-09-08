package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProgressManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Immutable evidence from local metadata reads. File identity/stamps also reject HEAD undo/redo ABA. */
internal data class GitHeadSnapshot(
    val root: Path,
    val metadata: GitRepositoryMetadata,
    val files: Map<Path, GitMetadataFileState>,
) {
    fun revalidate(checkCanceled: () -> Unit = ProgressManager::checkCanceled): GitPermalinkResult<Unit> =
        when (val current = capture(root, checkCanceled)) {
            is GitPermalinkResult.Failure -> current
            is GitPermalinkResult.Success -> if (current.value == this) GitPermalinkResult.Success(Unit)
                else GitPermalinkResult.Failure(GitPermalinkFailureReason.HEAD_CHANGED,
                    GitPermalinkDiagnostic(GitPermalinkOperation.REVALIDATE_HEAD))
        }

    companion object {
        fun capture(root: Path, checkCanceled: () -> Unit = ProgressManager::checkCanceled): GitPermalinkResult<GitHeadSnapshot> =
            gitLookupBoundary(GitPermalinkOperation.REVALIDATE_HEAD) {
                val states = linkedMapOf<Path, GitMetadataFileState>()
                val metadata = when (val result = GitRepositoryMetadataResolver.resolve(root) { path ->
                    checkCanceled()
                    val before = attributes(path)
                    val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_METADATA_BYTES + 1) }
                    if (bytes.size > MAX_METADATA_BYTES) throw IOException("Git metadata exceeds lookup limit")
                    val after = attributes(path)
                    if (before != after) throw IOException("Git metadata changed during lookup")
                    states[path.toAbsolutePath().normalize()] = after.copy(
                        digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                    )
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                }) {
                    is GitPermalinkResult.Failure -> return@gitLookupBoundary result
                    is GitPermalinkResult.Success -> result.value
                }
                checkCanceled()
                GitPermalinkResult.Success(GitHeadSnapshot(root, metadata, states.toMap()))
            }

        private fun attributes(path: Path): GitMetadataFileState {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            return GitMetadataFileState(attributes.fileKey()?.toString(), attributes.lastModifiedTime(), attributes.size())
        }

        private const val MAX_METADATA_BYTES = 1024 * 1024
    }
}

internal data class GitMetadataFileState(val fileKey: String?, val modified: FileTime, val size: Long, val digest: String = "")
