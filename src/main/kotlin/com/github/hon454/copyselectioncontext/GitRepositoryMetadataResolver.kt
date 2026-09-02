package com.github.hon454.copyselectioncontext

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class GitRepositoryMetadata(
    val remoteUrl: String,
    val commitSha: String
)

internal object GitRepositoryMetadataResolver {
    private val objectIdPattern = Regex("^[0-9a-fA-F]{40}(?:[0-9a-fA-F]{24})?$")
    private val sectionPattern = Regex("""^\[\s*([A-Za-z0-9.-]+)(?:\s+\"((?:\\.|[^\"])*)\")?\s*]$""")
    private val valuePattern = Regex("""^([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$""")

    fun resolve(
        root: Path,
        fileReader: (Path) -> String = { Files.readString(it) },
    ): GitPermalinkResult<GitRepositoryMetadata> = try {
        resolveMetadata(root, fileReader)
            ?.let { GitPermalinkResult.Success(it) }
            ?: GitPermalinkResult.Failure(
                reason = GitPermalinkFailureReason.UNRESOLVED_GIT_METADATA,
                diagnostic = GitPermalinkDiagnostic(GitPermalinkOperation.RESOLVE_GIT_METADATA),
            )
    } catch (exception: IOException) {
        GitPermalinkResult.Failure(
            reason = GitPermalinkFailureReason.IO_FAILURE,
            diagnostic = GitPermalinkDiagnostic(
                operation = GitPermalinkOperation.RESOLVE_GIT_METADATA,
                exceptionType = exception.javaClass.name,
            ),
        )
    } catch (exception: Exception) {
        GitPermalinkResult.Failure(
            reason = GitPermalinkFailureReason.UNEXPECTED_FAILURE,
            diagnostic = GitPermalinkDiagnostic(
                operation = GitPermalinkOperation.RESOLVE_GIT_METADATA,
                exceptionType = exception.javaClass.name,
            ),
        )
    }

    private fun resolveMetadata(root: Path, fileReader: (Path) -> String): GitRepositoryMetadata? {
        val gitDir = resolveGitDir(root, fileReader) ?: return null
        val commonDir = resolveCommonDir(gitDir, fileReader) ?: return null
        val head = resolveHead(gitDir, commonDir, fileReader) ?: return null
        val config = readText(commonDir.resolve("config"), fileReader) ?: return null
        val remoteUrl = extractRemoteUrl(config, head.branchName) ?: return null

        return GitRepositoryMetadata(remoteUrl, head.commitSha)
    }

    private fun resolveGitDir(root: Path, fileReader: (Path) -> String): Path? {
        val dotGit = root.toAbsolutePath().normalize().resolve(".git")
        if (Files.isDirectory(dotGit)) return dotGit
        if (!Files.isRegularFile(dotGit)) return null

        val marker = readText(dotGit, fileReader)
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("gitdir:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val markerPath = Path.of(marker)
        val resolved = if (markerPath.isAbsolute) markerPath else dotGit.parent.resolve(markerPath)

        return resolved.normalize().takeIf(Files::isDirectory)
    }

    private fun resolveCommonDir(gitDir: Path, fileReader: (Path) -> String): Path? {
        val commonDirFile = gitDir.resolve("commondir")
        if (!Files.exists(commonDirFile)) return gitDir

        val marker = readText(commonDirFile, fileReader)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val markerPath = Path.of(marker)
        val resolved = if (markerPath.isAbsolute) markerPath else gitDir.resolve(markerPath)

        return resolved.normalize().takeIf(Files::isDirectory)
    }

    private fun resolveHead(
        gitDir: Path,
        commonDir: Path,
        fileReader: (Path) -> String,
    ): HeadResolution? {
        val headContent = readText(gitDir.resolve("HEAD"), fileReader)?.trim() ?: return null
        if (!headContent.startsWith("ref: ")) {
            return headContent.takeIf(::isObjectId)?.let { HeadResolution(it.lowercase(), null) }
        }

        val refName = headContent.removePrefix("ref: ").trim()
        val commitSha = resolveRef(refName, gitDir, commonDir, fileReader) ?: return null
        val branchName = refName.removePrefix("refs/heads/").takeIf { refName.startsWith("refs/heads/") }
        return HeadResolution(commitSha, branchName)
    }

    private fun resolveRef(
        refName: String,
        gitDir: Path,
        commonDir: Path,
        fileReader: (Path) -> String,
    ): String? {
        val relativeRef = safeRelativePath(refName) ?: return null
        val looseRef = sequenceOf(gitDir, commonDir)
            .distinct()
            .mapNotNull { directory -> readText(directory.resolve(relativeRef), fileReader)?.trim() }
            .firstOrNull()
        if (looseRef != null) return looseRef.takeIf(::isObjectId)?.lowercase()

        return sequenceOf(gitDir, commonDir)
            .distinct()
            .mapNotNull { directory -> resolvePackedRef(directory.resolve("packed-refs"), refName, fileReader) }
            .firstOrNull()
    }

    private fun resolvePackedRef(
        packedRefs: Path,
        refName: String,
        fileReader: (Path) -> String,
    ): String? =
        readText(packedRefs, fileReader)
            ?.lineSequence()
            ?.map(String::trim)
            ?.filterNot { it.isEmpty() || it.startsWith('#') || it.startsWith('^') }
            ?.mapNotNull { line ->
                val separator = line.indexOf(' ')
                if (separator <= 0) return@mapNotNull null
                val objectId = line.substring(0, separator)
                val candidateRef = line.substring(separator + 1).trim()
                objectId.takeIf { candidateRef == refName && isObjectId(it) }?.lowercase()
            }
            ?.firstOrNull()

    private fun extractRemoteUrl(config: String, branchName: String?): String? {
        val remotes = linkedMapOf<String, String>()
        val branchRemotes = mutableMapOf<String, String>()
        var section: ConfigSection? = null

        config.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith(';')) return@forEach

            sectionPattern.matchEntire(trimmed)?.let { match ->
                section = ConfigSection(
                    type = match.groupValues[1].lowercase(),
                    name = match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
                )
                return@forEach
            }

            val currentSection = section ?: return@forEach
            val valueMatch = valuePattern.matchEntire(trimmed) ?: return@forEach
            val key = valueMatch.groupValues[1].lowercase()
            val value = valueMatch.groupValues[2].trim().removeSurrounding("\"")
            when {
                currentSection.type == "remote" && key == "url" ->
                    remotes.putIfAbsent(currentSection.name, value)
                currentSection.type == "branch" && key == "remote" ->
                    branchRemotes.putIfAbsent(currentSection.name, value)
            }
        }

        branchName?.let { branchRemotes[it] }?.let { remoteName ->
            remotes[remoteName]?.let { return it }
        }
        remotes["origin"]?.let { return it }
        return remotes.values.singleOrNull()
    }

    private fun safeRelativePath(refName: String): Path? {
        val path = runCatching { Path.of(refName) }.getOrNull() ?: return null
        if (path.isAbsolute || path.any { it.toString() == ".." }) return null
        return path
    }

    private fun readText(path: Path, fileReader: (Path) -> String): String? =
        path.takeIf(Files::isRegularFile)?.let(fileReader)

    private fun isObjectId(value: String): Boolean = objectIdPattern.matches(value)

    private data class HeadResolution(val commitSha: String, val branchName: String?)
    private data class ConfigSection(val type: String, val name: String)
}
