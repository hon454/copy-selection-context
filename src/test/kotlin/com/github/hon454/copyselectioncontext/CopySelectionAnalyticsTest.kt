package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
    fun `reset clears all data`() {
        val analytics = CopySelectionAnalytics()
        analytics.recordCopy("claude")
        analytics.reset()
        assertEquals(0, analytics.getTotalCopyCount())
        assertTrue(analytics.getFormatUsage().isEmpty())
    }

    @Test
    fun `analyticsEnabled defaults to false in settings`() {
        val settings = CopySelectionSettings.State()
        assertFalse(settings.analyticsEnabled)
    }
}
