package com.github.hon454.copyselectioncontext

object GitPermalinkGenerator {

    fun parseRemoteUrl(remoteUrl: String): Pair<String, String>? {
        val normalizedRemoteUrl = remoteUrl.trim()

        val sshPattern = Regex("""git@(github\.com|gitlab\.com):([^/]+)/(.+?)(?:\.git)?$""")
        sshPattern.matchEntire(normalizedRemoteUrl)?.let { match ->
            val host = match.groupValues[1]
            val owner = match.groupValues[2]
            val repo = match.groupValues[3]
            return Pair("https://$host/$owner/$repo", host)
        }

        val httpsPattern = Regex("""https?://(github\.com|gitlab\.com)/([^/]+)/(.+?)(?:\.git)?$""")
        httpsPattern.matchEntire(normalizedRemoteUrl)?.let { match ->
            val host = match.groupValues[1]
            val owner = match.groupValues[2]
            val repo = match.groupValues[3]
            return Pair("https://$host/$owner/$repo", host)
        }

        return null
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
        return "$repoUrl/blob/$sha/$filePath#$lineFragment"
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
}
