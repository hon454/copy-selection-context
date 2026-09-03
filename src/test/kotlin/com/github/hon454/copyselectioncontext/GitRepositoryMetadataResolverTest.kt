package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.AccessDeniedException
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
    fun `gitdir condition for linked worktree matches final git directory`() {
        val repositoryRoot = tempDir.resolve("conditional-worktree-repository")
        val commonDir = repositoryRoot.resolve(".git")
        val worktreeRoot = tempDir.resolve("conditional-linked-worktree")
        val worktreeGitDir = commonDir.resolve("worktrees/conditional-linked-worktree")
        write(worktreeRoot.resolve(".git"), "gitdir: ${worktreeGitDir.toAbsolutePath()}\n")
        write(worktreeGitDir.resolve("commondir"), "../..\n")
        write(worktreeGitDir.resolve("HEAD"), "ref: refs/heads/feature/worktree\n")
        write(commonDir.resolve("refs/heads/feature/worktree"), "$FEATURE_SHA\n")
        write(
            commonDir.resolve("config"),
            """
                [includeIf "gitdir:${worktreeGitDir.toAbsolutePath()}"]
                    path = worktree.conf
            """.trimIndent(),
        )
        write(
            commonDir.resolve("worktree.conf"),
            remoteConfig("origin", "https://github.com/owner/worktree.git"),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(worktreeRoot))

        assertEquals("https://github.com/owner/worktree.git", result.remoteUrl)
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
    fun `resolve tracked remote from absolute include path`() {
        val root = repository("absolute-include")
        val includedConfig = tempDir.resolve("shared config/remotes.conf")
        write(includedConfig, remoteConfig("upstream", "https://github.com/owner/included.git"))
        write(
            root.resolve(".git/config"),
            """
                [include]
                    path = "${includedConfig.toAbsolutePath()}" # resolved inline
                [branch "main"]
                    remote = upstream
            """.trimIndent(),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/included.git", result.remoteUrl)
    }

    @Test
    fun `ordinary remote fallback precedence remains tracked then origin then single`() {
        val trackedRoot = repository("tracked-precedence")
        write(
            trackedRoot.resolve(".git/config"),
            """
                [remote "origin"]
                    url = https://github.com/owner/origin.git
                [remote "upstream"]
                    url = https://github.com/owner/upstream.git
                [branch "main"]
                    remote = upstream
            """.trimIndent(),
        )
        val originRoot = repository("origin-precedence")
        write(
            originRoot.resolve(".git/config"),
            """
                [remote "secondary"]
                    url = https://github.com/owner/secondary.git
                [remote "origin"]
                    url = https://github.com/owner/origin.git
            """.trimIndent(),
        )
        val singleRoot = repository("single-precedence")
        write(
            singleRoot.resolve(".git/config"),
            remoteConfig("upstream", "https://github.com/owner/single.git"),
        )

        assertEquals(
            "https://github.com/owner/upstream.git",
            success(GitRepositoryMetadataResolver.resolve(trackedRoot)).remoteUrl,
        )
        assertEquals(
            "https://github.com/owner/origin.git",
            success(GitRepositoryMetadataResolver.resolve(originRoot)).remoteUrl,
        )
        assertEquals(
            "https://github.com/owner/single.git",
            success(GitRepositoryMetadataResolver.resolve(singleRoot)).remoteUrl,
        )
    }

    @Test
    fun `resolve nested relative includes from each including config file`() {
        val root = repository("relative-include")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("config"), "[include]\n    path = config.d/branch.conf\n")
        write(
            gitDir.resolve("config.d/branch.conf"),
            """
                [include]
                    path = remotes/upstream.conf
                [branch "main"]
                    remote = upstream
            """.trimIndent(),
        )
        write(
            gitDir.resolve("config.d/remotes/upstream.conf"),
            remoteConfig("upstream", "git@gitlab.com:team/included.git"),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("git@gitlab.com:team/included.git", result.remoteUrl)
    }

    @Test
    fun `inline includes preserve first value precedence before and after directives`() {
        val includeFirstRoot = repository("include-first")
        write(
            includeFirstRoot.resolve(".git/config"),
            """
                [include]
                    path = included.conf
                [remote "origin"]
                    url = https://github.com/owner/local.git
            """.trimIndent(),
        )
        write(
            includeFirstRoot.resolve(".git/included.conf"),
            remoteConfig("origin", "https://github.com/owner/included.git"),
        )

        val localFirstRoot = repository("local-first")
        write(
            localFirstRoot.resolve(".git/config"),
            """
                [remote "origin"]
                    url = https://github.com/owner/local.git
                [include]
                    path = included.conf
            """.trimIndent(),
        )
        write(
            localFirstRoot.resolve(".git/included.conf"),
            remoteConfig("origin", "https://github.com/owner/included.git"),
        )

        assertEquals(
            "https://github.com/owner/included.git",
            success(GitRepositoryMetadataResolver.resolve(includeFirstRoot)).remoteUrl,
        )
        assertEquals(
            "https://github.com/owner/local.git",
            success(GitRepositoryMetadataResolver.resolve(localFirstRoot)).remoteUrl,
        )
    }

    @Test
    fun `gitdir conditions match final git directory with documented case behavior`() {
        val root = repository("gitdir-condition")
        val gitDir = root.resolve(".git").toAbsolutePath().normalize()
        val included = gitDir.resolve("matched.conf")
        write(included, remoteConfig("origin", "https://github.com/owner/matched.git"))
        write(
            gitDir.resolve("config"),
            """
                [includeIf "gitdir:${gitDir.toString().uppercase()}"]
                    path = missing-case-sensitive.conf
                [includeIf "gitdir/i:${gitDir.toString().uppercase()}"]
                    path = matched.conf
            """.trimIndent(),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/matched.git", result.remoteUrl)
    }

    @Test
    fun `gitdir condition resolves dot pattern relative to declaring config`() {
        val root = repository("gitdir-relative-condition")
        write(
            root.resolve(".git/config"),
            """
                [includeIf "gitdir:./"]
                    path = matched.conf
            """.trimIndent(),
        )
        write(
            root.resolve(".git/matched.conf"),
            remoteConfig("origin", "https://github.com/owner/relative-condition.git"),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/relative-condition.git", result.remoteUrl)
    }

    @Test
    fun `onbranch condition matches wildcard and skips non-matches`() {
        val root = repository("onbranch-condition", branchName = "feature/nested/topic")
        write(
            root.resolve(".git/config"),
            """
                [includeIf "onbranch:release/**"]
                    path = missing-release.conf
                [includeIf "onbranch:feature/"]
                    path = feature.conf
            """.trimIndent(),
        )
        write(
            root.resolve(".git/feature.conf"),
            remoteConfig("origin", "https://github.com/owner/feature.git"),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/feature.git", result.remoteUrl)
    }

    @Test
    fun `unsupported includeIf conditions are treated as non-matching`() {
        val root = repository("unsupported-condition")
        write(
            root.resolve(".git/config"),
            """
                [includeIf "hasconfig:remote.*.url:https://github.com/**"]
                    path = missing-unsupported.conf
                [remote "origin"]
                    url = https://github.com/owner/local.git
            """.trimIndent(),
        )

        val result = success(GitRepositoryMetadataResolver.resolve(root))

        assertEquals("https://github.com/owner/local.git", result.remoteUrl)
    }

    @Test
    fun `cyclic includes return a typed failure`() {
        val root = repository("include-cycle")
        write(root.resolve(".git/config"), "[include]\n    path = cycle.conf\n")
        write(root.resolve(".git/cycle.conf"), "[include]\n    path = config\n")

        val failure = assertIs<GitPermalinkResult.Failure>(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_CYCLE, failure.reason)
        assertEquals(GitPermalinkOperation.EXPAND_GIT_CONFIG_INCLUDES, failure.diagnostic.operation)
    }

    @Test
    fun `include depth beyond conservative limit returns a typed failure`() {
        val root = repository("include-depth")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("config"), "[include]\n    path = include-0.conf\n")
        repeat(GitConfigIncludeResolver.MAX_INCLUDE_DEPTH + 1) { index ->
            val next = index + 1
            write(gitDir.resolve("include-$index.conf"), "[include]\n    path = include-$next.conf\n")
        }

        val failure = assertIs<GitPermalinkResult.Failure>(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_DEPTH_EXCEEDED, failure.reason)
        assertEquals(GitPermalinkOperation.EXPAND_GIT_CONFIG_INCLUDES, failure.diagnostic.operation)
    }

    @Test
    fun `missing included config returns an actionable io failure`() {
        val root = repository("missing-include")
        write(root.resolve(".git/config"), "[include]\n    path = missing.conf\n")

        val failure = assertIs<GitPermalinkResult.Failure>(GitRepositoryMetadataResolver.resolve(root))

        assertEquals(GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE, failure.reason)
        assertEquals(GitPermalinkOperation.EXPAND_GIT_CONFIG_INCLUDES, failure.diagnostic.operation)
        assertEquals(java.nio.file.NoSuchFileException::class.java.name, failure.diagnostic.exceptionType)
    }

    @Test
    fun `unreadable included config retains only safe exception type metadata`() {
        val root = repository("unreadable-include")
        val included = root.resolve(".git/private.conf")
        write(root.resolve(".git/config"), "[include]\n    path = private.conf\n")
        write(included, remoteConfig("origin", "https://user:password@github.com/owner/repo.git"))
        val sensitiveMessage = "token=secret at ${included.toAbsolutePath()}"

        val failure = assertIs<GitPermalinkResult.Failure>(
            GitRepositoryMetadataResolver.resolve(root) { path ->
                if (path == included) throw AccessDeniedException(sensitiveMessage)
                Files.readString(path)
            },
        )
        val logMessage = failure.safeLogMessage()

        assertEquals(GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE, failure.reason)
        assertEquals(AccessDeniedException::class.java.name, failure.diagnostic.exceptionType)
        assertFalse(logMessage.contains("secret"))
        assertFalse(logMessage.contains("password"))
        assertFalse(logMessage.contains(included.toString()))
    }

    @Test
    fun `secret-bearing values and include paths never enter failure diagnostics`() {
        val root = repository("secret-diagnostics")
        val secretUrl = "https://user:password@github.com/owner/repo.git?token=secret"
        val included = root.resolve(".git/credentials/private.conf")
        write(
            root.resolve(".git/config"),
            """
                [remote "origin"]
                    url = $secretUrl
                [include]
                    path = credentials/private.conf
            """.trimIndent(),
        )
        write(included, "[include]\n    path = ../config\n")

        val failure = assertIs<GitPermalinkResult.Failure>(GitRepositoryMetadataResolver.resolve(root))
        val logMessage = failure.safeLogMessage()

        assertEquals(GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_CYCLE, failure.reason)
        assertFalse(logMessage.contains("password"))
        assertFalse(logMessage.contains("token"))
        assertFalse(logMessage.contains(root.toString()))
        assertFalse(logMessage.contains(included.toString()))
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

    private fun repository(name: String, branchName: String = "main"): Path {
        val root = tempDir.resolve(name)
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("HEAD"), "ref: refs/heads/$branchName\n")
        write(gitDir.resolve("refs/heads/$branchName"), "$MAIN_SHA\n")
        return root
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
