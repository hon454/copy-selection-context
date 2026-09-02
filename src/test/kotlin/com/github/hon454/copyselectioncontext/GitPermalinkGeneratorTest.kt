package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GitPermalinkGeneratorTest {

    @Test
    fun `parse SSH github remote url`() {
        val result = success("git@github.com:owner/repo.git")

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
        assertEquals("github.com", result.host)
    }

    @Test
    fun `parse HTTPS github remote url`() {
        val result = success("https://github.com/owner/repo.git")

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
        assertEquals("github.com", result.host)
    }

    @Test
    fun `parse SSH gitlab remote url`() {
        val result = success("git@gitlab.com:owner/repo.git")

        assertTrue(result.repositoryUrl.contains("gitlab.com"))
        assertEquals("gitlab.com", result.host)
    }

    @Test
    fun `parse SSH URI github remote url`() {
        val result = success("ssh://git@github.com/owner/repo.git")

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
        assertEquals("github.com", result.host)
    }

    @Test
    fun `parse github SSH over HTTPS port remote url`() {
        val result = success(
            "ssh://git@ssh.github.com:443/owner/repo.git"
        )

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
        assertEquals("github.com", result.host)
    }

    @Test
    fun `parse git plus SSH URI with nested gitlab namespace`() {
        val result = success("git+ssh://git@gitlab.com/group/subgroup/repo.git")

        assertEquals("https://gitlab.com/group/subgroup/repo", result.repositoryUrl)
        assertEquals("gitlab.com", result.host)
    }

    @Test
    fun `parse HTTPS without git suffix`() {
        val result = success("https://github.com/owner/repo")

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
    }

    @Test
    fun `unknown remote returns an explicit unsupported host reason`() {
        val result = failure("https://bitbucket.org/owner/repo.git")

        assertEquals(GitPermalinkFailureReason.UNSUPPORTED_REMOTE_HOST, result.reason)
        assertEquals("bitbucket.org", result.diagnostic.remoteHost)
    }

    @Test
    fun `invalid remote paths return typed failures`() {
        listOf(
            "https://github.com/owner/../victim.git",
            "https://github.com/owner/%2e%2e/victim.git",
            "https://github.com/owner/./repo.git",
            "https://github.com/owner//repo.git",
            "https://github.com/owner/repo/extra.git",
            "git@gitlab.com:group//project.git"
        ).forEach { remoteUrl ->
            assertEquals(
                GitPermalinkFailureReason.UNSUPPORTED_REMOTE_HOST,
                failure(remoteUrl).reason,
                "Remote '$remoteUrl' should be rejected",
            )
        }
    }

    @Test
    fun `credential bearing supported remote is canonicalized without credentials`() {
        val result = success("https://user:super-secret@github.com/owner/repo.git")

        assertEquals("https://github.com/owner/repo", result.repositoryUrl)
        assertFalse(result.repositoryUrl.contains("user"))
        assertFalse(result.repositoryUrl.contains("super-secret"))
    }

    @Test
    fun `credential bearing unsupported remote is redacted from diagnostics`() {
        val remoteUrl = "https://user:super-secret@bitbucket.org/private/repo.git"
        val failure = failure(remoteUrl)
        val logMessage = failure.safeLogMessage()

        assertEquals("bitbucket.org", failure.diagnostic.remoteHost)
        assertTrue(logMessage.contains("remoteHost=bitbucket.org"))
        assertFalse(logMessage.contains("user"))
        assertFalse(logMessage.contains("super-secret"))
        assertFalse(logMessage.contains("private/repo"))
        assertFalse(logMessage.contains(remoteUrl))
    }

    @Test
    fun `build permalink single line`() {
        val url = GitPermalinkGenerator.buildPermalink(
            repoUrl = "https://github.com/owner/repo",
            host = "github.com",
            sha = "abc123",
            filePath = "src/Main.kt",
            startLine = 42,
            endLine = 42
        )

        assertEquals("https://github.com/owner/repo/blob/abc123/src/Main.kt#L42", url)
    }

    @Test
    fun `build permalink range`() {
        val url = GitPermalinkGenerator.buildPermalink(
            repoUrl = "https://github.com/owner/repo",
            host = "github.com",
            sha = "abc123",
            filePath = "src/Main.kt",
            startLine = 10,
            endLine = 20
        )

        assertEquals("https://github.com/owner/repo/blob/abc123/src/Main.kt#L10-L20", url)
    }

    @Test
    fun `build permalink encodes URL sensitive and unicode path characters`() {
        val url = GitPermalinkGenerator.buildPermalink(
            repoUrl = "https://github.com/owner/repo",
            host = "github.com",
            sha = "abc123",
            filePath = "src/My file #1/한글.kt",
            startLine = 7,
            endLine = 7
        )

        assertEquals(
            "https://github.com/owner/repo/blob/abc123/src/My%20file%20%231/%ED%95%9C%EA%B8%80.kt#L7",
            url
        )
    }

    @Test
    fun `fallback returns typed failure for unknown remote`() {
        val result = GitPermalinkGenerator.buildPermalinkFromRemote(
            remoteUrl = "https://bitbucket.org/owner/repo.git",
            sha = "abc123",
            filePath = "src/Main.kt",
            startLine = 1,
            endLine = 5
        )

        val failure = assertIs<GitPermalinkResult.Failure>(result)
        assertEquals(GitPermalinkFailureReason.UNSUPPORTED_REMOTE_HOST, failure.reason)
    }

    private fun success(remoteUrl: String): GitRemoteRepository =
        assertIs<GitPermalinkResult.Success<GitRemoteRepository>>(
            GitPermalinkGenerator.parseRemoteUrl(remoteUrl)
        ).value

    private fun failure(remoteUrl: String): GitPermalinkResult.Failure =
        assertIs(GitPermalinkGenerator.parseRemoteUrl(remoteUrl))
}
