package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitPermalinkGeneratorTest {

    @Test
    fun `parse SSH github remote url`() {
        val result = GitPermalinkGenerator.parseRemoteUrl("git@github.com:owner/repo.git")

        assertNotNull(result)
        assertEquals("https://github.com/owner/repo", result!!.first)
        assertEquals("github.com", result.second)
    }

    @Test
    fun `parse HTTPS github remote url`() {
        val result = GitPermalinkGenerator.parseRemoteUrl("https://github.com/owner/repo.git")

        assertNotNull(result)
        assertEquals("https://github.com/owner/repo", result!!.first)
        assertEquals("github.com", result.second)
    }

    @Test
    fun `parse SSH gitlab remote url`() {
        val result = GitPermalinkGenerator.parseRemoteUrl("git@gitlab.com:owner/repo.git")

        assertNotNull(result)
        assertTrue(result!!.first.contains("gitlab.com"))
        assertEquals("gitlab.com", result.second)
    }

    @Test
    fun `parse HTTPS without git suffix`() {
        val result = GitPermalinkGenerator.parseRemoteUrl("https://github.com/owner/repo")

        assertNotNull(result)
        assertEquals("https://github.com/owner/repo", result!!.first)
    }

    @Test
    fun `unknown remote returns null`() {
        val result = GitPermalinkGenerator.parseRemoteUrl("https://bitbucket.org/owner/repo.git")

        assertNull(result)
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
    fun `fallback returns null for unknown remote`() {
        val result = GitPermalinkGenerator.buildPermalinkFromRemote(
            remoteUrl = "https://bitbucket.org/owner/repo.git",
            sha = "abc123",
            filePath = "src/Main.kt",
            startLine = 1,
            endLine = 5
        )

        assertNull(result)
    }
}
