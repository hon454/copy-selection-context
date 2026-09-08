package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Process seam injection plus explicitly named real helper-process checks; neither is a real OS malfunction. */
class GitProcessRunnerTest {
    @TempDir lateinit var root: Path

    @Test fun `missing Git is rediscovered on the next explicit attempt`() {
        var available = false
        var discoveries = 0
        val runner = GitProcessRunner(environment = { emptyMap() }, executable = {
            discoveries++
            root.resolve("git").takeIf { available }
        }, startProcess = { _, _, _ -> FakeProcess() })
        assertEquals(GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE, failure(runner).reason)
        available = true
        assertIs<GitProcessResult.Success>(run(runner))
        assertEquals(2, discoveries)
    }

    @Test fun `execution permission denial and invalid executable format become unavailable Git`() {
        listOf("permission denied", "exec format error").forEach { injected ->
            val runner = runner(start = { throw IOException(injected) })
            assertEquals(GitPermalinkFailureReason.SYSTEM_GIT_UNAVAILABLE, failure(runner).reason)
        }
    }

    @Test fun `nonzero exit is typed without retaining raw stderr or code`() {
        val process = FakeProcess(error = TrackingInput("private source and credentials".toByteArray()), code = 19)
        val result = failure(runner(process))
        assertEquals(GitPermalinkFailureReason.GIT_EXECUTION_FAILED, result.reason)
        assertFalse(result.toString().contains("private"))
        assertClosed(process)
    }

    @Test fun `timeout terminates the owned process and closes every stream`() {
        val process = FakeProcess(alive = true)
        assertEquals(GitPermalinkFailureReason.GIT_TIMEOUT,
            failure(runner(process, GitProcessLimits(timeoutMillis = 0))).reason)
        assertClosed(process)
    }

    @Test fun `stdout and stderr have independent exact output limits`() {
        val limits = GitProcessLimits(stdoutBytes = 16, stderrBytes = 8)
        val exact = FakeProcess(output = TrackingInput(ByteArray(16) { 42 }), error = TrackingInput(ByteArray(8)))
        assertContentEquals(ByteArray(16) { 42 }, assertIs<GitProcessResult.Success>(run(runner(exact, limits))).stdout)
        assertClosed(exact)
        for (process in listOf(FakeProcess(output = TrackingInput(ByteArray(17))), FakeProcess(error = TrackingInput(ByteArray(9))))) {
            assertEquals(GitPermalinkFailureReason.GIT_OUTPUT_LIMIT, failure(runner(process, limits)).reason)
            assertClosed(process)
        }
    }

    @Test fun `stdout and stderr drain concurrently even when one pipe requires the other to progress`() {
        val stdoutEntered = CountDownLatch(1)
        val stderrEntered = CountDownLatch(1)
        val process = FakeProcess(output = RendezvousInput(stdoutEntered, stderrEntered), error = RendezvousInput(stderrEntered, stdoutEntered))
        assertIs<GitProcessResult.Success>(run(runner(process)))
        assertEquals(0, stdoutEntered.count)
        assertEquals(0, stderrEntered.count)
        assertClosed(process)
    }

    @Test fun `cancellation after process start cleans up then propagates the original exception`() {
        for (canceled in listOf(ProcessCanceledException(), CancellationException("injected"))) {
            val process = FakeProcess(alive = true)
            var checkpoints = 0
            val thrown = assertFailsWith<RuntimeException> {
                runner(process).run(root, listOf("cat-file", "blob", SHA)) {
                    if (++checkpoints >= 2) throw canceled
                }
            }
            assertTrue(thrown === canceled)
            assertClosed(process)
        }
    }

    @Test fun `cancellation from process startup is not converted to an ordinary error`() {
        assertFailsWith<ProcessCanceledException> { run(runner(start = { throw ProcessCanceledException() })) }
        assertFailsWith<CancellationException> { run(runner(start = { throw CancellationException() })) }
    }

    @Test fun `pipe read failure cannot become an empty successful result`() {
        val process = FakeProcess(output = object : TrackingInput() {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = throw IOException("private read failure")
        })
        assertEquals(GitPermalinkFailureReason.GIT_EXECUTION_FAILED, failure(runner(process)).reason)
        assertClosed(process)
    }

    @Test fun `pipe cancellation reaches the owner unchanged after stream cleanup`() {
        for (canceled in listOf(ProcessCanceledException(), CancellationException())) {
            val process = FakeProcess(error = object : TrackingInput() {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int = throw canceled
            })
            assertTrue(assertFailsWith<RuntimeException> { run(runner(process)) } === canceled)
            assertClosed(process)
        }
    }

    @Test fun `argv preserves repository executable SHA and metacharacter paths exactly`() {
        val executable = root.resolve("Git 폴더 & (safe)").resolve("git.exe")
        val repository = root.resolve("repo space ' \$(echo ignored)")
        val args = listOf("cat-file", "blob", "$SHA:dir/한글 [x] & \$().txt")
        var actual: List<String>? = null
        val runner = GitProcessRunner(environment = { emptyMap() }, executable = { executable },
            startProcess = { argv, workingDirectory, env ->
                actual = argv
                assertEquals(repository, workingDirectory)
                assertEquals("", env["GIT_ALLOW_PROTOCOL"])
                FakeProcess()
            })
        assertIs<GitProcessResult.Success>(runner.run(repository, args) {})
        assertEquals(executable.toString(), actual?.first())
        assertEquals(args, actual?.takeLast(3))
        assertEquals(repository.toString(), actual?.let { it[it.indexOf("-C") + 1] })
        assertTrue(actual.orEmpty().containsAll(listOf("--no-replace-objects", "--no-lazy-fetch", "--literal-pathspecs")))
        assertFalse(actual.orEmpty().any { it in listOf("sh", "bash", "cmd", "--textconv", "--filters") })
    }

