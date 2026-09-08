package com.github.hon454.copyselectioncontext

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

/** Local source evidence read on BGT, independently of the IDE's asynchronously refreshed VFS. */
internal data class GitSourceSnapshot(
    val path: Path,
    val realPath: Path,
    val fileKey: String,
    val modified: FileTime,
    val size: Long,
) {
    fun revalidate(checkCanceled: () -> Unit): GitPermalinkResult<Unit> = when (val current = capture(path, checkCanceled)) {
        is GitPermalinkResult.Failure -> current
        is GitPermalinkResult.Success -> if (current.value == this) GitPermalinkResult.Success(Unit) else unavailable()
    }

    companion object {
        fun capture(path: Path, checkCanceled: () -> Unit): GitPermalinkResult<GitSourceSnapshot> {
            checkCanceled()
            return try {
                val before = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                val key = before.fileKey()?.toString()
                if (!before.isRegularFile || key == null) return unavailable()
                val realPath = path.toRealPath()
                val after = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                checkCanceled()
                if (!after.isRegularFile || key != after.fileKey()?.toString() ||
                    before.lastModifiedTime() != after.lastModifiedTime() || before.size() != after.size()) return unavailable()
                GitPermalinkResult.Success(GitSourceSnapshot(path, realPath, key, after.lastModifiedTime(), after.size()))
            } catch (_: IOException) {
                unavailable()
            }
        }

        private fun unavailable() = GitPermalinkResult.Failure(GitPermalinkFailureReason.TARGET_UNAVAILABLE,
            GitPermalinkDiagnostic(GitPermalinkOperation.READ_HEAD_TARGET))
    }
}
