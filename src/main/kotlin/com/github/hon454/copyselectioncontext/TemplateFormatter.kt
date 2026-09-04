package com.github.hon454.copyselectioncontext

private const val PATH_AND_RANGE_TEMPLATE = "{path}:{range}"
private const val CLAUDE_REFERENCE_TEMPLATE = " @{path}#L{range} "
private const val WITH_CODE_BLOCK_TEMPLATE = "{path}:{range}\n```{lang}\n{code}\n```"

enum class TemplatePreset(
    val key: String,
    val messageKey: String,
    val template: String?,
) {
    CUSTOM("custom", "settings.template.preset.custom", null),
    PATH_AND_RANGE("path-and-range", "settings.template.preset.path.and.range", PATH_AND_RANGE_TEMPLATE),
    CLAUDE_REFERENCE("claude-reference", "settings.template.preset.claude.reference", CLAUDE_REFERENCE_TEMPLATE),
    WITH_CODE_BLOCK("with-code-block", "settings.template.preset.with.code.block", WITH_CODE_BLOCK_TEMPLATE);

    override fun toString(): String = CopySelectionBundle.message(messageKey)
}

class TemplateFormatter(private val template: String) : OutputFormatter {
    override val key = "template"
    override val displayName: String
        get() = CopySelectionBundle.message("settings.format.template")

    override fun format(context: FormatContext): String = buildString {
        appendTo(context) { text, start, end -> append(text, start, end) }
    }

    /** Streams the same single-pass substitutions to a bounded collection sink. */
    internal fun appendTo(context: FormatContext, append: (String, Int, Int) -> Unit) {
        val replacements = mapOf(
            "path" to context.path.replace("\\", "/"),
            "line" to context.startLine.toString(),
            "range" to context.lineRange,
            "code" to (context.code ?: ""),
            "lang" to context.language,
            "filename" to context.filename,
        )
        var offset = 0
        for (match in VARIABLE_REGEX.findAll(template)) {
            append(template, offset, match.range.first)
            val replacement = replacements[match.groupValues[1]]
            if (replacement == null) append(template, match.range.first, match.range.last + 1)
            else append(replacement, 0, replacement.length)
            offset = match.range.last + 1
        }
        append(template, offset, template.length)
    }

    companion object {
        const val PRESET_PATH_AND_RANGE = PATH_AND_RANGE_TEMPLATE
        const val PRESET_CLAUDE_REFERENCE = CLAUDE_REFERENCE_TEMPLATE
        const val PRESET_WITH_CODE_BLOCK = WITH_CODE_BLOCK_TEMPLATE

        val PRESETS: List<TemplatePreset> = TemplatePreset.entries.filter { it.template != null }

        val VALID_VARIABLES = setOf("path", "line", "range", "code", "lang", "filename")

        fun findUnknownVariables(template: String): List<String> {
            return VARIABLE_REGEX.findAll(template)
                .map { it.groupValues[1] }
                .filter { it !in VALID_VARIABLES }
                .distinct()
                .toList()
        }

        private val VARIABLE_REGEX = Regex("""\{(\w+)\}""")
    }
}
