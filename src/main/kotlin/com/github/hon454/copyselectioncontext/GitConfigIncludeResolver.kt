package com.github.hon454.copyselectioncontext

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal class GitConfigIncludeException(
    val reason: GitPermalinkFailureReason,
    val safeExceptionType: String,
    cause: Throwable? = null,
) : RuntimeException(null, cause, false, false)

internal class GitConfigIncludeResolver(
    private val gitDir: Path,
    private val branchName: String?,
    private val fileReader: (Path) -> String,
) {
    private val remotes = linkedMapOf<String, String>()
    private val branchRemotes = mutableMapOf<String, String>()
    private val activeFiles = mutableSetOf<Path>()

    fun resolve(configPath: Path, config: String): String? {
        parseConfig(configPath.toAbsolutePath().normalize(), config, depth = 0)

        branchName?.let { branchRemotes[it] }?.let { remoteName ->
            remotes[remoteName]?.let { return it }
        }
        remotes[ORIGIN_REMOTE]?.let { return it }
        return remotes.values.singleOrNull()
    }

    private fun parseConfig(configPath: Path, config: String, depth: Int) {
        val identity = fileIdentity(configPath)
        if (!activeFiles.add(identity)) {
            throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_CYCLE,
                GitConfigIncludeCycleException::class.java.name,
            )
        }

        try {
            var section: ConfigSection? = null
            config.lineSequence().forEach { line ->
                val trimmed = stripInlineComment(line).trim()
                if (trimmed.isEmpty()) return@forEach

                SECTION_PATTERN.matchEntire(trimmed)?.let { match ->
                    section = ConfigSection(
                        type = match.groupValues[1].lowercase(),
                        name = unescapeSectionName(match.groupValues[2]),
                    )
                    return@forEach
                }

                val currentSection = section ?: return@forEach
                val valueMatch = VALUE_PATTERN.matchEntire(trimmed) ?: return@forEach
                val key = valueMatch.groupValues[1].lowercase()
                val value = parseValue(valueMatch.groupValues[2])
                when {
                    key == INCLUDE_PATH_KEY && shouldInclude(currentSection, configPath) ->
                        parseIncludedConfig(configPath, value, depth)
                    currentSection.type == REMOTE_SECTION && key == URL_KEY ->
                        remotes.putIfAbsent(currentSection.name, value)
                    currentSection.type == BRANCH_SECTION && key == REMOTE_KEY ->
                        branchRemotes.putIfAbsent(currentSection.name, value)
                }
            }
        } finally {
            activeFiles.remove(identity)
        }
    }

    private fun parseIncludedConfig(includingConfig: Path, includeValue: String, depth: Int) {
        if (depth >= MAX_INCLUDE_DEPTH) {
            throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_DEPTH_EXCEEDED,
                GitConfigIncludeDepthException::class.java.name,
            )
        }

        val includedPath = resolveIncludePath(includingConfig, includeValue)
        if (!Files.isRegularFile(includedPath)) {
            throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE,
                NoSuchFileException::class.java.name,
            )
        }
        val includedConfig = try {
            fileReader(includedPath)
        } catch (exception: IOException) {
            throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE,
                exception.javaClass.name,
                exception,
            )
        }
        parseConfig(includedPath, includedConfig, depth + 1)
    }

    private fun shouldInclude(section: ConfigSection, configPath: Path): Boolean = when (section.type) {
        INCLUDE_SECTION -> true
        INCLUDE_IF_SECTION -> matchesCondition(section.name, configPath)
        else -> false
    }

    private fun matchesCondition(condition: String, configPath: Path): Boolean {
        val separator = condition.indexOf(':')
        if (separator <= 0) return false
        val keyword = condition.substring(0, separator).lowercase()
        val pattern = condition.substring(separator + 1)
        return when (keyword) {
            GIT_DIR_CONDITION -> matchesGitDir(pattern, configPath, ignoreCase = false)
            GIT_DIR_IGNORE_CASE_CONDITION -> matchesGitDir(pattern, configPath, ignoreCase = true)
            ON_BRANCH_CONDITION -> branchName?.let { matchesGlob(patternForBranch(pattern), it, false) } ?: false
            else -> false
        }
    }

    private fun matchesGitDir(pattern: String, configPath: Path, ignoreCase: Boolean): Boolean {
        val resolvedPattern = patternForGitDir(pattern, configPath)
        val candidates = buildSet {
            add(normalizeSeparators(gitDir.toAbsolutePath().normalize().toString()))
            runCatching { gitDir.toRealPath() }
                .getOrNull()
                ?.let { add(normalizeSeparators(it.toString())) }
        }
        return candidates.any { candidate -> matchesGlob(resolvedPattern, candidate, ignoreCase) }
    }

    private fun patternForGitDir(pattern: String, configPath: Path): String {
        val expanded = when {
            pattern.startsWith("~/") -> resolveHomePath(pattern.removePrefix("~/"))
            pattern.startsWith("./") -> normalizeSeparators(
                configPath.parent.resolve(pattern.removePrefix("./")).normalize().toString()
            )
            isAbsolutePattern(pattern) -> normalizeSeparators(pattern)
            else -> "**/${normalizeSeparators(pattern)}"
        }
        return if (expanded.endsWith('/')) "${expanded}**" else expanded
    }

    private fun patternForBranch(pattern: String): String =
        if (pattern.endsWith('/')) "${pattern}**" else pattern

    private fun resolveIncludePath(includingConfig: Path, value: String): Path {
        val path = try {
            when {
                value.startsWith("~/") -> Path.of(resolveHomePath(value.removePrefix("~/")))
                value.startsWith('~') || value.startsWith("%(prefix)/") -> throw InvalidPathException(value, "")
                else -> Path.of(value)
            }
        } catch (_: InvalidPathException) {
            throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE,
                InvalidPathException::class.java.name,
            )
        }
        return (if (path.isAbsolute) path else includingConfig.parent.resolve(path))
            .toAbsolutePath()
            .normalize()
    }

    private fun resolveHomePath(relativePath: String): String {
        val home = System.getProperty("user.home")?.takeIf(String::isNotBlank)
            ?: throw GitConfigIncludeException(
                GitPermalinkFailureReason.GIT_CONFIG_INCLUDE_IO_FAILURE,
                IllegalStateException::class.java.name,
            )
        return normalizeSeparators(Path.of(home).resolve(relativePath).normalize().toString())
    }

    private fun fileIdentity(path: Path): Path = runCatching { path.toRealPath() }
        .getOrElse { path.toAbsolutePath().normalize() }

    private fun isAbsolutePattern(pattern: String): Boolean =
        runCatching { Path.of(pattern).isAbsolute }.getOrDefault(false)

    private fun matchesGlob(pattern: String, value: String, ignoreCase: Boolean): Boolean {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(globToRegex(normalizeSeparators(pattern)), options).matches(normalizeSeparators(value))
    }

    private fun globToRegex(pattern: String): String = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when {
                pattern.startsWith("**/", index) -> {
                    append("(?:.*/)?")
                    index += 3
                }
                pattern.startsWith("/**", index) && index + 3 == pattern.length -> {
                    append("(?:/.*)?")
                    index += 3
                }
                pattern.startsWith("**", index) -> {
                    append(".*")
                    index += 2
                }
                pattern[index] == '*' -> {
                    append("[^/]*")
                    index++
                }
                pattern[index] == '?' -> {
                    append("[^/]")
                    index++
                }
                pattern[index] == '[' -> index = appendCharacterClass(pattern, index)
                else -> {
                    append(Regex.escape(pattern[index].toString()))
                    index++
                }
            }
        }
        append('$')
    }

    private fun StringBuilder.appendCharacterClass(pattern: String, start: Int): Int {
        val end = pattern.indexOf(']', start + 1)
        if (end < 0) {
            append("\\[")
            return start + 1
        }
        val content = pattern.substring(start + 1, end)
        append('[')
        if (content.startsWith('!')) {
            append('^')
            append(content.drop(1))
        } else {
            append(content)
        }
        append(']')
        return end + 1
    }

    private fun parseValue(value: String): String = value.trim().removeSurrounding(QUOTE).let(::unescapeValue)

    private fun stripInlineComment(line: String): String {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, character ->
            when {
                escaped -> escaped = false
                quoted && character == '\\' -> escaped = true
                character == '"' -> quoted = !quoted
                !quoted && (character == '#' || character == ';') -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun unescapeValue(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun unescapeSectionName(value: String): String = unescapeValue(value)

    private fun normalizeSeparators(value: String): String = value.replace('\\', '/')

    private data class ConfigSection(val type: String, val name: String)

    private class GitConfigIncludeCycleException
    private class GitConfigIncludeDepthException

    companion object {
        internal const val MAX_INCLUDE_DEPTH = 10
        private const val INCLUDE_PATH_KEY = "path"
        private const val INCLUDE_SECTION = "include"
        private const val INCLUDE_IF_SECTION = "includeif"
        private const val REMOTE_SECTION = "remote"
        private const val BRANCH_SECTION = "branch"
        private const val URL_KEY = "url"
        private const val REMOTE_KEY = "remote"
        private const val ORIGIN_REMOTE = "origin"
        private const val GIT_DIR_CONDITION = "gitdir"
        private const val GIT_DIR_IGNORE_CASE_CONDITION = "gitdir/i"
        private const val ON_BRANCH_CONDITION = "onbranch"
        private const val QUOTE = "\""
        private val SECTION_PATTERN = Regex("""^\[\s*([A-Za-z0-9.-]+)(?:\s+\"((?:\\.|[^\"])*)\")?\s*]$""")
        private val VALUE_PATTERN = Regex("""^([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$""")
    }

}
