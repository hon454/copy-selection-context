package com.github.hon454.copyselectioncontext

import com.intellij.openapi.help.WebHelpProvider

class CopySelectionWebHelpProvider : WebHelpProvider() {
    companion object {
        const val HELP_TOPIC_MAIN = "com.github.hon454.copyselectioncontext.main"
        const val HELP_TOPIC_SETTINGS = "com.github.hon454.copyselectioncontext.settings"
        const val HELP_TOPIC_FORMATS = "com.github.hon454.copyselectioncontext.formats"
        private const val BASE_URL = "https://github.com/hon454/copy-selection-context"
    }

    override fun getHelpPageUrl(helpTopicId: String): String? {
        return when (helpTopicId) {
            HELP_TOPIC_MAIN -> "$BASE_URL#readme"
            HELP_TOPIC_SETTINGS -> "$BASE_URL#settings"
            HELP_TOPIC_FORMATS -> "$BASE_URL#output-format-claude-code-style"
            else -> null
        }
    }
}
