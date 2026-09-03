package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.Editor
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyResultPublisherTest {
    private val editor = mockk<Editor>()
    private val result = CopyResult(
        content = "copied content",
        editor = editor,
        lineRanges = listOf(Pair(2, 4)),
        language = "kotlin",
    )

    @Test
    fun `registered copy policies explicitly cover every side effect`() {
        assertEquals(
            setOf(CopyResultPolicy.STANDARD, CopyResultPolicy.GIT_PERMALINK),
            CopyResultPolicy.entries.toSet(),
        )
        assertEquals(
            listOf(true, true, true, true, true, true, true),
            CopyResultPolicy.STANDARD.flags(),
        )
        assertEquals(
            listOf(true, false, true, true, true, true, false),
            CopyResultPolicy.GIT_PERMALINK.flags(),
        )
    }

    @Test
    fun `standard policy publishes every enabled side effect exactly once in order`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = true)

        publisher.publish(result, CopyResultPolicy.STANDARD)

        assertEquals(
            listOf("clipboard", "analytics", "highlight", "history", "notification", "status", "review"),
            effects.events,
        )
        assertEquals(listOf(Pair("pathline", "kotlin")), effects.analyticsRecords)
        assertEquals(listOf(Pair("copied content", 17)), effects.historyRecords)
        assertEquals(listOf(listOf(Pair(2, 4))), effects.highlightedRanges)
    }

    @Test
    fun `standard policy preserves all non-analytics effects when analytics is disabled`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = false)

        publisher.publish(result, CopyResultPolicy.STANDARD)

        assertEquals(
            listOf("clipboard", "highlight", "history", "notification", "status", "review"),
            effects.events,
        )
        assertTrue(effects.analyticsRecords.isEmpty())
    }

    @Test
    fun `permalink policy publishes feedback but never analytics or review accounting`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = true)

        publisher.publish(result, CopyResultPolicy.GIT_PERMALINK)

        assertEquals(
            listOf("clipboard", "highlight", "history", "notification", "status"),
            effects.events,
        )
        assertTrue(effects.analyticsRecords.isEmpty())
    }

    @Test
    fun `newer standard publication suppresses an older permalink completion`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = true)
        val permalinkRequest = publisher.beginRequest()

        publisher.publish(result.copy(content = "standard"), CopyResultPolicy.STANDARD)
        val stalePublished = publisher.publishIfCurrent(
            permalinkRequest,
            result.copy(content = "stale permalink"),
            CopyResultPolicy.GIT_PERMALINK,
        )

        assertFalse(stalePublished)
        assertEquals(listOf("standard"), effects.clipboardWrites)
        assertEquals(listOf("standard"), effects.statusUpdates)
    }

    @Test
    fun `newer permalink request suppresses an older completion across action instances`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = true)
        val firstRequest = publisher.beginRequest()
        val secondRequest = publisher.beginRequest()

        val secondPublished = publisher.publishIfCurrent(
            secondRequest,
            result.copy(content = "second permalink"),
            CopyResultPolicy.GIT_PERMALINK,
        )
        val firstPublished = publisher.publishIfCurrent(
            firstRequest,
            result.copy(content = "first permalink"),
            CopyResultPolicy.GIT_PERMALINK,
        )

        assertTrue(secondPublished)
        assertFalse(firstPublished)
        assertEquals(listOf("second permalink"), effects.clipboardWrites)
        assertEquals(listOf("second permalink"), effects.statusUpdates)
    }

    @Test
    fun `stale failure callback produces no effects`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, analyticsEnabled = true)
        val firstRequest = publisher.beginRequest()
        publisher.beginRequest()

        val failureHandled = publisher.runIfCurrent(firstRequest) {
            effects.events += "failure"
        }

        assertFalse(failureHandled)
        assertTrue(effects.events.isEmpty())
    }

    private fun publisher(
        effects: CopyResultSideEffects,
        analyticsEnabled: Boolean,
    ): CopyResultPublisher = CopyResultPublisher.createForTest(effects) {
        CopyResultSettings(
            analyticsEnabled = analyticsEnabled,
            outputFormat = "pathline",
            historySize = 17,
        )
    }

    private fun CopyResultPolicy.flags(): List<Boolean> = listOf(
        clipboard,
        analytics,
        gutterHighlight,
        history,
        notification,
        statusBar,
        reviewAccounting,
    )

    private class RecordingSideEffects : CopyResultSideEffects {
        val events = mutableListOf<String>()
        val clipboardWrites = mutableListOf<String>()
        val analyticsRecords = mutableListOf<Pair<String, String>>()
        val highlightedRanges = mutableListOf<List<Pair<Int, Int>>>()
        val historyRecords = mutableListOf<Pair<String, Int>>()
        val statusUpdates = mutableListOf<String>()

        override fun writeClipboard(content: String) {
            events += "clipboard"
            clipboardWrites += content
        }

        override fun recordAnalytics(format: String, language: String) {
            events += "analytics"
            analyticsRecords += Pair(format, language)
        }

        override fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>) {
            events += "highlight"
            highlightedRanges += lineRanges
        }

        override fun addToHistory(content: String, maxSize: Int) {
            events += "history"
            historyRecords += Pair(content, maxSize)
        }

        override fun showNotification(content: String) {
            events += "notification"
        }

        override fun updateStatusBar(content: String) {
            events += "status"
            statusUpdates += content
        }

        override fun recordReviewEligibleCopy() {
            events += "review"
        }
    }
}
