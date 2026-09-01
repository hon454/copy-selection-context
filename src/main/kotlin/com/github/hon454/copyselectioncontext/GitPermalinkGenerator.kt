package com.github.hon454.copyselectioncontext

import java.net.URI

object GitPermalinkGenerator {

    fun parseRemoteUrl(remoteUrl: String): Pair<String, String>? {
        val normalizedRemoteUrl = remoteUrl.trim()

        val sshPattern = Regex("""git@(github\.com|gitlab\.com):(.+)$""", RegexOption.IGNORE_CASE)
        sshPattern.matchEntire(normalizedRemoteUrl)?.let { match ->
            return canonicalRepository(match.groupValues[1], match.groupValues[2])
        }

        val uri = runCatching { URI(normalizedRemoteUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https", "ssh", "git", "git+ssh")) return null
        val remoteHost = uri.host?.lowercase() ?: return null
        if (scheme == "ssh" || scheme == "git+ssh") {
            if (uri.userInfo != "git") return null
        }
        val host = when {
            remoteHost == "github.com" || remoteHost == "gitlab.com" -> remoteHost
            remoteHost == "ssh.github.com" && scheme == "ssh" && uri.port == 443 -> "github.com"
            else -> return null
        }

        return canonicalRepository(host, uri.path)
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

    fun buildPermalinkFromRemote(
        remoteUrl: String,
        sha: String,
        filePath: String,
        startLine: Int,
        endLine: Int
    ): String? {
        val (repoUrl, host) = parseRemoteUrl(remoteUrl) ?: return null
        return buildPermalink(repoUrl, host, sha, filePath, startLine, endLine)
    }

    private fun canonicalRepository(host: String, rawPath: String): Pair<String, String>? {
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

        return Pair("https://$normalizedHost/$repositoryPath", normalizedHost)
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
