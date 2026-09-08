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
    fun `release credentials are scoped to only the steps that consume them`() {
        val workflow = readWorkflowMapping("release.yml")
        val job = workflow.mappingForKey("jobs").mappingForKey("release")
        assertTrue(workflow.valueForKey("env") == null, "Release must not inherit workflow credentials")
        assertTrue(job.valueForKey("env") == null, "Release must not inherit job credentials")
        val expected = mapOf(
            "Resolve release mode" to setOf("PUBLISH_TOKEN", "CERTIFICATE_CHAIN", "PRIVATE_KEY"),
            "Sign and verify canonical release ZIP" to
                setOf("CERTIFICATE_CHAIN", "PRIVATE_KEY", "PRIVATE_KEY_PASSWORD"),
            "Publish to JetBrains Marketplace" to setOf("PUBLISH_TOKEN"),
        )
        val steps = workflowSteps("release.yml", "release")
        expected.keys.forEach { steps.stepNamed(it) }
        steps.forEach { step ->
            val name = step.scalarForKey("name")
            val env = step.valueForKey("env") as? MappingNode
            val secrets = env?.value.orEmpty().filter { entry ->
                (entry.valueNode as ScalarNode).value.contains("secrets.")
            }.associate { entry ->
                (entry.keyNode as ScalarNode).value to (entry.valueNode as ScalarNode).value
            }
            assertEquals(
                expected[name].orEmpty().associateWith { "${'$'}{{ secrets.$it }}" },
                secrets,
                "$name must receive only its required credentials",
            )
            step.value.filter { (it.keyNode as ScalarNode).value != "env" }.forEach {
                assertFalse(it.valueNode.toString().contains("secrets."), "$name must inject secrets only via env")
            }
        }
    }

    @Test
    fun `release shell sources never interpolate workflow expressions`() {
        workflowSteps("release.yml", "release").forEach { step ->
            val command = step.valueForKey("run") as? ScalarNode
            assertFalse(
                command?.value.orEmpty().contains("${'$'}{{"),
                "${step.scalarForKey("name")} must pass workflow values through env instead of shell source",
            )
        }
    }

    @Test
    fun `release env inputs preserve validated outputs and canonical artifact bindings`() {
        val steps = workflowSteps("release.yml", "release")
        val expected = mapOf(
            "Generate release notes from changelog" to mapOf(
                "RELEASE_VERSION" to "${'$'}{{ steps.version.outputs.version }}",
                "RELEASE_TAG" to "${'$'}{{ steps.version.outputs.tag }}",
            ),
            "Select canonical release ZIP" to mapOf("RELEASE_SIGNED" to "${'$'}{{ steps.release-mode.outputs.signed }}"),
            "Generate and verify release checksum" to mapOf("RELEASE_ARCHIVE" to "${'$'}{{ steps.release-artifact.outputs.path }}"),
            "Verify release ZIP attestation" to mapOf(
                "RELEASE_ARCHIVE" to "${'$'}{{ steps.release-artifact.outputs.path }}",
                "ATTESTATION_BUNDLE" to "${'$'}{{ steps.attestation.outputs.bundle-path }}",
                "GH_TOKEN" to "${'$'}{{ github.token }}",
            ),
            "Publish to JetBrains Marketplace" to mapOf("RELEASE_ARCHIVE" to "${'$'}{{ steps.release-artifact.outputs.path }}"),
        )
        expected.forEach { (name, bindings) ->
            val env = steps.stepNamed(name).mappingForKey("env")
            bindings.forEach { (key, value) -> assertEquals(value, env.scalarForKey(key), "$name: $key") }
        }
        val version = steps.stepNamed("Verify version matches build.gradle.kts")
        assertEquals("version", version.scalarForKey("id"))
        assertTrue(version.valueForKey("if") == null, "Version validation must always gate the release")
        assertTrue(version.valueForKey("continue-on-error") == null, "Version errors must fail closed")
    }

    @Test
    fun `build validates before packaging and artifact upload`() {
        assertValidationPipeline(
            workflowName = "build.yml",
            publicationStep = "Upload build artifact",
        )
    }

    @Test
    fun `release publication requires the complete reusable validation gate`() {
        val workflow = readWorkflowMapping("release.yml")
        val validationWorkflow = readWorkflowMapping("release-validation.yml")

        assertReleasePublicationGate(workflow)
        listOf("failure", "cancelled", "skipped").forEach { result ->
            assertFalse(
                releaseCanPublishForValidationResult(workflow, result),
                "A $result validation must block every publication step",
            )
        }
        assertTrue(releaseCanPublishForValidationResult(workflow, "success"))

        val requiredResults =
            mapOf(
                "ubuntu-latest" to "success",
                "macos-latest" to "success",
                "windows-latest" to "success",
                "linux-compatibility" to "success",
            )
        assertTrue(releaseCanPublishForRequiredResults(workflow, validationWorkflow, requiredResults))
        requiredResults.keys.forEach { requiredJob ->
            listOf("failure", "cancelled", "skipped").forEach { result ->
                assertFalse(
                    releaseCanPublishForRequiredResults(
                        workflow,
                        validationWorkflow,
                        requiredResults + (requiredJob to result),
                    ),
                    "$requiredJob=$result must block publication",
                )
            }
        }

        listOf(
            "release-gate-missing-needs.yml",
            "release-gate-permissive-job-condition.yml",
            "release-gate-permissive-publication-step.yml",
            "release-gate-failure-publication-step.yml",
        ).forEach { fixture ->
            assertFailsWith<AssertionError>("Negative release fixture must fail closed: $fixture") {
                assertReleasePublicationGate(
                    readYamlMapping(Path.of("src", "test", "fixtures", "workflows", fixture)),
                )
            }
        }
    }

    @Test
    fun `release validation runs all OS tests and Linux IDE compatibility without publication`() {
        val workflow = readWorkflowMapping("release-validation.yml")
        val source = readWorkflow("release-validation.yml")
        val triggers = workflow.mappingForKey("on")
        assertEquals(
            setOf("workflow_call", "workflow_dispatch", "push"),
            triggers.keyNames(),
            "The validation workflow must support release reuse, post-merge dispatch, and pre-merge branch pushes",
        )
        assertEquals(
            listOf("codex/verify-*"),
            triggers.mappingForKey("push").sequenceForKey("branches").scalarValues(),
        )
        assertEquals(listOf("contents: read"), workflowPermissions("release-validation.yml"))
        assertFalse(source.contains("secrets."), "Validation must not receive repository secrets")
        assertFalse(
            listOf("action-gh-release", "publishPlugin", "signPlugin", "attest").any(source::contains),
            "The validation-only workflow must not contain release or Marketplace publication operations",
        )

        val jobs = workflow.mappingForKey("jobs")
        val osTests = jobs.mappingForKey("os-tests")
        assertOsMatrixTestCoverage(workflow)
        assertEquals("${'$'}{{ matrix.os }}", osTests.scalarForKey("runs-on"))
        assertTrue(osTests.valueForKey("if") == null, "Every OS matrix row must start unconditionally")
        assertTrue(osTests.valueForKey("continue-on-error") == null, "OS failures must fail the matrix")
        val strategy = osTests.mappingForKey("strategy")
        assertEquals("false", strategy.scalarForKey("fail-fast"))
        assertEquals(
            listOf("ubuntu-latest", "macos-latest", "windows-latest"),
            strategy.mappingForKey("matrix").sequenceForKey("os").scalarValues(),
        )

        val osSteps = osTests.sequenceForKey("steps").mappingValues()
        val windowsBashSelector = osSteps.stepNamed("Select Git Bash for Windows child processes")
        assertEquals("runner.os == 'Windows'", windowsBashSelector.scalarForKey("if"))
        assertEquals("pwsh", windowsBashSelector.scalarForKey("shell"))
        assertTrue(
            windowsBashSelector.scalarForKey("run").contains("BASH_EXE=") &&
                windowsBashSelector.scalarForKey("run").contains("${'$'}env:GITHUB_ENV") &&
                windowsBashSelector.scalarForKey("run").contains("bin\\bash.exe"),
            "Windows must expose the reviewed Git Bash executable to Gradle child processes",
        )
        val windowsBashCheck = osSteps.stepNamed("Check Windows Bash prerequisites")
        assertEquals("runner.os == 'Windows'", windowsBashCheck.scalarForKey("if"))
        assertEquals("pwsh", windowsBashCheck.scalarForKey("shell"))
        assertTrue(
            windowsBashCheck.scalarForKey("run").contains("& ${'$'}env:BASH_EXE"),
            "The prerequisite check must execute the same Bash path used by tests",
        )
        val shellDrivenTests =
            listOf(
                "ReleaseNotesGenerationTest.kt",
                "ReleaseArtifactSelectionTest.kt",
                "ReleaseChecksumGenerationTest.kt",
                "ReleaseVersionParityTest.kt",
            )
        shellDrivenTests.forEach { fileName ->
            val source = Files.readString(Path.of("src", "test", "kotlin", "com", "github", "hon454", "copyselectioncontext", fileName))
            assertTrue(
                source.contains("TestShell.bashExecutable()"),
                "$fileName must use the workflow-selected Bash executable on Windows",
            )
        }
        val testSteps =
            mapOf(
                "Run Linux test suite and generate Kotlin coverage" to
                    Pair("matrix.os == 'ubuntu-latest'", "./gradlew"),
                "Run macOS test suite" to Pair("matrix.os == 'macos-latest'", "./gradlew"),
                "Run Windows test suite" to Pair("matrix.os == 'windows-latest'", ".\\gradlew.bat"),
            )
        testSteps.forEach { (name, expected) ->
            val step = osSteps.stepNamed(name)
            assertEquals(expected.first, step.scalarForKey("if"), "$name must run for exactly its matrix row")
            val command = step.scalarForKey("run")
            assertTrue(command.startsWith(expected.second), "$name must use the OS-specific Gradle wrapper")
            assertTrue(command.contains("allTests") && command.contains("--continue"), "$name must run both test partitions")
        }
        assertTrue(
            osSteps.stepNamed("Run Linux test suite and generate Kotlin coverage").scalarForKey("run")
                .contains("koverXmlReport") &&
                osSteps.stepNamed("Run Linux test suite and generate Kotlin coverage").scalarForKey("run")
                    .contains("koverHtmlReport"),
            "Coverage is generated once on Linux instead of on every OS",
        )
        val osReports = osSteps.stepNamed("Upload OS validation reports")
        assertEquals("always()", osReports.scalarForKey("if"))
        assertEquals("release-validation-${'$'}{{ matrix.os }}", osReports.mappingForKey("with").scalarForKey("name"))
        assertRequiredTestReportPaths(osReports.mappingForKey("with").scalarForKey("path"))

        val compatibility = jobs.mappingForKey("linux-compatibility")
        assertLinuxCompatibilityJob(workflow)
        assertEquals("ubuntu-latest", compatibility.scalarForKey("runs-on"))
        assertTrue(compatibility.valueForKey("strategy") == null, "Plugin Verifier must run once, not as an OS matrix")
        val compatibilitySteps = compatibility.sequenceForKey("steps").mappingValues()
        assertEquals(
            "./gradlew verifyPlugin --stacktrace --console=plain",
            compatibilitySteps.stepNamed("Verify plugin compatibility").scalarForKey("run"),
        )
        val compatibilityReports = compatibilitySteps.stepNamed("Upload IDE compatibility reports")
        assertEquals("always()", compatibilityReports.scalarForKey("if"))
        assertEquals(
            "release-validation-linux-ide-compatibility",
            compatibilityReports.mappingForKey("with").scalarForKey("name"),
        )

        val gate = jobs.mappingForKey("validation-gate")
        assertReusableValidationGate(workflow)
        assertEquals("always()", gate.scalarForKey("if"))
        assertEquals(setOf("os-tests", "linux-compatibility"), gate.jobNeeds())
        val gateStep = gate.sequenceForKey("steps").mappingValues().stepNamed("Require successful OS and IDE validation")
        val gateEnv = gateStep.mappingForKey("env")
        assertEquals("${'$'}{{ needs.os-tests.result }}", gateEnv.scalarForKey("OS_TESTS_RESULT"))
        assertEquals(
            "${'$'}{{ needs.linux-compatibility.result }}",
            gateEnv.scalarForKey("IDE_COMPATIBILITY_RESULT"),
        )
        assertTrue(
            gateStep.scalarForKey("run").contains("!= \"success\""),
            "The reusable workflow must fail when either required job fails, is cancelled, or is skipped",
        )

        val buildScript = Files.readString(Path.of("build.gradle.kts"))
        val expectedTargets =
            listOf(
                listOf("minimum-intellij-idea-community", "IntellijIdeaCommunity", "2024.3", "243.21565.193"),
                listOf("latest-intellij-idea", "IntellijIdea", "2026.2.2", "262.10315.125"),
                listOf("latest-rider", "Rider", "2026.2.1", "262.9437.287"),
            )
        expectedTargets.flatten().forEach { marker ->
            assertTrue(buildScript.contains(marker), "Missing explicit verification target marker: $marker")
        }
        assertTrue(buildScript.contains("create(target.type, target.ideVersion)"))
        assertFalse(buildScript.contains("recommended()"), "Verifier targets must not drift with an implicit recommendation")

        assertFailsWith<AssertionError>("A matrix row without a runnable Windows test must fail closed") {
            assertOsMatrixTestCoverage(
                readYamlMapping(
                    Path.of("src", "test", "fixtures", "workflows", "release-validation-skipped-windows.yml"),
                ),
            )
        }
        assertFailsWith<AssertionError>("A matrix exclusion must not remove a required OS row") {
            assertOsMatrixTestCoverage(
                readYamlMapping(
                    Path.of("src", "test", "fixtures", "workflows", "release-validation-excluded-windows.yml"),
                ),
            )
        }
        assertFailsWith<AssertionError>("A validation gate missing an IDE prerequisite must fail closed") {
            assertReusableValidationGate(
                readYamlMapping(
                    Path.of("src", "test", "fixtures", "workflows", "release-validation-missing-gate-need.yml"),
                ),
            )
        }
        listOf(
            "release-validation-gate-and.yml",
            "release-validation-gate-exit-zero.yml",
        ).forEach { fixture ->
            assertFailsWith<AssertionError>("A permissive aggregate gate must fail closed: $fixture") {
                assertReusableValidationGate(
                    readYamlMapping(Path.of("src", "test", "fixtures", "workflows", fixture)),
                )
            }
        }
        assertFailsWith<AssertionError>("Skipped or optional Plugin Verifier validation must fail closed") {
            assertLinuxCompatibilityJob(
                readYamlMapping(
                    Path.of("src", "test", "fixtures", "workflows", "release-validation-optional-verifier.yml"),
                ),
            )
        }
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
            "name: Upload release build diagnostics",
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
                workflow.contains("\"${'$'}RELEASE_SIGNED\"") &&
                workflow.contains("\"${'$'}GITHUB_OUTPUT\""),
            "release.yml must select the canonical ZIP through the fail-closed selector",
        )
        assertTrue(
            workflow.contains("subject-path: ${'$'}{{ steps.release-artifact.outputs.path }}"),
            "the attestation must identify the exact ZIP selected for publication",
        )
        assertTrue(
            workflow.contains("--bundle \"${'$'}ATTESTATION_BUNDLE\"") &&
                workflow.contains("--source-digest \"${'$'}GITHUB_SHA\"") &&
                workflow.contains("--source-ref \"${'$'}GITHUB_REF\""),
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
                workflow.contains("-PcanonicalPluginArchive=\"${'$'}RELEASE_ARCHIVE\""),
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
            "name: Build plugin",
        )
        val generationCommand =
            "bash scripts/generate-release-notes.sh \\\n" +
                "            \"${'$'}RELEASE_VERSION\" \\\n" +
                "            \"${'$'}RELEASE_TAG\""
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
            workflowPermissions("build.yml"),
            "build.yml must keep the default GITHUB_TOKEN read-only",
        )
        assertEquals(
            listOf("contents: read"),
            workflowPermissions("release.yml"),
            "release.yml must keep its workflow-level token read-only",
        )
        assertEquals(
            listOf("contents: write", "id-token: write", "attestations: write"),
            jobPermissions("release.yml", "release"),
            "Only the gated Linux release job may publish and attest the canonical artifact",
        )
        assertEquals(
            listOf("contents: read"),
            jobPermissions("release.yml", "validation"),
            "The reusable validation call must not inherit publication permissions",
        )
    }

    @Test
    fun `Dependabot safely updates action pins through pull request validation`() {
        val dependabot = readDependabotConfiguration()
        val githubActions =
            dependabot.sequenceForKey("updates").value
                .filterIsInstance<MappingNode>()
                .single { it.scalarForKey("package-ecosystem") == "github-actions" }
        val pullRequest =
            readWorkflowMapping("build.yml")
                .mappingForKey("on")
                .mappingForKey("pull_request")

        assertEquals("/", githubActions.scalarForKey("directory"))
        assertEquals("weekly", githubActions.mappingForKey("schedule").scalarForKey("interval"))
        assertEquals(
            listOf("main"),
            pullRequest.sequenceForKey("branches").value.map { (it as ScalarNode).value },
            "Every pull request targeting main, including Dependabot updates, must run the build workflow",
        )
        assertFalse(
            Files.readString(Path.of(".github", "dependabot.yml")).contains("automerge", ignoreCase = true),
            "GitHub Actions updates must not be configured for automatic merging",
        )
    }

    @Test
    fun `Dependabot conservatively groups Gradle updates without auto merge`() {
        val dependabot = readDependabotConfiguration()
        assertEquals("2", dependabot.scalarForKey("version"))
        val updates = dependabot.sequenceForKey("updates")
        assertEquals(
            setOf("github-actions", "gradle"),
            updates.value
                .filterIsInstance<MappingNode>()
                .map { it.scalarForKey("package-ecosystem") }
                .toSet(),
            "Dependabot must contain only the reviewed update ecosystems",
        )
        val gradle =
            updates.value
                .filterIsInstance<MappingNode>()
                .single { it.scalarForKey("package-ecosystem") == "gradle" }

        assertEquals("/", gradle.scalarForKey("directory"))
        assertEquals("main", gradle.scalarForKey("target-branch"))
        assertEquals("weekly", gradle.mappingForKey("schedule").scalarForKey("interval"))
        assertEquals("5", gradle.scalarForKey("open-pull-requests-limit"))
        assertEquals(
            listOf("dependencies"),
            gradle.sequenceForKey("labels").value.map { (it as ScalarNode).value },
        )

        val groups = gradle.mappingForKey("groups")
        val expectedPatterns =
            mapOf(
                "kotlin-tooling" to listOf("org.jetbrains.kotlin*", "org.jetbrains.kotlinx*", "dev.detekt*"),
                "intellij-tooling" to listOf("org.jetbrains.intellij.platform*", "org.jetbrains.changelog*"),
                "junit" to listOf("org.junit*", "junit*"),
            )
        assertEquals(
            expectedPatterns.keys,
            groups.value.map { (it.keyNode as ScalarNode).value }.toSet(),
            "Gradle grouping must stay limited to reviewed dependency families",
        )
        groups.value.forEach { groupEntry ->
            val groupName = (groupEntry.keyNode as ScalarNode).value
            val group = groupEntry.valueNode as MappingNode
            assertEquals(
                expectedPatterns.getValue(groupName),
                group.sequenceForKey("patterns").value.map { (it as ScalarNode).value },
                "$groupName must remain limited to its reviewed dependency family",
            )
            assertEquals(
                listOf("minor", "patch"),
                group.sequenceForKey("update-types").value.map { (it as ScalarNode).value },
                "Grouped updates must exclude majors",
            )
        }
        assertFalse(
            Files.readString(Path.of(".github", "dependabot.yml")).contains("automerge", ignoreCase = true),
            "Gradle updates must never be configured for automatic merging",
        )
        val githubAutomation =
            Files.walk(Path.of(".github")).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { it.fileName.toString().endsWith(".yml") || it.fileName.toString().endsWith(".yaml") }
                    .map(Files::readString)
                    .toList()
                    .joinToString("\n")
                    .lowercase()
            }
        assertFalse(
            listOf("automerge", "auto-merge", "enablepullrequestautomerge", "gh pr merge --auto")
                .any(githubAutomation::contains),
            "GitHub automation must not auto-merge dependency pull requests",
        )
    }

    @Test
    fun `Detekt gate is baseline free and limited to reviewed defect rules`() {
        val buildScript = Files.readString(Path.of("build.gradle.kts"))
        val detekt = readYamlMapping(Path.of("config", "detekt", "detekt.yml"))
        val detektConfig = detekt.mappingForKey("config")
        val configuredRules =
            detekt.value
                .filter { (it.keyNode as ScalarNode).value != "config" }
                .flatMap { ruleSetEntry ->
                    val ruleSet = (ruleSetEntry.keyNode as ScalarNode).value
                    val rules = ruleSetEntry.valueNode as MappingNode
                    rules.value.map { ruleEntry ->
                        "$ruleSet.${(ruleEntry.keyNode as ScalarNode).value}" to
                            (ruleEntry.valueNode as MappingNode)
                    }
                }.toMap()

        assertEquals(
            setOf(
                "empty-blocks.EmptyCatchBlock",
                "exceptions.SwallowedException",
                "potential-bugs.UnreachableCode",
                "style.EqualsNullCall",
            ),
            configuredRules.keys,
        )
        assertTrue(configuredRules.values.all { it.scalarForKey("active") == "true" })
        assertEquals("true", detektConfig.scalarForKey("validation"))
        assertEquals("true", detektConfig.scalarForKey("warningsAsErrors"))
        assertTrue(buildScript.contains("buildUponDefaultConfig = false"))
        assertTrue(buildScript.contains("ignoreFailures = false"))
        assertFalse(buildScript.contains("baseline"), "Detekt must not hide findings behind a baseline")
        val baselineFiles =
            Files.walk(Path.of(".")).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { path ->
                        val normalizedPath = path.normalize().toString().replace('\\', '/')
                        !normalizedPath.startsWith(".git/") &&
                            !normalizedPath.startsWith(".gradle/") &&
                            !normalizedPath.startsWith("build/") &&
                            path.fileName.toString().contains("baseline", ignoreCase = true)
                    }.toList()
            }
        assertTrue(baselineFiles.isEmpty(), "Detekt baseline files are forbidden: $baselineFiles")
    }

    private fun assertValidationPipeline(
        workflowName: String,
        publicationStep: String,
    ) {
        val steps = workflowSteps(workflowName, "build")
        val detektStep = steps.stepNamed("Run Kotlin static analysis")
        val coverageStep = steps.stepNamed("Run test suite and generate Kotlin coverage")
        val reportStep = steps.stepNamed("Upload validation reports")
        val coverageCommand = coverageStep.scalarForKey("run")
        val reportPaths = reportStep.mappingForKey("with").scalarForKey("path")
        val buildScript = Files.readString(Path.of("build.gradle.kts"))

        assertStepsInOrder(
            steps,
            "name: Run Kotlin static analysis",
            "name: Run test suite and generate Kotlin coverage",
            "name: Verify plugin project and structure",
            "name: Verify plugin compatibility",
            "name: Build plugin",
            "name: Upload validation reports",
            "name: $publicationStep",
        )
        assertEquals(
            "./gradlew detekt --stacktrace --console=plain",
            detektStep.scalarForKey("run"),
            "$workflowName must fail on Detekt findings",
        )
        assertEquals(
            "${'$'}{{ !cancelled() }}",
            coverageStep.scalarForKey("if"),
            "$workflowName must still run tests after a static-analysis failure",
        )
        assertTrue(
            listOf("allTests", "koverXmlReport", "koverHtmlReport", "--continue").all { task ->
                Regex("""(?:^|\s)$task(?:\s|${'$'})""").containsMatchIn(coverageCommand)
            },
            "$workflowName must run both test tasks and generate Kotlin XML and HTML coverage",
        )
        assertFalse(
            coverageCommand.contains("koverVerify") ||
                buildScript.contains("minBound(") ||
                buildScript.contains("maxBound("),
            "$workflowName must report coverage without enforcing a vanity percentage",
        )
        val verificationStep = steps.stepNamed("Verify plugin project and structure").scalarForKey("run")
        assertTrue(
            verificationStep.contains("verifyPluginProjectConfiguration") &&
                verificationStep.contains("verifyPluginStructure") &&
                steps.stepNamed("Verify plugin compatibility").scalarForKey("run") ==
                "./gradlew verifyPlugin --stacktrace --console=plain",
            "$workflowName must run project, structure, and compatibility verification",
        )
        assertEquals(
            "always()",
            reportStep.scalarForKey("if"),
            "$workflowName must preserve validation reports when a gate fails",
        )
        assertTrue(
            listOf(
                "build/reports/pluginVerifier/",
                "build/reports/tests/test/",
                "build/test-results/test/",
                "build/reports/tests/platformTest/",
                "build/test-results/platformTest/",
                "build/reports/detekt/",
                "build/reports/kover/",
            ).all(reportPaths::contains),
            "$workflowName must upload static analysis, coverage, plugin verification, and test diagnostics",
        )
        assertTrue(
            buildScript.contains("intellijPlatformTesting.testIde.register(\"platformTest\")") &&
                buildScript.contains("CopyHistoryPersistenceTest") &&
                buildScript.contains("CopySelectionActionFixtureTest") &&
                buildScript.contains("CopySelectionDumbModeFixtureTest") &&
                buildScript.contains("forkEvery = 0") &&
                buildScript.contains("forkEvery = 1") &&
                buildScript.contains("register(\"allTests\")"),
            "Gradle must reuse pure-test workers, isolate platform-state classes, and aggregate both tasks",
        )
        assertPlatformStateTestsAreExplicitlyPartitioned(buildScript)
        assertTrue(
            steps.stepNamed("Setup Gradle").scalarForKey("uses").startsWith("gradle/actions/setup-gradle@"),
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
            Regex("""(?s)named\("check"\)\s*\{.*?dependsOn\(allTests\).*?}""").containsMatchIn(buildScript),
            "check must include the complete test aggregate",
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

    private fun assertReleasePublicationGate(workflow: MappingNode) {
        val jobs = workflow.mappingForKey("jobs")
        val validation = jobs.mappingForKey("validation")
        val release = jobs.mappingForKey("release")
        assertEquals(
            "./.github/workflows/release-validation.yml",
            validation.scalarForKey("uses"),
            "Release must invoke the repository's non-publishing validation workflow",
        )
        assertEquals(setOf("validation"), release.jobNeeds(), "Publication must directly require validation")
        assertEquals(
            "needs.validation.result == 'success'",
            release.scalarForKey("if"),
            "Failure, cancellation, or skipping must not satisfy the publication job condition",
        )
        assertEquals("ubuntu-latest", release.scalarForKey("runs-on"))
        assertTrue(release.valueForKey("strategy") == null, "Canonical artifact publication must not be a matrix")

        val publicationStepNames = setOf("Create GitHub Release", "Publish to JetBrains Marketplace")
        val publicationSteps =
            jobs.value.flatMap { jobEntry ->
                val jobName = (jobEntry.keyNode as ScalarNode).value
                val job = jobEntry.valueNode as? MappingNode ?: return@flatMap emptyList()
                val steps = (job.valueForKey("steps") as? SequenceNode)?.mappingValues().orEmpty()
                steps.mapNotNull { step ->
                    step.scalarForKeyOrNull("name")
                        ?.takeIf(publicationStepNames::contains)
                        ?.let { Triple(jobName, it, step) }
                }
            }
        assertEquals(
            publicationStepNames,
            publicationSteps.map { it.second }.toSet(),
            "Both GitHub Release and Marketplace publication steps are required",
        )
        assertTrue(
            publicationSteps.all { it.first == "release" },
            "Every publication step must remain inside the gated Linux release job",
        )
        publicationSteps.forEach { (_, name, step) ->
            assertTrue(step.valueForKey("continue-on-error") == null, "$name must fail the release job")
            val expectedCondition =
                if (name == "Publish to JetBrains Marketplace") {
                    "steps.release-mode.outputs.publish == 'true'"
                } else {
                    null
                }
            assertEquals(
                expectedCondition,
                step.scalarForKeyOrNull("if"),
                "$name must not bypass prerequisite failures",
            )
        }
    }

    private fun releaseCanPublishForValidationResult(
        workflow: MappingNode,
        validationResult: String,
    ): Boolean {
        val release = workflow.mappingForKey("jobs").mappingForKey("release")
        return release.jobNeeds() == setOf("validation") &&
            release.scalarForKeyOrNull("if") == "needs.validation.result == 'success'" &&
            validationResult == "success"
    }

    private fun releaseCanPublishForRequiredResults(
        releaseWorkflow: MappingNode,
        validationWorkflow: MappingNode,
        results: Map<String, String>,
    ): Boolean {
        assertOsMatrixTestCoverage(validationWorkflow)
        assertReusableValidationGate(validationWorkflow)
        assertEquals(
            setOf("ubuntu-latest", "macos-latest", "windows-latest", "linux-compatibility"),
            results.keys,
            "Every required OS row and compatibility job must have an explicit result",
        )
        val aggregateResult = if (results.values.all { it == "success" }) "success" else "failure"
        return releaseCanPublishForValidationResult(releaseWorkflow, aggregateResult)
    }

    private fun assertRequiredTestReportPaths(reportPaths: String) {
        assertTrue(
            listOf(
                "build/reports/tests/test/",
                "build/test-results/test/",
                "build/reports/tests/platformTest/",
                "build/test-results/platformTest/",
            ).all(reportPaths::contains),
            "Both unit and platform test reports must be preserved for every OS",
        )
    }

    private fun assertOsMatrixTestCoverage(workflow: MappingNode) {
        val osTests = workflow.mappingForKey("jobs").mappingForKey("os-tests")
        assertEquals("${'$'}{{ matrix.os }}", osTests.scalarForKey("runs-on"))
        assertTrue(osTests.valueForKey("if") == null, "Every OS matrix row must start unconditionally")
        assertTrue(osTests.valueForKey("continue-on-error") == null, "OS failures must fail the matrix")
        val strategy = osTests.mappingForKey("strategy")
        assertEquals("false", strategy.scalarForKey("fail-fast"))
        val matrix = strategy.mappingForKey("matrix")
        assertEquals(
            setOf("os"),
            matrix.keyNames(),
            "The OS matrix must not use include or exclude to add, replace, or remove required rows",
        )
        val matrixOperatingSystems = matrix.sequenceForKey("os").scalarValues()
        assertEquals(
            listOf("ubuntu-latest", "macos-latest", "windows-latest"),
            matrixOperatingSystems,
            "The release test matrix must contain every required operating system",
        )
        val steps = osTests.sequenceForKey("steps").mappingValues()
        val expectedTests =
            mapOf(
                "ubuntu-latest" to
                    Triple(
                        "matrix.os == 'ubuntu-latest'",
                        "bash",
                        "./gradlew allTests koverXmlReport koverHtmlReport --continue --stacktrace --console=plain",
                    ),
                "macos-latest" to
                    Triple(
                        "matrix.os == 'macos-latest'",
                        "bash",
                        "./gradlew allTests --continue --stacktrace --console=plain",
                    ),
                "windows-latest" to
                    Triple(
                        "matrix.os == 'windows-latest'",
                        "pwsh",
                        ".\\gradlew.bat allTests --continue --stacktrace --console=plain",
                    ),
            )
        expectedTests.forEach { (operatingSystem, expected) ->
            val matchingSteps =
                steps.filter { step ->
                    step.scalarForKeyOrNull("if") == expected.first &&
                        step.scalarForKeyOrNull("run").orEmpty().contains("allTests")
                }
            assertEquals(
                1,
                matchingSteps.size,
                "$operatingSystem must have exactly one non-optional allTests step",
            )
            assertTrue(
                matchingSteps.single().valueForKey("continue-on-error") == null,
                "$operatingSystem tests must fail the matrix row",
            )
            assertEquals(expected.second, matchingSteps.single().scalarForKey("shell"))
            assertEquals(
                expected.third,
                matchingSteps.single().scalarForKey("run").normalizedWhitespace(),
                "$operatingSystem must run the fail-closed task set with its native wrapper and shell",
            )
        }
    }

    private fun assertReusableValidationGate(workflow: MappingNode) {
        val gate = workflow.mappingForKey("jobs").mappingForKey("validation-gate")
        assertEquals("always()", gate.scalarForKey("if"))
        assertEquals(setOf("os-tests", "linux-compatibility"), gate.jobNeeds())
        assertEquals("ubuntu-latest", gate.scalarForKey("runs-on"))
        assertTrue(gate.valueForKey("continue-on-error") == null, "The aggregate gate must fail the workflow")
        val step = gate.sequenceForKey("steps").mappingValues().single()
        assertEquals("Require successful OS and IDE validation", step.scalarForKey("name"))
        assertTrue(step.valueForKey("if") == null, "The aggregate assertion step must always run with its gate job")
        assertTrue(step.valueForKey("continue-on-error") == null, "The aggregate assertion must fail closed")
        val environment = step.mappingForKey("env")
        assertEquals(setOf("OS_TESTS_RESULT", "IDE_COMPATIBILITY_RESULT"), environment.keyNames())
        assertEquals("${'$'}{{ needs.os-tests.result }}", environment.scalarForKey("OS_TESTS_RESULT"))
        assertEquals(
            "${'$'}{{ needs.linux-compatibility.result }}",
            environment.scalarForKey("IDE_COMPATIBILITY_RESULT"),
        )
        assertEquals(
            """
            if [ "${'$'}OS_TESTS_RESULT" != "success" ] || [ "${'$'}IDE_COMPATIBILITY_RESULT" != "success" ]; then
              echo "::error::Release validation did not complete successfully."
              exit 1
            fi
            """.trimIndent(),
            step.scalarForKey("run").trim(),
            "The gate must reject every non-success result and exit non-zero",
        )
    }

    private fun assertLinuxCompatibilityJob(workflow: MappingNode) {
        val compatibility = workflow.mappingForKey("jobs").mappingForKey("linux-compatibility")
        assertEquals("ubuntu-latest", compatibility.scalarForKey("runs-on"))
        assertTrue(compatibility.valueForKey("strategy") == null, "Plugin Verifier must run once on Linux")
        assertTrue(compatibility.valueForKey("if") == null, "IDE compatibility must not be skipped")
        assertTrue(
            compatibility.valueForKey("continue-on-error") == null,
            "IDE compatibility failures must fail validation",
        )
        val expectedCommands =
            mapOf(
                "Run Kotlin static analysis" to "./gradlew detekt --stacktrace --console=plain",
                "Verify plugin project and structure" to
                    "./gradlew verifyPluginProjectConfiguration verifyPluginStructure --stacktrace --console=plain",
                "Verify plugin compatibility" to "./gradlew verifyPlugin --stacktrace --console=plain",
            )
        val steps = compatibility.sequenceForKey("steps").mappingValues()
        expectedCommands.forEach { (name, expectedCommand) ->
            val step = steps.stepNamed(name)
            assertTrue(step.valueForKey("if") == null, "$name must run unconditionally")
            assertTrue(step.valueForKey("continue-on-error") == null, "$name must fail closed")
            assertEquals(expectedCommand, step.scalarForKey("run").normalizedWhitespace())
        }
    }

    private fun readWorkflow(workflowName: String): String {
        val path = Path.of(".github", "workflows", workflowName)
        assertTrue(Files.isRegularFile(path), "Workflow not found: $path")
        return Files.readString(path)
    }

    private fun readWorkflowMapping(workflowName: String): MappingNode =
        readYamlMapping(Path.of(".github", "workflows", workflowName))

    private fun workflowSteps(
        workflowName: String,
        jobName: String,
    ): List<MappingNode> {
        val job = readWorkflowMapping(workflowName).mappingForKey("jobs").mappingForKey(jobName)
        return job.sequenceForKey("steps").value.map { step ->
            step as? MappingNode ?: throw AssertionError("$workflowName contains a non-mapping step")
        }
    }

    private fun readDependabotConfiguration(): MappingNode =
        readYamlMapping(Path.of(".github", "dependabot.yml"))

    private fun readYamlMapping(path: Path): MappingNode {
        assertTrue(Files.isRegularFile(path), "YAML configuration is required: $path")
        return parseYamlMapping(Files.readString(path), path.toString())
    }

    private fun parseYamlMapping(
        yaml: String,
        source: String,
    ): MappingNode {
        val settings =
            LoadSettings
                .builder()
                .setLabel(source)
                .setAllowDuplicateKeys(false)
                .build()
        return Compose(settings).composeString(yaml).orElseThrow() as MappingNode
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

    private fun MappingNode.keyNames(): Set<String> =
        value.map { entry -> (entry.keyNode as ScalarNode).value }.toSet()

    private fun MappingNode.scalarForKey(key: String): String =
        (valueForKey(key) as? ScalarNode)?.value
            ?: throw AssertionError("Missing scalar Dependabot key: $key")

    private fun MappingNode.scalarForKeyOrNull(key: String): String? =
        (valueForKey(key) as? ScalarNode)?.value

    private fun MappingNode.mappingForKey(key: String): MappingNode =
        valueForKey(key) as? MappingNode
            ?: throw AssertionError("Missing mapping Dependabot key: $key")

    private fun MappingNode.sequenceForKey(key: String): SequenceNode =
        valueForKey(key) as? SequenceNode
            ?: throw AssertionError("Missing sequence Dependabot key: $key")

    private fun SequenceNode.scalarValues(): List<String> = value.map { (it as ScalarNode).value }

    private fun SequenceNode.mappingValues(): List<MappingNode> =
        value.map { node -> node as? MappingNode ?: throw AssertionError("Expected a mapping sequence item") }

    private fun MappingNode.jobNeeds(): Set<String> =
        when (val needs = valueForKey("needs")) {
            is ScalarNode -> setOf(needs.value)
            is SequenceNode -> needs.scalarValues().toSet()
            else -> emptySet()
        }

    private fun String.normalizedWhitespace(): String = trim().split(Regex("""\s+""")).joinToString(" ")

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

    private fun workflowPermissions(workflowName: String): List<String> =
        readWorkflowMapping(workflowName)
            .mappingForKey("permissions")
            .value
            .map { permission ->
                "${(permission.keyNode as ScalarNode).value}: ${(permission.valueNode as ScalarNode).value}"
            }

    private fun jobPermissions(
        workflowName: String,
        jobName: String,
    ): List<String> =
        readWorkflowMapping(workflowName)
            .mappingForKey("jobs")
            .mappingForKey(jobName)
            .mappingForKey("permissions")
            .value
            .map { permission ->
                "${(permission.keyNode as ScalarNode).value}: ${(permission.valueNode as ScalarNode).value}"
            }

    private fun List<MappingNode>.stepNamed(name: String): MappingNode =
        singleOrNull { step -> (step.valueForKey("name") as? ScalarNode)?.value == name }
            ?: throw AssertionError("Missing or duplicate workflow step: $name")

    private fun assertStepsInOrder(
        steps: List<MappingNode>,
        vararg markers: String,
    ) {
        val names = steps.map { step -> (step.valueForKey("name") as? ScalarNode)?.value }
        var previousIndex = -1
        markers.forEach { marker ->
            val name = marker.removePrefix("name: ")
            val markerIndex = names.indexOf(name)
            assertTrue(markerIndex >= 0, "Missing workflow step: $name")
            assertTrue(markerIndex > previousIndex, "Workflow step is out of order: $name")
            previousIndex = markerIndex
        }
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
