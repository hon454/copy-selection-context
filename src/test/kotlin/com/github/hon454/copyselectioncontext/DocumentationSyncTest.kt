package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationSyncTest {
    private val repositoryRoot = Path.of(System.getProperty("user.dir"))
    private val readmePaths = listOf(
        "README.md",
        "README.ko.md",
        "README.zh-CN.md",
        "README.zh-TW.md",
        "README.ja.md",
    )

    @Test
    fun `localized readmes keep the same markdown structure`() {
        val signatures = readmePaths.associateWith { path ->
            markdownStructure(repositoryRoot.resolve(path).readText())
        }

        val englishSignature = signatures.getValue("README.md")
        signatures.forEach { (path, signature) ->
            assertEquals(englishSignature, signature, "$path must match README.md structure")
        }
    }

    @Test
    fun `localized readmes document current formats settings and copy feedback`() {
        val requiredContent = listOf(
            "`claude`",
            "`pathline`",
            "`template`",
            "`{path}`",
            "`{line}`",
            "`{range}`",
            "`{code}`",
            "`{lang}`",
            "`{filename}`",
            "`selectionEnd - 1`",
            "**Path type**",
            "**Output format**",
            "**Custom format template**",
            "**Include code content**",
            "**Trim code whitespace**",
            "**Show copy notifications**",
            "**Copy history size**",
            "**Local usage analytics**",
            "Ctrl+Alt+H",
            "40",
            "copySelectionHistory.xml",
            "CopySelectionActionFixtureTest",
            "`verifyPlugin`",
        )

        readmePaths.forEach { path ->
            val content = repositoryRoot.resolve(path).readText()
            requiredContent.forEach { required ->
                assertTrue(content.contains(required), "$path must document $required")
            }
        }
    }

    @Test
    fun `architecture docs derive toolchain values from build sources of truth`() {
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()
        val wrapperProperties = repositoryRoot.resolve("gradle/wrapper/gradle-wrapper.properties").readText()
        val gradleProperties = Properties().apply {
            Files.newBufferedReader(repositoryRoot.resolve("gradle.properties")).use { load(it) }
        }

        val kotlinVersion = capture(buildScript, "id\\(\"org\\.jetbrains\\.kotlin\\.jvm\"\\)\\s+version\\s+\"([^\"]+)\"")
        val intellijPluginVersion = capture(buildScript, "id\\(\"org\\.jetbrains\\.intellij\\.platform\"\\)\\s+version\\s+\"([^\"]+)\"")
        val intellijPlatformVersion = capture(buildScript, "intellijIdeaCommunity\\(\"([^\"]+)\"\\)")
        val jvmVersion = capture(buildScript, "jvmToolchain\\((\\d+)\\)")
        val minimumBuild = capture(buildScript, "sinceBuild\\.set\\(\"([^\"]+)\"\\)")
        val junitVersion = capture(buildScript, "junit-jupiter-api:([^\"]+)\"")
        val mockkVersion = capture(buildScript, "io\\.mockk:mockk:([^\"]+)\"")
        val gradleVersion = capture(wrapperProperties, "gradle-([0-9.]+)-bin\\.zip")
        val useBundledKotlinStdlib = gradleProperties.getProperty("kotlin.stdlib.default.dependency")
            ?: error("gradle.properties must declare kotlin.stdlib.default.dependency")

        val knowledgeBase = repositoryRoot.resolve("AGENTS.md").readText()
        assertTableValue(knowledgeBase, "Kotlin", kotlinVersion)
        assertTableValue(knowledgeBase, "Gradle", gradleVersion)
        assertTableValue(knowledgeBase, "IntelliJ Platform Plugin", intellijPluginVersion)
        assertTableValue(knowledgeBase, "JVM Toolchain", jvmVersion)
        assertTableValue(knowledgeBase, "Min IDE Version", intellijPlatformVersion)

        val architecture = repositoryRoot.resolve(".agents/architecture.md").readText()
        assertTableValue(architecture, "Kotlin", kotlinVersion)
        assertTableValue(architecture, "Gradle wrapper", gradleVersion)
        assertTableValue(architecture, "IntelliJ Platform Gradle Plugin", intellijPluginVersion)
        assertTableValue(architecture, "IntelliJ IDEA Community test platform", intellijPlatformVersion)
        assertTableValue(architecture, "JVM toolchain and target", jvmVersion)
        assertTableValue(architecture, "Minimum IDE build", "$minimumBuild ($intellijPlatformVersion)")
        assertTableValue(architecture, "JUnit Jupiter", junitVersion)
        assertTableValue(architecture, "MockK", mockkVersion)
        assertTrue(
            architecture.contains("`kotlin.stdlib.default.dependency=$useBundledKotlinStdlib`"),
            "architecture must document the Kotlin stdlib dependency setting from gradle.properties",
        )
    }

    @Test
    fun `architecture docs describe current source files and implemented widget`() {
        val architecturePaths = listOf("AGENTS.md", ".agents/architecture.md", ".agents/patterns.md")
        architecturePaths.forEach { path ->
            val content = repositoryRoot.resolve(path).readText()
            assertFalse(content.contains("stub", ignoreCase = true), "$path must not describe the status widget as a stub")
        }

        val architecture = repositoryRoot.resolve(".agents/architecture.md").readText()
        listOf(
            "selectionEnd - 1",
            "multiple carets",
            "{filename}",
            "pooled thread",
            "linked worktrees",
            "local, non-roaming workspace",
            "CopyPreview",
            "CopySelectionBundle",
            "six-row multiline template editor",
            "CopySelectionActionFixtureTest",
            "three-IDE Plugin Verifier",
        ).forEach { contract ->
            assertTrue(architecture.contains(contract), "architecture must document $contract")
        }

        val sourceDirectory = repositoryRoot.resolve("src/main/kotlin/com/github/hon454/copyselectioncontext")
        Files.list(sourceDirectory).use { files ->
            files.filter { it.extension == "kt" }
                .map { it.name }
                .forEach { filename ->
                    assertTrue(architecture.contains("`$filename`"), "architecture must list $filename")
                }
        }
    }

    @Test
    fun `action inheritance guide matches registered action source`() {
        val registeredActions = registeredActionClassNames()
        val directParents = registeredActions.associateWith(::directParentOf)

        assertEquals("AnAction", directParentOf("CopySelectionBaseAction"))

        val sharedPipelineActions = directParents
            .filterValues { it == "CopySelectionBaseAction" }
            .keys
        val directActions = directParents
            .filterValues { it == "AnAction" }
            .keys
        assertEquals(registeredActions, sharedPipelineActions + directActions)

        val patterns = repositoryRoot.resolve(".agents/patterns.md").readText()
        val sharedPipelineSection = patterns.substringAfter("### Shared copy-pipeline actions")
            .substringBefore("### Specialized direct actions")
        val directActionSection = patterns.substringAfter("### Specialized direct actions")
            .substringBefore("## Clipboard")

        val documentedSharedActions = registeredActions.filter { sharedPipelineSection.contains("`$it`") }.toSet()
        val documentedDirectActions = registeredActions.filter { directActionSection.contains("`$it`") }.toSet()
        assertEquals(sharedPipelineActions, documentedSharedActions)
        assertEquals(directActions, documentedDirectActions)
    }

    private fun registeredActionClassNames(): Set<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder()
            .parse(repositoryRoot.resolve("src/main/resources/META-INF/plugin.xml").toFile())
        val actions = document.getElementsByTagName("action")

        return buildSet {
            for (index in 0 until actions.length) {
                val qualifiedName = actions.item(index).attributes.getNamedItem("class").nodeValue
                add(qualifiedName.substringAfterLast('.'))
            }
        }
    }

    private fun directParentOf(className: String): String {
        val source = repositoryRoot
            .resolve("src/main/kotlin/com/github/hon454/copyselectioncontext/$className.kt")
            .readText()
        val declaration = Regex("(?:abstract\\s+)?class\\s+${Regex.escape(className)}\\s*:\\s*([A-Za-z0-9_.]+)")
            .find(source)
            ?: error("Could not resolve direct parent for $className")
        return declaration.groupValues[1].substringAfterLast('.')
    }

    private fun capture(content: String, pattern: String): String = Regex(pattern)
        .find(content)
        ?.groupValues
        ?.get(1)
        ?: error("Could not resolve build value with pattern: $pattern")

    private fun assertTableValue(document: String, label: String, value: String) {
        val expectedRow = "| $label | $value |"
        assertTrue(
            document.lineSequence().any { it.trim() == expectedRow },
            "documentation must contain the SOT-derived row: $expectedRow",
        )
    }

    private fun markdownStructure(content: String): List<String> = content.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> "heading:${trimmed.takeWhile { it == '#' }.length}"
                trimmed.matches(Regex("""\d+\. .*""")) -> "ordered-item"
                trimmed.startsWith("- ") -> "unordered-item"
                trimmed.startsWith("|") -> "table-row"
                trimmed.startsWith("```") -> "fence:${trimmed.takeWhile { it == '`' }.length}"
                else -> null
            }
        }
        .toList()
}
