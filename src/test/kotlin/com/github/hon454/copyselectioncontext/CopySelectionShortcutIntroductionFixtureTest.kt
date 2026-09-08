package com.github.hon454.copyselectioncontext

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.KeyStroke

class CopySelectionShortcutIntroductionFixtureTest : BasePlatformTestCase() {
    private lateinit var introduction: CopySelectionShortcutIntroduction
    private lateinit var originalIntroductionState: CopySelectionShortcutIntroduction.State

    override fun setUp() {
        super.setUp()
        introduction = CopySelectionShortcutIntroduction.getInstance()
        originalIntroductionState = introduction.state
        introduction.loadState(CopySelectionShortcutIntroduction.State())
    }

    override fun tearDown() {
        try {
            introduction.loadState(originalIntroductionState)
        } finally {
            super.tearDown()
        }
    }

    fun testProductionEntryDoesNotClaimOrNotifyInTestEnvironment() {
        assertFalse(introduction.show(project))
        assertFalse(introduction.state.introduced)
    }

    fun testIneligibleRuntimeAndDisposedProjectNeverConsumeTheClaim() {
        val activeKeymap = KeymapManager.getInstance().activeKeymap
        val cases = listOf(
            liveProject() to RecordingRuntime(activeKeymap, unitTestMode = true),
            liveProject() to RecordingRuntime(activeKeymap, headlessEnvironment = true),
            liveProject() to RecordingRuntime(activeKeymap = null),
            disposedProject() to RecordingRuntime(activeKeymap),
        )

        cases.forEach { (owner, runtime) ->
            val service = CopySelectionShortcutIntroduction()
            assertFalse(service.show(owner, runtime))
            assertFalse(service.state.introduced)
            assertEmpty(runtime.notifications)
            assertEmpty(runtime.publishedProjects)
        }
    }

    fun testApplicationClaimPublishesOnceAcrossProjectsAndIgnoresCopyNotificationPreference() {
        val settings = CopySelectionSettings.getInstance()
        val originalSettings = settings.state.copy()
        val runtime = RecordingRuntime(KeymapManager.getInstance().activeKeymap)
        val secondProject = liveProject()
        try {
            settings.loadState(originalSettings.copy(enableNotification = false))

            assertTrue(introduction.show(project, runtime))
            assertFalse(introduction.show(secondProject, runtime))

            assertTrue(introduction.state.introduced)
            assertEquals(listOf(project), runtime.publishedProjects)
            assertEquals(1, runtime.notifications.size)
            assertEquals(NotificationType.INFORMATION, runtime.notifications.single().type)
            assertEquals(
                CopySelectionShortcutIntroduction.NOTIFICATION_GROUP_ID,
                runtime.notifications.single().groupId,
            )
            assertFalse(settings.state.enableNotification)
        } finally {
            settings.loadState(originalSettings)
        }
    }

    fun testProjectDisposedDuringNotificationConstructionDoesNotConsumeTheClaim() {
        val disposed = AtomicBoolean(false)
        val owner = project(disposed)
        val runtime = RecordingRuntime(KeymapManager.getInstance().activeKeymap).apply {
            afterCreate = { disposed.set(true) }
        }

        assertFalse(introduction.show(owner, runtime))

        assertFalse(introduction.state.introduced)
        assertEquals(1, runtime.notifications.size)
        assertEmpty(runtime.publishedProjects)
    }

    fun testAllNotificationActionsRecheckProjectLifetimeAndExpireOnlyWhileLive() {
        val settings = fireAction(actionIndex = 0, disposeBeforeAction = false)
        assertEquals(1, settings.runtime.pluginSettingsOpenCount)
        assertEquals(0, settings.runtime.keymapSettingsOpenCount)
        assertTrue(settings.notification.isExpired)

        val keymap = fireAction(actionIndex = 1, disposeBeforeAction = false)
        assertEquals(0, keymap.runtime.pluginSettingsOpenCount)
        assertEquals(1, keymap.runtime.keymapSettingsOpenCount)
        assertTrue(keymap.notification.isExpired)

        val dismiss = fireAction(actionIndex = 2, disposeBeforeAction = false)
        assertEquals(0, dismiss.runtime.pluginSettingsOpenCount)
        assertEquals(0, dismiss.runtime.keymapSettingsOpenCount)
        assertTrue(dismiss.notification.isExpired)

        (0..2).forEach { actionIndex ->
            val disposed = fireAction(actionIndex, disposeBeforeAction = true)
            assertEquals(0, disposed.runtime.pluginSettingsOpenCount)
            assertEquals(0, disposed.runtime.keymapSettingsOpenCount)
            assertFalse(disposed.notification.isExpired)
        }
    }

