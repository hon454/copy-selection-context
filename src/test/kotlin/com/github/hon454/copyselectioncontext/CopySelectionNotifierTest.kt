package com.github.hon454.copyselectioncontext

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionNotifierTest {

    private lateinit var mockSettings: CopySelectionSettings

    @BeforeEach
    fun setUp() {
        mockSettings = mockk()
        mockkObject(CopySelectionSettings.Companion)
        every { CopySelectionSettings.getInstance() } returns mockSettings
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CopySelectionSettings.Companion)
    }

    @Test
    fun `notify returns early when project is null`() {
        val mockState = mockk<CopySelectionSettings.State>()
        every { mockState.enableNotification } returns true
        every { mockSettings.state } returns mockState
        
        CopySelectionNotifier.notify(null, "test message")
    }

    @Test
    fun `notify checks enableNotification setting`() {
        val mockState = mockk<CopySelectionSettings.State>()
        every { mockState.enableNotification } returns false
        every { mockSettings.state } returns mockState
        
        val mockProject = mockk<com.intellij.openapi.project.Project>()
        
        CopySelectionNotifier.notify(mockProject, "test message")
    }

    @Test
    fun `notification text uses a safe bounded preview`() {
        val message = "src/App.kt:10-20\n<script>😀 ${"x".repeat(1_000)}</script>"

        val notification = CopySelectionNotifier.notificationText(message)

        assertTrue(notification.contains("src/App.kt:10-20"))
        assertTrue(notification.length <= CopyPreview.NOTIFICATION_MAX_LENGTH + 20)
        assertFalse(notification.any { it == '\n' || it == '\r' })
        assertFalse(notification.contains("<script>"))
        assertTrue(notification.contains("&lt;script&gt;"))
    }

    @Test
    fun `each permalink failure reason resolves distinct actionable guidance`() {
        val messages = GitPermalinkFailureReason.entries.associateWith(CopySelectionNotifier::permalinkFailureText)

        assertEquals(GitPermalinkFailureReason.entries.size, messages.values.toSet().size)
        messages.values.forEach { message ->
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("notification.permalink.failed"))
        }
    }
}
