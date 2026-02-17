package com.github.hon454.copyselectioncontext

data class FormatContext(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val code: String? = null,
    val language: String = "",
    val filename: String = ""
) {
    val lineRange: String get() = if (startLine == endLine) "$startLine" else "$startLine-$endLine"
}

interface OutputFormatter {
    val key: String
    val displayName: String
    fun format(context: FormatContext): String
}

class ClaudeCodeFormatter : OutputFormatter {
    override val key = "claude"
    override val displayName = "Claude Code (@path#L)"
    override fun format(context: FormatContext): String {
        val normalizedPath = context.path.replace("\\", "/")
        return if (context.code == null) {
            " @$normalizedPath#L${context.lineRange} "
        } else {
            " @$normalizedPath#L${context.lineRange} \n```${context.language}\n${context.code}\n```"
        }
    }
}

class PathLineFormatter : OutputFormatter {
    override val key = "pathline"
    override val displayName = "Path:Line (path:line)"
    override fun format(context: FormatContext): String {
        val normalizedPath = context.path.replace("\\", "/")
        return if (context.code == null) {
            "$normalizedPath:${context.lineRange}"
        } else {
            "$normalizedPath:${context.lineRange}\n```${context.language}\n${context.code}\n```"
        }
    }
}

object OutputFormatterFactory {
    private val formatters: Map<String, OutputFormatter> = listOf(
        ClaudeCodeFormatter(),
        PathLineFormatter()
    ).associateBy { it.key }

    fun getFormatter(key: String): OutputFormatter = formatters[key] ?: ClaudeCodeFormatter()
    fun getAvailableFormatters(): List<OutputFormatter> = formatters.values.toList()
}
