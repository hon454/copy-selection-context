package com.github.hon454.copyselectioncontext

import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
    fun `release signing checksum and attestation gate exact artifact publication`() {
        val workflow = readWorkflow("release.yml")
        val buildScript = Files.readString(Path.of("build.gradle.kts"))

        assertInOrder(
            workflow,
            "name: Build plugin",
            "name: Resolve release mode",
            "name: Sign and verify canonical release ZIP",
            "name: Select canonical release ZIP",
            "name: Generate and verify release checksum",
            "name: Generate release ZIP attestation",
            "name: Verify release ZIP attestation",
            "name: Upload validation reports",
            "name: Create GitHub Release",
            "name: Publish to JetBrains Marketplace",
        )
        assertTrue(
            workflow.contains("bash scripts/resolve-release-mode.sh \"${'$'}GITHUB_OUTPUT\"") &&
                workflow.contains("if: steps.release-mode.outputs.signed == 'true'") &&
                workflow.contains("env -u CERTIFICATE_CHAIN CERTIFICATE_CHAIN_FILE=\"${'$'}signing_certificate_file\"") &&
                workflow.contains("./gradlew signPlugin --stacktrace --console=plain") &&
                workflow.contains("./gradlew verifyPluginSignature --stacktrace --console=plain") &&
                workflow.contains("trap 'rm -f \"${'$'}signing_certificate_file\"' EXIT"),
            "release.yml must resolve, produce, and verify the signed canonical path when signing is configured",
        )
        assertTrue(
            workflow.contains("bash scripts/select-release-artifact.sh") &&
                workflow.contains("\"${'$'}{{ steps.release-mode.outputs.signed }}\"") &&
                workflow.contains("\"${'$'}GITHUB_OUTPUT\""),
            "release.yml must select the canonical ZIP through the fail-closed selector",
        )
        assertTrue(
            workflow.contains("subject-path: ${'$'}{{ steps.release-artifact.outputs.path }}"),
            "the attestation must identify the exact ZIP selected for publication",
        )
        assertTrue(
            workflow.contains("--bundle \"${'$'}{{ steps.attestation.outputs.bundle-path }}\"") &&
                workflow.contains("--source-digest \"${'$'}{{ github.sha }}\"") &&
                workflow.contains("--source-ref \"${'$'}{{ github.ref }}\""),
            "release.yml must verify the generated attestation against the triggering commit and tag",
        )
        assertTrue(
            workflow.contains("${'$'}{{ steps.release-artifact.outputs.path }}\n            SHA256SUMS"),
            "the release must upload the attested ZIP and its checksum file",
        )
        assertFalse(
            workflow.contains("files: build/distributions/*.zip"),
            "release publication must not re-expand a ZIP glob after attestation",
        )
        assertTrue(
            workflow.contains("if: steps.release-mode.outputs.publish == 'true'") &&
                workflow.contains("-PcanonicalPluginArchive=\"${'$'}{{ steps.release-artifact.outputs.path }}\""),
            "Marketplace publication must receive the exact canonical ZIP selected for release",
        )
        assertTrue(
            buildScript.contains("named<PublishPluginTask>(\"publishPlugin\")") &&
                buildScript.contains("archiveFile.set(layout.projectDirectory.file(canonicalArchive))") &&
                buildScript.contains("setDependsOn(emptyList<Any>())"),
            "the explicit canonical archive input must prevent publishPlugin from rebuilding or re-signing",
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

        val externalActionCount =
            workflowPaths.sumOf { path ->
                assertWorkflowActionReferencesAreImmutable(
                    workflow = Files.readString(path),
                    source = path.toString(),
                )
            }

        assertTrue(externalActionCount > 0, "No external actions found to validate")
    }

    @Test
    fun `mutable action references fail closed across YAML key styles`() {
        val mutableWorkflows =
            listOf(
                workflowWithStep("- uses: actions/checkout@v7"),
                workflowWithStep("- 'uses': actions/checkout@v7 # v7.0.1"),
                workflowWithStep("- uses: actions/checkout@v7 # v7.0.1 upstream release"),
                workflowWithStep("- { name: Checkout code, uses: actions/checkout@v7 } # v7.0.1"),
                """
                name: Reusable workflow pin check
                on: push
                jobs:
                  reuse:
                    'uses': owner/repository/.github/workflows/reusable.yml@main # v1.2.3
                """.trimIndent(),
            )

        mutableWorkflows.forEachIndexed { index, workflow ->
            assertFailsWith<AssertionError>("Mutable workflow variant ${index + 1} must fail") {
                assertWorkflowActionReferencesAreImmutable(workflow, "inline-workflow-${index + 1}")
            }
        }
    }

    @Test
    fun `pinned actions require readable version comments`() {
        val pinnedReference = "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
        val invalidComments =
            listOf(
                workflowWithStep("- uses: $pinnedReference"),
                workflowWithStep("- uses: $pinnedReference # upstream release"),
                workflowWithStep("- uses: $pinnedReference # v7.0.1upstream"),
            )

        invalidComments.forEachIndexed { index, workflow ->
            assertFailsWith<AssertionError>("Missing or invalid version comment ${index + 1} must fail") {
                assertWorkflowActionReferencesAreImmutable(workflow, "inline-workflow-${index + 1}")
            }
        }
    }

    @Test
    fun `version comments may include descriptive text after the version`() {
        val workflow =
            workflowWithStep(
                "- { 'uses': actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 } " +
                    "# v7.0.1 upstream release",
            )

        assertEquals(1, assertWorkflowActionReferencesAreImmutable(workflow, "inline-workflow"))
    }

    @Test
    fun `local and Docker uses values are not GitHub repository references`() {
        val workflow =
            """
            name: Non-repository references
            on: push
            jobs:
              test:
                runs-on: ubuntu-latest
                steps:
                  - { uses: ./actions/local }
                  - 'uses': docker://alpine:3.22
            """.trimIndent()

        assertEquals(0, assertWorkflowActionReferencesAreImmutable(workflow, "inline-workflow"))
    }

    @Test
    fun `uses keys in workflow data are not action references`() {
        val workflow =
            """
            name: Uses data keys
            on: push
            env:
              uses: actions/checkout@v7
            jobs:
              test:
                runs-on: ubuntu-latest
                env: { uses: actions/checkout@v7 }
                steps:
                  - name: Environment data
                    run: echo ok
                    env:
                      'uses': actions/checkout@v7
                  - uses: ./actions/local
                    with: { uses: actions/checkout@v7 }
            """.trimIndent()

        assertEquals(0, assertWorkflowActionReferencesAreImmutable(workflow, "inline-workflow"))
    }

    @Test
    fun `workflows declare only required token permissions`() {
        assertEquals(
            listOf("contents: read"),
            workflowPermissions(readWorkflow("build.yml")),
            "build.yml must keep the default GITHUB_TOKEN read-only",
        )
        assertEquals(
            listOf("contents: write", "id-token: write", "attestations: write"),
            workflowPermissions(readWorkflow("release.yml")),
            "release.yml needs release publication and artifact attestation permissions only",
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
        val buildScript = Files.readString(Path.of("build.gradle.kts"))

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
            workflow.contains("run: ./gradlew allTests --continue --stacktrace --console=plain"),
            "$workflowName must run both test tasks even when one fails",
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
                listOf(
                    "build/reports/tests/test/",
                    "build/test-results/test/",
                    "build/reports/tests/platformTest/",
                    "build/test-results/platformTest/",
                ).all(workflow::contains),
            "$workflowName must upload plugin verification and every test task report",
        )
        assertTrue(
            buildScript.contains("intellijPlatformTesting.testIde.register(\"platformTest\")") &&
                buildScript.contains("CopyHistoryPersistenceTest") &&
                buildScript.contains("CopySelectionActionFixtureTest") &&
                buildScript.contains("forkEvery = 0") &&
                buildScript.contains("forkEvery = 1") &&
                buildScript.contains("register(\"allTests\")"),
            "Gradle must reuse pure-test workers, isolate platform-state classes, and aggregate both tasks",
        )
        assertPlatformStateTestsAreExplicitlyPartitioned(buildScript)
        assertTrue(
            workflow.contains("uses: gradle/actions/setup-gradle@"),
            "$workflowName must cache Gradle dependencies used by plugin verification",
        )
    }

    private fun assertPlatformStateTestsAreExplicitlyPartitioned(buildScript: String) {
        val configuredBlock =
            Regex("""(?s)val platformStateTestClasses = listOf\((.*?)\n\)""")
                .find(buildScript)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val configuredClasses =
            Regex("\"(com\\.github\\.hon454\\.copyselectioncontext\\.[A-Za-z0-9_]+)\"")
                .findAll(configuredBlock)
                .map { it.groupValues[1] }
                .toSet()

        assertTrue(configuredClasses.isNotEmpty(), "Gradle must declare explicit platform-state test classes")
        assertTrue(
            buildScript.contains("platformStateTestClasses.forEach(::includeTestsMatching)") &&
                buildScript.contains("platformStateTestClasses.forEach(::excludeTestsMatching)"),
            "test and platformTest must use the same partition list",
        )
        assertTrue(
            Regex("""(?s)named\("check"\)\s*\{.*?dependsOn\(allTests\).*?}""").containsMatchIn(buildScript) &&
                Regex("""(?s)named\("buildPlugin"\)\s*\{.*?dependsOn\(allTests\).*?}""")
                    .containsMatchIn(buildScript),
            "check and buildPlugin must include the complete test aggregate",
        )

        val testSourceRoot = Path.of("src", "test", "kotlin")
        val detectedClasses =
            Files.walk(testSourceRoot).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith("Test.kt") }
                    .map { path ->
                        val source = Files.readString(path)
                        val lines = source.lines()
                        val hasTestApplication = lines.any { it.trim() == "@TestApplication" }
                        val hasPlatformFixture =
                            lines.any { line ->
                                Regex(
                                    """^class\s+[A-Za-z0-9_]+\s*:\s*(?:BasePlatformTestCase|LightPlatformTestCase)\b""",
                                ).containsMatchIn(line.trim())
                            }
                        if (!hasTestApplication && !hasPlatformFixture) {
                            return@map null
                        }

                        val packageName =
                            lines
                                .firstOrNull { it.startsWith("package ") }
                                ?.removePrefix("package ")
                                ?.trim()
                                .orEmpty()
                        val className =
                            lines
                                .firstNotNullOfOrNull { line ->
                                    Regex("""^class\s+([A-Za-z0-9_]+)\b""")
                                        .find(line.trim())
                                        ?.groupValues
                                        ?.get(1)
                                }.orEmpty()
                        "$packageName.$className"
                    }.toList()
                    .filterNotNull()
                    .toSet()
            }

        assertEquals(
            detectedClasses,
            configuredClasses,
            "Every IntelliJ application or fixture test must be isolated in platformTest, with no stale entries",
        )
    }

    private fun readWorkflow(workflowName: String): String {
        val path = Path.of(".github", "workflows", workflowName)
        assertTrue(Files.isRegularFile(path), "Workflow not found: $path")
        return Files.readString(path)
    }

    private fun assertWorkflowActionReferencesAreImmutable(
        workflow: String,
        source: String,
    ): Int {
        val immutableReferencePattern = Regex("""^[^/@\s]+/[^@\s]+@[0-9a-f]{40}${'$'}""")
        val versionCommentPattern = Regex("""^v\d+(?:\.\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?${'$'}""")
        val settings =
            LoadSettings
                .builder()
                .setLabel(source)
                .setAllowDuplicateKeys(false)
                .setParseComments(true)
                .setUseMarks(true)
                .build()
        val usesEntries =
            Compose(settings)
                .composeAllFromString(workflow)
                .flatMap(::collectActionReferenceEntries)
        var externalActionCount = 0

        usesEntries.forEach { entry ->
            val line = entry.key.startMark.map { it.line + 1 }.orElse(1)
            val location = "$source:$line"
            val scalarValue = entry.value as? ScalarNode
            assertTrue(scalarValue != null, "$location must use a scalar action reference")
            val reference = scalarValue.value.trim()
            if (reference.startsWith("./") || reference.startsWith("docker://")) {
                return@forEach
            }

            externalActionCount += 1
            assertTrue(
                immutableReferencePattern.matches(reference),
                "$location must pin external action '$reference' to a full 40-character commit SHA",
            )
            val endIndex = scalarValue.endMark.map { it.index }.orElse(-1)
            assertTrue(endIndex in 0..workflow.length, "$location must retain the action source location")
            val sourceSuffix = workflow.substring(endIndex).lineSequence().firstOrNull().orEmpty()
            val versionComment = sourceSuffix.substringAfter('#', missingDelimiterValue = "").trim()
            val versionToken = versionComment.split(Regex("""\s+"""), limit = 2).firstOrNull().orEmpty()
            assertTrue(
                versionCommentPattern.matches(versionToken),
                "$location must include a readable version comment such as '# v1.2.3'",
            )
        }

        return externalActionCount
    }

    private fun collectActionReferenceEntries(document: Node): List<UsesEntry> {
        val root = document as? MappingNode ?: return emptyList()
        val jobs = root.valueForKey("jobs") as? MappingNode ?: return emptyList()

        return jobs.value.flatMap { jobTuple ->
            val job = jobTuple.valueNode as? MappingNode ?: return@flatMap emptyList()
            val reusableWorkflow = job.entriesForKey("uses")
            val stepActions =
                (job.valueForKey("steps") as? SequenceNode)
                    ?.value
                    .orEmpty()
                    .filterIsInstance<MappingNode>()
                    .flatMap { step -> step.entriesForKey("uses") }
            reusableWorkflow + stepActions
        }
    }

    private fun MappingNode.entriesForKey(key: String): List<UsesEntry> =
        value.mapNotNull { tuple ->
            val scalarKey = tuple.keyNode as? ScalarNode
            scalarKey
                ?.takeIf { it.value == key }
                ?.let { UsesEntry(it, tuple.valueNode) }
        }

    private fun MappingNode.valueForKey(key: String): Node? = entriesForKey(key).singleOrNull()?.value

    private fun workflowWithStep(step: String): String =
        """
        name: Action pin check
        on: push
        jobs:
          test:
            runs-on: ubuntu-latest
            steps:
              $step
        """.trimIndent()

    private data class UsesEntry(
        val key: ScalarNode,
        val value: Node,
    )

    private fun workflowPermissions(workflow: String): List<String> {
        val lines = workflow.lines()
        val permissionsIndex = lines.indexOfFirst { it == "permissions:" }
        assertTrue(permissionsIndex >= 0, "Workflow must declare permissions explicitly")

        val permissionLines =
            lines
                .drop(permissionsIndex + 1)
                .takeWhile { it.startsWith("  ") && it.isNotBlank() }
                .map { it.trim() }
        return permissionLines
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
