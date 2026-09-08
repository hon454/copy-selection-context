package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CopyFailureReporterTest {
    private val failed = CopyPublicationOutcome.NotPublished(CopyNotPublishedReason.CLIPBOARD_FAILURE)

    @Test fun `only a current failed attempt can claim an error and every new request resets the claim`() {
        val coordinator = ClipboardRequestCoordinator()
        val unattempted = coordinator.beginRequest()
        assertFalse(coordinator.claimFailureReport(unattempted))
        coordinator.writeIfCurrent(unattempted, { null }) {}
        assertFalse(coordinator.claimFailureReport(unattempted))
        repeat(2) {
            val request = failWrite(coordinator)
            assertTrue(coordinator.claimFailureReport(request))
            assertFalse(coordinator.claimFailureReport(request))
            assertEquals(CopyNotPublishedReason.ALREADY_ATTEMPTED,
                coordinator.writeIfCurrent(request, { error("No validation retry") }) { error("No write retry") })
            assertFalse(coordinator.claimFailureReport(unattempted))
        }
    }

    @Test fun `duplicate reports from distinct reporters and reentrant presentation reserve only one error`() {
        val coordinator = ClipboardRequestCoordinator()
        val request = failWrite(coordinator)
        val queue = ArrayDeque<() -> Unit>()
        var shown = 0
        lateinit var reporter: CopyFailureReporter
        reporter = CopyFailureReporter.createForTest(coordinator, dispatch = queue::addLast, showError = { isCurrent ->
            assertTrue(isCurrent())
            shown++
            reporter.report(request, failed)
        })
        val other = CopyFailureReporter.createForTest(coordinator, dispatch = queue::addLast, showError = { error("Duplicate") })
        repeat(2) { reporter.report(request, failed); other.report(request, failed) }
        assertEquals(1, queue.size)
        queue.removeFirst().invoke()
        assertEquals(1, shown)
        assertTrue(queue.isEmpty())
    }

    @Test fun `every non clipboard outcome including optional feedback failure stays silent`() {
        val coordinator = ClipboardRequestCoordinator()
        val request = failWrite(coordinator)
        val reporter = CopyFailureReporter.createForTest(coordinator,
            dispatch = { error("Must not dispatch") }, showError = { error("Must not display") })
        CopyNotPublishedReason.entries.filter { it != CopyNotPublishedReason.CLIPBOARD_FAILURE }.forEach {
            reporter.report(request, CopyPublicationOutcome.NotPublished(it))
        }
        reporter.report(request, CopyPublicationOutcome.Published(emptyList()))
        reporter.report(request, CopyPublicationOutcome.Published(listOf(CopyFeedbackFailure(CopyFeedbackEffect.STATUS, "IllegalStateException"))))
        assertTrue(coordinator.claimFailureReport(request))
    }

    @Test fun `dispatch and visible boundary both guard identity and project or owner lifetime`() {
        for (invalidateAt in listOf("before report", "queued", "presentation")) {
            for (invalidate in listOf("request", "project", "owner")) {
                val coordinator = ClipboardRequestCoordinator()
                val request = failWrite(coordinator)
                val queue = ArrayDeque<() -> Unit>()
                var projectAlive = true
                var ownerAlive = true
                var shown = 0
                fun invalidate() {
                    when (invalidate) {
                        "request" -> coordinator.beginRequest()
                        "project" -> projectAlive = false
                        else -> ownerAlive = false
                    }
                }
                val reporter = CopyFailureReporter.createForTest(coordinator, { projectAlive }, queue::addLast, { current ->
                    if (invalidateAt == "presentation") invalidate()
                    if (current()) shown++
                })
                if (invalidateAt == "before report") invalidate()
                reporter.report(request, failed) { ownerAlive }
                if (invalidateAt == "queued") invalidate()
                while (queue.isNotEmpty()) queue.removeFirst().invoke()
                assertEquals(0, shown, "$invalidateAt / $invalidate")
            }
        }
    }

    @Test fun `old throwing write cannot give a reentrant new request a failed state`() {
        val coordinator = ClipboardRequestCoordinator()
        val old = coordinator.beginRequest()
        lateinit var next: CopyResultRequest
        coordinator.writeIfCurrent(old, { null }) {
            next = coordinator.beginRequest()
            throw IllegalStateException("private content")
        }
        assertFalse(coordinator.claimFailureReport(old))
        assertFalse(coordinator.claimFailureReport(next))
        assertEquals(null, coordinator.writeIfCurrent(next, { null }) {})
        assertFalse(coordinator.claimFailureReport(next))
    }

    @Test fun `platform and coroutine cancellation consume the token without clipboard error or retry`() {
        for (cancelled in listOf(ProcessCanceledException(), CancellationException("private content"))) {
            val coordinator = ClipboardRequestCoordinator()
            val request = coordinator.beginRequest()
            val thrown = assertFailsWith<RuntimeException> {
                coordinator.writeIfCurrent(request, { null }) { throw cancelled }
            }
            assertSame(cancelled, thrown)
            assertFalse(coordinator.claimFailureReport(request))
            assertEquals(CopyNotPublishedReason.ALREADY_ATTEMPTED,
                coordinator.writeIfCurrent(request, { error("No retry") }) { error("No retry") })
        }
    }

    @Test fun `application coordinator retains only scalar sequence and attempt state`() {
        val state = ClipboardRequestCoordinator::class.java.declaredFields.filterNot {
            java.lang.reflect.Modifier.isStatic(it.modifiers)
        }
        assertTrue(state.isNotEmpty())
        assertTrue(state.all { it.type.isPrimitive }, state.joinToString { "${it.name}: ${it.type}" })
    }

    private fun failWrite(coordinator: ClipboardRequestCoordinator): CopyResultRequest {
        val request = coordinator.beginRequest()
        assertEquals(CopyNotPublishedReason.CLIPBOARD_FAILURE,
            coordinator.writeIfCurrent(request, { null }) { throw IllegalStateException("private code/path/credential") })
        return request
    }
}
