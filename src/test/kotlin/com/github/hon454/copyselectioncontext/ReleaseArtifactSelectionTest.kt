package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReleaseArtifactSelectionTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val modeResolver = projectRoot.resolve("scripts/resolve-release-mode.sh")
    private val artifactSelector = projectRoot.resolve("scripts/select-release-artifact.sh")

    @Test
    fun `complete signing and publishing secrets enable signed publication`(@TempDir tempDir: Path) {
        val result = resolveMode(
            tempDir,
            mapOf(
                "CERTIFICATE_CHAIN" to "fixture certificate",
                "PRIVATE_KEY" to "fixture key",
                "PUBLISH_TOKEN" to "fixture token",
            ),
        )

        assertEquals(0, result.exitCode, result.output)
        assertEquals(listOf("signed=true", "publish=true"), Files.readAllLines(result.outputFile))
        assertTrue(result.output.contains("signed; Marketplace publication is enabled"), result.output)
    }

    @Test
    fun `missing signing secrets select unsigned mode and skip publication`(@TempDir tempDir: Path) {
        val result = resolveMode(tempDir, mapOf("PUBLISH_TOKEN" to "fixture token"))

        assertEquals(0, result.exitCode, result.output)
        assertEquals(listOf("signed=false", "publish=false"), Files.readAllLines(result.outputFile))
        assertTrue(result.output.contains("unsigned"), result.output)
        assertTrue(result.output.contains("Marketplace publication is skipped"), result.output)
    }

    @Test
    fun `signing without publishing token still selects signed canonical mode`(@TempDir tempDir: Path) {
        val result = resolveMode(
            tempDir,
            mapOf(
                "CERTIFICATE_CHAIN" to "fixture certificate",
                "PRIVATE_KEY" to "fixture key",
            ),
        )

        assertEquals(0, result.exitCode, result.output)
        assertEquals(listOf("signed=true", "publish=false"), Files.readAllLines(result.outputFile))
    }

    @Test
    fun `partial signing configuration fails closed`(@TempDir tempDir: Path) {
        val result = resolveMode(tempDir, mapOf("PRIVATE_KEY" to "fixture key"))

        assertEquals(1, result.exitCode, result.output)
        assertTrue(result.output.contains("Signing configuration is incomplete"), result.output)
        assertFalse(Files.exists(result.outputFile))
    }

    @Test
    fun `signed mode selects the signed ZIP and records the exact path`(@TempDir tempDir: Path) {
        val distributions = Files.createDirectory(tempDir.resolve("distributions"))
        Files.writeString(distributions.resolve("copy-selection-context-1.2.0.zip"), "unsigned")
        val signedZip = distributions.resolve("copy-selection-context-1.2.0-signed.zip")
        Files.writeString(signedZip, "signed")

        val result = selectArtifact(tempDir, distributions, signed = true)

        assertEquals(0, result.exitCode, result.output)
        assertEquals(
            listOf("path=$signedZip", "signed=true"),
            Files.readAllLines(result.outputFile),
        )
    }

    @Test
    fun `unsigned mode selects only the unsigned ZIP`(@TempDir tempDir: Path) {
        val distributions = Files.createDirectory(tempDir.resolve("distributions"))
        val unsignedZip = distributions.resolve("copy-selection-context-1.2.0.zip")
        Files.writeString(unsignedZip, "unsigned")

        val result = selectArtifact(tempDir, distributions, signed = false)

        assertEquals(0, result.exitCode, result.output)
        assertEquals(
            listOf("path=$unsignedZip", "signed=false"),
            Files.readAllLines(result.outputFile),
        )
    }

    @Test
    fun `selector rejects missing signed output and stale signed output`(@TempDir tempDir: Path) {
        val distributions = Files.createDirectory(tempDir.resolve("distributions"))
        Files.writeString(distributions.resolve("copy-selection-context-1.2.0.zip"), "unsigned")

        val missingSigned = selectArtifact(tempDir.resolve("missing"), distributions, signed = true)
        Files.writeString(distributions.resolve("copy-selection-context-1.2.0-signed.zip"), "signed")
        val staleSigned = selectArtifact(tempDir.resolve("stale"), distributions, signed = false)

        assertEquals(1, missingSigned.exitCode, missingSigned.output)
        assertTrue(missingSigned.output.contains("Expected exactly one signed plugin ZIP"), missingSigned.output)
        assertEquals(1, staleSigned.exitCode, staleSigned.output)
        assertTrue(staleSigned.output.contains("must not contain signed plugin ZIPs"), staleSigned.output)
    }

    private fun resolveMode(tempDir: Path, environment: Map<String, String>): ScriptResult {
        Files.createDirectories(tempDir)
        val outputFile = tempDir.resolve("mode-output.txt")
        return runScript(modeResolver, listOf(outputFile.toString()), environment, outputFile)
    }

    private fun selectArtifact(tempDir: Path, distributions: Path, signed: Boolean): ScriptResult {
        Files.createDirectories(tempDir)
        val outputFile = tempDir.resolve("artifact-output.txt")
        return runScript(
            artifactSelector,
            listOf(distributions.toString(), signed.toString(), outputFile.toString()),
            emptyMap(),
            outputFile,
        )
    }

    private fun runScript(
        script: Path,
        arguments: List<String>,
        environment: Map<String, String>,
        outputFile: Path,
    ): ScriptResult {
        val processBuilder = ProcessBuilder(listOf("bash", script.toString()) + arguments)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
        listOf("CERTIFICATE_CHAIN", "PRIVATE_KEY", "PUBLISH_TOKEN").forEach {
            processBuilder.environment().remove(it)
        }
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ScriptResult(process.waitFor(), output, outputFile)
    }

    private data class ScriptResult(
        val exitCode: Int,
        val output: String,
        val outputFile: Path,
    )
}
