package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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
