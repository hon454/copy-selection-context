package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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
            workflow.contains("uses: gradle/actions/setup-gradle@v6"),
            "$workflowName must cache Gradle dependencies used by plugin verification",
        )
        assertTrue(
            workflow.contains(
                "uses: actions/setup-java@dd06d9cba3e5552c54d9f8ea23572deb30010f7c # v6.0.0",
            ),
            "$workflowName must pin the verified setup-java v6.0.0 commit",
        )
    }

    private fun readWorkflow(workflowName: String): String {
        val path = Path.of(".github", "workflows", workflowName)
        assertTrue(Files.isRegularFile(path), "Workflow not found: $path")
        return Files.readString(path)
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
