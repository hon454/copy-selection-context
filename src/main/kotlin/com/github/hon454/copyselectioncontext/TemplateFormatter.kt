package com.github.hon454.copyselectioncontext

class TemplateFormatter(private val template: String) : OutputFormatter {
    override val key = "template"
    override val displayName = "Custom Template"

    override fun format(context: FormatContext): String {
        return template
            .replace("{path}", context.path.replace("\\", "/"))
            .replace("{line}", context.startLine.toString())
            .replace("{range}", context.lineRange)
            .replace("{code}", context.code ?: "")
            .replace("{lang}", context.language)
            .replace("{filename}", context.filename)
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
            val regex = Regex("""\{(\w+)\}""")
            return regex.findAll(template)
                .map { it.groupValues[1] }
                .filter { it !in VALID_VARIABLES }
                .distinct()
                .toList()
        }
    }
}
