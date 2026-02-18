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
        const val PRESET_WITH_CODE_BLOCK = "{path}:{range}\\n```{lang}\\n{code}\\n```"
    }
}
