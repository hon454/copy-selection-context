package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GitRepositoryMetadataResolverTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `resolve normal repository with loose ref`() {
        val root = tempDir.resolve("repository")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("HEAD"), "ref: refs/heads/main\n")
        write(gitDir.resolve("refs/heads/main"), "$MAIN_SHA\n")
        write(gitDir.resolve("config"), remoteConfig("origin", "https://github.com/owner/repo.git"))

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/repo.git", result.remoteUrl)
        assertEquals(MAIN_SHA, result.commitSha)
    }

    @Test
    fun `resolve linked worktree from common git directory`() {
        val repositoryRoot = tempDir.resolve("repository")
        val commonDir = repositoryRoot.resolve(".git")
        val worktreeRoot = tempDir.resolve("linked-worktree")
        val worktreeGitDir = commonDir.resolve("worktrees/linked-worktree")
        write(worktreeRoot.resolve(".git"), "gitdir: ${worktreeGitDir.toAbsolutePath()}\n")
        write(worktreeGitDir.resolve("commondir"), "../..\n")
        write(worktreeGitDir.resolve("HEAD"), "ref: refs/heads/feature/worktree\n")
        write(commonDir.resolve("refs/heads/feature/worktree"), "$FEATURE_SHA\n")
        write(
            commonDir.resolve("config"),
            """
                [remote "origin"]
                    url = https://github.com/owner/origin.git
                [remote "upstream"]
                    url = git@gitlab.com:team/project.git
                [branch "feature/worktree"]
                    remote = upstream
            """.trimIndent()
        )

        val result = success(GitRepositoryMetadataResolver.resolve(worktreeRoot))

        assertEquals("git@gitlab.com:team/project.git", result.remoteUrl)
        assertEquals(FEATURE_SHA, result.commitSha)
    }

    @Test
    fun `resolve branch from packed refs`() {
        val root = tempDir.resolve("packed-repository")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("HEAD"), "ref: refs/heads/release\n")
        write(
            gitDir.resolve("packed-refs"),
            """
                # pack-refs with: peeled fully-peeled sorted
                $PACKED_SHA refs/heads/release
                ${"1".repeat(40)} refs/tags/v1.0.0
                ^${"2".repeat(40)}
            """.trimIndent()
        )
        write(gitDir.resolve("config"), remoteConfig("origin", "git@github.com:owner/repo.git"))

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(PACKED_SHA, result.commitSha)
    }

    @Test
    fun `resolve detached head`() {
        val root = tempDir.resolve("detached-repository")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("HEAD"), "$DETACHED_SHA\n")
        write(gitDir.resolve("config"), remoteConfig("origin", "https://gitlab.com/team/project.git"))

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(DETACHED_SHA, result.commitSha)
        assertEquals("https://gitlab.com/team/project.git", result.remoteUrl)
    }

    @Test
    fun `missing or invalid metadata returns an explicit unresolved reason`() {
        val root = tempDir.resolve("missing-metadata")
        Files.createDirectories(root)

        val failure = assertIs<GitPermalinkResult.Failure>(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(GitPermalinkFailureReason.UNRESOLVED_GIT_METADATA, failure.reason)
        assertEquals(GitPermalinkOperation.RESOLVE_GIT_METADATA, failure.diagnostic.operation)
    }

    @Test
    fun `io failures retain only the exception type in safe diagnostics`() {
        val root = tempDir.resolve("io-failure")
        write(root.resolve(".git/HEAD"), "ref: refs/heads/main\n")
        val sensitiveMessage = "token=secret at ${root.toAbsolutePath()}"

        val failure = assertIs<GitPermalinkResult.Failure>(
            GitRepositoryMetadataResolver.resolve(root) { throw IOException(sensitiveMessage) }
        )
        val logMessage = failure.safeLogMessage()

        assertEquals(GitPermalinkFailureReason.IO_FAILURE, failure.reason)
        assertEquals(IOException::class.java.name, failure.diagnostic.exceptionType)
        assertFalse(logMessage.contains("secret"))
        assertFalse(logMessage.contains(root.toString()))
    }

    @Test
    fun `unexpected resolver failures return a typed reason without exception text`() {
        val root = tempDir.resolve("unexpected-failure")
        write(root.resolve(".git/HEAD"), "ref: refs/heads/main\n")

        val failure = assertIs<GitPermalinkResult.Failure>(
            GitRepositoryMetadataResolver.resolve(root) { throw IllegalStateException("copied code") }
        )

        assertEquals(GitPermalinkFailureReason.UNEXPECTED_FAILURE, failure.reason)
        assertFalse(failure.safeLogMessage().contains("copied code"))
    }

    private fun remoteConfig(name: String, url: String): String =
        """
            [remote "$name"]
                url = $url
        """.trimIndent()

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun success(result: GitPermalinkResult<GitRepositoryMetadata>): GitRepositoryMetadata =
        assertIs<GitPermalinkResult.Success<GitRepositoryMetadata>>(result).value

    private companion object {
        const val MAIN_SHA = "0123456789abcdef0123456789abcdef01234567"
        const val FEATURE_SHA = "123456789abcdef0123456789abcdef012345678"
        const val PACKED_SHA = "23456789abcdef0123456789abcdef0123456789"
        const val DETACHED_SHA = "3456789abcdef0123456789abcdef0123456789a"
    }
}
