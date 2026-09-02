package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReleaseVersionParityTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val verifier = projectRoot.resolve("scripts/verify-release-version.sh")

    @Test
    fun `canonical project version is 1_3_0`() {
        assertEquals("1.3.0", canonicalVersion(projectRoot.resolve("build.gradle.kts")))
    }

    @Test
    fun `verifier accepts the tag matching the canonical version`() {
        val result = runVerifier("v${canonicalVersion(projectRoot.resolve("build.gradle.kts"))}")

        assertEquals(0, result.exitCode, result.output)
        assertTrue(result.output.contains("Version verified: v1.3.0"), result.output)
    }

    @Test
    fun `verifier rejects mismatched and malformed tags`() {
        val mismatch = runVerifier("v9.9.9")
        val malformed = runVerifier("1.1.0")

        assertEquals(1, mismatch.exitCode, mismatch.output)
        assertTrue(mismatch.output.contains("Version mismatch"), mismatch.output)
        assertEquals(1, malformed.exitCode, malformed.output)
        assertTrue(malformed.output.contains("must use v<major>.<minor>.<patch>"), malformed.output)
    }

    @Test
    fun `verifier rejects ambiguous canonical declarations`(@TempDir tempDir: Path) {
        val ambiguousBuildFile = tempDir.resolve("build.gradle.kts")
        Files.writeString(
            ambiguousBuildFile,
            """
            version = "1.1.0"
            version = "1.1.0"
            """.trimIndent()
        )

        val result = runVerifier("v1.1.0", ambiguousBuildFile)

        assertEquals(1, result.exitCode, result.output)
        assertTrue(result.output.contains("exactly one canonical version declaration"), result.output)
    }

    @Test
    fun `release workflow preserves deterministic version and changelog inputs`() {
        val workflow = Files.readString(projectRoot.resolve(".github/workflows/release.yml"))
        val releaseNotesGenerator = Files.readString(projectRoot.resolve("scripts/generate-release-notes.sh"))

        assertTrue(
            workflow.contains("bash scripts/verify-release-version.sh \"${'$'}{{ steps.version.outputs.tag }}\""),
            workflow
        )
        assertFalse(workflow.contains("grep -oP"), workflow)
        assertTrue(
            workflow.contains("bash scripts/generate-release-notes.sh"),
            workflow,
        )
        assertTrue(
            releaseNotesGenerator.contains("--project-version \"${'$'}release_version\""),
            releaseNotesGenerator,
        )
    }

    @Test
    fun `versioned release candidate notes remain explicitly unreleased`() {
        val changelog = Files.readString(projectRoot.resolve("CHANGELOG.md"))
        val releaseCandidateNotes = changelog
            .substringAfter("## [1.1.0]")
            .substringBefore("## [1.0.4]")

        assertTrue(releaseCandidateNotes.contains("remain unreleased until"), releaseCandidateNotes)
        (5..19).forEach { issue ->
            assertTrue(releaseCandidateNotes.contains("(#$issue)"), "Missing issue #$issue from v1.1.0 notes")
        }
    }

    private fun canonicalVersion(buildFile: Path): String {
        val match = Regex("(?m)^\\s*version\\s*=\\s*\"([^\"]+)\"").find(Files.readString(buildFile))
        return requireNotNull(match) { "Canonical version declaration not found in $buildFile" }.groupValues[1]
    }

    private fun runVerifier(tag: String, buildFile: Path? = null): CommandResult {
        val command = mutableListOf("bash", verifier.toString(), tag)
        buildFile?.let { command.add(it.toString()) }
        val process = ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return CommandResult(process.waitFor(), output)
    }

    private data class CommandResult(val exitCode: Int, val output: String)
}
