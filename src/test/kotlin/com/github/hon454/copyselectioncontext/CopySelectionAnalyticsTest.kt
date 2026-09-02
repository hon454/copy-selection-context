package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class CopySelectionAnalyticsTest {

    @Test
    fun `default state has zero copy count`() {
        val analytics = CopySelectionAnalytics()
        assertEquals(0, analytics.getTotalCopyCount())
    }

    @Test
    fun `recordCopy increments total count`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude")
        assertEquals(1, analytics.getTotalCopyCount())
    }

    @Test
    fun `recordCopy tracks format usage`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude")
        analytics.recordCopy("claude")
        analytics.recordCopy("pathline")
        assertEquals(2, analytics.getFormatUsage()["claude"])
        assertEquals(1, analytics.getFormatUsage()["pathline"])
    }

    @Test
    fun `recordCopy tracks language usage`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude", "kotlin")
        analytics.recordCopy("claude", "kotlin")
        assertEquals(2, analytics.getLanguageUsage()["kotlin"])
    }

    @Test
    fun `recordCopy omits blank language while retaining action counters`() {
        val analytics = CopySelectionAnalytics()

        analytics.recordCopy("claude", "   ")

        assertEquals(1, analytics.snapshot().totalCopyCount)
        assertEquals(mapOf("claude" to 1), analytics.snapshot().formatUsage)
        assertTrue(analytics.snapshot().languageUsage.isEmpty())
    }

    @Test
    fun `reset clears all data`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude")
        analytics.reset()
        assertEquals(0, analytics.getTotalCopyCount())
        assertTrue(analytics.getFormatUsage().isEmpty())
    }

    @Test
    fun `snapshot is detached and immutable`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude", "kotlin")

        val snapshot = analytics.snapshot()
        analytics.recordCopy("pathline", "java")

        assertEquals(1, snapshot.totalCopyCount)
        assertEquals(mapOf("claude" to 1), snapshot.formatUsage)
        assertEquals(mapOf("kotlin" to 1), snapshot.languageUsage)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.formatUsage as MutableMap<String, Int>)["template"] = 1
        }
    }

    @Test
    fun `loadState preserves legacy counters without retaining mutable aliases`() {
        val analytics = CopySelectionAnalytics()
        val persistedState = CopySelectionAnalytics.State(
            totalCopyCount = 3,
            formatUsage = mutableMapOf("claude" to 2, "pathline" to 1),
            languageUsage = mutableMapOf("kotlin" to 3),
        )

        analytics.loadState(persistedState)
        persistedState.formatUsage.clear()
        persistedState.languageUsage.clear()

        assertEquals(3, analytics.snapshot().totalCopyCount)
        assertEquals(mapOf("claude" to 2, "pathline" to 1), analytics.snapshot().formatUsage)
        assertEquals(mapOf("kotlin" to 3), analytics.snapshot().languageUsage)
    }

    @Test
    fun `reset state remains empty after persistence reload`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("template", "typescript")
        analytics.reset()

        val reloaded = CopySelectionAnalytics()
        reloaded.loadState(analytics.state)

        assertEquals(CopySelectionAnalytics.Snapshot(0, emptyMap(), emptyMap()), reloaded.snapshot())
    }

    @Test
    fun `concurrent recording retains every action`() {
        val analytics = CopySelectionAnalytics()
        val workers = List(8) {
            thread(start = true) {
                repeat(1_000) { analytics.recordCopy("claude", "kotlin") }
            }
        }

        workers.forEach(Thread::join)

        assertEquals(8_000, analytics.snapshot().totalCopyCount)
        assertEquals(8_000, analytics.snapshot().formatUsage["claude"])
        assertEquals(8_000, analytics.snapshot().languageUsage["kotlin"])
    }

    @Test
    fun `analyticsEnabled defaults to false in settings`() {
        val settings = CopySelectionSettings.State()
        assertFalse(settings.analyticsEnabled)
    }
}
