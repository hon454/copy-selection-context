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
    override val displayName: String
        get() = CopySelectionBundle.message("settings.format.claude")
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
    override val displayName: String
        get() = CopySelectionBundle.message("settings.format.pathline")
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

object OutputFormatterFactory {
    private val formatters: Map<String, OutputFormatter> = listOf(
        ClaudeCodeFormatter(),
        PathLineFormatter(),
        getTemplateFormatter("")
    ).associateBy { it.key }

    fun getFormatter(key: String): OutputFormatter = formatters[key] ?: ClaudeCodeFormatter()

    fun getFormatterForSettings(settings: CopySelectionSettings.State): OutputFormatter {
        return if (settings.outputFormat == "template") {
            getTemplateFormatter(settings.customFormatTemplate)
        } else {
            getFormatter(settings.outputFormat)
        }
    }

    internal fun getTemplateFormatter(template: String): TemplateFormatter =
        TemplateFormatter(template.ifBlank { TemplateFormatter.PRESET_PATH_AND_RANGE })

    fun getAvailableFormatters(): List<OutputFormatter> = formatters.values.toList()
}
