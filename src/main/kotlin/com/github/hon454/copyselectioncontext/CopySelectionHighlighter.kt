package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key

internal object CopySelectionHighlighter {
    private val lastHighlightersKey = Key.create<List<RangeHighlighter>>(
        "CopySelectionContext.lastHighlighters"
    )

    fun update(editor: Editor, startLine: Int, endLine: Int) {
        update(editor, listOf(Pair(startLine, endLine)))
    }

    fun update(editor: Editor, lineRanges: List<Pair<Int, Int>>) {
        val markupModel = editor.markupModel
        editor.getUserData(lastHighlightersKey)
            .orEmpty()
            .filter { it.isValid }
            .forEach(markupModel::removeHighlighter)

        val document = editor.document
        val highlighters = lineRanges.map { (startLine, endLine) ->
            val startOffset = document.getLineStartOffset(startLine - 1)
            val endOffset = document.getLineEndOffset(endLine - 1)
            markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.ADDITIONAL_SYNTAX,
                null,
                HighlighterTargetArea.LINES_IN_RANGE,
            ).also { it.gutterIconRenderer = CopySelectionGutterIconRenderer() }
        }

        editor.putUserData(lastHighlightersKey, highlighters)
    }
}