    @Test fun `child environment removes all inherited Git controls while leaving unrelated IDE environment intact`() {
        val unsafe = mapOf("GIT_DIR" to "other", "git_object_directory" to "other", "GIT_CONFIG_COUNT" to "1",
            "GIT_TRACE" to "private-log", "GIT_SSH_COMMAND" to "helper", "GIT_EXEC_PATH" to "other-bin", "KEEP_ME" to "kept")
        val env = runner().isolatedEnvironment(unsafe)
        assertEquals("kept", env["KEEP_ME"])
        unsafe.keys.filter { it != "KEEP_ME" }.forEach { assertNull(env[it]) }
        assertEquals("1", env["GIT_NO_LAZY_FETCH"])
        assertEquals("1", env["GIT_NO_REPLACE_OBJECTS"])
        assertEquals("", env["GIT_ALLOW_PROTOCOL"])
        assertEquals("0", env["GIT_TERMINAL_PROMPT"])
    }

    @Test fun `PATH discovery supports git exe and space unicode directories without caching failure`() {
        val bin = Files.createDirectories(root.resolve("Git 폴더 & space"))
        assertNull(SystemGitExecutable.find(mapOf("Path" to bin.toString()), windows = true))
        val exe = Files.write(bin.resolve("git.exe"), byteArrayOf(1))
        assertTrue(exe.toFile().setExecutable(true) || Files.isExecutable(exe))
        assertEquals(exe, SystemGitExecutable.find(mapOf("Path" to "\"$bin\""), windows = true))
    }

    @Test fun `real helper process fills stderr without deadlock and leaves no owned process or drain thread`() {
        val child = AtomicReference<Process>()
        val runner = runner(limits = GitProcessLimits(timeoutMillis = 15_000, stderrBytes = 256 * 1024), start = {
            helperProcess("pipes").also(child::set)
        })
        assertEquals("complete", assertIs<GitProcessResult.Success>(run(runner)).stdout.toString(Charsets.UTF_8))
        assertFalse(child.get().isAlive)
        assertNoDrainThreads()
    }

    @Test fun `real helper process is terminated on timeout and cancellation`() {
        for (cancel in listOf(false, true)) {
            val child = AtomicReference<Process>()
            val runner = runner(limits = GitProcessLimits(timeoutMillis = if (cancel) 5_000 else 0), start = {
                helperProcess("wait").also(child::set)
            })
            if (cancel) {
                var checks = 0
                assertFailsWith<ProcessCanceledException> {
                    runner.run(root, listOf("cat-file", "blob", SHA)) { if (++checks > 1) throw ProcessCanceledException() }
                }
            } else {
                assertEquals(GitPermalinkFailureReason.GIT_TIMEOUT, failure(runner).reason)
            }
            assertFalse(child.get().isAlive)
            assertNoDrainThreads()
        }
    }

    private fun helperProcess(mode: String): Process {
        val source = root.resolve("RunnerProbe.java")
        Files.writeString(source, """
            class RunnerProbe {
                public static void main(String[] args) throws Exception {
                    if (args[0].equals("wait")) { Thread.sleep(60000); return; }
                    System.err.write(new byte[131072]);
                    System.err.flush();
                    System.out.print("complete");
                }
            }
        """.trimIndent())
        val java = Path.of(System.getProperty("java.home"), "bin", if (SystemGitExecutable.isWindows) "java.exe" else "java")
        return ProcessBuilder(java.toString(), "-Xmx128m", source.toString(), mode).start()
    }

    private fun assertNoDrainThreads() {
        assertTrue(Thread.getAllStackTraces().keys.none { it.isAlive && it.name == "copy-selection-git-output" })
    }

    private fun runner(process: FakeProcess = FakeProcess(), limits: GitProcessLimits = GitProcessLimits(), start: (() -> Process)? = null) =
        GitProcessRunner(environment = { emptyMap() }, executable = { root.resolve("git") },
            startProcess = { _, _, _ -> start?.invoke() ?: process }, limits = limits)

    private fun run(runner: GitProcessRunner) = runner.run(root, listOf("cat-file", "blob", SHA)) {}
    private fun failure(runner: GitProcessRunner) = assertIs<GitProcessResult.Failure>(run(runner))
    private fun assertClosed(process: FakeProcess) {
        assertFalse(process.isAlive)
        assertTrue(process.destroyed)
        assertTrue(process.output.closed)
        assertTrue(process.error.closed)
        assertTrue(process.inputClosed)
    }

    private open class TrackingInput(bytes: ByteArray = byteArrayOf()) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    private class RendezvousInput(private val entered: CountDownLatch, private val other: CountDownLatch) : TrackingInput() {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            entered.countDown()
            check(other.await(2, TimeUnit.SECONDS)) { "Both Git pipes must be drained concurrently" }
            return -1
        }
    }

    private class FakeProcess(
        val output: TrackingInput = TrackingInput(),
        val error: TrackingInput = TrackingInput(),
        private val code: Int = 0,
        alive: Boolean = false,
    ) : Process() {
        private val running = AtomicBoolean(alive)
        var destroyed = false
        var inputClosed = false
        private val input = object : ByteArrayOutputStream() { override fun close() { inputClosed = true } }
        override fun getOutputStream() = input
        override fun getInputStream(): InputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !running.get()
        override fun exitValue(): Int = code
        override fun isAlive(): Boolean = running.get()
        override fun destroy() { destroyed = true; running.set(false) }
        override fun destroyForcibly(): Process { destroy(); return this }
    }

    private companion object { const val SHA = "0123456789abcdef0123456789abcdef01234567" }
}
