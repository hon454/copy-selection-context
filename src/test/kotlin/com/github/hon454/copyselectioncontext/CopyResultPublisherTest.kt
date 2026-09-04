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
            setOf(CopyResultPolicy.STANDARD, CopyResultPolicy.GIT_PERMALINK, CopyResultPolicy.COLLECTION),
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
        assertEquals(listOf(true, true, false, false, true, true, true), CopyResultPolicy.COLLECTION.flags())
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

    @Test fun `collection has no editor history or marker and attributes the actual prepared format`() {
        val effects = RecordingSideEffects()
        val publisher = publisher(effects, true)
        val outcome = publisher.publish(CopyResult("collection", language = "mixed", actualFormat = "template"), CopyResultPolicy.COLLECTION)
        assertEquals(CopyPublicationOutcome.Published(emptyList()), outcome)
        assertEquals(listOf("clipboard", "analytics", "notification", "status", "review"), effects.events)
        assertEquals(listOf("template" to "mixed"), effects.analyticsRecords)
        assertTrue(effects.historyRecords.isEmpty())
        assertTrue(effects.highlightedRanges.isEmpty())
    }

    @Test fun `each optional failure is isolated and attempted at most once after clipboard success`() {
        val names = mapOf("analytics" to CopyFeedbackEffect.ANALYTICS, "notification" to CopyFeedbackEffect.NOTIFICATION,
            "status" to CopyFeedbackEffect.STATUS, "review" to CopyFeedbackEffect.REVIEW)
        for ((name, effect) in names) {
            val effects = RecordingSideEffects(name)
            val publisher = publisher(effects, true)
            val request = publisher.beginRequest()
            assertEquals(CopyPublicationOutcome.Published(listOf(CopyFeedbackFailure(effect, "IllegalStateException"))),
                publisher.publishOutcomeIfCurrent(request, result, CopyResultPolicy.COLLECTION))
            assertEquals(listOf("clipboard", "analytics", "notification", "status", "review"), effects.events)
            assertEquals(CopyPublicationOutcome.NotPublished(CopyNotPublishedReason.ALREADY_ATTEMPTED),
                publisher.publishOutcomeIfCurrent(request, result, CopyResultPolicy.COLLECTION))
            assertEquals(1, effects.clipboardWrites.size)
        }
    }

    @Test fun `clipboard failure prevents all optional effects and is never retried`() {
        val effects = RecordingSideEffects("clipboard")
        val publisher = publisher(effects, true)
        val request = publisher.beginRequest()
        assertEquals(CopyPublicationOutcome.NotPublished(CopyNotPublishedReason.CLIPBOARD_FAILURE),
            publisher.publishOutcomeIfCurrent(request, result, CopyResultPolicy.COLLECTION))
        publisher.publishOutcomeIfCurrent(request, result, CopyResultPolicy.COLLECTION)
        assertEquals(listOf("clipboard"), effects.events)
    }

    @Test fun `each newer project policy and managed recopy suppress both delayed policies and stale failures`() {
        for (oldPolicy in listOf(CopyResultPolicy.COLLECTION, CopyResultPolicy.GIT_PERMALINK)) {
            for (newPolicy in CopyResultPolicy.entries + listOf(null)) {
                val coordinator = ClipboardRequestCoordinator()
                val aEffects = RecordingSideEffects()
                val bEffects = RecordingSideEffects()
                val a = CopyResultPublisher.createForTest(aEffects, coordinator) { CopyResultSettings(true, "pathline", 10) }
                val b = CopyResultPublisher.createForTest(bEffects, coordinator) { CopyResultSettings(true, "pathline", 10) }
                val oldRequest = a.beginRequest()
                if (newPolicy == null) coordinator.writeIfCurrent(coordinator.beginRequest(), { null }) { bEffects.writeClipboard("B") }
                else b.publish(result.copy(content = "B"), newPolicy)
                assertFalse(a.publishIfCurrent(oldRequest, result.copy(content = "A"), oldPolicy))
                assertFalse(a.runIfCurrent(oldRequest) { aEffects.events += "stale failure" })
                assertTrue(aEffects.events.isEmpty())
                assertEquals(listOf("B"), bEffects.clipboardWrites)
            }
        }
    }

    @Test fun `cancel failed write invalid input and project disposal never revive old requests`() {
        for (failure in listOf("cancel", "clipboard", "input", "disposed")) {
            val coordinator = ClipboardRequestCoordinator()
            val effects = RecordingSideEffects()
            val old = CopyResultPublisher.createForTest(effects, coordinator) { CopyResultSettings(false, "claude", 10) }
            val oldRequest = old.beginRequest()
            val new = CopyResultPublisher.createForTest(RecordingSideEffects(if (failure == "clipboard") "clipboard" else null),
                coordinator, { failure != "disposed" }) { CopyResultSettings(false, "claude", 10) }
            val newRequest = new.beginRequest()
            if (failure != "cancel") new.publishOutcomeIfCurrent(newRequest, result, CopyResultPolicy.COLLECTION) { failure != "input" }
            assertFalse(old.publishIfCurrent(oldRequest, result, CopyResultPolicy.GIT_PERMALINK))
            assertTrue(effects.events.isEmpty())
        }
    }

    @Test fun `atomic coordinator prevents token acquisition between final validation and write`() {
        val coordinator = ClipboardRequestCoordinator()
        val request = coordinator.beginRequest()
        val validating = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val otherStarted = java.util.concurrent.CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                coordinator.writeIfCurrent(request, { validating.countDown(); release.await(); null }) { events += "A" }
            }
            assertTrue(validating.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val second = executor.submit {
                otherStarted.countDown()
                val next = coordinator.beginRequest()
                coordinator.writeIfCurrent(next, { null }) { events += "B" }
            }
            assertTrue(otherStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
            release.countDown()
            first.get(5, java.util.concurrent.TimeUnit.SECONDS)
            second.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(listOf("A", "B"), events)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `reentrant copy after successful collection write preserves accounting and newest visible feedback`() {
        for (reenterAt in listOf("clipboard", "analytics", "notification")) {
            val coordinator = ClipboardRequestCoordinator()
            val aEffects = RecordingSideEffects()
            val bEffects = RecordingSideEffects()
            val a = CopyResultPublisher.createForTest(aEffects, coordinator) { CopyResultSettings(true, "pathline", 10) }
            val b = CopyResultPublisher.createForTest(bEffects, coordinator) { CopyResultSettings(true, "pathline", 10) }
            aEffects.afterEvent = { event -> if (event == reenterAt) b.publish(result.copy(content = "B"), CopyResultPolicy.STANDARD) }
            assertEquals(CopyPublicationOutcome.Published(emptyList()), a.publish(result.copy(content = "A"), CopyResultPolicy.COLLECTION))
            assertEquals(listOf("clipboard", "analytics", "notification", "status", "review"), aEffects.events)
            assertEquals(1, aEffects.analyticsRecords.size)
            assertEquals(listOf("B"), bEffects.statusUpdates)
            assertTrue(aEffects.statusUpdates.isEmpty())
        }
    }

    @Test fun `reentrant validation cannot overwrite a newer request and disposed feedback stays guarded`() {
        val coordinator = ClipboardRequestCoordinator()
        val request = coordinator.beginRequest()
        var wrote = false
        assertEquals(CopyNotPublishedReason.STALE, coordinator.writeIfCurrent(request, {
            coordinator.beginRequest(); null
        }) { wrote = true })
        assertFalse(wrote)
        var alive = true
        val effects = RecordingSideEffects()
        val publisher = CopyResultPublisher.createForTest(effects, coordinator, { alive }) { CopyResultSettings(true, "pathline", 10) }
        effects.afterEvent = { if (it == "clipboard") alive = false }
        assertEquals(CopyPublicationOutcome.Published(emptyList()), publisher.publish(result, CopyResultPolicy.COLLECTION))
        assertEquals(listOf("clipboard", "analytics", "notification", "status", "review"), effects.events)
        assertTrue(effects.statusUpdates.isEmpty())
        assertEquals(1, effects.analyticsRecords.size)
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

    private class RecordingSideEffects(private val failAt: String? = null) : CopyResultSideEffects {
        var afterEvent: (String) -> Unit = {}
        val events = mutableListOf<String>()
        val clipboardWrites = mutableListOf<String>()
        val analyticsRecords = mutableListOf<Pair<String, String>>()
        val highlightedRanges = mutableListOf<List<Pair<Int, Int>>>()
        val historyRecords = mutableListOf<Pair<String, Int>>()
        val statusUpdates = mutableListOf<String>()

        override fun writeClipboard(content: String) {
            events += "clipboard"
            fail("clipboard")
            clipboardWrites += content
            afterEvent("clipboard")
        }

        override fun recordAnalytics(format: String, language: String) {
            events += "analytics"
            fail("analytics")
            analyticsRecords += Pair(format, language)
            afterEvent("analytics")
        }

        override fun updateGutterHighlight(editor: Editor, lineRanges: List<Pair<Int, Int>>) {
            events += "highlight"
            highlightedRanges += lineRanges
        }

        override fun addToHistory(content: String, maxSize: Int) {
            events += "history"
            historyRecords += Pair(content, maxSize)
        }

        override fun showNotification(content: String, isCurrent: () -> Boolean) {
            events += "notification"
            fail("notification")
            if (isCurrent()) afterEvent("notification")
        }

        override fun updateStatusBar(content: String, isCurrent: () -> Boolean) {
            events += "status"
            fail("status")
            if (isCurrent()) statusUpdates += content
        }

        override fun recordReviewEligibleCopy() {
            events += "review"
            fail("review")
        }
        private fun fail(effect: String) { if (failAt == effect) throw IllegalStateException("private source must never escape") }
    }
}
