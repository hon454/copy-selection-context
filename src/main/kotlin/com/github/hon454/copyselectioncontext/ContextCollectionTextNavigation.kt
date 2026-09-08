package com.github.hon454.copyselectioncontext

import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultEditorKit
import javax.swing.text.Position
import javax.swing.text.TextAction

/** Native row actions call Utilities.getRowStart/End, which visit every UTF-16 offset on EDT. */
internal fun installCollectionLineNavigation(area: JTextArea) {
    fun install(name: String, end: Boolean, select: Boolean, vertical: Boolean = false) {
        area.actionMap.put(name, object : TextAction(name) {
            override fun actionPerformed(event: ActionEvent) {
                val target = getTextComponent(event) ?: return
                val model = target.document.getProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY) as? ContextCollectionTextLayout ?: return
                val row = model.lines[model.lineAtOffset(target.caretPosition)]
                val boundary = if (end) row.end else row.start
                if (vertical && target.caretPosition == boundary) {
                    val caret = target.caret
                    val bidiCaret = caret as? DefaultCaret
                    val bias = bidiCaret?.dotBias ?: Position.Bias.Forward
                    val bounds = target.ui.modelToView2D(target, caret.dot, bias)
                    val magic = caret.magicCaretPosition ?: Point(bounds.x.toInt(), bounds.y.toInt())
                    val returnedBias = arrayOf(Position.Bias.Forward)
                    val direction = if (end) SwingConstants.SOUTH else SwingConstants.NORTH
                    val next = target.navigationFilter?.getNextVisualPositionFrom(target, caret.dot, bias, direction, returnedBias)
                        ?: target.ui.getNextVisualPositionFrom(target, caret.dot, bias, direction, returnedBias)
                    if (bidiCaret != null) {
                        if (select) bidiCaret.moveDot(next, returnedBias[0]) else bidiCaret.setDot(next, returnedBias[0])
                    } else if (select) caret.moveDot(next) else caret.setDot(next)
                    caret.magicCaretPosition = magic
                    if (!end) return
                }
                val current = model.lines[model.lineAtOffset(target.caretPosition)]
                val offset = if (end) current.end else current.start
                if (select) target.moveCaretPosition(offset) else target.caretPosition = offset
            }
        })
    }
    install(DefaultEditorKit.beginLineAction, end = false, select = false)
    install(DefaultEditorKit.endLineAction, end = true, select = false)
    install(DefaultEditorKit.selectionBeginLineAction, end = false, select = true)
    install(DefaultEditorKit.selectionEndLineAction, end = true, select = true)
    install(DefaultEditorKit.beginLineUpAction, end = false, select = false, vertical = true)
    install(DefaultEditorKit.endLineDownAction, end = true, select = false, vertical = true)
    // Some LAFs expose selection variants of the up/down row actions.
    if (area.actionMap.get("selection-begin-line-up") != null) install("selection-begin-line-up", end = false, select = true, vertical = true)
    if (area.actionMap.get("selection-end-line-down") != null) install("selection-end-line-down", end = true, select = true, vertical = true)
    area.actionMap.put(DefaultEditorKit.selectLineAction, object : TextAction(DefaultEditorKit.selectLineAction) {
        override fun actionPerformed(event: ActionEvent) {
            val target = getTextComponent(event) ?: return
            val model = target.document.getProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY) as? ContextCollectionTextLayout ?: return
            val row = model.lines[model.lineAtOffset(target.caretPosition)]
            target.select(row.start, row.end)
        }
    })
}
