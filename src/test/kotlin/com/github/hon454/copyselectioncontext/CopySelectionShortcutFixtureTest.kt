package com.github.hon454.copyselectioncontext

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke
import org.jdom.Element

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

    fun testPersistedUserChoicesSurviveDefaultUpgradeAcrossAllCommands() {
        val oldParent = keymap(PARENT_NAME)
        val oldCopy = keyboard("control alt C")
        val oldHistory = keyboard("control alt H")
        val removedIdeShortcut = keyboard("control alt I")
        oldParent.addShortcut(COPY, oldCopy)
        oldParent.addShortcut(HISTORY, oldHistory)
        oldParent.addShortcut(REMOVED_IDE_ACTION, removedIdeShortcut)
        oldParent.canModify = false

        val customKeyboard = keyboard("control shift A")
        val customMouse = MouseShortcut(2, 0, 1)
        val mouseOnly = MouseShortcut(3, 0, 1)
        val unrelatedKeyboard = keyboard("control Q")
        val staged = oldParent.deriveKeymap("Persisted user keymap").apply {
            addShortcut(ADD_TO_COLLECTION, customKeyboard)
            addShortcut(ADD_TO_COLLECTION, customMouse)
            addShortcut(COPY_ALL_COLLECTION, mouseOnly)
            removeAllActionShortcuts(REMOVED_IDE_ACTION)
            addShortcut(UNRELATED_ACTION, unrelatedKeyboard)
        }
        val persisted = staged.writeScheme().apply {
            replaceAction(COPY, oldCopy)
            replaceAction(HISTORY, oldHistory)
            replaceAction(SHOW_COLLECTION)
        }

        val before = loadPersisted(persisted, oldParent)
        val beforeCommands = shortcutSnapshot(before, CopySelectionShortcuts.commands.keys)
        assertEquals(listOf(oldCopy), beforeCommands.getValue(COPY))
        assertEquals(listOf(oldHistory), beforeCommands.getValue(HISTORY))
        assertEquals(listOf(customKeyboard, customMouse), beforeCommands.getValue(ADD_TO_COLLECTION))
        assertEmpty(beforeCommands.getValue(SHOW_COLLECTION))
        assertEquals(listOf(mouseOnly), beforeCommands.getValue(COPY_ALL_COLLECTION))
        assertEmpty(before.getShortcuts(REMOVED_IDE_ACTION).toList())
        assertEquals(listOf(unrelatedKeyboard), before.getShortcuts(UNRELATED_ACTION).toList())

        val newParent = keymap(PARENT_NAME).apply {
            CopySelectionShortcuts.defaultShortcuts(mac = false).forEach { (actionId, shortcut) ->
                addShortcut(actionId, shortcut)
            }
            addShortcut(REMOVED_IDE_ACTION, removedIdeShortcut)
            canModify = false
        }
        val after = loadPersisted(persisted, newParent)
        val afterCommands = shortcutSnapshot(after, CopySelectionShortcuts.commands.keys)
        val explicitlyPersisted = setOf(
            COPY,
            HISTORY,
            ADD_TO_COLLECTION,
            SHOW_COLLECTION,
            COPY_ALL_COLLECTION,
        )
        val expectedAfter = CopySelectionShortcuts.commands.keys.associateWith { actionId ->
            if (actionId in explicitlyPersisted) {
                beforeCommands.getValue(actionId)
            } else {
                listOf(CopySelectionShortcuts.defaultShortcuts(mac = false).getValue(actionId))
            }
        }

        assertEquals(expectedAfter, afterCommands)
        assertEmpty(after.getShortcuts(REMOVED_IDE_ACTION).toList())
        assertEquals(listOf(unrelatedKeyboard), after.getShortcuts(UNRELATED_ACTION).toList())
    }

    fun testPersistedGPrefixDistinguishesInheritedAndExplicitAssignments() {
        val oldDefaults = CopySelectionShortcuts.commands.mapValues { (_, secondKey) ->
            twoStroke("control alt shift G", secondKey)
        }
        val oldParent = keymap(G_PREFIX_PARENT_NAME).apply {
            oldDefaults.forEach { (actionId, shortcut) -> addShortcut(actionId, shortcut) }
            canModify = false
        }
        val unrelatedKeyboard = keyboard("control Q")
        val staged = oldParent.deriveKeymap("Persisted G-prefix user keymap").apply {
            addShortcut(UNRELATED_ACTION, unrelatedKeyboard)
        }
        val persisted = staged.writeScheme().apply {
            replaceAction(COPY, oldDefaults.getValue(COPY))
        }

        val before = loadPersisted(persisted, oldParent)
        val beforeCommands = shortcutSnapshot(before, CopySelectionShortcuts.commands.keys)
        assertEquals(oldDefaults.mapValues { (_, shortcut) -> listOf(shortcut) }, beforeCommands)

        val newDefaults = CopySelectionShortcuts.defaultShortcuts(mac = false)
        val newParent = keymap(G_PREFIX_PARENT_NAME).apply {
            newDefaults.forEach { (actionId, shortcut) ->
                addShortcut(actionId, shortcut)
            }
            canModify = false
        }
        val after = loadPersisted(persisted, newParent)
        val expectedAfter = CopySelectionShortcuts.commands.keys.associateWith { actionId ->
            listOf(
                if (actionId == COPY) {
                    oldDefaults.getValue(actionId)
                } else {
                    newDefaults.getValue(actionId)
                },
            )
        }

        assertEquals(expectedAfter, shortcutSnapshot(after, CopySelectionShortcuts.commands.keys))
        assertEquals(listOf(unrelatedKeyboard), after.getShortcuts(UNRELATED_ACTION).toList())
        CopySelectionShortcuts.commands.keys.filterNot { it == COPY }.forEach { actionId ->
            assertFalse(after.getShortcuts(actionId).contains(oldDefaults.getValue(actionId)))
        }
    }

    private fun loadPersisted(element: Element, parent: Keymap): Keymap = PersistedKeymap(parent, element)

    private fun shortcutSnapshot(keymap: Keymap, actionIds: Collection<String>): Map<String, List<Shortcut>> =
        actionIds.associateWith { actionId -> keymap.getShortcuts(actionId).toList() }

    private fun Element.replaceAction(actionId: String, vararg shortcuts: KeyboardShortcut) {
        getChildren("action").firstOrNull { it.getAttributeValue("id") == actionId }?.let(::removeContent)
        addContent(Element("action").setAttribute("id", actionId).apply {
            shortcuts.forEach { shortcut ->
                addContent(Element("keyboard-shortcut").apply {
                    setAttribute("first-keystroke", shortcut.firstKeyStroke.toString())
                    shortcut.secondKeyStroke?.let { setAttribute("second-keystroke", it.toString()) }
                })
            }
        })
    }

    private class PersistedKeymap(
        private val resolvedParent: Keymap,
        element: Element,
    ) : KeymapImpl(
        object : SchemeDataHolder<KeymapImpl> {
            override fun read(): Element = element.clone()
        },
    ) {
        init {
            name = element.getAttributeValue("name")
        }

        override fun findParentScheme(parentSchemeName: String): Keymap? =
            resolvedParent.takeIf { it.name == parentSchemeName }
    }

    private fun keymap(name: String) = KeymapImpl().apply { this.name = name }

    private fun keyboard(stroke: String) = KeyboardShortcut(KeyStroke.getKeyStroke(stroke), null)

    private fun twoStroke(first: String, second: String) = KeyboardShortcut(
        KeyStroke.getKeyStroke(first),
        KeyStroke.getKeyStroke(second),
    )

    companion object {
        private const val PARENT_NAME = "Synthetic plugin defaults"
        private const val G_PREFIX_PARENT_NAME = "Synthetic G-prefix plugin defaults"
        private const val COPY = "CopySelectionContext.Copy"
        private const val HISTORY = "CopySelectionContext.ShowHistory"
        private const val ADD_TO_COLLECTION = "CopySelectionContext.AddToCollection"
        private const val SHOW_COLLECTION = "CopySelectionContext.ShowCollection"
        private const val COPY_ALL_COLLECTION = "CopySelectionContext.CopyAllCollection"
        private const val REMOVED_IDE_ACTION = "IntroduceConstant"
        private const val UNRELATED_ACTION = "Unrelated.Action"
    }
}
