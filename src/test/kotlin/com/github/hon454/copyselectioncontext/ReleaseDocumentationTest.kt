package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReleaseDocumentationTest {
    private val projectRoot: Path = Path.of(System.getProperty("user.dir"))
    private val contributing = readProjectFile("CONTRIBUTING.md")
    private val agents = readProjectFile("AGENTS.md")
    private val releaseWorkflow = readProjectFile(".github/workflows/release.yml")
    private val releaseNotesGenerator = readProjectFile("scripts/generate-release-notes.sh")

    @Test
    fun `release guidance uses the changelog workflow commands`() {
        val getChangelogOptions = listOf(
            "--console=plain",
            "-q",
            "--no-header",
            "--no-links",
            "--no-summary",
        )

        assertContains(contributing, "CHANGELOG.md")
        assertContains(contributing, "./gradlew patchChangelog")
        assertContains(contributing, "./gradlew getChangelog")
        assertContains(releaseNotesGenerator, "\"${'$'}gradle_wrapper\" getChangelog")
        getChangelogOptions.forEach { option ->
            assertContains(contributing, option)
            assertContains(releaseNotesGenerator, option)
        }
        assertContains(releaseWorkflow, "bash scripts/generate-release-notes.sh")
        assertFalse(contributing.contains("commit-based release notes"))
        assertFalse(agents.contains("commit-based release notes"))
    }

    @Test
    fun `release guidance matches tag and marketplace workflow conditions`() {
        assertContains(releaseWorkflow, "- 'v*'")
        assertContains(contributing, "any pushed tag matching `v*`")
        assertContains(contributing, "must match exactly")
        assertContains(releaseWorkflow, "env.PUBLISH_TOKEN != '' &&")
        assertContains(releaseWorkflow, "env.CERTIFICATE_CHAIN != '' &&")
        assertContains(releaseWorkflow, "env.PRIVATE_KEY != ''")
        assertContains(
            contributing,
            "`PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty",
        )
        assertContains(contributing, "`PRIVATE_KEY_PASSWORD` is available to the signing configuration")
    }

    @Test
    fun `release guidance records canonical artifact and reproducibility limits`() {
        assertContains(contributing, "canonical release artifact")
        assertContains(contributing, "Cross-environment byte-for-byte reproducibility is not currently supported")
        assertContains(contributing, "Build-JVM")
        assertContains(contributing, "Build-OS")
        assertContains(contributing, "SHA256SUMS")
        assertContains(contributing, "attestations: write")
        assertContains(releaseWorkflow, "bash scripts/generate-release-checksum.sh")
        assertContains(releaseWorkflow, "gh attestation verify")
    }

    @Test
    fun `contributing commit types stay aligned with agent guidance`() {
        assertEquals(commitTypes(agents), commitTypes(contributing))
        assertContains(contributing, "72 characters or fewer")
        assertContains(contributing, "body for every non-trivial commit")
        assertContains(contributing, "Do not add AI agent attribution")
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(projectRoot.resolve(relativePath))

    private fun commitTypes(document: String): List<String> =
        Regex("\\*\\*Allowed types:\\*\\* ([^\\n]+)")
            .find(document)
            ?.groupValues
            ?.get(1)
            ?.split(", ")
            ?: emptyList()
}
