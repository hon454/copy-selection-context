package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** These tests execute installed Git against actual commit/tree/blob objects, without a remote server. */
class GitHeadTargetValidatorTest {
    @TempDir lateinit var tempDir: Path
    private val validator = GitHeadTargetValidator()

    @Test fun `clean content uses original SHA and current multicaret ranges with literal path escaping`() {
        val repo = repository("repo 한글 & (safe) ' \$(touch marker)")
        val path = "src/한글 [x] & ' \$().kt"
        val text = "first\n한국어 😀\nthird\n"
        val sha = repo.commit(path, text)
        val prepared = success(repo.input(path, text, ranges = listOf(2 to 3, 1 to 1)))
        assertEquals(GitHeadContentState.CLEAN, prepared.state)
        assertEquals(listOf(2 to 3, 1 to 1).joinToString("\n\n") { (start, end) ->
            GitPermalinkGenerator.buildPermalink("https://github.com/owner/repo", "github.com", sha, path, start, end)
        }, prepared.content)
        assertFalse(Files.exists(repo.root.resolve("marker")))
    }

    @Test fun `unsaved insertion deletion and replacement compare the document rather than disk or index`() {
        val repo = repository()
        val original = "first\nsecond\nthird\n"
        val sha = repo.commit("source.txt", original)
        listOf("inserted\n$original", "first\nthird\n", "first\nreplaced\nthird\n").forEach { document ->
            val prepared = success(repo.input("source.txt", document, ranges = listOf(3 to 4)))
            assertEquals(GitHeadContentState.DIRTY, prepared.state)
            assertTrue(prepared.content.endsWith("/$sha/source.txt#L3-L4"))
        }
        assertEquals(original, Files.readString(repo.root.resolve("source.txt")))
    }

