package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyPreviewTest {
    @Test
    fun `multiline content becomes a single line`() {
        val content = "src/main/App.kt:10-12\r\n```kotlin\n\tfun main() = Unit\n```"

        val preview = CopyPreview.create(content, 120)

        assertEquals(
            "src/main/App.kt:10-12 ```kotlin fun main() = Unit ```",
            preview
        )
        assertFalse(preview.any { it == '\n' || it == '\r' || it == '\t' })
    }

    @Test
    fun `very large content is bounded and marked as truncated`() {
        val content = "src/main/Large.kt:1-5000\n" + "x".repeat(100_000)

        val preview = CopyPreview.create(content, 80)

        assertTrue(preview.startsWith("src/main/Large.kt:1-5000 "))
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 80)
    }

    @Test
    fun `unicode content is not split during truncation`() {
        val preview = CopyPreview.create("가😀나다라마바사", 5)

        assertEquals("가😀나…", preview)
        assertTrue(preview.hasOnlyPairedSurrogates())
        assertTrue(preview.length <= 5)
    }

    @Test
    fun `unicode grapheme clusters are not split during truncation`() {
        val combiningPreview = CopyPreview.history("a".repeat(78) + "e\u0301z")
        val emojiPreview = CopyPreview.history("a".repeat(76) + "👩‍💻z")

        assertEquals("a".repeat(78) + "…", combiningPreview)
        assertEquals("a".repeat(76) + "…", emojiPreview)
    }

    @Test
    fun `markup-like content is escaped without partial entities`() {
        val preview = CopyPreview.create("src/App.kt:7\n<script title=\"x\">& 'value'</script>", 120)

        assertEquals(
            "src/App.kt:7 &lt;script title=&quot;x&quot;&gt;&amp; &#39;value&#39;&lt;/script&gt;",
            preview
        )
        assertFalse(preview.contains("<script"))
    }

    @Test
    fun `history preview uses the shared safe preview pipeline`() {
        val content = "e\u0301 😀\n\t" + "x".repeat(200)

        val preview = CopyPreview.history(content)

        assertEquals(CopyPreview.create(content, CopyPreview.HISTORY_MAX_LENGTH), preview)
        assertTrue(preview.length <= CopyPreview.HISTORY_MAX_LENGTH)
        assertTrue(preview.hasOnlyPairedSurrogates())
        assertFalse(preview.any { it == '\n' || it == '\r' || it == '\t' })
    }

    private fun String.hasOnlyPairedSurrogates(): Boolean {
        forEachIndexed { index, character ->
            if (character.isHighSurrogate()) {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
            }
            if (character.isLowSurrogate()) {
                if (index == 0 || !this[index - 1].isHighSurrogate()) return false
            }
        }
        return true
    }
}
