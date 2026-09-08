package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.EnvironmentUtil
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal sealed interface GitProcessResult {
    data class Success(val stdout: ByteArray) : GitProcessResult
    data class Failure(val reason: GitPermalinkFailureReason) : GitProcessResult
}

internal data class GitProcessLimits(
    val timeoutMillis: Long = 5_000,
    val stdoutBytes: Int = 8 * 1024 * 1024,
    val stderrBytes: Int = 64 * 1024,
    val cleanupMillis: Long = 500,
)

/** Only local plumbing commands belong here. Never pass a shell, diff, filters or a transport. */
internal class GitProcessRunner(
    private val environment: () -> Map<String, String> = { EnvironmentUtil.getEnvironmentMap() },
    private val executable: (Map<String, String>) -> Path? = SystemGitExecutable::find,
    private val startProcess: (List<String>, Path, Map<String, String>) -> Process = { argv, root, env ->
        ProcessBuilder(argv).directory(root.toFile()).apply {
            this.environment().clear()
            this.environment().putAll(env)
        }.start()
    },
    private val limits: GitProcessLimits = GitProcessLimits(),
) {
    fun run(
        root: Path,
        arguments: List<String>,
        checkCanceled: () -> Unit = ProgressManager::checkCanceled,
    ): GitProcessResult {
        checkCanceled()
        var process: Process? = null
        var stdout: GitOutputDrain? = null
        var stderr: GitOutputDrain? = null
        val descendants = linkedMapOf<Long, ProcessHandle>()
        try {
            val inherited = environment()
            val git = executable(inherited) ?: return failed(GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.timeoutMillis)
            process = try {
                startProcess(command(git, root, arguments), root, isolatedEnvironment(inherited))
            } catch (_: IOException) {
                return failed(GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE)
            }
            // No command consumes stdin. Closing it also prevents an unexpected executable from prompting.
            process.outputStream.close()
            rememberDescendants(process, descendants)
            stdout = GitOutputDrain(process.inputStream, limits.stdoutBytes, retain = true)
            stderr = GitOutputDrain(process.errorStream, limits.stderrBytes, retain = false)
            stdout.start()
            stderr.start()
            while (true) {
                checkCanceled()
                rememberDescendants(process, descendants)
                if (stdout.exceeded.get() || stderr.exceeded.get()) {
                    return failed(GitPermalinkFailureReason.GIT_OUTPUT_LIMIT)
                }
                (stdout.failure.get() ?: stderr.failure.get())?.let { throw it }
                if (!process.isAlive && stdout.finished.get() && stderr.finished.get()) {
                    // Acquire finished first, then inspect the terminal result again: a drain can
                    // finish between the earlier failure/limit reads and this terminal branch.
                    (stdout.failure.get() ?: stderr.failure.get())?.let { throw it }
                    if (stdout.exceeded.get() || stderr.exceeded.get()) {
                        return failed(GitPermalinkFailureReason.GIT_OUTPUT_LIMIT)
                    }
                    checkCanceled()
                    return if (process.exitValue() == 0) GitProcessResult.Success(stdout.bytes())
                    else failed(GitPermalinkFailureReason.GIT_EXECUTION_FAILED)
                }
                if (System.nanoTime() >= deadline) return failed(GitPermalinkFailureReason.GIT_TIMEOUT)
                // Both pipes are drained concurrently; waiting never depends on either pipe reaching EOF.
                if (process.isAlive) process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)
                else if (!stdout.finished.get()) stdout.await(POLL_MILLIS)
                else stderr.await(POLL_MILLIS)
            }
        } catch (canceled: ProcessCanceledException) {
            throw canceled
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Git lookup interrupted").also { it.initCause(interrupted) }
        } catch (_: Exception) {
            return failed(GitPermalinkFailureReason.GIT_EXECUTION_FAILED)
        } finally {
            process?.let { cleanup(it, stdout, stderr, descendants) }
        }
    }

    private fun cleanup(process: Process, stdout: GitOutputDrain?, stderr: GitOutputDrain?, descendants: MutableMap<Long, ProcessHandle>) {
        // The worker owns this process and its pipes. No process or stream escapes the invocation.
        rememberDescendants(process, descendants)
        descendants.values.forEach { if (it.isAlive) it.destroyForcibly() }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        closeStream(process.outputStream)
        closeStream(process.inputStream)
        closeStream(process.errorStream)
        val interrupted = Thread.interrupted()
        try {
            process.waitFor(limits.cleanupMillis, TimeUnit.MILLISECONDS)
            stdout?.await(limits.cleanupMillis)
            stderr?.await(limits.cleanupMillis)
        } catch (exception: InterruptedException) {
            // Preserve interruption while still closing all streams and terminating the owned process.
            Thread.currentThread().interrupt()
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun rememberDescendants(process: Process, descendants: MutableMap<Long, ProcessHandle>) {
        try {
            process.descendants().use { children -> children.forEach { descendants[it.pid()] = it } }
        } catch (_: UnsupportedOperationException) {
            // A test Process may have no native handle. Production ProcessBuilder processes do.
        } catch (_: SecurityException) {
            // A restricted runtime may deny enumeration; still terminate the directly owned process.
        }
    }

    internal fun command(git: Path, root: Path, arguments: List<String>): List<String> = listOf(
        git.toString(), "--no-pager", "--no-replace-objects", "--no-lazy-fetch", "--no-optional-locks",
        "--literal-pathspecs", "-C", root.toAbsolutePath().normalize().toString(),
        "-c", "protocol.allow=never", "-c", "credential.helper=", "-c", "core.fsmonitor=false", "-c", "core.attributesFile=",
    ) + arguments

    internal fun isolatedEnvironment(inherited: Map<String, String>): Map<String, String> =
        inherited.filterKeys { !it.startsWith("GIT_", ignoreCase = true) }.toMutableMap().apply {
            put("GIT_CONFIG_NOSYSTEM", "1")
            put("GIT_ATTR_NOSYSTEM", "1")
            put("GIT_CONFIG_GLOBAL", if (SystemGitExecutable.isWindows) "NUL" else "/dev/null")
            put("GIT_NO_REPLACE_OBJECTS", "1")
            put("GIT_NO_LAZY_FETCH", "1")
            put("GIT_ALLOW_PROTOCOL", "")
            put("GIT_TERMINAL_PROMPT", "0")
            put("GIT_OPTIONAL_LOCKS", "0")
        }

    private fun failed(reason: GitPermalinkFailureReason) = GitProcessResult.Failure(reason)

    private companion object {
        const val POLL_MILLIS = 20L
    }
}

internal object SystemGitExecutable {
    val isWindows: Boolean get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    fun find(environment: Map<String, String>): Path? = find(environment, isWindows)

    internal fun find(environment: Map<String, String>, windows: Boolean): Path? {
        val path = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = windows) }?.value
            ?: return null
        return path.split(if (windows) ';' else ':').asSequence().filter(String::isNotBlank)
            .mapNotNull {
                try { Path.of(it.removeSurrounding("\""), if (windows) "git.exe" else "git") }
                catch (_: java.nio.file.InvalidPathException) { null }
            }
            .firstOrNull { it.isAbsolute && Files.isRegularFile(it) && Files.isExecutable(it) }
    }
}

private class GitOutputDrain(private val stream: InputStream, private val limit: Int, retain: Boolean) {
    val exceeded = AtomicBoolean()
    val finished = AtomicBoolean()
    val failure = AtomicReference<Throwable?>()
    private val output = if (retain) ByteArrayOutputStream() else null
    private val thread = Thread({ drain() }, "copy-selection-git-output").apply { isDaemon = true }

    fun start() = thread.start()
    fun await(millis: Long) = thread.join(millis)
    fun bytes(): ByteArray = output?.toByteArray() ?: byteArrayOf()

    private fun drain() {
        try {
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) {
                    exceeded.set(true)
                    break
                }
                output?.write(buffer, 0, count)
            }
        } catch (exception: Throwable) {
            // Transfer control flow to the owner: cancellation/fatal errors must escape after cleanup.
            // Ordinary failures are converted there without exposing this exception or pipe contents.
            failure.set(exception)
        } finally {
            finished.set(true)
        }
    }
}

private fun closeStream(stream: java.io.Closeable) {
    try {
        stream.close()
    } catch (_: IOException) {
        // Cleanup is best effort after process termination; this cannot turn cancellation into failure.
    }
}
