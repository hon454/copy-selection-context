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

    fun hit(x: Float): TextHitInfo = if (x < advance / 2) left else right

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
                leading[offset] = ContextCollectionTextLayout.baselineX(layout, TextHitInfo.leading(offset))
                trailing[offset] = ContextCollectionTextLayout.baselineX(layout, TextHitInfo.trailing(offset - 1))
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
            return ContextCollectionTextRaster(layout.advance, leading, trailing, real(left), real(right), stops, !layout.isLeftToRight,
                top, height, firstX, scaleX, scaleY, tiles,
                max(0f, -bounds.x.toFloat()), max(0f, bounds.maxX.toFloat() - layout.advance))
        }
    }
}
