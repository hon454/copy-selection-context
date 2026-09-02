package com.github.hon454.copyselectioncontext

class TemplateFormatter(private val template: String) : OutputFormatter {
    override val key = "template"
    override val displayName = "Custom Template"

    override fun format(context: FormatContext): String {
        val replacements = mapOf(
            "path" to context.path.replace("\\", "/"),
            "line" to context.startLine.toString(),
            "range" to context.lineRange,
            "code" to (context.code ?: ""),
            "lang" to context.language,
            "filename" to context.filename,
        )
        return VARIABLE_REGEX.replace(template) { match ->
            replacements[match.groupValues[1]] ?: match.value
        }
    }

    companion object {
        const val PRESET_PATH_AND_RANGE = "{path}:{range}"
        const val PRESET_CLAUDE_REFERENCE = " @{path}#L{range} "
        const val PRESET_WITH_CODE_BLOCK = "{path}:{range}\n```{lang}\n{code}\n```"

        val PRESETS: List<Pair<String, String>> = listOf(
            "Path and Range" to PRESET_PATH_AND_RANGE,
            "Claude Reference" to PRESET_CLAUDE_REFERENCE,
            "With Code Block" to PRESET_WITH_CODE_BLOCK
        )

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
