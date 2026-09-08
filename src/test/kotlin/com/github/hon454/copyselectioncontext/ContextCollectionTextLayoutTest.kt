package com.github.hon454.copyselectioncontext

import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextHitInfo
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.text.AttributedString
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContextCollectionTextLayoutTest {
    private val font = Font(Font.MONOSPACED, Font.PLAIN, 13)
    private val context = FontRenderContext(AffineTransform(), true, false)
    private fun prepare(text: String) = ContextCollectionTextLayout.prepare(text, font, context) {}

    @Test
    fun `chunk geometry matches whole paragraph bidi and contextual shaping`() {
        val examples = listOf(
            "small preview", "한글과 supplementary 😀 𝄞", "abc مرحبا 123 שלום xyz",
            "مرحبا 123 world שלום", "before \u202Bשלום 123 مرحبا\u202C after",
            "a \u2067مرحبا 123\u2069 z", "سلام".repeat(400),
            "ا".repeat(511) + "لَا" + "سلام".repeat(300),
            "x".repeat(511) + "😀𝄞e\u0301" + "אבג".repeat(400),
            "אב".repeat(255) + "ש\u05B8" + "אב".repeat(400),
            "a" + "\u0301".repeat(1536) + "b",
        )
        val errors = mutableListOf<String>()
        for ((example, text) in examples.withIndex()) {
            val expected = TextLayout(AttributedString(text).apply { addAttribute(TextAttribute.FONT, font) }.iterator, context)
            val actual = prepare(text).lines.single()
            assertTrue(abs(expected.advance - actual.width) < 0.1f, "example $example width: ${expected.advance} != ${actual.width}")
            var hit: TextHitInfo? = expected.hitTestChar(-1f, 0f)
            while (hit != null) {
                val current = hit
                val offset = current.insertionIndex
                val referenceX = java.awt.geom.Point2D.Float().also { expected.hitToPoint(current, it) }.x
                val actualX = actual.caretX(offset, !current.isLeadingEdge)
                if (abs(referenceX - actualX) >= 0.01f) errors.add("example $example visual offset $offset: $referenceX != $actualX")
                hit = expected.getNextRightHit(current)
            }
            val width = kotlin.math.ceil(expected.advance).toInt() + 8
            fun image(draw: (java.awt.Graphics2D) -> Unit): BufferedImage =
                BufferedImage(width, 60, BufferedImage.TYPE_INT_ARGB).also { image ->
                    val graphics = image.createGraphics()
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
                        graphics.translate(4, 35)
                        draw(graphics)
                    } finally { graphics.dispose() }
                }
            val referenceImage = image { expected.draw(it, 0f, 0f) }
            val actualImage = image { graphics -> actual.visual.forEach { it.draw(graphics, it.x, 0f) } }
            val referencePixels = referenceImage.getRGB(0, 0, width, 60, null, 0, width)
            val actualPixels = actualImage.getRGB(0, 0, width, 60, null, 0, width)
            val different = referencePixels.indices.count { referencePixels[it] != actualPixels[it] }
            if (different > 0) errors.add("example $example has $different different glyph pixels")
        }
        assertTrue(errors.isEmpty(), "${errors.size} mismatches: ${errors.take(25)}")
    }

    @Test
    fun `line and cell indexes cover raw UTF16 without splitting supplementary pairs`() {
        val text = "\talpha\r\n\n" + "😀".repeat(300) + "\tمرحبا שלום\t한글\n"
        val model = prepare(text)
        assertEquals(4, model.lines.size)
        for (line in model.lines) {
            val restored = line.logical.joinToString("") { text.substring(it.start, it.end) }
            assertEquals(text.substring(line.start, line.end), restored)
            assertEquals(line.logical.toSet(), line.visual.toSet())
            line.logical.forEach { cell ->
                assertTrue(cell.end - cell.start <= ContextCollectionTextLayout.CELL_CHARACTERS)
                assertTrue(cell.start == 0 || !Character.isLowSurrogate(text[cell.start]) || !Character.isHighSurrogate(text[cell.start - 1]))
                assertTrue(cell.width >= 0f)
                assertEquals(cell, line.cellAtOffset(cell.start))
            }
        }
        for (offset in 0..text.length) {
            val line = model.lines[model.lineAtOffset(offset)]
            assertTrue(offset in line.start..line.end)
        }
    }

    @Test
    fun `viewport work stays bounded at either end of a 4 MiB line`() {
        for (text in listOf("ا".repeat(131072), "x".repeat(4194304))) {
            val model = prepare(text)
            val line = model.lines.single()
            assertTrue(line.logical.size >= 256)
            for (left in listOf(0f, line.width / 2, line.width - 500f)) {
                var visitedCharacters = 0
                var visitedCells = 0
                line.visitVisible(left, left + 500f) {
                    visitedCharacters += it.end - it.start
                    visitedCells++
                }
                assertTrue(visitedCells in 1..3, "Only viewport cells may be painted: $visitedCells")
                assertTrue(visitedCharacters <= 3 * ContextCollectionTextLayout.CELL_CHARACTERS)
            }
        }
        var invisibleVisits = 0
        prepare("\u200E".repeat(262144)).lines.single().visitVisible(0f, 500f) { invisibleVisits++ }
        assertEquals(0, invisibleVisits)
    }

    @Test
    fun `preparation has cancellation checkpoints between bounded shaping steps`() {
        var checks = 0
        assertFailsWith<CancellationException> {
            ContextCollectionTextLayout.prepare("ا".repeat(131072), font, context) {
                if (++checks == 4) throw CancellationException()
            }
        }
        assertEquals(4, checks)
    }

    @Test
    fun `overlapping combining glyphs cannot turn a narrow viewport into a whole-line draw`() {
        val model = prepare("a" + "\u0301".repeat(131072) + "b")
        var cells = 0
        var directlyDrawnCharacters = 0
        var masks = 0
        model.lines.single().visitVisible(0f, 500f) {
            cells++
            if (it.raster != null) masks++ else directlyDrawnCharacters += it.end - it.start
        }
        assertTrue(cells <= 4)
        assertEquals(1, masks)
        assertTrue(directlyDrawnCharacters <= 2 * ContextCollectionTextLayout.CELL_CHARACTERS)
    }

    @Test
    fun `oversized masks retain mouse access to internal glyph boundaries`() {
        for (text in listOf("a" + "\u0301".repeat(1536) + "b", "א" + "\u05B0".repeat(1536) + "ב",
            "ا" + "\u064B".repeat(1536) + "ب")) {
            val expected = TextLayout(text, font, context)
            val model = prepare(text).lines.single()
            val cell = model.logical.single()
            assertTrue(cell.raster != null)
            for (halfPixel in -1..(expected.advance * 2).toInt()) {
                val x = halfPixel / 2f
                for (y in listOf(-5f, 0f, 3f)) {
                    assertEquals(expected.hitTestChar(x, y), cell.hit(x, y), "${text.first()} x=$x y=$y")
                }
            }
        }
    }

    @Test
    fun `mouse hits preserve the tab edge beside a bidirectional run`() {
        for (text in listOf("a\tאב", "אב\txyz", "\tאב", "אב\t")) {
            val geometry = prepare(text)
            val tab = geometry.lines.single().visual.single { it.isTab }
            javax.swing.SwingUtilities.invokeAndWait {
                val document = javax.swing.text.PlainDocument().apply {
                    insertString(0, text, null)
                    putProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY, geometry)
                }
                val area = ContextCollectionTextArea().apply { this.font = this@ContextCollectionTextLayoutTest.font }
                area.document = document
                area.setSize(500, 300)
                val view = area.ui.getRootView(area).getView(0)
                val allocation = java.awt.Rectangle(0, 0, 500, 300)
                for (right in listOf(false, true)) {
                    val bias = arrayOf(javax.swing.text.Position.Bias.Forward)
                    val hit = view.viewToModel(tab.x + tab.width * if (right) 0.75f else 0.25f, 5f, allocation, bias)
                    val returned = view.modelToView(hit, allocation, bias[0]).bounds2D.x
                    assertEquals((tab.x + if (right) tab.width else 0f).toDouble(), returned, 0.01, text)
                }
            }
        }
    }

    @Test
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    fun `native line action keys use indexed boundaries instead of scanning the row`() {
        val names = listOf("caret-begin-line", "caret-end-line", "selection-begin-line", "selection-end-line",
            "caret-begin-line-and-up", "caret-end-line-and-down", "select-line")
        // Preserve native action semantics on small inputs, including repeated up/down edge moves.
        for (text in listOf("first\nsecond\nlast", "abc\nאבג\nمرحبا")) {
            val geometry = prepare(text)
            javax.swing.SwingUtilities.invokeAndWait {
                val reference = javax.swing.JTextArea(text).apply { font = this@ContextCollectionTextLayoutTest.font; setSize(500, 300) }
                val candidate = ContextCollectionTextArea().apply {
                    font = this@ContextCollectionTextLayoutTest.font
                    document = javax.swing.text.PlainDocument().apply {
                        insertString(0, text, null)
                        putProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY, geometry)
                    }
                    setSize(500, 300)
                }
                for (name in names) for (offset in text.indices) {
                    reference.caretPosition = offset
                    candidate.caretPosition = offset
                    repeat(2) {
                        for (area in listOf(reference, candidate)) area.actionMap.get(name)
                            .actionPerformed(java.awt.event.ActionEvent(area, 0, name))
                        assertEquals(reference.caretPosition, candidate.caretPosition, "$name at $offset")
                        assertEquals(reference.selectionStart, candidate.selectionStart, "$name at $offset")
                        assertEquals(reference.selectionEnd, candidate.selectionEnd, "$name at $offset")
                    }
                }
            }
        }
        for (text in listOf("ا".repeat(131072), "x".repeat(4194304))) {
            val geometry = prepare(text)
            val document = javax.swing.text.PlainDocument().apply {
                insertString(0, text, null)
                putProperty(ContextCollectionTextLayout.DOCUMENT_PROPERTY, geometry)
            }
            javax.swing.SwingUtilities.invokeAndWait {
                val area = object : ContextCollectionTextArea() {
                    var lookups = 0
                    override fun modelToView(position: Int): java.awt.Rectangle? {
                        assertTrue(++lookups <= 64, "A line action must not visit every character")
                        return super.modelToView(position)
                    }
                }.apply { this.document = document; setSize(500, 300) }
                for (name in names) {
                    area.caretPosition = text.length / 2
                    area.lookups = 0
                    area.actionMap.get(name).actionPerformed(java.awt.event.ActionEvent(area, 0, name))
                    assertEquals(if (name.contains("begin")) 0 else text.length, area.caretPosition)
                    if (name.startsWith("selection")) assertEquals(text.length / 2, area.selectionEnd - area.selectionStart)
                    if (name == "select-line") assertEquals(text, area.selectedText)
                }
            }
        }
    }
}
