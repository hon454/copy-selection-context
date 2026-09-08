package com.github.hon454.copyselectioncontext

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextHitInfo
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * An unusually large indivisible shaping cluster (for example thousands of combining marks) cannot
 * be split into text layouts. Prepare its glyph masks and caret coordinates off EDT instead.
 */
internal class ContextCollectionTextRaster private constructor(
    val advance: Float,
    private val leading: FloatArray,
    private val trailing: FloatArray,
    private val left: TextHitInfo,
    private val right: TextHitInfo,
    private val stops: List<TextHitInfo>,
    private val hitIndex: CollectionRasterHitIndex,
    private val rtl: Boolean,
    private val top: Float,
    private val height: Float,
    private val firstX: Int,
    private val scaleX: Double,
    private val scaleY: Double,
    private val tiles: List<BufferedImage>,
    val leftOverhang: Float,
    val rightOverhang: Float,
) {
    fun caretX(hit: TextHitInfo): Float = if (hit.isLeadingEdge) leading[hit.insertionIndex] else trailing[hit.insertionIndex]

    fun hit(x: Float, y: Float): TextHitInfo = when {
        x < 0f -> left
        x >= advance -> right
        else -> hitIndex.hit(x, y, rtl)
    }

    fun next(hit: TextHitInfo, forward: Boolean): TextHitInfo? {
        val offset = if (rtl) -hit.insertionIndex else hit.insertionIndex
        var low = 0
        var high = stops.size
        while (low < high) {
            val middle = (low + high) ushr 1
            val position = if (rtl) -stops[middle].insertionIndex else stops[middle].insertionIndex
            if (position < offset || (forward && position == offset)) low = middle + 1 else high = middle
        }
        return stops.getOrNull(if (forward) low else low - 1)
    }

    fun highlight(from: Int, to: Int): Shape {
        val startX = leading[from]
        val endX = trailing[to]
        return Rectangle2D.Float(min(startX, endX), top, abs(endX - startX), height)
    }

    fun draw(graphics: Graphics2D, x: Float, baseline: Float) {
        val clip = graphics.clipBounds
        val first = if (clip == null) 0 else floor((clip.x - x - firstX) / TILE_WIDTH).toInt().coerceAtLeast(0)
        val last = if (clip == null) tiles.lastIndex else floor((clip.maxX - x - firstX) / TILE_WIDTH).toInt().coerceAtMost(tiles.lastIndex)
        for (index in first..last) {
            val mask = tiles[index]
            // Only visible tiles are tinted, so a color/LAF change never reshapes a giant cluster.
            val colored = BufferedImage(mask.width, mask.height, BufferedImage.TYPE_INT_ARGB)
            val layer = colored.createGraphics()
            try {
                layer.color = graphics.color
                layer.fillRect(0, 0, mask.width, mask.height)
                layer.composite = AlphaComposite.DstIn
                layer.drawImage(mask, 0, 0, null)
            } finally { layer.dispose() }
            val transform = AffineTransform.getTranslateInstance((x + firstX + index * TILE_WIDTH).toDouble(), (baseline + top).toDouble())
            transform.scale(1 / scaleX, 1 / scaleY)
            graphics.drawImage(colored, transform, null)
        }
    }

    companion object {
        private const val TILE_WIDTH = 512

        fun prepare(layout: TextLayout, context: FontRenderContext, checkCancelled: () -> Unit): ContextCollectionTextRaster {
            val count = layout.characterCount
            val leading = FloatArray(count + 1)
            val trailing = FloatArray(count + 1)
            for (offset in 0..count) {
                if (offset % ContextCollectionTextLayout.CELL_CHARACTERS == 0) checkCancelled()
                leading[offset] = ContextCollectionTextLayout.caretX(layout, TextHitInfo.leading(offset))
                trailing[offset] = ContextCollectionTextLayout.caretX(layout, TextHitInfo.trailing(offset - 1))
            }
            fun real(hit: TextHitInfo): TextHitInfo =
                if (hit.charIndex !in 0 until count) layout.getVisualOtherHit(hit) else hit
            val left = layout.hitTestChar(-1f, 0f)
            val right = layout.hitTestChar(layout.advance + 1f, 0f)
            val stops = ArrayList<TextHitInfo>()
            var hit: TextHitInfo? = left
            while (hit != null) {
                if (stops.size % ContextCollectionTextLayout.CELL_CHARACTERS == 0) checkCancelled()
                stops.add(real(hit))
                hit = layout.getNextRightHit(hit)
            }
            val bounds = layout.bounds
            val firstX = floor(min(0.0, bounds.minX)).toInt()
            val endX = ceil(max(layout.advance.toDouble(), bounds.maxX)).toInt().coerceAtLeast(firstX + 1)
            val top = floor(min(-layout.ascent.toDouble(), bounds.minY)).toFloat()
            val height = ceil(max(layout.descent.toDouble(), bounds.maxY) - top).toFloat().coerceAtLeast(1f)
            val scaleX = abs(context.transform.scaleX).coerceAtLeast(1.0)
            val scaleY = abs(context.transform.scaleY).coerceAtLeast(1.0)
            val tiles = ArrayList<BufferedImage>()
            for (x in firstX until endX step TILE_WIDTH) {
                checkCancelled()
                val tile = BufferedImage(ceil(minOf(TILE_WIDTH, endX - x) * scaleX).toInt(), ceil(height * scaleY).toInt(), BufferedImage.TYPE_INT_ARGB)
                val graphics = tile.createGraphics()
                try {
                    graphics.color = Color.WHITE
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        if (context.isAntiAliased) RenderingHints.VALUE_TEXT_ANTIALIAS_ON else RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        if (context.usesFractionalMetrics()) RenderingHints.VALUE_FRACTIONALMETRICS_ON else RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
                    graphics.scale(scaleX, scaleY)
                    graphics.translate(-x.toDouble(), -top.toDouble())
                    layout.draw(graphics, 0f, 0f)
                } finally { graphics.dispose() }
                tiles.add(tile)
            }
            checkCancelled()
            val hitIndex = CollectionRasterHitIndex.prepare(layout, stops, checkCancelled)
            return ContextCollectionTextRaster(layout.advance, leading, trailing, real(left), real(right), stops, hitIndex, !layout.isLeftToRight,
                top, height, firstX, scaleX, scaleY, tiles,
                max(0f, -bounds.x.toFloat()), max(0f, bounds.maxX.toFloat() - layout.advance))
        }
    }
}

