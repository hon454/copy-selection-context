package com.github.hon454.copyselectioncontext

enum class GitPermalinkFailureReason {
    MISSING_VCS_ROOT,
    UNRESOLVED_GIT_METADATA,
    GIT_CONFIG_INCLUDE_IO_FAILURE,
    GIT_CONFIG_INCLUDE_CYCLE,
    GIT_CONFIG_INCLUDE_DEPTH_EXCEEDED,
    UNSUPPORTED_REMOTE_HOST,
    OUT_OF_ROOT_FILE,
    IO_FAILURE,
    UNEXPECTED_FAILURE,
}

enum class GitPermalinkOperation {
    LOCATE_VCS_ROOT,
    RESOLVE_GIT_METADATA,
    EXPAND_GIT_CONFIG_INCLUDES,
    PARSE_REMOTE,
    RELATIVIZE_FILE,
    BUILD_PERMALINK,
}

data class GitPermalinkDiagnostic(
    val operation: GitPermalinkOperation,
    val remoteHost: String? = null,
    val exceptionType: String? = null,
)

sealed interface GitPermalinkResult<out T> {
    data class Success<T>(val value: T) : GitPermalinkResult<T>

    data class Failure(
        val reason: GitPermalinkFailureReason,
        val diagnostic: GitPermalinkDiagnostic,
    ) : GitPermalinkResult<Nothing>
}

internal fun GitPermalinkResult.Failure.safeLogMessage(): String = buildString {
    append("Git permalink failed [reason=")
    append(reason.name.lowercase())
    append(", operation=")
    append(diagnostic.operation.name.lowercase())
    diagnostic.remoteHost
        ?.takeIf { SAFE_HOST_PATTERN.matches(it) }
        ?.let {
            append(", remoteHost=")
            append(it.lowercase())
        }
    diagnostic.exceptionType
        ?.takeIf { SAFE_EXCEPTION_PATTERN.matches(it) }
        ?.let {
            append(", exceptionType=")
            append(it)
        }
    append(']')
}

private val SAFE_HOST_PATTERN = Regex("^[A-Za-z0-9.-]+$")
private val SAFE_EXCEPTION_PATTERN = Regex("^[A-Za-z0-9_.$]+$")