    fun testActiveCustomAndUnassignedCopyPresentationNeverMutatesTheKeymap() {
        val manager = KeymapManagerEx.getInstanceEx()
        val originalKeymap = manager.activeKeymap
        val customKeymap = originalKeymap.deriveKeymap("Shortcut introduction fixture ${System.nanoTime()}")
        manager.schemeManager.addScheme(customKeymap)
        manager.setActiveKeymap(customKeymap)
        try {
            val custom = KeyboardShortcut(KeyStroke.getKeyStroke("control K"), null)
            customKeymap.removeAllActionShortcuts(CopySelectionShortcutIntroduction.COPY_ACTION_ID)
            customKeymap.addShortcut(CopySelectionShortcutIntroduction.COPY_ACTION_ID, custom)
            val customSnapshot = shortcutSnapshot(customKeymap)
            val customService = CopySelectionShortcutIntroduction()
            val customRuntime = RecordingRuntime(manager.activeKeymap)
            val customPresentation = customService.presentation(customKeymap)

            assertTrue(customService.show(project, customRuntime))
            assertTrue(customRuntime.notifications.single().content.contains(customPresentation.defaultPrefix))
            assertTrue(customRuntime.notifications.single().content.contains(customPresentation.currentCopy))
            assertEquals(customSnapshot, shortcutSnapshot(customKeymap))
            assertSame(customKeymap, manager.activeKeymap)

            customKeymap.removeAllActionShortcuts(CopySelectionShortcutIntroduction.COPY_ACTION_ID)
            val unassignedSnapshot = shortcutSnapshot(customKeymap)
            val unassignedService = CopySelectionShortcutIntroduction()
            val unassignedRuntime = RecordingRuntime(manager.activeKeymap)
            val unassignedPresentation = unassignedService.presentation(customKeymap)

            assertTrue(unassignedService.show(project, unassignedRuntime))
            assertEquals(
                CopyPreview.notification(CopySelectionBundle.message("shortcuts.intro.unassigned")),
                unassignedPresentation.currentCopy,
            )
            assertTrue(unassignedRuntime.notifications.single().content.contains(unassignedPresentation.currentCopy))
            assertEquals(unassignedSnapshot, shortcutSnapshot(customKeymap))
            assertSame(customKeymap, manager.activeKeymap)
        } finally {
            manager.setActiveKeymap(originalKeymap)
            manager.schemeManager.removeScheme(customKeymap)
        }
        assertSame(originalKeymap, manager.activeKeymap)
    }

    private fun fireAction(actionIndex: Int, disposeBeforeAction: Boolean): ActionResult {
        val disposed = AtomicBoolean(false)
        val owner = project(disposed)
        val runtime = RecordingRuntime(KeymapManager.getInstance().activeKeymap)
        val service = CopySelectionShortcutIntroduction()
        assertTrue(service.show(owner, runtime))
        val notification = runtime.notifications.single()
        disposed.set(disposeBeforeAction)

        val action = notification.actions[actionIndex] as NotificationAction
        action.actionPerformed(mockk<AnActionEvent>(relaxed = true), notification)

        return ActionResult(runtime, notification)
    }

    private fun shortcutSnapshot(keymap: Keymap): Map<String, List<Shortcut>> =
        CopySelectionShortcuts.commands.keys.associateWith { actionId -> keymap.getShortcuts(actionId).toList() }

    private fun liveProject(): Project = project(AtomicBoolean(false))

    private fun disposedProject(): Project = project(AtomicBoolean(true))

    private fun project(disposed: AtomicBoolean): Project = mockk<Project>().also { project ->
        every { project.isDisposed } answers { disposed.get() }
    }

    private data class ActionResult(
        val runtime: RecordingRuntime,
        val notification: Notification,
    )

    private class RecordingRuntime(
        override val activeKeymap: Keymap?,
        override val unitTestMode: Boolean = false,
        override val headlessEnvironment: Boolean = false,
    ) : ShortcutIntroductionRuntime {
        val notifications = mutableListOf<Notification>()
        val publishedProjects = mutableListOf<Project>()
        var pluginSettingsOpenCount = 0
        var keymapSettingsOpenCount = 0
        var afterCreate: (() -> Unit)? = null

        override fun createNotification(title: String, content: String): Notification =
            Notification(
                CopySelectionShortcutIntroduction.NOTIFICATION_GROUP_ID,
                title,
                content,
                NotificationType.INFORMATION,
            ).also { notification ->
                notifications += notification
                afterCreate?.invoke()
            }

        override fun publish(notification: Notification, project: Project) {
            publishedProjects += project
        }

        override fun openPluginSettings(project: Project) {
            pluginSettingsOpenCount++
        }

        override fun openKeymapSettings(project: Project) {
            keymapSettingsOpenCount++
        }
    }
}
