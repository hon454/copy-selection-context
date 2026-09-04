package com.github.hon454.copyselectioncontext

import java.time.Instant
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ContextCollectionFormatterTest {
    private val options = ContextCollectionOutputOptions("pathline", "", false)

    @Test fun `builtins retain golden output separators dynamic fences and duplicate filenames`() {
        val first = item(1, "src/A.kt", "```nested\nbody", "kotlin")
        val second = item(2, "other/A.kt", "print(1)", "python")
        val ready = ready(listOf(first, second))
        assertEquals("src/A.kt:10\n````kotlin\n```nested\nbody\n````\n\nother/A.kt:10\n```python\nprint(1)\n```", ready.payload)
        assertEquals("mixed", ready.language)
        val claude = ready(listOf(first), options.copy(format = "claude"), false)
        assertEquals(" @src/A.kt#L10 ", claude.payload)
        assertEquals("kotlin", claude.language)
        assertEquals("", ready(listOf(first.copy(language = ""))).language)
    }

    @Test fun `conflicts group source location not display path and labels survive reorder`() {
        val first = item(1, "src/A.kt", "old")
        val second = first.copy(id = 2, captureNumber = 2, displayPath = "/project/src/A.kt", code = "new")
        val result = ready(listOf(second, first), includeCode = false)
        assertEquals("[Snapshot #2 · 2026-09-04T07:30:00.000Z]\n/project/src/A.kt:10\n\n" +
            "[Snapshot #1 · 2026-09-04T07:30:00.000Z]\nsrc/A.kt:10", result.payload)
        assertEquals(setOf(ContextCollectionWarning.HISTORICAL_CODE_ABSENT), result.warnings)
        val removed = ready(listOf(first), includeCode = false)
        assertEquals("src/A.kt:10", removed.payload)
        assertTrue(removed.warnings.isEmpty())
        val changedLocation = second.copy(sourceLocation = ContextCollectionSourceLocation(1, "file:///renamed"))
        assertFalse(ready(listOf(first, changedLocation)).payload.contains("[Snapshot"))
    }

    @Test fun `custom semantics preserve single pass unknown variables fallback and literal code placement`() {
        val raw = item(1, "src\\A.kt", " {path} {code} ")
        val templates = listOf("{filename}: {code}", "{path}|{line}|{range}|{lang}|{filename}|{unknown}|{code}{code}", "", "   ")
        for (template in templates) for (includeCode in listOf(true, false)) for (trim in listOf(true, false)) {
            val opts = options.copy(format = "template", template = template, trimCode = trim)
            val actual = ready(listOf(raw), opts, includeCode)
            val code = if (!includeCode) null else if (trim) raw.code.trim() else raw.code
            val expected = OutputFormatterFactory.getTemplateFormatter(template).format(
                FormatContext(raw.displayPath, 10, 10, code, raw.language, raw.filename))
            assertEquals(expected, actual.payload)
            assertEquals("template", actual.actualFormat)
            assertEquals(" {path} {code} ", raw.code)
        }
        assertEquals("claude", ready(listOf(raw), options.copy(format = "unknown")).actualFormat)
    }

    @Test fun `custom conflicts combine absent labels missing code and size in one reason set`() {
        val first = item(1, "src/A.kt", "old")
        val second = first.copy(id = 2, captureNumber = 2, code = "new")
        val template = "x".repeat(131073) + "{filename}"
        val result = ready(listOf(first, second), options.copy(format = "template", template = template))
        assertEquals(ContextCollectionWarning.entries.toSet(), result.warnings)
        assertFalse(result.payload.contains("Snapshot"))
        val small = ready(listOf(first, second), options.copy(format = "template", template = "{code}"))
        assertEquals("old\n\nnew", small.payload)
        assertEquals(setOf(ContextCollectionWarning.SNAPSHOT_LABELS_ABSENT), small.warnings)
        val blankCode = ready(listOf(first.copy(code = "  "), second))
        assertEquals(setOf(ContextCollectionWarning.HISTORICAL_CODE_ABSENT), blankCode.warnings)
    }

    @Test fun `empty collection or any blank item never silently drops a capture`() {
        assertEquals(ContextCollectionOutputResult.Empty, format(emptyList()))
        for (includeCode in listOf(true, false)) {
            val result = format(listOf(item(1, code = ""), item(2, code = "valid")),
                options.copy(format = "template", template = "{code}"), includeCode)
            assertEquals(ContextCollectionOutputResult.BlankItem(1, "template"), result)
        }
    }

    @Test fun `exact final UTF8 boundaries include template repetition and annotation overhead`() {
        for (limit in listOf(ContextCollectionFormatter.WARNING_BYTES, ContextCollectionFormatter.MAX_BYTES)) {
            for (delta in -1..1) {
                val count = limit + delta
                val result = format(listOf(item(1, code = "x")), options.copy(format = "template", template = "x".repeat(count)))
                if (count > ContextCollectionFormatter.MAX_BYTES) assertEquals(ContextCollectionOutputResult.AboveHardLimit, result)
                else {
                    val ready = assertIs<ContextCollectionOutputResult.Ready>(result)
                    assertEquals(count, ready.bytes)
                    assertEquals(count > ContextCollectionFormatter.WARNING_BYTES, ContextCollectionWarning.SIZE in ready.warnings)
                }
            }
        }
        val repeated = options.copy(format = "template", template = "{code}".repeat(100000))
        var checks = 0
        assertEquals(ContextCollectionOutputResult.AboveHardLimit,
            ContextCollectionFormatter.format(snapshot(listOf(item(1, code = "x".repeat(262144)))), repeated) { checks++ })
        assertTrue(checks < 1200, "must stop after a bounded prefix instead of visiting every repetition")
        val a = item(1, code = "x".repeat(131000))
        val b = a.copy(id = 2, captureNumber = 2)
        val labels = ready(listOf(a, b))
        assertEquals(labels.payload.toByteArray(Charsets.UTF_8).size, labels.bytes)
    }

    @Test fun `UTF8 matches JVM for supplementary malformed and cross replacement surrogate pairs`() {
        val codes = listOf("한😀", "\uD800", "\uDC00", "\uD800x\uDC00", "\uD800\uD800\uDC00")
        for (code in codes) {
            val result = ready(listOf(item(1, code = code)), options.copy(format = "template", template = "{code}{code}\uDC00"))
            assertEquals(result.payload.toByteArray(Charsets.UTF_8).size, result.bytes)
        }
        val joined = ready(listOf(item(1, code = "\uD800")), options.copy(format = "template", template = "{code}\uDC00"))
        assertEquals(4, joined.bytes)
        val unicode = "😀".repeat(65536)
        assertEquals(262144, ready(listOf(item(1, code = unicode)), options.copy(format = "template", template = "{code}")).bytes)
    }

    @Test fun `superseded output can stop inside a large template`() {
        var checks = 0
        assertFailsWith<CancellationException> {
            ContextCollectionFormatter.format(snapshot(listOf(item(1))), options.copy(format = "template", template = "x".repeat(100000))) {
                if (++checks == 3) throw CancellationException()
            }
        }
    }

    @Test fun `cancellation interrupts repeated empty substitutions independently of output bytes`() {
        for ((code, includeCode) in listOf("" to true, "retained code" to false)) {
            var checks = 0
            assertFailsWith<CancellationException> {
                ContextCollectionFormatter.format(snapshot(listOf(item(1, code = code)), includeCode),
                    options.copy(format = "template", template = "{code}".repeat(100000) + "x")) {
                    if (++checks == 2) throw CancellationException()
                }
            }
            assertEquals(2, checks)
            assertEquals("x", ready(listOf(item(1, code = code)),
                options.copy(format = "template", template = "{code}".repeat(100000) + "x"), includeCode).payload)
        }
    }

    @Test fun `screenshot warning sample has the documented exact raw and final bytes`() {
        val source = item(1, "src/demo/Warning.txt", "x".repeat(262144), "txt")
        val result = ready(listOf(source), options.copy(format = "template", template = "{code}\n"))
        assertEquals(262144, source.codeBytes)
        assertEquals(262145, result.bytes)
        assertEquals(1, result.itemCount)
        assertEquals(setOf(ContextCollectionWarning.SIZE), result.warnings)
        assertEquals(source.code + "\n", result.payload)
    }

    @Test fun `snapshot annotation overhead participates at both exact size thresholds`() {
        for (limit in listOf(ContextCollectionFormatter.WARNING_BYTES, ContextCollectionFormatter.MAX_BYTES)) {
            val count = if (limit == ContextCollectionFormatter.WARNING_BYTES) 2 else 8
            val ticks = if (count == 2) 40000 else 174000
            val base = (1..count).map { id -> item(id.toLong(), code = "`".repeat(ticks) + "\n$id")
                .copy(sourceLocation = ContextCollectionSourceLocation(1, "file:///A.kt")) }
            val initial = ready(base)
            for (delta in -1..1) {
                val padding = limit + delta - initial.bytes
                assertTrue(padding > 0)
                val last = base.last().copy(code = base.last().code + "x".repeat(padding))
                assertTrue(last.code.toByteArray().size <= 262144)
                val result = format(base.dropLast(1) + last)
                if (limit + delta > ContextCollectionFormatter.MAX_BYTES) assertEquals(ContextCollectionOutputResult.AboveHardLimit, result)
                else {
                    val ready = assertIs<ContextCollectionOutputResult.Ready>(result)
                    assertEquals(limit + delta, ready.bytes)
                    assertEquals(ready.payload.toByteArray(Charsets.UTF_8).size, ready.bytes)
                    assertEquals(limit + delta > ContextCollectionFormatter.WARNING_BYTES, ContextCollectionWarning.SIZE in ready.warnings)
                }
            }
        }
    }

    private fun item(id: Long, path: String = "A.kt", code: String = "code", language: String = "kotlin") = ContextCollectionItem(
        id, ContextCollectionSourceLocation(id, "file:///$path"), "/project/$path", path, path,
        "A.kt", language, 10, 10, code, id, Instant.parse("2026-09-04T07:30:00Z"), code.toByteArray().size)
    private fun snapshot(items: List<ContextCollectionItem>, includeCode: Boolean = true) =
        ContextCollectionSnapshot(items, items.sumOf { it.codeBytes.toLong() }, includeCode, 1)
    private fun format(items: List<ContextCollectionItem>, opts: ContextCollectionOutputOptions = options, includeCode: Boolean = true) =
        ContextCollectionFormatter.format(snapshot(items, includeCode), opts)
    private fun ready(items: List<ContextCollectionItem>, opts: ContextCollectionOutputOptions = options, includeCode: Boolean = true) =
        assertIs<ContextCollectionOutputResult.Ready>(format(items, opts, includeCode))
}
