package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.KeyStroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopySelectionShortcutIntroductionTest {
    @Test
    fun `first claim wins once and persisted restart remains introduced`() {
        val firstSession = CopySelectionShortcutIntroduction()

        assertTrue(firstSession.claimIfFirst())
        assertFalse(firstSession.claimIfFirst())

        val restarted = CopySelectionShortcutIntroduction().apply { loadState(firstSession.state) }
        assertFalse(restarted.claimIfFirst())
        assertTrue(restarted.state.introduced)
    }

    @Test
    fun `getState and loadState do not retain mutable aliases`() {
        val service = CopySelectionShortcutIntroduction()
        val loaded = CopySelectionShortcutIntroduction.State(introduced = true)

        service.loadState(loaded)
        loaded.introduced = false
        val returned = service.state
        returned.introduced = false

        assertTrue(service.state.introduced)
    }

    @Test
    fun `concurrent callers produce exactly one claim winner`() {
        val service = CopySelectionShortcutIntroduction()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (1..64).map {
                executor.submit<Boolean> {
                    start.await()
                    service.claimIfFirst()
                }
            }

            start.countDown()

            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(service.state.introduced)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `state is application scoped local and contains only the introduction flag`() {
        val service = requireNotNull(CopySelectionShortcutIntroduction::class.java.getAnnotation(Service::class.java))
        val state = requireNotNull(CopySelectionShortcutIntroduction::class.java.getAnnotation(State::class.java))

        assertEquals(listOf(Service.Level.APP), service.value.toList())
        assertEquals("CopySelectionShortcutIntroduction", state.name)
        assertEquals(1, state.storages.size)
        assertEquals("copySelectionShortcuts.xml", state.storages.single().value)
        assertEquals(RoamingType.DISABLED, state.storages.single().roamingType)
        assertEquals(
            setOf("introduced"),
            CopySelectionShortcutIntroduction.State::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("\$") }
                .toSet(),
        )
    }

    @Test
    fun `presentation uses the shared prefix and actual active copy assignment`() {
        val custom = KeyboardShortcut(KeyStroke.getKeyStroke("control K"), null)
        val keymap = keymap(arrayOf(custom))

        val presentation = CopySelectionShortcutIntroduction().presentation(keymap)
        val sharedDefault = CopySelectionShortcuts.defaultShortcuts(keymap)
            .getValue(CopySelectionShortcutIntroduction.COPY_ACTION_ID)

        assertEquals(
            CopyPreview.notification(KeymapUtil.getKeystrokeText(sharedDefault.firstKeyStroke)),
            presentation.defaultPrefix,
        )
        assertEquals(CopyPreview.notification(KeymapUtil.getShortcutText(custom)), presentation.currentCopy)
        assertNotEquals(CopyPreview.notification(KeymapUtil.getShortcutText(sharedDefault)), presentation.currentCopy)
    }

    @Test
    fun `unassigned copy remains explicitly unassigned instead of falling back to defaults`() {
        val keymap = keymap(emptyArray())

        val presentation = CopySelectionShortcutIntroduction().presentation(keymap)

        assertEquals(
            CopyPreview.notification(CopySelectionBundle.message("shortcuts.intro.unassigned")),
            presentation.currentCopy,
        )
    }

    @Test
    fun `long hostile shortcut text is bounded flattened and markup escaped`() {
        val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke("control K"), null)
        val keymap = keymap(Array<Shortcut>(12) { shortcut })
        val hostile = "<shortcut>&\"'\n" + "x".repeat(240) + "\uD83E\uDDEA"

        val presentation = CopySelectionShortcutIntroduction().presentation(keymap) { hostile }

        assertTrue(presentation.currentCopy.length <= CopyPreview.NOTIFICATION_MAX_LENGTH)
        assertTrue(presentation.currentCopy.contains("&lt;shortcut&gt;"))
        assertTrue(presentation.currentCopy.contains("&amp;"))
        assertFalse(presentation.currentCopy.contains("<shortcut>"))
        assertFalse(presentation.currentCopy.any { it == '\n' || it == '\r' })
        assertTrue(presentation.currentCopy.endsWith("…"))
    }

    @Test
    fun `settings destinations use exact configurable ids`() {
        assertEquals("CopySelectionContext.Settings", CopySelectionShortcutIntroduction.PLUGIN_SETTINGS_ID)
        assertEquals("preferences.keymap", CopySelectionShortcutIntroduction.KEYMAP_SETTINGS_ID)
    }

    private fun keymap(shortcuts: Array<Shortcut>): Keymap = mockk<Keymap>().also { keymap ->
        every { keymap.name } returns KeymapManager.DEFAULT_IDEA_KEYMAP
        every { keymap.parent } returns null
        every { keymap.getShortcuts(CopySelectionShortcutIntroduction.COPY_ACTION_ID) } returns shortcuts
    }
}
