package com.github.hon454.copyselectioncontext

import java.lang.Character.isISOControl
import java.lang.Character.isSpaceChar
import java.lang.Character.isWhitespace
import java.text.BreakIterator
import java.util.Locale

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
        val graphemeIterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply {
            setText(content)
        }
        var graphemeStart = graphemeIterator.first()
        var graphemeEnd = graphemeIterator.next()
        var pendingSpace = false

        while (graphemeEnd != BreakIterator.DONE) {
            val grapheme = content.substring(graphemeStart, graphemeEnd)

            if (grapheme.isPreviewWhitespace()) {
                pendingSpace = preview.isNotEmpty()
                graphemeStart = graphemeEnd
                graphemeEnd = graphemeIterator.next()
                continue
            }

            if (pendingSpace) {
                if (!appendToken(preview, tokenLengths, " ", maxLength)) {
                    return appendEllipsis(preview, tokenLengths, maxLength)
                }
                pendingSpace = false
            }

            val token = escapeMarkup(grapheme)
            if (!appendToken(preview, tokenLengths, token, maxLength)) {
                return appendEllipsis(preview, tokenLengths, maxLength)
            }

            graphemeStart = graphemeEnd
            graphemeEnd = graphemeIterator.next()
        }

        return preview.toString()
    }

    private fun String.isPreviewWhitespace(): Boolean {
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (!isWhitespace(codePoint) && !isSpaceChar(codePoint) && !isISOControl(codePoint)) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }

    private fun escapeMarkup(text: String): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            append(escapeMarkup(codePoint))
            index += Character.charCount(codePoint)
        }
    }

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
