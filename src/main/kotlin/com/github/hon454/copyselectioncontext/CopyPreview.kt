package com.github.hon454.copyselectioncontext

import java.lang.Character.isISOControl
import java.lang.Character.isSpaceChar
import java.lang.Character.isWhitespace

internal object CopyPreview {
    const val NOTIFICATION_MAX_LENGTH = 120
    const val STATUS_MAX_LENGTH = 40
    const val TOOLTIP_MAX_LENGTH = 120
    const val HISTORY_MAX_LENGTH = 80

    private const val ELLIPSIS = "…"

    fun notification(content: String): String = create(content, NOTIFICATION_MAX_LENGTH)

    fun status(content: String, maxLength: Int = STATUS_MAX_LENGTH): String = create(content, maxLength)

    fun tooltip(content: String): String = create(content, TOOLTIP_MAX_LENGTH)

    fun history(content: String): String = create(content, HISTORY_MAX_LENGTH)

    internal fun create(content: String, maxLength: Int): String {
        require(maxLength > 0) { "maxLength must be positive" }

        val preview = StringBuilder(maxLength)
        val tokenLengths = ArrayDeque<Int>()
        var index = 0
        var pendingSpace = false

        while (index < content.length) {
            val codePoint = content.codePointAt(index)
            index += Character.charCount(codePoint)

            if (isPreviewWhitespace(codePoint)) {
                pendingSpace = preview.isNotEmpty()
                continue
            }

            if (pendingSpace) {
                if (!appendToken(preview, tokenLengths, " ", maxLength)) {
                    return appendEllipsis(preview, tokenLengths, maxLength)
                }
                pendingSpace = false
            }

            val token = escapeMarkup(codePoint)
            if (!appendToken(preview, tokenLengths, token, maxLength)) {
                return appendEllipsis(preview, tokenLengths, maxLength)
            }
        }

        return preview.toString()
    }

    private fun isPreviewWhitespace(codePoint: Int): Boolean =
        isWhitespace(codePoint) || isSpaceChar(codePoint) || isISOControl(codePoint)

    private fun escapeMarkup(codePoint: Int): String = when (codePoint) {
        '&'.code -> "&amp;"
        '<'.code -> "&lt;"
        '>'.code -> "&gt;"
        '"'.code -> "&quot;"
        '\''.code -> "&#39;"
        else -> String(Character.toChars(codePoint))
    }

    private fun appendToken(
        preview: StringBuilder,
        tokenLengths: ArrayDeque<Int>,
        token: String,
        maxLength: Int
    ): Boolean {
        if (preview.length + token.length > maxLength) return false
        preview.append(token)
        tokenLengths.addLast(token.length)
        return true
    }

    private fun appendEllipsis(
        preview: StringBuilder,
        tokenLengths: ArrayDeque<Int>,
        maxLength: Int
    ): String {
        while (preview.length + ELLIPSIS.length > maxLength && tokenLengths.isNotEmpty()) {
            preview.setLength(preview.length - tokenLengths.removeLast())
        }
        return preview.append(ELLIPSIS).toString()
    }
}
