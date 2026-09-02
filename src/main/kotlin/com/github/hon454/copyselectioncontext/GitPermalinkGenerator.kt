package com.github.hon454.copyselectioncontext

import java.net.URI

object GitPermalinkGenerator {

    internal fun parseRemoteUrl(remoteUrl: String): GitPermalinkResult<GitRemoteRepository> {
        val normalizedRemoteUrl = remoteUrl.trim()

        val sshPattern = Regex("""git@(github\.com|gitlab\.com):(.+)$""", RegexOption.IGNORE_CASE)
        sshPattern.matchEntire(normalizedRemoteUrl)?.let { match ->
            return canonicalRepository(match.groupValues[1], match.groupValues[2])
                ?.let { GitPermalinkResult.Success(it) }
                ?: unsupportedRemote(normalizedRemoteUrl)
        }

        val uri = runCatching { URI(normalizedRemoteUrl) }.getOrNull()
            ?: return unsupportedRemote(normalizedRemoteUrl)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https", "ssh", "git", "git+ssh")) {
            return unsupportedRemote(normalizedRemoteUrl)
        }
        val remoteHost = uri.host?.lowercase() ?: return unsupportedRemote(normalizedRemoteUrl)
        if (scheme == "ssh" || scheme == "git+ssh") {
            if (uri.userInfo != "git") return unsupportedRemote(normalizedRemoteUrl)
        }
        val host = when {
            remoteHost == "github.com" || remoteHost == "gitlab.com" -> remoteHost
            remoteHost == "ssh.github.com" && scheme == "ssh" && uri.port == 443 -> "github.com"
            else -> return unsupportedRemote(normalizedRemoteUrl)
        }

        return canonicalRepository(host, uri.path)
            ?.let { GitPermalinkResult.Success(it) }
            ?: unsupportedRemote(normalizedRemoteUrl)
    }

    fun buildPermalink(
        repoUrl: String,
        host: String,
        sha: String,
        filePath: String,
        startLine: Int,
        endLine: Int
    ): String {
        val normalizedHost = host.lowercase()
        val lineFragment = when {
            startLine == endLine -> "L$startLine"
            normalizedHost == "github.com" || normalizedHost == "gitlab.com" -> "L$startLine-L$endLine"
            else -> "L$startLine-L$endLine"
        }
        return "$repoUrl/blob/$sha/${encodeFilePath(filePath)}#$lineFragment"
    }

    internal fun buildPermalinkFromRemote(
        remoteUrl: String,
        sha: String,
        filePath: String,
        startLine: Int,
        endLine: Int
    ): GitPermalinkResult<String> = when (val remote = parseRemoteUrl(remoteUrl)) {
        is GitPermalinkResult.Failure -> remote
        is GitPermalinkResult.Success -> GitPermalinkResult.Success(
            buildPermalink(
                remote.value.repositoryUrl,
                remote.value.host,
                sha,
                filePath,
                startLine,
                endLine,
            ),
        )
    }

    private fun canonicalRepository(host: String, rawPath: String): GitRemoteRepository? {
        val normalizedHost = host.lowercase()
        if (rawPath != rawPath.trim()) return null
        val repositoryPath = rawPath.removePrefix("/").removeSuffix(".git")
        val segments = repositoryPath.split('/')
        if (repositoryPath.contains('\\') ||
            segments.any { it.isEmpty() || it == "." || it == ".." }
        ) return null
        val validNamespace = when (normalizedHost) {
            "github.com" -> segments.size == 2
            "gitlab.com" -> segments.size >= 2
            else -> false
        }
        if (!validNamespace) return null

        return GitRemoteRepository("https://$normalizedHost/$repositoryPath", normalizedHost)
    }

    private fun unsupportedRemote(remoteUrl: String): GitPermalinkResult.Failure =
        GitPermalinkResult.Failure(
            reason = GitPermalinkFailureReason.UNSUPPORTED_REMOTE_HOST,
            diagnostic = GitPermalinkDiagnostic(
                operation = GitPermalinkOperation.PARSE_REMOTE,
                remoteHost = safeRemoteHost(remoteUrl),
            ),
        )

    private fun safeRemoteHost(remoteUrl: String): String? {
        val normalizedRemoteUrl = remoteUrl.trim()
        val scpHost = Regex("""^[^@\s]+@([A-Za-z0-9.-]+):""")
            .find(normalizedRemoteUrl)
            ?.groupValues
            ?.get(1)
        if (scpHost != null) return scpHost.lowercase()
        return runCatching { URI(normalizedRemoteUrl).host }
            .getOrNull()
            ?.lowercase()
    }

    private fun encodeFilePath(filePath: String): String = filePath
        .replace('\\', '/')
        .trimStart('/')
        .split('/')
        .joinToString("/") { segment -> encodePathSegment(segment) }

    private fun encodePathSegment(segment: String): String = buildString {
        segment.toByteArray(Charsets.UTF_8).forEach { byte ->
            val value = byte.toInt() and 0xff
            val isUnreserved = value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code || value == '.'.code || value == '_'.code || value == '~'.code
            if (isUnreserved) {
                append(value.toChar())
            } else {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

internal data class GitRemoteRepository(
    val repositoryUrl: String,
    val host: String,
)
