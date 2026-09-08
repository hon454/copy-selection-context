package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Path

class ReleaseVersionParityTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val verifier = projectRoot.resolve("scripts/verify-release-version.sh")

    @Test
    fun `canonical project version uses supported semantic version format`() {
        val version = canonicalVersion(projectRoot.resolve("build.gradle.kts"))

        assertTrue(version.matches(SEMVER), version)
    }

    @Test
    fun `runtime version resource matches the canonical project version`() {
        assertEquals(
            canonicalVersion(projectRoot.resolve("build.gradle.kts")),
            CopySelectionReviewService().currentPluginVersion(),
        )
    }

    @Test
    fun `verifier accepts the tag matching the canonical version`() {
        val version = canonicalVersion(projectRoot.resolve("build.gradle.kts"))
        val result = runVerifier("v$version")

        assertEquals(0, result.exitCode, result.output)
        assertTrue(result.output.contains("Version verified: v$version"), result.output)
    }

    @Test
    fun `verifier accepts matching tags for independent normal semantic version fixtures`(@TempDir tempDir: Path) {
        listOf("2.17.3", "7.24.8").forEachIndexed { index, version ->
            val buildFile = writeCanonicalBuildFile(tempDir.resolve("build-$index.gradle.kts"), version)
            val result = runVerifier("v$version", buildFile)

            assertEquals(0, result.exitCode, result.output)
            assertTrue(result.output.contains("Version verified: v$version"), result.output)
        }
    }

    @Test
    fun `verifier rejects mismatched malformed tag and malformed canonical version`(@TempDir tempDir: Path) {
        val buildFile = writeCanonicalBuildFile(tempDir.resolve("mismatch.gradle.kts"), "4.12.6")
        val mismatch = runVerifier("v4.12.7", buildFile)
        val malformedTag = runVerifier("4.12.6", buildFile)
        val malformedCanonical = runVerifier(
            "v4.12.6",
            writeCanonicalBuildFile(tempDir.resolve("malformed.gradle.kts"), "4.12.6-rc.1"),
        )

        assertEquals(1, mismatch.exitCode, mismatch.output)
        assertTrue(mismatch.output.contains("Version mismatch"), mismatch.output)
        assertEquals(1, malformedTag.exitCode, malformedTag.output)
        assertTrue(malformedTag.output.contains("must use v<major>.<minor>.<patch>"), malformedTag.output)
        assertEquals(1, malformedCanonical.exitCode, malformedCanonical.output)
        assertTrue(malformedCanonical.output.contains("Canonical version must use <major>.<minor>.<patch>"), malformedCanonical.output)
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
        val workflow = readNormalized(projectRoot.resolve(".github/workflows/release.yml"))
        val releaseNotesGenerator = Files.readString(projectRoot.resolve("scripts/generate-release-notes.sh"))

        assertTrue(
            workflow.contains("bash scripts/verify-release-version.sh \"${'$'}GITHUB_REF_NAME\""),
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
    fun `workflow publishes version outputs only after matching tag validation`(@TempDir tempDir: Path) {
        val version = canonicalVersion(projectRoot.resolve("build.gradle.kts"))
        val result = runWorkflowVersionBoundary("v$version", tempDir)

        assertEquals(0, result.exitCode, result.output)
        assertEquals(mapOf("version" to version, "tag" to "v$version"), result.outputs)
    }

    @Test
    fun `workflow rejects missing mismatched and malformed tags without outputs`(@TempDir tempDir: Path) {
        val version = canonicalVersion(projectRoot.resolve("build.gradle.kts"))
        val invalidTags = listOf("", "v${differentSemanticVersion(version)}", "3.14.1", "v3.14.1-rc.1")

        invalidTags.forEachIndexed { index, tag ->
            val result = runWorkflowVersionBoundary(tag, tempDir.resolve(index.toString()))

            assertEquals(1, result.exitCode, result.output)
            assertTrue(result.outputs.isEmpty(), "Invalid tag must not reach downstream outputs: $result")
        }
    }

    @Test
    fun `workflow rejects command substitutions as literal tag data`(@TempDir tempDir: Path) {
        val tags = listOf(
            "v${'$'}(cat<<<TAG_COMMAND_EXECUTED)",
            "v`cat<<<TAG_COMMAND_EXECUTED`",
        )
        tags.forEachIndexed { index, tag ->
            val refCheck = ProcessBuilder("git", "check-ref-format", "refs/tags/$tag").start()
            assertEquals(0, refCheck.waitFor(), "The harmless regression input must be a valid Git ref")
            assertLiteralRejection(tag, runWorkflowVersionBoundary(tag, tempDir.resolve(index.toString())))
        }
    }

    @Test
    fun `boundary regression detects reverting env input to shell interpolation`(@TempDir tempDir: Path) {
        val tag = "v${'$'}(cat<<<TAG_COMMAND_EXECUTED)"
        val workflow = readNormalized(projectRoot.resolve(".github/workflows/release.yml"))
        val unsafeWorkflow = workflow.replace(
            "\"${'$'}GITHUB_REF_NAME\"",
            "\"${'$'}{{ github.ref_name }}\"",
        )
        assertFalse(workflow == unsafeWorkflow, "Mutation must change the actual workflow boundary")
        val result = runWorkflowVersionBoundary(tag, tempDir, unsafeWorkflow)

        assertTrue(result.output.contains("vTAG_COMMAND_EXECUTED"), result.output)
        org.junit.jupiter.api.assertThrows<AssertionError> { assertLiteralRejection(tag, result) }
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

    private fun writeCanonicalBuildFile(path: Path, version: String): Path {
        Files.writeString(path, "version = \"$version\"")
        return path
    }

    private fun differentSemanticVersion(version: String): String {
        val (major, minor, patch) = version.split('.').map(String::toInt)
        return "$major.$minor.${patch + 1}"
    }

    private fun runVerifier(tag: String, buildFile: Path? = null): CommandResult {
        val command = mutableListOf(TestShell.bashExecutable(), verifier.toString(), tag)
        buildFile?.let { command.add(it.toString()) }
        val process = ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .apply { environment().remove("GITHUB_REF_NAME") }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return CommandResult(process.waitFor(), output)
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun assertLiteralRejection(tag: String, result: WorkflowResult) {
        assertEquals(1, result.exitCode, result.output)
        assertTrue(
            result.output.contains("Release tag must use v<major>.<minor>.<patch>: $tag"),
            "The workflow must reject the original tag without evaluating substitutions: ${result.output}",
        )
        assertTrue(result.outputs.isEmpty(), "Rejected tags must not produce version outputs: $result")
    }

    // Execute the checked-in run source with Actions-style expression substitution
    // and step outputs. Passing the tag only as a script argv would miss the bug.
    // Only the pre-JDK version boundary is executed, with no inherited credentials.
    private fun runWorkflowVersionBoundary(
        tag: String,
        tempDir: Path,
        workflow: String = readNormalized(projectRoot.resolve(".github/workflows/release.yml")),
    ): WorkflowResult {
        val root = Load(LoadSettings.builder().build()).loadFromString(workflow) as Map<*, *>
        val job = (root["jobs"] as Map<*, *>)["release"] as Map<*, *>
        val steps = (job["steps"] as List<*>).map { it as Map<*, *> }
            .takeWhile { it["name"] != "Set up JDK 21" }
            .filter { it["run"] is String }
        assertTrue(steps.isNotEmpty(), "The workflow must validate the version before JDK setup")
        val context = mutableMapOf("github.ref_name" to tag, "github.ref" to "refs/tags/$tag")
        val outputs = mutableMapOf<String, String>()
        val log = StringBuilder()
        Files.createDirectories(tempDir)
        steps.forEachIndexed { index, step ->
            val outputFile = Files.createFile(tempDir.resolve("step-$index-output"))
            val builder = ProcessBuilder(
                TestShell.bashExecutable(), "--noprofile", "--norc", "-e", "-o", "pipefail", "-c",
                renderExpressions(step["run"] as String, context),
            ).directory(projectRoot.toFile()).redirectErrorStream(true)
            val environment = builder.environment()
            val executablePath = environment["PATH"].orEmpty()
            environment.clear()
            environment.putAll(mapOf(
                "PATH" to executablePath,
                "GITHUB_REF_NAME" to tag,
                "GITHUB_REF" to "refs/tags/$tag",
                "GITHUB_OUTPUT" to outputFile.toString(),
            ))
            (step["env"] as? Map<*, *>)?.forEach { (key, value) ->
                environment[key as String] = renderExpressions(value as String, context)
            }
            val process = builder.start()
            log.append(process.inputStream.bufferedReader().use { it.readText() })
            val exitCode = process.waitFor()
            Files.readAllLines(outputFile).forEach { line ->
                val key = line.substringBefore('=')
                val value = line.substringAfter('=')
                outputs[key] = value
                context["steps.${step["id"]}.outputs.$key"] = value
            }
            if (exitCode != 0) return WorkflowResult(exitCode, log.toString(), outputs)
        }
        return WorkflowResult(0, log.toString(), outputs)
    }

    private fun renderExpressions(source: String, context: Map<String, String>): String =
        Regex("""\$\{\{\s*(.*?)\s*}}""").replace(source) { match ->
            context.getValue(match.groupValues[1])
        }

    private fun readNormalized(path: Path): String = Files.readString(path).replace("\r\n", "\n")

    private data class WorkflowResult(val exitCode: Int, val output: String, val outputs: Map<String, String>)

    private companion object {
        val SEMVER = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    }
}
