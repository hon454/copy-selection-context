package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

        val result = GitRepositoryMetadataResolver.resolve(root)

        assertNotNull(result)
        assertEquals("https://github.com/owner/repo.git", result?.remoteUrl)
        assertEquals(MAIN_SHA, result?.commitSha)
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

        val result = GitRepositoryMetadataResolver.resolve(worktreeRoot)

        assertNotNull(result)
        assertEquals("git@gitlab.com:team/project.git", result?.remoteUrl)
        assertEquals(FEATURE_SHA, result?.commitSha)
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

        val result = GitRepositoryMetadataResolver.resolve(root)

        assertNotNull(result)
        assertEquals(PACKED_SHA, result?.commitSha)
    }

    @Test
    fun `resolve detached head`() {
        val root = tempDir.resolve("detached-repository")
        val gitDir = root.resolve(".git")
        write(gitDir.resolve("HEAD"), "$DETACHED_SHA\n")
        write(gitDir.resolve("config"), remoteConfig("origin", "https://gitlab.com/team/project.git"))

        val result = GitRepositoryMetadataResolver.resolve(root)

        assertNotNull(result)
        assertEquals(DETACHED_SHA, result?.commitSha)
        assertEquals("https://gitlab.com/team/project.git", result?.remoteUrl)
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

    private companion object {
        const val MAIN_SHA = "0123456789abcdef0123456789abcdef01234567"
        const val FEATURE_SHA = "123456789abcdef0123456789abcdef012345678"
        const val PACKED_SHA = "23456789abcdef0123456789abcdef0123456789"
        const val DETACHED_SHA = "3456789abcdef0123456789abcdef0123456789a"
    }
}
