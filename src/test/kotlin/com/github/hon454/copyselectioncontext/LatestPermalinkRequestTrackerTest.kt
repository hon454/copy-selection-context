package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LatestPermalinkRequestTrackerTest {
    @Test
    fun `stale completion cannot apply after newer request starts`() {
        val tracker = LatestPermalinkRequestTracker()
        val sideEffects = CopyOnWriteArrayList<String>()
        val releaseFirstCompletion = CountDownLatch(1)
        val firstCompletionFinished = CountDownLatch(1)
        val firstRequest = tracker.begin()
        var firstApplied = true

        val firstWorker = thread(name = "stale-permalink-completion") {
            releaseFirstCompletion.await()
            firstApplied = tracker.runIfCurrent(firstRequest) {
                sideEffects += "first"
            }
            firstCompletionFinished.countDown()
        }

        val secondRequest = tracker.begin()
        val secondApplied = tracker.runIfCurrent(secondRequest) {
            sideEffects += "second"
        }
        releaseFirstCompletion.countDown()

        assertTrue(firstCompletionFinished.await(5, TimeUnit.SECONDS))
        firstWorker.join()
        assertTrue(secondApplied)
        assertFalse(firstApplied)
        assertEquals(listOf("second"), sideEffects)
    }
}
