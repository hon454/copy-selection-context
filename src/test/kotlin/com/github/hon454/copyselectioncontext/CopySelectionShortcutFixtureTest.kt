package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class CopySelectionShortcutFixtureTest : BasePlatformTestCase() {
    fun testRegisteredDefaultsUseOneTwoStrokeShortcutPerCommand() {
        val manager = KeymapManager.getInstance()
        val requiredKeymaps = listOf(
            "\$default",
            "Mac OS X",
            "Mac OS X 10.5+",
            "macOS System Shortcuts",
            "Sublime Text (Mac OS X)",
        )

        requiredKeymaps.forEach { keymapId ->
            val keymap = requireNotNull(manager.getKeymap(keymapId))
            val expected = CopySelectionShortcuts.defaultShortcuts(keymap, macHost = false)
            expected.forEach { (actionId, shortcut) ->
                val keyboardShortcuts = keymap.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>()
                assertEquals("$keymapId / $actionId", listOf(shortcut), keyboardShortcuts)
                assertNotNull(keyboardShortcuts.single().secondKeyStroke)
            }
        }
    }

    fun testKnownKeymapParentsResolveToTheirDeclaredFamilies() {
        val manager = KeymapManager.getInstance()
        val expectedParents = mapOf(
            "Mac OS X" to "\$default",
            "Mac OS X 10.5+" to "\$default",
            "macOS System Shortcuts" to "Mac OS X 10.5+",
            "Sublime Text (Mac OS X)" to "Mac OS X 10.5+",
        )
        expectedParents.forEach { (keymapId, parentId) ->
            val keymap = requireNotNull(manager.getKeymap(keymapId))
            assertEquals(parentId, keymap.parent?.name)
            assertTrue(keymapId, CopySelectionShortcuts.usesMacKeymap(keymap, macHost = false))
        }

        CopySelectionShortcuts.macKeymapIds.mapNotNull(manager::getKeymap).forEach { keymap ->
            assertTrue(keymap.name, CopySelectionShortcuts.usesMacKeymap(keymap, macHost = false))
        }
    }

    fun testActiveKeymapLineageWinsOverTheHostOperatingSystem() {
        val manager = KeymapManagerEx.getInstanceEx()
        val original = manager.activeKeymap
        val windowsKeymap = requireNotNull(manager.getKeymap("\$default"))
        val macKeymap = requireNotNull(manager.getKeymap("Mac OS X 10.5+"))
        try {
            manager.setActiveKeymap(windowsKeymap)
            assertEquals(
                CopySelectionShortcuts.defaultShortcuts(mac = false),
                CopySelectionShortcuts.activeDefaultShortcuts(manager, macHost = true),
            )

            manager.setActiveKeymap(macKeymap)
            assertEquals(
                CopySelectionShortcuts.defaultShortcuts(mac = true),
                CopySelectionShortcuts.activeDefaultShortcuts(manager, macHost = false),
            )
        } finally {
            manager.setActiveKeymap(original)
        }
        assertSame(original, manager.activeKeymap)
    }

    fun testUnknownIndependentKeymapUsesHostFallbackOnlyWithoutKnownLineage() {
        val independent = keymap("Independent")
        assertFalse(CopySelectionShortcuts.usesMacKeymap(independent, macHost = false))
        assertTrue(CopySelectionShortcuts.usesMacKeymap(independent, macHost = true))

        val windowsChild = keymap("\$default").apply { canModify = false }.deriveKeymap("Windows child")
        assertFalse(CopySelectionShortcuts.usesMacKeymap(windowsChild, macHost = true))

        val macChild = keymap("Mac OS X 10.5+").apply { canModify = false }.deriveKeymap("Mac child")
        assertTrue(CopySelectionShortcuts.usesMacKeymap(macChild, macHost = false))
    }

    fun testDefaultInheritancePreservesOverridesUnassignedAndUnrelatedEdits() {
        val parent = keymap("Old plugin defaults")
        val oldCopy = keyboard("control alt C")
        val oldHistory = keyboard("control alt H")
        parent.addShortcut(COPY, oldCopy)
        parent.addShortcut(HISTORY, oldHistory)
        parent.canModify = false

        val untouched = parent.deriveKeymap("Untouched")
        val unrelated = parent.deriveKeymap("Unrelated edits").apply {
            addShortcut("Unrelated.Action", keyboard("control Q"))
        }
        val explicitlyUnassigned = parent.deriveKeymap("Explicitly unassigned").apply {
            removeAllActionShortcuts(COPY)
            removeAllActionShortcuts(HISTORY)
        }

        CopySelectionShortcuts.defaultShortcuts(mac = false).forEach { (actionId, shortcut) ->
            parent.removeAllActionShortcuts(actionId)
            parent.addShortcut(actionId, shortcut)
        }
        val explicitOld = parent.deriveKeymap("Explicit old defaults").apply {
            removeAllActionShortcuts(COPY)
            addShortcut(COPY, oldCopy)
            removeAllActionShortcuts(HISTORY)
            addShortcut(HISTORY, oldHistory)
        }

        assertPluginDefaults(untouched)
        assertPluginDefaults(unrelated)
        assertEquals(listOf(keyboard("control Q")), unrelated.getShortcuts("Unrelated.Action").toList())
        assertContainsElements(explicitOld.getShortcuts(COPY).toList(), oldCopy)
        assertContainsElements(explicitOld.getShortcuts(HISTORY).toList(), oldHistory)
        assertEmpty(explicitlyUnassigned.getShortcuts(COPY).toList())
        assertEmpty(explicitlyUnassigned.getShortcuts(HISTORY).toList())
    }

    private fun assertPluginDefaults(keymap: Keymap) {
        CopySelectionShortcuts.defaultShortcuts(mac = false).forEach { (actionId, shortcut) ->
            assertEquals(actionId, listOf(shortcut), keymap.getShortcuts(actionId).toList())
        }
    }

    private fun keymap(name: String) = KeymapImpl().apply { this.name = name }

    private fun keyboard(stroke: String) = KeyboardShortcut(KeyStroke.getKeyStroke(stroke), null)

    companion object {
        private const val COPY = "CopySelectionContext.Copy"
        private const val HISTORY = "CopySelectionContext.ShowHistory"
    }
}
