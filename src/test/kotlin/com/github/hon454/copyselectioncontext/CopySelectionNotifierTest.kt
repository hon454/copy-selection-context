package com.github.hon454.copyselectioncontext

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
}