    @Test fun `disk and staged changes are dirty but an editor equal to HEAD stays clean`() {
        val repo = repository()
        val original = "original\n"
        repo.commit("source.txt", original)
        repo.write("source.txt", "disk change\n")
        assertEquals(GitHeadContentState.DIRTY, success(repo.input("source.txt", "disk change\n")).state)
        repo.command("add", "source.txt")
        assertEquals(GitHeadContentState.DIRTY, success(repo.input("source.txt", "disk change\n")).state)
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("source.txt", original)).state)
        repo.command("rm", "--cached", "source.txt")
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("source.txt", original)).state)
        Files.delete(repo.root.resolve("source.txt"))
        failure(repo.input("source.txt", original), GitPermalinkFailureReason.TARGET_UNAVAILABLE)
    }

    @Test fun `new paths and both unstaged and staged renamed destinations are absent in HEAD`() {
        val repo = repository()
        repo.commit("source.txt", "original\n")
        repo.write("new.txt", "new\n")
        failure(repo.input("new.txt", "new\n"), GitPermalinkFailureReason.HEAD_PATH_ABSENT)
        Files.move(repo.root.resolve("source.txt"), repo.root.resolve("renamed.txt"))
        failure(repo.input("renamed.txt", "original\n"), GitPermalinkFailureReason.HEAD_PATH_ABSENT)
        repo.command("add", "--all")
        failure(repo.input("renamed.txt", "original\n"), GitPermalinkFailureReason.HEAD_PATH_ABSENT)
    }

    @Test fun `linked worktree common directory and detached HEAD retain GitLab remote selection`() {
        val repo = repository()
        val sha = repo.commit("source.txt", "text\n")
        repo.command("remote", "add", "upstream", "git@gitlab.com:group/repo.git")
        val linked = tempDir.resolve("linked 작업 & space")
        repo.command("worktree", "add", "-b", "feature", linked.toString(), sha)
        repo.command("config", "branch.feature.remote", "upstream")
        val worktree = LocalGitRepository(linked, initialize = false)
        assertTrue(success(worktree.input("source.txt", "text\n")).content.startsWith("https://gitlab.com/group/repo/-/blob/$sha/"))
        worktree.command("checkout", "--detach", sha)
        assertEquals(GitHeadContentState.CLEAN, success(worktree.input("source.txt", "text\n")).state)
    }

    @Test fun `supported encodings and IDE normalized line endings compare clean without textconv`() {
        val repo = repository()
        val normalized = "한국어 日本語 😀\nlast\n"
        val crlf = normalized.replace("\n", "\r\n")
        repo.write("utf8.txt", crlf)
        repo.write("bom.txt", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + crlf.toByteArray())
        repo.write("utf16le.txt", byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + crlf.toByteArray(Charsets.UTF_16LE))
        repo.write("utf16be.txt", byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + crlf.toByteArray(Charsets.UTF_16BE))
        repo.write("cr.txt", normalized.replace('\n', '\r'))
        repo.write("shift-jis.txt", "日本語\r\n".toByteArray(Charset.forName("Shift_JIS")))
        repo.commitAll()
        for ((path, charset) in listOf("utf8.txt" to "UTF-8", "bom.txt" to "UTF-8", "utf16le.txt" to "UTF-16LE",
            "utf16be.txt" to "UTF-16BE", "cr.txt" to "UTF-8")) {
            assertEquals(GitHeadContentState.CLEAN, success(repo.input(path, normalized, charset)).state, path)
        }
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("shift-jis.txt", "日本語\n", "Shift_JIS")).state)
    }

    @Test fun `Git working tree encoding reads UTF8 HEAD bytes without invoking conversion helpers`() {
        val repo = repository()
        repo.write(".gitattributes", "source.txt working-tree-encoding=UTF-16LE\n")
        repo.write("source.txt", "한국어\n".toByteArray(Charsets.UTF_16LE))
        repo.commitAll()
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("source.txt", "한국어\n", "UTF-16LE")).state)
    }

    @Test fun `binary malformed text and unavailable charset never become clean`() {
        val repo = repository()
        repo.write("binary.txt", byteArrayOf(1, 0, 2))
        repo.write("malformed.txt", byteArrayOf(0xC3.toByte(), 0x28))
        repo.commitAll()
        failure(repo.input("binary.txt", "text"), GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        failure(repo.input("malformed.txt", "text"), GitPermalinkFailureReason.UNSUPPORTED_HEAD_CONTENT)
        failure(repo.input("binary.txt", "text", "no-such-charset"), GitPermalinkFailureReason.UNEXPECTED_FAILURE)
    }

    @Test fun `replace refs cannot add a phantom path or change original HEAD contents`() {
        val repo = repository()
        val original = repo.commit("source.txt", "original\n")
        repo.write("source.txt", "replacement\n")
        val replacement = repo.commit("phantom.txt", "phantom\n")
        repo.command("update-ref", "refs/heads/main", original)
        repo.command("replace", original, replacement)
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("source.txt", "original\n")).state)
        failure(repo.input("phantom.txt", "phantom\n"), GitPermalinkFailureReason.HEAD_PATH_ABSENT)
    }

    @Test fun `inherited Git repository object namespace and config environments cannot redirect lookup`() {
        val repo = repository()
        repo.commit("source.txt", "target\n")
        val other = repository("other")
        other.commit("source.txt", "other\n")
        val hostile = System.getenv() + mapOf(
            "GIT_DIR" to other.root.resolve(".git").toString(), "GIT_WORK_TREE" to other.root.toString(),
            "GIT_COMMON_DIR" to other.root.resolve(".git").toString(), "GIT_OBJECT_DIRECTORY" to other.root.resolve(".git/objects").toString(),
            "GIT_ALTERNATE_OBJECT_DIRECTORIES" to other.root.resolve(".git/objects").toString(), "GIT_NAMESPACE" to "other",
            "GIT_CONFIG_COUNT" to "1", "GIT_CONFIG_KEY_0" to "core.bare", "GIT_CONFIG_VALUE_0" to "true",
            "GIT_REPLACE_REF_BASE" to "refs/custom-replace/", "GIT_CONFIG" to other.root.resolve(".git/config").toString(),
        )
        val result = assertIs<GitPermalinkResult.Success<GitPreparedPermalink>>(
            GitHeadTargetValidator(GitProcessRunner(environment = { hostile })).prepare(repo.input("source.txt", "target\n")))
        assertEquals(GitHeadContentState.CLEAN, result.value.state)
    }

    @Test fun `repository owned local alternates remain usable`() {
        val repo = repository()
        repo.commit("source.txt", "shared object\n")
        val clone = tempDir.resolve("shared clone 한글")
        repo.command("clone", "--shared", repo.root.toString(), clone.toString())
        val shared = LocalGitRepository(clone, initialize = false)
        shared.command("remote", "set-url", "origin", "https://github.com/owner/repo.git")
        assertTrue(Files.isRegularFile(clone.resolve(".git/objects/info/alternates")))
        assertEquals(GitHeadContentState.CLEAN, success(shared.input("source.txt", "shared object\n")).state)
    }

    @Test fun `missing promisor object fails without external diff textconv credential or transport helper`() {
        val repo = repository()
        repo.write(".gitattributes", "*.txt diff=fixture filter=fixture\n")
        repo.commit("source.txt", "original\n")
        val marker = tempDir.resolve("helper-was-run")
        val script = "echo invoked > '${marker.toString().replace("'", "'\\''")}'"
        repo.command("config", "diff.fixture.textconv", script)
        repo.command("config", "diff.external", script)
        repo.command("config", "filter.fixture.smudge", script)
        repo.command("config", "credential.helper", "!$script")
        repo.command("config", "protocol.ext.allow", "always")
        repo.command("config", "url.ext::fixture-helper.insteadOf", "https://github.com/")
        assertEquals(GitHeadContentState.CLEAN, success(repo.input("source.txt", "original\n")).state)
        val objectId = repo.command("rev-parse", "HEAD:source.txt").trim()
        Files.delete(repo.root.resolve(".git/objects/${objectId.take(2)}/${objectId.drop(2)}"))
        repo.command("config", "remote.origin.promisor", "true")
        repo.command("config", "extensions.partialClone", "origin")
        failure(repo.input("source.txt", "original\n"), GitPermalinkFailureReason.GIT_EXECUTION_FAILED)
        assertFalse(Files.exists(marker))
    }

    @Test fun `HEAD change during object lookup and metadata ABA invalidate the prepared commit`() {
        val repo = repository()
        val original = repo.commit("source.txt", "original\n")
        val changed = repo.commit("source.txt", "changed\n")
        repo.command("update-ref", "refs/heads/main", original)
        val snapshot = assertIs<GitPermalinkResult.Success<GitHeadSnapshot>>(GitHeadSnapshot.capture(repo.root)).value
        repo.command("update-ref", "refs/heads/main", changed)
        repo.command("update-ref", "refs/heads/main", original)
        assertEquals(GitPermalinkFailureReason.HEAD_CHANGED, assertIs<GitPermalinkResult.Failure>(snapshot.revalidate()).reason)
        val runner = GitProcessRunner(startProcess = { argv, root, env ->
            if ("cat-file" in argv) repo.command("update-ref", "refs/heads/main", changed)
            ProcessBuilder(argv).directory(root.toFile()).apply { environment().clear(); environment().putAll(env) }.start()
        })
        val result = GitHeadTargetValidator(runner).prepare(repo.input("source.txt", "original\n"))
        assertEquals(GitPermalinkFailureReason.HEAD_CHANGED, assertIs<GitPermalinkResult.Failure>(result).reason)
    }

    @Test fun `prepublication boundary propagates both platform and coroutine cancellation`() {
        val repo = repository()
        repo.commit("source.txt", "text\n")
        assertFailsWith<ProcessCanceledException> { validator.prepare(repo.input("source.txt", "text\n")) { throw ProcessCanceledException() } }
        assertFailsWith<CancellationException> { validator.prepare(repo.input("source.txt", "text\n")) { throw CancellationException() } }
        assertFailsWith<ProcessCanceledException> { GitRepositoryMetadataResolver.resolve(repo.root) { throw ProcessCanceledException() } }
        assertFailsWith<CancellationException> { GitRepositoryMetadataResolver.resolve(repo.root) { throw CancellationException() } }
    }

    private fun repository(name: String = "repository") = LocalGitRepository(tempDir.resolve(name))

    private fun success(input: GitPermalinkInput): GitPreparedPermalink =
        assertIs<GitPermalinkResult.Success<GitPreparedPermalink>>(validator.prepare(input)).value

    private fun failure(input: GitPermalinkInput, reason: GitPermalinkFailureReason) {
        val failure = assertIs<GitPermalinkResult.Failure>(validator.prepare(input))
        assertEquals(reason, failure.reason)
        assertFalse(failure.safeLogMessage().contains(input.documentText))
        assertFalse(failure.safeLogMessage().contains(input.filePath))
    }
}
