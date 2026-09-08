package com.github.hon454.copyselectioncontext

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.font.TextHitInfo
import java.awt.geom.Rectangle2D
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicTextAreaUI
import javax.swing.text.BadLocationException
import javax.swing.text.Element
import javax.swing.text.Position
import javax.swing.text.View

/** The same native component is used by the plugin, fixture tests and the standalone profiler. */
internal class ContextCollectionTextArea : JTextArea() {
    override fun updateUI() {
        setUI(object : BasicTextAreaUI() {
            override fun create(element: Element): View = ContextCollectionTextView(element)
        })
        // Selection is painted from logical ranges, including disjoint bidi regions.
        highlighter = null
    }
}

/** A single Swing view with indexed geometry, rather than one Swing child per line or cell. */
internal class ContextCollectionTextView(element: Element) : View(element) {
    private val geometry: ContextCollectionTextLayout?
        get() = document.getProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY) as? ContextCollectionTextLayout
    private val area: JTextArea get() = container as JTextArea

    override fun getPreferredSpan(axis: Int): Float = when (axis) {
        X_AXIS -> (geometry?.width ?: 0f) + 1f
        Y_AXIS -> geometry?.height ?: 1f
        else -> throw IllegalArgumentException("Invalid axis")
    }

    override fun paint(graphics: Graphics, allocation: Shape) {
        val model = geometry ?: return
        val origin = allocation.bounds
        val clip = graphics.clipBounds ?: origin
        val first = ((clip.y - origin.y) / model.lineHeight).coerceAtLeast(0)
        val last = ((clip.y + clip.height - origin.y) / model.lineHeight).coerceAtMost(model.lines.lastIndex)
        val selectionStart = area.selectionStart
        val selectionEnd = area.selectionEnd
        val selected = selectionStart < selectionEnd && area.caret.isSelectionVisible
        val g = graphics.create() as Graphics2D
        try {
            for (row in first..last) {
                val line = model.lines[row]
                val top = origin.y + row * model.lineHeight
                val baseline = top + model.ascent
                line.visitVisible((clip.x - origin.x).toFloat(), (clip.x + clip.width - origin.x).toFloat()) { cell ->
                    val x = origin.x + cell.x
                    val from = maxOf(selectionStart, cell.start)
                    val to = minOf(selectionEnd, cell.end)
                    val highlight = if (selected && from < to) {
                        cell.highlight(from - cell.start, to - cell.start)?.let {
                            java.awt.geom.AffineTransform.getTranslateInstance(x.toDouble(), baseline.toDouble()).createTransformedShape(it)
                        } ?: Rectangle2D.Float(x, top.toFloat(), cell.width, model.lineHeight.toFloat())
                    } else null
                    if (highlight != null) {
                        g.color = area.selectionColor
                        g.fill(highlight)
                    }
                    g.color = area.foreground
                    cell.draw(g, x, baseline)
                    if (highlight != null && !cell.isTab) {
                        val oldClip = g.clip
                        g.clip(highlight)
                        g.color = area.selectedTextColor
                        cell.draw(g, x, baseline)
                        g.clip = oldClip
                    }
                }
                // A selected newline still has visible feedback, without adding a character to the document.
                if (selected && line.end < document.length && selectionStart <= line.end && selectionEnd > line.end) {
                    g.color = area.selectionColor
                    g.fill(Rectangle2D.Float(origin.x + line.width, top.toFloat(), 2f, model.lineHeight.toFloat()))
                }
            }
        } finally {
            g.dispose()
        }
    }

    override fun modelToView(position: Int, allocation: Shape, bias: Position.Bias): Shape {
        if (position < 0 || position > document.length) throw BadLocationException("Invalid position", position)
        val origin = allocation.bounds
        val model = geometry ?: return Rectangle2D.Float(origin.x.toFloat(), origin.y.toFloat(), 1f, 1f)
        val row = model.lineAtOffset(position)
        val x = model.lines[row].caretX(position, bias == Position.Bias.Backward)
        return Rectangle2D.Float(origin.x + x, (origin.y + row * model.lineHeight).toFloat(), 1f, model.lineHeight.toFloat())
    }

    override fun viewToModel(x: Float, y: Float, allocation: Shape, bias: Array<Position.Bias>): Int {
        bias[0] = Position.Bias.Forward
        val model = geometry ?: return 0
        val origin = allocation.bounds
        val row = ((y - origin.y) / model.lineHeight).toInt().coerceIn(model.lines.indices)
        val line = model.lines[row]
        if (x <= origin.x || x >= origin.x + line.width) {
            val atStart = (x <= origin.x) == line.leftToRight
            bias[0] = if (atStart) Position.Bias.Backward else Position.Bias.Forward
            return if (atStart) line.start else line.end
        }
        val cell = line.cellAtX(x - origin.x) ?: return line.start
        if (cell.isTab) {
            val left = x - origin.x - cell.x < cell.width / 2
            return if (left != cell.rtl) cell.start else cell.end
        }
        val hit = cell.realHit(requireNotNull(cell.hit(x - origin.x - cell.x, y - origin.y - row * model.lineHeight - model.ascent)))
        bias[0] = if (hit.isLeadingEdge) Position.Bias.Forward else Position.Bias.Backward
        return cell.start + hit.insertionIndex
    }

    override fun getNextVisualPositionFrom(position: Int, bias: Position.Bias, allocation: Shape, direction: Int,
        biasRet: Array<Position.Bias>): Int {
        val model = geometry ?: return 0
        if (position < 0) {
            biasRet[0] = Position.Bias.Forward
            return if (direction == SwingConstants.WEST || direction == SwingConstants.NORTH) document.length else 0
        }
        val row = model.lineAtOffset(position)
        if (direction == SwingConstants.NORTH || direction == SwingConstants.SOUTH) {
            val bounds = modelToView(position, allocation, bias).bounds2D
            val x = area.caret.magicCaretPosition?.x?.toFloat() ?: bounds.x.toFloat()
            val nextRow = row + if (direction == SwingConstants.NORTH) -1 else 1
            if (nextRow !in model.lines.indices) return position.also { biasRet[0] = bias }
            return viewToModel(x, allocation.bounds.y + (nextRow + 0.5f) * model.lineHeight, allocation, biasRet)
        }
        require(direction == SwingConstants.EAST || direction == SwingConstants.WEST)
        val right = direction == SwingConstants.EAST
        val line = model.lines[row]
        val located = line.locate(position, bias == Position.Bias.Backward)
        val cell = located?.first
        val hit = located?.let { it.first.next(it.second, right) }
        if (hit != null && cell != null) return hitPosition(cell, cell.realHit(hit), biasRet)
        if (cell != null && cell.isTab && (((right != cell.rtl) && position < cell.end) || ((right == cell.rtl) && position > cell.start))) {
            biasRet[0] = Position.Bias.Forward
            return if (right != cell.rtl) cell.end else cell.start
        }
        val adjacent = cell?.let { line.visual.getOrNull(it.visualIndex + if (right) 1 else -1) }
        if (adjacent != null) return visualEdge(adjacent, right, biasRet)
        val next = model.lines.getOrNull(row + if (right) 1 else -1)
            ?: return position.also { biasRet[0] = bias }
        return (if (right) next.visual.firstOrNull() else next.visual.lastOrNull())?.let {
            visualEdge(it, right, biasRet)
        } ?: next.start.also { biasRet[0] = Position.Bias.Forward }
    }

    private fun visualEdge(cell: ContextCollectionTextLayout.Cell, left: Boolean, bias: Array<Position.Bias>): Int {
        return hitPosition(cell, cell.edge(left), bias)
    }

    private fun hitPosition(cell: ContextCollectionTextLayout.Cell, hit: TextHitInfo, bias: Array<Position.Bias>): Int {
        bias[0] = if (hit.isLeadingEdge) Position.Bias.Forward else Position.Bias.Backward
        return cell.start + hit.insertionIndex
    }

}
