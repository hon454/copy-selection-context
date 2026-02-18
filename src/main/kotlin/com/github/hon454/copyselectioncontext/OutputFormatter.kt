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
        return if (context.code.isNullOrBlank()) {
            " @$normalizedPath#L${context.lineRange} "
        } else {
            val fence = buildFence(context.code)
            " @$normalizedPath#L${context.lineRange} \n$fence${context.language}\n${context.code}\n$fence"
        }
    }

    private fun buildFence(code: String): String {
        val maxBackticks = code.lines().maxOfOrNull { line ->
            line.takeWhile { it == '`' }.length
        } ?: 0
        return "`".repeat(maxOf(3, maxBackticks + 1))
    }
}

class PathLineFormatter : OutputFormatter {
    override val key = "pathline"
    override val displayName = "Path:Line (path:line)"
    override fun format(context: FormatContext): String {
        val normalizedPath = context.path.replace("\\", "/")
        return if (context.code.isNullOrBlank()) {
            "$normalizedPath:${context.lineRange}"
        } else {
            val fence = buildFence(context.code)
            "$normalizedPath:${context.lineRange}\n$fence${context.language}\n${context.code}\n$fence"
        }
    }

    private fun buildFence(code: String): String {
        val maxBackticks = code.lines().maxOfOrNull { line ->
            line.takeWhile { it == '`' }.length
        } ?: 0
        return "`".repeat(maxOf(3, maxBackticks + 1))
    }
}

class GitHubPermalinkTemplateFormatter : OutputFormatter {
    override val key = "github"
    override val displayName = "GitHub Permalink"

    override fun format(context: FormatContext): String {
        val normalizedPath = context.path.replace("\\", "/")
        val lineFragment = if (context.startLine == context.endLine) {
            "L${context.startLine}"
        } else {
            "L${context.startLine}-L${context.endLine}"
        }
        return "https://github.com/{owner}/{repo}/blob/{sha}/$normalizedPath#$lineFragment"
    }
}

object OutputFormatterFactory {
    private val formatters: Map<String, OutputFormatter> = listOf(
        ClaudeCodeFormatter(),
        PathLineFormatter(),
        GitHubPermalinkTemplateFormatter(),
        TemplateFormatter(TemplateFormatter.PRESET_PATH_AND_RANGE)
    ).associateBy { it.key }

    fun getFormatter(key: String): OutputFormatter = formatters[key] ?: ClaudeCodeFormatter()

    fun getFormatterForSettings(settings: CopySelectionSettings.State): OutputFormatter {
        return if (settings.outputFormat == "template" && settings.customFormatTemplate.isNotBlank()) {
            TemplateFormatter(settings.customFormatTemplate)
        } else {
            getFormatter(settings.outputFormat)
        }
    }

    fun getAvailableFormatters(): List<OutputFormatter> = formatters.values.toList()
}
