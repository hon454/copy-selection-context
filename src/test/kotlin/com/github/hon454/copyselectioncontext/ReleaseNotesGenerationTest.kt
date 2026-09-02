package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReleaseNotesGenerationTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val generator = projectRoot.resolve("scripts/generate-release-notes.sh")

    @Test
    fun `cold wrapper output stays out of requested version notes`(@TempDir tempDir: Path) {
        val expectedNotes = """
            ### Fixed
            - Keep Gradle output out of release notes (#53)
        """.trimIndent() + "\n"
        val result = runGenerator(tempDir, expectedNotes)

        assertEquals(0, result.exitCode, result.output)
        assertTrue(
            result.output.contains("Downloading https://services.gradle.org/distributions/gradle.zip"),
            result.output,
        )
        assertEquals(expectedNotes, Files.readString(result.releaseNotes))
        assertFalse(Files.readString(result.releaseNotes).contains("Downloading"))
        assertEquals(
            listOf(
                "--version --console=plain",
                "getChangelog --project-version 1.2.0 --console=plain -q --no-header --no-links --no-summary",
            ),
            Files.readAllLines(result.invocationLog),
        )
    }

    @Test
    fun `empty changelog output uses the release tag fallback`(@TempDir tempDir: Path) {
        val result = runGenerator(tempDir, "")

        assertEquals(0, result.exitCode, result.output)
        assertEquals("Release v1.2.0\n", Files.readString(result.releaseNotes))
    }

    private fun runGenerator(tempDir: Path, changelogOutput: String): GenerationResult {
        val fakeWrapper = tempDir.resolve("gradlew")
        val invocationLog = tempDir.resolve("gradle-invocations.log")
        val releaseNotes = tempDir.resolve("release-notes.md")
        Files.writeString(
            fakeWrapper,
            """
            #!/usr/bin/env bash
            set -euo pipefail

            printf '%s\n' "${'$'}*" >> "${'$'}FAKE_GRADLE_LOG"
            case "${'$'}{1:-}" in
              --version)
                echo "Downloading https://services.gradle.org/distributions/gradle.zip"
                echo "10%...100%"
                echo "Welcome to Gradle"
                ;;
              getChangelog)
                printf '%s' "${'$'}FAKE_CHANGELOG_OUTPUT"
                ;;
              *)
                exit 2
                ;;
            esac
            """.trimIndent(),
        )
        assertTrue(
            fakeWrapper.toFile().setExecutable(true),
            "Failed to make fake Gradle wrapper executable",
        )

        val process = ProcessBuilder(
            "bash",
            generator.toString(),
            "1.2.0",
            "v1.2.0",
            releaseNotes.toString(),
        )
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
        process.environment()["GRADLE_WRAPPER"] = fakeWrapper.toString()
        process.environment()["FAKE_GRADLE_LOG"] = invocationLog.toString()
        process.environment()["FAKE_CHANGELOG_OUTPUT"] = changelogOutput

        val startedProcess = process.start()
        val output = startedProcess.inputStream.bufferedReader().use { it.readText() }
        return GenerationResult(startedProcess.waitFor(), output, releaseNotes, invocationLog)
    }

    private data class GenerationResult(
        val exitCode: Int,
        val output: String,
        val releaseNotes: Path,
        val invocationLog: Path,
    )
}
