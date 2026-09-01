package com.github.hon454.copyselectioncontext

enum class OutputFormatOption(
    val key: String,
    val messageKey: String
) {
    CLAUDE("claude", "settings.format.claude"),
    PATH_LINE("pathline", "settings.format.pathline"),
    TEMPLATE("template", "settings.format.template");

    override fun toString(): String = CopySelectionBundle.message(messageKey)

    companion object {
        val default: OutputFormatOption = CLAUDE

        fun fromKey(key: String?): OutputFormatOption =
            entries.firstOrNull { it.key == key } ?: default
    }
}
