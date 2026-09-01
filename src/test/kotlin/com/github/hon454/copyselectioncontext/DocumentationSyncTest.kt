package com.github.hon454.copyselectioncontext

import java.nio.file.Files
import java.nio.file.Path
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
        )

        readmePaths.forEach { path ->
            val content = repositoryRoot.resolve(path).readText()
            requiredContent.forEach { required ->
                assertTrue(content.contains(required), "$path must document $required")
            }
        }
    }

    @Test
    fun `architecture docs match the current toolchain and source files`() {
        val architecturePaths = listOf("AGENTS.md", ".agents/architecture.md", ".agents/patterns.md")
        architecturePaths.forEach { path ->
            val content = repositoryRoot.resolve(path).readText()
            assertFalse(content.contains("stub", ignoreCase = true), "$path must not describe the status widget as a stub")
        }

        val architecture = repositoryRoot.resolve(".agents/architecture.md").readText()
        listOf("2.4.10", "2.18.1", "9.7.1", "2024.3", "JUnit Jupiter", "MockK").forEach { version ->
            assertTrue(architecture.contains(version), "architecture must document $version")
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
