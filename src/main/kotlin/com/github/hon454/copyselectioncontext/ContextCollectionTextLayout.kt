package com.github.hon454.copyselectioncontext

import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextHitInfo
import java.awt.font.TextLayout
import java.awt.font.TextMeasurer
import java.text.AttributedString
import java.text.Bidi
import kotlin.math.ceil
import kotlin.math.max

/**
 * Detached, immutable geometry. All text analysis and shaping happens before publication to Swing.
 * The original document is never wrapped, split or rewritten; cells only partition its geometry.
 */
internal class ContextCollectionTextLayout private constructor(
    val lines: List<Line>,
    val width: Float,
    val lineHeight: Int,
    val ascent: Float,
) {
    val height: Float = lines.size.toFloat() * lineHeight

    class Cell(val start: Int, val end: Int, val layout: TextLayout?, val rtl: Boolean, val raster: ContextCollectionTextRaster? = null) {
        // Assigned only by prepare(), before the model is published.
        var x: Float = 0f
            internal set
        var width: Float = layout?.advance ?: raster?.advance ?: 0f
            internal set
        var visualIndex: Int = 0
            internal set
        var leadingBoundary: Float? = null
            internal set
        var trailingBoundary: Float? = null
            internal set
        val leftOverhang: Float = layout?.bounds?.let { max(0f, -it.x.toFloat()) } ?: raster?.leftOverhang ?: 0f
        val rightOverhang: Float = layout?.bounds?.let { max(0f, it.maxX.toFloat() - width) } ?: raster?.rightOverhang ?: 0f
        val isTab: Boolean get() = layout == null && raster == null
        fun caretX(hit: TextHitInfo): Float {
            if (hit.isLeadingEdge && hit.insertionIndex == 0) leadingBoundary?.let { return it }
            if (!hit.isLeadingEdge && hit.insertionIndex == end - start) trailingBoundary?.let { return it }
            return if (layout != null) caretX(layout, hit) else raster?.caretX(hit) ?: 0f
        }
        fun hit(x: Float, y: Float): TextHitInfo? = layout?.hitTestChar(x, y) ?: raster?.hit(x, y)
        fun next(hit: TextHitInfo, right: Boolean): TextHitInfo? = if (layout != null) {
            if (right) layout.getNextRightHit(hit) else layout.getNextLeftHit(hit)
        } else raster?.next(hit, right)
        fun highlight(from: Int, to: Int): Shape? = layout?.getLogicalHighlightShape(from, to) ?: raster?.highlight(from, to)
        fun draw(graphics: Graphics2D, x: Float, y: Float) {
            if (layout != null) layout.draw(graphics, x, y) else raster?.draw(graphics, x, y)
        }
        fun realHit(hit: TextHitInfo): TextHitInfo =
            if (layout != null && hit.charIndex !in 0 until layout.characterCount) layout.getVisualOtherHit(hit) else hit
        fun edge(left: Boolean): TextHitInfo = if (isTab) {
            if (left != rtl) TextHitInfo.leading(0) else TextHitInfo.trailing(0)
        } else realHit(requireNotNull(hit(if (left) -1f else width + 1f, 0f)))
    }

    class Line(val start: Int, val end: Int, val logical: List<Cell>, val visual: List<Cell>, val width: Float, val leftToRight: Boolean) {
        // Zero-width bidi controls do not make painting traverse an invisible, unbounded run.
        private val drawable = visual.filter { it.width > 0f || it.leftOverhang > 0f || it.rightOverhang > 0f }
        private val leftOverhang = visual.maxOfOrNull { it.leftOverhang } ?: 0f
        private val rightOverhang = visual.maxOfOrNull { it.rightOverhang } ?: 0f
        private val forwardMaxima = FloatArray(visual.size).also { maxima ->
            var maximum = Float.NEGATIVE_INFINITY
            for ((index, cell) in visual.withIndex()) {
                val last = cell.x + if (cell.isTab) 0f else cell.caretX(TextHitInfo.leading(if (cell.rtl) 0 else cell.end - cell.start - 1))
                maximum = max(maximum, last)
                maxima[index] = maximum
            }
        }

        /** ParagraphView's vertical motion chooses the first forward caret at or beyond x. */
        fun forwardPositionAt(x: Float): Int {
            val index = upperBound(visual.size) { forwardMaxima[it] < x }
            val cell = visual.getOrNull(index) ?: return end
            if (cell.isTab) return cell.start
            val length = cell.end - cell.start
            fun offset(visualOffset: Int) = if (cell.rtl) length - visualOffset - 1 else visualOffset
            fun caret(visualOffset: Int) = cell.x + cell.caretX(TextHitInfo.leading(offset(visualOffset)))
            val first = upperBound(length) { caret(it) < x }.coerceAtMost(length - 1)
            val at = caret(first)
            val last = upperBound(length) { caret(it) <= at }.minus(1).coerceAtLeast(first)
            return cell.start + offset(last)
        }

        fun cellAtOffset(offset: Int, backward: Boolean = false): Cell? {
            if (logical.isEmpty()) return null
            val position = if (backward && offset > start) offset - 1 else offset
            return logical[upperBound(logical.size) { logical[it].start <= position }.minus(1).coerceAtLeast(0)]
        }

        fun cellAtX(x: Float): Cell? {
            if (drawable.isEmpty()) return visual.firstOrNull()
            return drawable[upperBound(drawable.size) { drawable[it].x <= x }.minus(1).coerceAtLeast(0)]
        }

        fun caretX(offset: Int, backward: Boolean): Float {
            if (offset == start && backward) return if (leftToRight) 0f else width
            if (offset == end && !backward) return if (leftToRight) width else 0f
            val cell = cellAtOffset(offset, backward) ?: return 0f
            if (cell.isTab) return cell.x + if ((offset <= cell.start) != cell.rtl) 0f else cell.width
            val local = (offset - cell.start).coerceIn(0, cell.end - cell.start)
            val hit = if (backward) TextHitInfo.trailing(local - 1) else TextHitInfo.leading(local)
            return cell.x + cell.caretX(hit)
        }

        /** Map paragraph exterior strong carets to the correct physical edge, not a logical end cell. */
        fun locate(offset: Int, backward: Boolean): Pair<Cell, TextHitInfo>? {
            val exterior = (offset == start && backward) || (offset == end && !backward)
            if (exterior) {
                val left = (offset == start && backward) == leftToRight
                val cell = (if (left) visual.firstOrNull() else visual.lastOrNull()) ?: return null
                return cell to cell.edge(left)
            }
            val cell = cellAtOffset(offset, backward) ?: return null
            val local = (offset - cell.start).coerceIn(0, cell.end - cell.start)
            return cell to if (backward) TextHitInfo.trailing(local - 1) else TextHitInfo.leading(local)
        }

        /** Binary search followed only by cells intersecting the viewport; no hidden prefix scan. */
        fun visitVisible(left: Float, right: Float, visit: (Cell) -> Unit) {
            var index = upperBound(drawable.size) { drawable[it].x + drawable[it].width < left - rightOverhang }
            while (index < drawable.size) {
                val cell = drawable[index++]
                if (cell.x > right + leftOverhang) break
                visit(cell)
            }
        }
    }

    fun lineAtOffset(offset: Int): Int =
        upperBound(lines.size) { lines[it].start <= offset }.minus(1).coerceAtLeast(0)

    companion object {
        const val CELL_CHARACTERS = 512
        val DOCUMENT_PROPERTY = Any()

        // GlyphPainter2 positions the native caret at getCaretInfo[0], including overlapping marks.
        fun caretX(layout: TextLayout, hit: TextHitInfo): Float = layout.getCaretInfo(hit)[0]

        fun prepare(text: String, font: Font, context: FontRenderContext, leftToRight: Boolean = true, checkCancelled: () -> Unit): ContextCollectionTextLayout {
            val metrics = font.getLineMetrics("Ag", context)
            var ascent = metrics.ascent
            var descent = metrics.descent + metrics.leading
            var width = 0f
            val tabWidth = TextLayout(" ", font, context).advance * 8
            val lines = ArrayList<Line>()
            var start = 0
            do {
                checkCancelled()
                val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
                val cells = ArrayList<Cell>()
                val visual = ArrayList<Cell>()
                var x = 0f
                var paragraphLeftToRight = leftToRight
                if (start < end) {
                    val value = text.substring(start, end)
                    // Resolve the complete paragraph once, including embeddings, isolates and tabs.
                    val bidi = Bidi(value, if (leftToRight) Bidi.DIRECTION_LEFT_TO_RIGHT else Bidi.DIRECTION_RIGHT_TO_LEFT)
                    paragraphLeftToRight = bidi.baseIsLeftToRight()
                    val simple = value.all { it in ' '..'~' || it == '\t' } &&
                        font.attributes[TextAttribute.LIGATURES] != TextAttribute.LIGATURES_ON &&
                        font.attributes[TextAttribute.KERNING] != TextAttribute.KERNING_ON
                    val measurers = arrayOfNulls<Pair<TextMeasurer, TextLayout>>(2)
                    val runs = Array(bidi.runCount) { run ->
                        checkCancelled()
                        val from = bidi.getRunStart(run)
                        val to = bidi.getRunLimit(run)
                        val rtl = bidi.getRunLevel(run) % 2 != 0
                        val direction = if (rtl) 1 else 0
                        val measured = if (simple) null else measurers[direction] ?: TextMeasurer(AttributedString(value).apply {
                            addAttribute(TextAttribute.FONT, font)
                            addAttribute(TextAttribute.RUN_DIRECTION, if (rtl) TextAttribute.RUN_DIRECTION_RTL else TextAttribute.RUN_DIRECTION_LTR)
                            for (index in 0 until bidi.runCount) {
                                addAttribute(TextAttribute.BIDI_EMBEDDING, if (bidi.getRunLevel(index) % 2 == 0) -2 else -1,
                                    bidi.getRunStart(index), bidi.getRunLimit(index))
                            }
                        }.iterator, context).let {
                            // Prime once: otherwise AWT clones the paragraph for every subset.
                            (it to it.getLayout(0, value.length)).also { pair -> measurers[direction] = pair }
                        }
                        val runCells = ArrayList<Cell>()
                        var offset = from
                        var nextTab = value.indexOf('\t', offset).let { if (it < 0) to else minOf(to, it) }
                        while (offset < to) {
                            checkCancelled()
                            val tab = value[offset] == '\t'
                            var next = if (tab) offset + 1 else minOf(offset + CELL_CHARACTERS, nextTab)
                            if (!tab && measured != null && next < minOf(nextTab, to)) {
                                val whole = measured.second
                                val safe = if (rtl) whole.getNextRightHit(next) else whole.getNextLeftHit(next)
                                next = safe?.insertionIndex?.takeIf { it > offset && it <= next }
                                    ?: (if (rtl) whole.getNextLeftHit(offset) else whole.getNextRightHit(offset))
                                        ?.insertionIndex?.takeIf { it > offset }?.coerceAtMost(minOf(nextTab, to))
                                    ?: minOf(nextTab, to)
                            }
                            if (next < to && Character.isHighSurrogate(value[next - 1]) && Character.isLowSurrogate(value[next])) next--
                            if (!tab && measured != null && next < minOf(nextTab, to) && isCombining(value.codePointAt(next))) {
                                var clusterStart = next
                                while (clusterStart > offset && isCombining(value.codePointBefore(clusterStart))) {
                                    clusterStart -= Character.charCount(value.codePointBefore(clusterStart))
                                }
                                if (clusterStart > offset) clusterStart -= Character.charCount(value.codePointBefore(clusterStart))
                                if (clusterStart > offset) {
                                    next = clusterStart
                                } else {
                                    // Keep all overlapping marks and their base in one mask. Compositing a
                                    // separately rasterized tail would introduce extra alpha rounding.
                                    val markStart = next
                                    while (next < minOf(nextTab, to) && isCombining(value.codePointAt(next))) {
                                        if ((next - markStart) % CELL_CHARACTERS == 0) checkCancelled()
                                        next += Character.charCount(value.codePointAt(next))
                                    }
                                    if (next < minOf(nextTab, to)) {
                                        val following = if (rtl) measured.second.getNextLeftHit(next) else measured.second.getNextRightHit(next)
                                        next = following?.insertionIndex?.takeIf { it > next }?.coerceAtMost(minOf(nextTab, to)) ?: next
                                    }
                                }
                            }
                            val layout = if (tab) null else {
                                measured?.first?.getLayout(offset, next) ?: TextLayout(value.substring(offset, next), font, context)
                            }
                            if (layout != null) {
                                // Warm AWT's lazy geometry caches on this worker, not on the first paint.
                                layout.bounds
                                ascent = max(ascent, layout.ascent)
                                descent = max(descent, layout.descent + layout.leading)
                            }
                            val raster = layout?.takeIf { next - offset > CELL_CHARACTERS }?.let {
                                ContextCollectionTextRaster.prepare(it, context, checkCancelled)
                            }
                            val cell = Cell(start + offset, start + next, layout.takeIf { raster == null }, rtl, raster)
                            cells.add(cell)
                            runCells.add(cell)
                            offset = next
                            if (tab) nextTab = value.indexOf('\t', offset).let { if (it < 0) to else minOf(to, it) }
                        }
                        if (rtl) runCells.reverse()
                        runCells
                    }
                    val levels = ByteArray(bidi.runCount) { bidi.getRunLevel(it).toByte() }
                    Bidi.reorderVisually(levels, 0, runs, 0, runs.size)
                    for (run in runs) for (cell in run) {
                        cell.x = x
                        if (cell.isTab) cell.width = tabWidth - x % tabWidth
                        x += cell.width
                        visual.add(cell)
                    }
                    if (!simple && cells.size > 1 && '\t' !in value) {
                        // A boundary caret may average glyph edges from both bidi runs. Preserve
                        // that paragraph context without retaining or querying the full layout on EDT.
                        val whole = if (bidi.runCount == 1) requireNotNull(measurers[bidi.getRunLevel(0) % 2]).second
                        else TextLayout(AttributedString(value).apply {
                            addAttribute(TextAttribute.FONT, font)
                            addAttribute(TextAttribute.RUN_DIRECTION, if (leftToRight) TextAttribute.RUN_DIRECTION_LTR else TextAttribute.RUN_DIRECTION_RTL)
                        }.iterator, context)
                        for (cell in cells) {
                            checkCancelled()
                            cell.leadingBoundary = caretX(whole, TextHitInfo.leading(cell.start - start)) - cell.x
                            cell.trailingBoundary = caretX(whole, TextHitInfo.trailing(cell.end - start - 1)) - cell.x
                        }
                    }
                }
                visual.forEachIndexed { index, cell -> cell.visualIndex = index }
                lines.add(Line(start, end, cells, visual, x, paragraphLeftToRight))
                width = max(width, x)
                start = end + 1
            } while (start <= text.length)
            checkCancelled()
            return ContextCollectionTextLayout(lines, width, ceil(ascent + descent).toInt().coerceAtLeast(1), ascent)
        }

        private inline fun upperBound(size: Int, predicate: (Int) -> Boolean): Int {
            var low = 0
            var high = size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (predicate(middle)) low = middle + 1 else high = middle
            }
            return low
        }

        private fun isCombining(point: Int): Boolean = Character.getType(point).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
        }
    }
}