/** Native TextLayout hit semantics, indexed by the physical font metrics used by this one-font run. */
private class CollectionRasterHitIndex private constructor(private val groups: List<Group>) {
    private data class Metrics(val y: Float, val italic: Float)
    private data class Character(val offset: Int, val next: Int, val x: Float)
    private data class Group(val metrics: Metrics, val characters: List<Character>)

    fun hit(x: Float, y: Float, rtl: Boolean): TextHitInfo {
        var closest: Character? = null
        var closestMetrics: Metrics? = null
        var distance = Double.MAX_VALUE
        for (group in groups) {
            val characters = group.characters
            var low = 0
            var high = characters.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (characters[middle].x < x) low = middle + 1 else high = middle
            }
            // Within one metrics group, the nearest x is also the nearest character center.
            for (index in max(0, low - 1)..min(low, characters.lastIndex)) {
                val candidate = characters[index]
                val dx = candidate.x - x
                val dy = group.metrics.y - y
                val nextDistance = (4 * dx * dx + dy * dy).toDouble()
                if (nextDistance < distance || (nextDistance == distance && candidate.offset < (closest?.offset ?: Int.MAX_VALUE))) {
                    closest = candidate
                    closestMetrics = group.metrics
                    distance = nextDistance
                }
            }
        }
        val character = requireNotNull(closest)
        val metrics = requireNotNull(closestMetrics)
        val left = x < character.x - (y - metrics.y) * metrics.italic
        return if (left != rtl) TextHitInfo.leading(character.offset) else TextHitInfo.trailing(character.next - 1)
    }

    companion object {
        fun prepare(layout: TextLayout, stops: List<TextHitInfo>, checkCancelled: () -> Unit): CollectionRasterHitIndex {
            val offsets = stops.map { it.insertionIndex }.distinct().sorted()
            val groups = LinkedHashMap<Metrics, MutableList<Character>>()
            for ((index, offset) in offsets.withIndex()) {
                if (index % ContextCollectionTextLayout.CELL_CHARACTERS == 0) checkCancelled()
                if (offset >= layout.characterCount) continue
                val leading = layout.getCaretInfo(TextHitInfo.leading(offset))
                val trailing = layout.getCaretInfo(TextHitInfo.trailing(offset))
                // Public physical caret endpoints expose each fallback font's baseline and slant.
                val cy = (leading[3] + leading[5]) / 2
                val italic = (leading[2] - leading[4]) / (leading[5] - leading[3])
                val cx = (leading[2] + leading[4] + trailing[2] + trailing[4]) / 4
                groups.getOrPut(Metrics(cy, italic)) { ArrayList() }
                    .add(Character(offset, offsets.getOrElse(index + 1) { layout.characterCount }, cx))
            }
            return CollectionRasterHitIndex(groups.map { (metrics, characters) ->
                // Native ties choose the first logical character, including zero-advance marks.
                Group(metrics, characters.sortedWith(compareBy<Character> { it.x }.thenBy { it.offset }).distinctBy { it.x })
            })
        }
    }
}
