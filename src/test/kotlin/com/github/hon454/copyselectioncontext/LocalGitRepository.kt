package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real, local-only Git fixture setup. These mutating setup commands never belong in plugin code. */
internal class LocalGitRepository(val root: Path, initialize: Boolean = true) {
    private val git = requireNotNull(SystemGitExecutable.find(System.getenv())) { "System Git is required for local Git integration tests" }

    init {
        Files.createDirectories(root)
        if (initialize) {
            command("init", "-b", "main")
            command("remote", "add", "origin", "https://github.com/owner/repo.git")
        }
    }

    fun write(relative: String, content: String): Path = write(relative, content.toByteArray())

    fun write(relative: String, bytes: ByteArray): Path = root.resolve(relative).also {
        Files.createDirectories(it.parent)
        Files.write(it, bytes)
    }

    fun commit(relative: String, content: String): String {
        write(relative, content)
        return commitAll()
    }

    fun commitAll(): String {
        command("add", "--all")
        command("commit", "-m", "local fixture")
        return command("rev-parse", "HEAD").trim()
    }

    fun command(vararg args: String): String {
        val process = ProcessBuilder(listOf(git.toString(), "-C", root.toString(),
            "-c", "user.name=Local Fixture", "-c", "user.email=fixture@example.invalid",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=${root.resolve("empty-hooks")}",
            "-c", "core.autocrlf=false", "-c", "maintenance.auto=false") + args)
            .redirectErrorStream(true).apply {
                environment().keys.removeIf { it.startsWith("GIT_", true) }
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = if (SystemGitExecutable.isWindows) "NUL" else "/dev/null"
                environment()["GIT_TERMINAL_PROMPT"] = "0"
            }.start()
        process.outputStream.close()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Local Git fixture setup timed out")
            val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
            assertEquals(0, process.exitValue(), "Local Git fixture command ${args.firstOrNull()} failed: $output")
            return output
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.inputStream.close()
            process.errorStream.close()
        }
    }

    fun input(relative: String, document: String, charset: String = "UTF-8", ranges: List<Pair<Int, Int>> = listOf(1 to 1)) =
        GitPermalinkInput(root.toString(), root.resolve(relative).toString(), document, charset, ranges)
}
