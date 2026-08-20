package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key

internal object CopySelectionHighlighter {
    private val lastHighlighterKey = Key.create<RangeHighlighter>(
        "CopySelectionContext.lastHighlighter"
    )

    fun update(editor: Editor, startLine: Int, endLine: Int) {
        val markupModel = editor.markupModel
        editor.getUserData(lastHighlighterKey)
            ?.takeIf { it.isValid }
            ?.let(markupModel::removeHighlighter)

        val document = editor.document
        val startOffset = document.getLineStartOffset(startLine - 1)
        val endOffset = document.getLineEndOffset(endLine - 1)
        val highlighter = markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.LINES_IN_RANGE,
        ).also { it.gutterIconRenderer = CopySelectionGutterIconRenderer() }

        editor.putUserData(lastHighlighterKey, highlighter)
    }
}
