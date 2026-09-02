package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CiWorkflowTest {
    @Test
    fun `build validates before packaging and artifact upload`() {
        assertValidationPipeline(
            workflowName = "build.yml",
            publicationStep = "Upload build artifact",
        )
    }

    @Test
    fun `release validates before packaging and publication`() {
        assertValidationPipeline(
            workflowName = "release.yml",
            publicationStep = "Create GitHub Release",
        )
    }

    @Test
    fun `release generates notes only after version and Gradle setup`() {
        val workflow = readWorkflow("release.yml")

        assertInOrder(
            workflow,
            "name: Verify version matches build.gradle.kts",
            "name: Set up JDK 21",
            "name: Setup Gradle",
            "name: Ensure gradlew is executable",
            "name: Generate release notes from changelog",
            "name: Run test suite",
        )
        val generationCommand =
            "bash scripts/generate-release-notes.sh \\\n" +
                "            \"${'$'}{{ steps.version.outputs.version }}\" \\\n" +
                "            \"${'$'}{{ steps.version.outputs.tag }}\""
        assertTrue(
            workflow.contains(generationCommand),
            "release.yml must delegate deterministic note generation to the tested script",
        )
        assertFalse(
            workflow.contains("--no-summary > release-notes.md"),
            "release.yml must not redirect a cold Gradle wrapper invocation into the release body",
        )
    }

    @Test
    fun `external actions use immutable full SHA pins with version comments`() {
        val workflowDirectory = Path.of(".github", "workflows")
        val workflowPaths =
            Files.list(workflowDirectory).use { paths ->
                paths
                    .filter { path ->
                        val fileName = path.fileName.toString()
                        fileName.endsWith(".yml") || fileName.endsWith(".yaml")
                    }.sorted()
                    .toList()
            }
        assertTrue(workflowPaths.isNotEmpty(), "No GitHub Actions workflows found")

        val externalActionPattern = Regex("""^\s*(?:-\s*)?uses:\s*([^\s#]+)(?:\s+#\s*(\S+))?\s*${'$'}""")
        val immutableReferencePattern = Regex("""^[^/@\s]+/[^@\s]+@[0-9a-f]{40}${'$'}""")
        val versionCommentPattern = Regex("""^v\d+(?:\.\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?${'$'}""")
        var externalActionCount = 0

        workflowPaths.forEach { path ->
            Files.readAllLines(path).forEachIndexed { index, line ->
                val match = externalActionPattern.matchEntire(line) ?: return@forEachIndexed
                val reference = match.groupValues[1]
                if (reference.startsWith("./") || reference.startsWith("docker://")) {
                    return@forEachIndexed
                }

                externalActionCount += 1
                val location = "${path}:${index + 1}"
                assertTrue(
                    immutableReferencePattern.matches(reference),
                    "$location must pin external action '$reference' to a full 40-character commit SHA",
                )
                val versionComment = match.groupValues[2]
                assertTrue(
                    versionCommentPattern.matches(versionComment),
                    "$location must include a readable version comment such as '# v1.2.3'",
                )
            }
        }

        assertTrue(externalActionCount > 0, "No external actions found to validate")
    }

    @Test
    fun `workflows declare only required token permissions`() {
        assertEquals(
            "contents: read",
            workflowPermissions(readWorkflow("build.yml")),
            "build.yml must keep the default GITHUB_TOKEN read-only",
        )
        assertEquals(
            "contents: write",
            workflowPermissions(readWorkflow("release.yml")),
            "release.yml needs only contents write access to create the GitHub release",
        )
    }

    @Test
    fun `Dependabot safely updates action pins through pull request validation`() {
        val dependabotPath = Path.of(".github", "dependabot.yml")
        assertTrue(Files.isRegularFile(dependabotPath), "Dependabot configuration is required")
        val dependabot = Files.readString(dependabotPath)
        val buildWorkflow = readWorkflow("build.yml")

        assertTrue(
            dependabot.contains("package-ecosystem: \"github-actions\"") &&
                dependabot.contains("directory: \"/\"") &&
                dependabot.contains("interval: \"weekly\""),
            "Dependabot must check GitHub Actions pins at the repository root on a weekly schedule",
        )
        assertFalse(
            dependabot.contains("automerge", ignoreCase = true),
            "GitHub Actions updates must not be configured for automatic merging",
        )
        assertTrue(
            Regex("""(?ms)^on:\s.*?^\s{2}pull_request:\s*\n\s{4}branches:\s*\[\s*main\s*]""")
                .containsMatchIn(buildWorkflow),
            "Dependabot pull requests targeting main must run the complete build workflow",
        )
    }

    private fun assertValidationPipeline(
        workflowName: String,
        publicationStep: String,
    ) {
        val workflow = readWorkflow(workflowName)

        assertInOrder(
            workflow,
            "name: Run test suite",
            "name: Verify plugin project and structure",
            "name: Verify plugin compatibility",
            "name: Build plugin",
            "name: Upload validation reports",
            "name: $publicationStep",
        )
        assertTrue(
            workflow.contains("run: ./gradlew test --stacktrace --console=plain"),
            "$workflowName must run the complete test task with diagnostic output",
        )
        assertTrue(
            workflow.contains("verifyPluginProjectConfiguration") &&
                workflow.contains("verifyPluginStructure") &&
                workflow.contains("run: ./gradlew verifyPlugin --stacktrace --console=plain"),
            "$workflowName must run project, structure, and compatibility verification",
        )
        assertTrue(
            workflow.contains("name: Upload validation reports\n        if: always()"),
            "$workflowName must preserve validation reports when a gate fails",
        )
        assertTrue(
            workflow.contains("build/reports/pluginVerifier/") &&
                workflow.contains("build/reports/tests/") &&
                workflow.contains("build/test-results/"),
            "$workflowName must upload plugin verification and test diagnostics",
        )
        assertTrue(
            workflow.contains("uses: gradle/actions/setup-gradle@"),
            "$workflowName must cache Gradle dependencies used by plugin verification",
        )
    }

    private fun readWorkflow(workflowName: String): String {
        val path = Path.of(".github", "workflows", workflowName)
        assertTrue(Files.isRegularFile(path), "Workflow not found: $path")
        return Files.readString(path)
    }

    private fun workflowPermissions(workflow: String): String {
        val lines = workflow.lines()
        val permissionsIndex = lines.indexOfFirst { it == "permissions:" }
        assertTrue(permissionsIndex >= 0, "Workflow must declare permissions explicitly")

        val permissionLines =
            lines
                .drop(permissionsIndex + 1)
                .takeWhile { it.startsWith("  ") && it.isNotBlank() }
                .map { it.trim() }
        assertEquals(1, permissionLines.size, "Workflow must grant exactly one explicit token permission")
        return permissionLines.single()
    }

    private fun assertInOrder(
        workflow: String,
        vararg markers: String,
    ) {
        var previousIndex = -1
        markers.forEach { marker ->
            val markerIndex = workflow.indexOf(marker)
            assertTrue(markerIndex >= 0, "Missing workflow step: $marker")
            assertTrue(markerIndex > previousIndex, "Workflow step is out of order: $marker")
            previousIndex = markerIndex
        }
    }
}
