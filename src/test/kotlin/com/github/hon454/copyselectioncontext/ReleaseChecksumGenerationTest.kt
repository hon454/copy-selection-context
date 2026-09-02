package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class ReleaseChecksumGenerationTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val generator = projectRoot.resolve("scripts/generate-release-checksum.sh")

    @Test
    fun `generator writes and verifies a portable SHA256SUMS entry`(@TempDir tempDir: Path) {
        val distributionDirectory = Files.createDirectory(tempDir.resolve("distributions"))
        val pluginZip = distributionDirectory.resolve("copy-selection-context-1.2.0.zip")
        val checksumFile = tempDir.resolve("SHA256SUMS")
        val artifactBytes = "canonical release fixture\n".toByteArray()
        Files.write(pluginZip, artifactBytes)

        val result = runGenerator(pluginZip, checksumFile)

        assertEquals(0, result.exitCode, result.output)
        assertTrue(result.output.contains("OK"), result.output)
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(artifactBytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        assertEquals(
            "$expectedDigest  ${pluginZip.fileName}\n",
            Files.readString(checksumFile),
        )
    }

    @Test
    fun `generator fails closed for missing and non ZIP artifacts`(@TempDir tempDir: Path) {
        val missing = runGenerator(tempDir.resolve("missing.zip"), tempDir.resolve("SHA256SUMS"))
        val textArtifact = tempDir.resolve("plugin.txt")
        Files.writeString(textArtifact, "not a plugin ZIP")
        val wrongType = runGenerator(textArtifact, tempDir.resolve("SHA256SUMS"))

        assertEquals(1, missing.exitCode, missing.output)
        assertTrue(missing.output.contains("Plugin ZIP not found"), missing.output)
        assertEquals(1, wrongType.exitCode, wrongType.output)
        assertTrue(wrongType.output.contains("must be a ZIP file"), wrongType.output)
        assertFalse(Files.exists(tempDir.resolve("SHA256SUMS")))
    }

    private fun runGenerator(pluginZip: Path, checksumFile: Path): CommandResult {
        val process = ProcessBuilder(
            "bash",
            generator.toString(),
            pluginZip.toString(),
            checksumFile.toString(),
        )
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return CommandResult(process.waitFor(), output)
    }

    private data class CommandResult(val exitCode: Int, val output: String)
}
