package com.github.hon454.copyselectioncontext

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.KeyStroke
import org.jdom.Element

class CopySelectionShortcutFixtureTest : BasePlatformTestCase() {
    fun testRestoreMutableSourcePreservesAllAssignmentsAndExplicitInheritedRemoval() = withManager { manager ->
        listOf("\$default", "Mac OS X 10.5+").forEach { parentName ->
            val parent = requireNotNull(manager.getKeymap(parentName))
            val source = parent.deriveKeymap("Restore source $parentName") as KeymapImpl
            val defaults = CopySelectionShortcuts.defaultShortcuts(source)
            assertFalse(parent.getShortcuts("EditorCopy").isEmpty())
            source.removeAllActionShortcuts("EditorCopy")
            val mouse = MouseShortcut(2, 0, 1)
            defaults.keys.forEachIndexed { index, id ->
                source.removeAllActionShortcuts(id)
                if (index % 2 == 0) {
                    source.addShortcut(id, keyboard("control F10"))
                    source.addShortcut(id, keyboard("control F11"))
                }
                source.addShortcut(id, mouse)
            }
            source.addShortcut(UNRELATED_ACTION, keyboard("control Q"))
            source.addShortcut(UNRELATED_ACTION, mouse)
            register(manager, source)
            manager.setActiveKeymap(source)
            val original = allShortcuts(source)
            val originalXml = JDOMUtil.writeElement(source.writeScheme())
            val otherSchemes = manager.schemeManager.allSchemes.associateWith(::allShortcuts)

            CopySelectionShortcuts.applyRestore(ShortcutRestorePlan(source), manager)

            val restored = manager.activeKeymap as KeymapImpl
            assertNotSame(source, restored)
            assertSame(parent, restored.parent)
            assertEquals(original, allShortcuts(source))
            assertEquals(originalXml, JDOMUtil.writeElement(source.writeScheme()))
            otherSchemes.forEach { (scheme, shortcuts) -> assertEquals(shortcuts, allShortcuts(scheme)) }
            original.forEach { (id, shortcuts) ->
                val expected = defaults[id]?.let { shortcuts.filterNot { it is KeyboardShortcut } + it } ?: shortcuts
                assertEquals(id, expected, restored.getShortcuts(id).toList())
            }
            assertEmpty(restored.getShortcuts("EditorCopy").toList())
            val persisted = restored.writeScheme()
            val removal = persisted.getChildren("action").single { it.getAttributeValue("id") == "EditorCopy" }
            assertEmpty(removal.children)
            val reloaded = loadPersisted(persisted, parent)
            assertEquals(allShortcuts(restored), allShortcuts(reloaded))
        }
    }

    fun testRestoreReadonlyKeymapRetainsOriginalAndUsesItsFamilyDefaults() = withManager { manager ->
        listOf("\$default", "Mac OS X 10.5+").forEach { name ->
            val source = requireNotNull(manager.getKeymap(name))
            assertFalse(source.canModify())
            val original = allShortcuts(source)
            manager.setActiveKeymap(source)
            CopySelectionShortcuts.applyRestore(ShortcutRestorePlan(source), manager)
            val restored = manager.activeKeymap
            assertNotSame(source, restored)
            assertSame(source, restored.parent)
            assertTrue(restored.canModify())
            assertEquals(original, allShortcuts(source))
            CopySelectionShortcuts.defaultShortcuts(source).forEach { (id, shortcut) ->
                assertEquals(listOf(shortcut), restored.getShortcuts(id).filterIsInstance<KeyboardShortcut>())
            }
        }
    }

    fun testRestoreAvoidsEveryExistingSchemeName() = withManager { manager ->
        val source = register(manager, keymap("Unique restore source"))
        val occupied = listOf(
            register(manager, keymap("Unique restore source (Copy Selection Context)")),
            register(manager, keymap("Unique restore source (Copy Selection Context) 2")),
        )
        manager.setActiveKeymap(source)
        CopySelectionShortcuts.applyRestore(ShortcutRestorePlan(source), manager)
        assertEquals("Unique restore source (Copy Selection Context) 3", manager.activeKeymap.name)
        occupied.forEach { assertSame(it, manager.getKeymap(it.name)) }
        assertSame(source, manager.getKeymap(source.name))
    }

    fun testPreparationBlocksSingleAndOtherChordPrefixesWithoutChangingAnyScheme() = withManager { manager ->
        val source = register(manager, keymap("Prefix conflict source"))
        val defaults = CopySelectionShortcuts.defaultShortcuts(source)
        defaults.forEach { (id, shortcut) -> source.addShortcut(id, shortcut) }
        manager.setActiveKeymap(source)
        val proposed = defaults.values.first()
        val collisions = listOf(
            KeyboardShortcut(proposed.firstKeyStroke, null),
            proposed,
            KeyboardShortcut(proposed.firstKeyStroke, KeyStroke.getKeyStroke("Z")),
        )
        collisions.forEach { collision ->
            source.addShortcut("OtherPlugin.Action", collision)
            source.addShortcut("CopySelectionContext.ExternalAction", collision)
            val before = allShortcuts(source)
            val schemes = manager.schemeManager.allSchemes.toList()
            var confirmations = 0
            var shownConflicts = emptyList<String>()
            val plan = CopySelectionShortcuts.prepareRestore(
                manager, confirm = { confirmations++; true }, showConflicts = { shownConflicts = it },
            )
            assertNull(plan)
            assertEquals(0, confirmations)
            assertEquals(listOf("CopySelectionContext.ExternalAction", "OtherPlugin.Action"), shownConflicts)
            assertSame(source, manager.activeKeymap)
            assertEquals(schemes, manager.schemeManager.allSchemes)
            assertEquals(before, allShortcuts(source))
            source.removeAllActionShortcuts("OtherPlugin.Action")
            source.removeAllActionShortcuts("CopySelectionContext.ExternalAction")
        }
    }

    fun testExplicitlyRemovedInheritedPrefixConflictRemainsRemovedAfterRestore() = withManager { manager ->
        val parent = keymap("Conflict parent")
        val shortcut = CopySelectionShortcuts.defaultShortcuts(parent).values.first()
        parent.addShortcut(UNRELATED_ACTION, KeyboardShortcut(shortcut.firstKeyStroke, null))
        parent.canModify = false
        register(manager, parent)
        val source = register(manager, parent.deriveKeymap("Conflict child"))
        assertEquals(listOf(UNRELATED_ACTION), ShortcutRestorePlan(source).conflicts())
        source.removeAllActionShortcuts(UNRELATED_ACTION)
        assertEmpty(ShortcutRestorePlan(source).conflicts())
        manager.setActiveKeymap(source)
        CopySelectionShortcuts.applyRestore(ShortcutRestorePlan(source), manager)
        assertEmpty(manager.activeKeymap.getShortcuts(UNRELATED_ACTION).toList())
        assertEmpty(source.getShortcuts(UNRELATED_ACTION).toList())
        assertEquals(1, parent.getShortcuts(UNRELATED_ACTION).size)
    }

    fun testApplyRejectsChangedActiveIdentityEvenWithTheSameName() = withManager { manager ->
        val source = register(manager, keymap("Identity source"))
        manager.setActiveKeymap(source)
        val plan = ShortcutRestorePlan(source)
        assertThrows(ConfigurationException::class.java) { plan.validate(keymap(source.name)) }
        val other = register(manager, keymap("Other active keymap"))
        manager.setActiveKeymap(other)
        val schemes = manager.schemeManager.allSchemes.toList()
        assertThrows(ConfigurationException::class.java) { CopySelectionShortcuts.applyRestore(plan, manager) }
        assertSame(other, manager.activeKeymap)
        assertEquals(schemes, manager.schemeManager.allSchemes)
    }

    fun testApplyRechecksEveryCommandIncludingMouseOnlyChanges() = withManager { manager ->
        val source = register(manager, keymap("Changed assignment source"))
        manager.setActiveKeymap(source)
        val additions = listOf(keyboard("control F10"), MouseShortcut(2, 0, 1))
        CopySelectionShortcuts.commands.keys.forEach { id ->
            additions.forEach { addition ->
                val plan = ShortcutRestorePlan(source)
                val schemes = manager.schemeManager.allSchemes.toList()
                source.addShortcut(id, addition)
                assertThrows(ConfigurationException::class.java) { CopySelectionShortcuts.applyRestore(plan, manager) }
                assertSame(source, manager.activeKeymap)
                assertEquals(schemes, manager.schemeManager.allSchemes)
                source.removeShortcut(id, addition)
            }
        }
    }

    fun testApplyKeepsUnrelatedEditsMadeAfterConfirmation() = withManager { manager ->
        val source = register(manager, keymap("Unrelated edit source"))
        manager.setActiveKeymap(source)
        val plan = ShortcutRestorePlan(source)
        val unrelated = keyboard("control Q")
        source.addShortcut(UNRELATED_ACTION, unrelated)
        CopySelectionShortcuts.applyRestore(plan, manager)
        assertEquals(listOf(unrelated), manager.activeKeymap.getShortcuts(UNRELATED_ACTION).toList())
        assertEquals(listOf(unrelated), source.getShortcuts(UNRELATED_ACTION).toList())
    }

    fun testConfigurableStagesOnceAndCancelAfterApplyKeepsDerivedActive() = withManager { manager ->
        val source = register(manager, keymap("Configurable apply source"))
        manager.setActiveKeymap(source)
        val schemes = manager.schemeManager.allSchemes.toList()
        settingsFixture(manager).use { fixture ->
            fixture.restore.doClick()
            assertTrue(fixture.configurable.isModified())
            assertSame(source, manager.activeKeymap)
            assertEquals(schemes, manager.schemeManager.allSchemes)
            fixture.configurable.apply()
            val restored = manager.activeKeymap
            assertNotSame(source, restored)
            assertEquals(schemes.size + 1, manager.schemeManager.allSchemes.size)
            assertFalse(fixture.configurable.isModified())
            fixture.configurable.apply()
            fixture.configurable.cancel()
            fixture.configurable.reset()
            assertSame(restored, manager.activeKeymap)
            assertEquals(schemes.size + 1, manager.schemeManager.allSchemes.size)
        }
    }

    fun testConfigurableCancelResetDisposeAndCancelledReconfirmationNeverRegisterSchemes() = withManager { manager ->
        val source = register(manager, keymap("Configurable discard source"))
        manager.setActiveKeymap(source)
        val schemes = manager.schemeManager.allSchemes.toList()
        val discards: List<(CopySelectionConfigurable) -> Unit> = listOf(
            { it.reset() }, { it.cancel() }, { it.disposeUIResources() },
        )
        discards.forEach { discard ->
            settingsFixture(manager).use { fixture ->
                fixture.restore.doClick()
                discard(fixture.configurable)
                fixture.configurable.apply()
                assertFalse(fixture.configurable.isModified())
            }
        }
        var confirm = true
        settingsFixture(manager, confirm = { confirm }).use { fixture ->
            fixture.restore.doClick()
            confirm = false
            fixture.restore.doClick()
            fixture.configurable.apply()
            assertFalse(fixture.configurable.isModified())
        }
        assertSame(source, manager.activeKeymap)
        assertEquals(schemes, manager.schemeManager.allSchemes)
    }

    fun testConfigurableRejectsInvalidTemplateBeforeRestoreAndRetriesAfterCorrection() = withManager { manager ->
        val source = register(manager, keymap("Invalid template source"))
        manager.setActiveKeymap(source)
        val schemes = manager.schemeManager.allSchemes.toList()
        settingsFixture(manager).use { fixture ->
            fixture.restore.doClick()
            fixture.format.selectedItem = OutputFormatOption.TEMPLATE
            fixture.editor.text = "{unknown}"
            assertThrows(ConfigurationException::class.java) { fixture.configurable.apply() }
            assertSame(source, manager.activeKeymap)
            assertEquals(schemes, manager.schemeManager.allSchemes)
            assertEquals("claude", fixture.settings.state.outputFormat)
            fixture.editor.text = "{path}:{range}"
            fixture.configurable.apply()
            assertNotSame(source, manager.activeKeymap)
            assertEquals(schemes.size + 1, manager.schemeManager.allSchemes.size)
            assertEquals("template", fixture.settings.state.outputFormat)
        }
    }

    fun testConfigurableRechecksNewConflictBeforeApplyAndAllowsNewConfirmation() = withManager { manager ->
        val source = register(manager, keymap("New conflict source"))
        manager.setActiveKeymap(source)
        val prefix = CopySelectionShortcuts.defaultShortcuts(source).values.first().firstKeyStroke
        val schemes = manager.schemeManager.allSchemes.toList()
        settingsFixture(manager).use { fixture ->
            fixture.restore.doClick()
            source.addShortcut(UNRELATED_ACTION, KeyboardShortcut(prefix, KeyStroke.getKeyStroke("Z")))
            assertThrows(ConfigurationException::class.java) { fixture.configurable.apply() }
            assertSame(source, manager.activeKeymap)
            assertEquals(schemes, manager.schemeManager.allSchemes)
            source.removeAllActionShortcuts(UNRELATED_ACTION)
            fixture.configurable.reset()
            fixture.restore.doClick()
            fixture.configurable.apply()
            assertNotSame(source, manager.activeKeymap)
            assertEquals(schemes.size + 1, manager.schemeManager.allSchemes.size)
        }
    }

    fun testSummaryShowsAllCurrentBindingsAndConfirmationUsesCapturedAssignments() = withManager { manager ->
        val source = register(manager, keymap("Summary source"))
        val shortcuts = listOf(keyboard("control F10"), keyboard("control F11"), MouseShortcut(2, 0, 1))
        shortcuts.forEach { source.addShortcut(COPY, it) }
        val summary = CopySelectionShortcuts.summary(source)
        assertEquals(CopySelectionShortcuts.commands.size, summary.lines().size)
        CopySelectionShortcuts.commands.keys.forEach {
            assertTrue(summary.contains(CopySelectionBundle.message("action.$it.text")))
        }
        shortcuts.forEach { assertTrue(summary.contains(KeymapUtil.getShortcutText(it))) }
        assertTrue(summary.contains(CopySelectionBundle.message("shortcuts.unassigned")))
        val plan = ShortcutRestorePlan(source)
        source.removeAllActionShortcuts(COPY)
        assertEquals(summary, plan.summary())
        assertFalse(summary == CopySelectionShortcuts.summary(source))
    }

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

    private fun loadPersisted(element: Element, parent: Keymap): Keymap = PersistedKeymap(parent, element)

    private fun allShortcuts(keymap: Keymap): Map<String, List<Shortcut>> = shortcutSnapshot(
        keymap, (keymap.actionIdList + CopySelectionShortcuts.commands.keys + "EditorCopy").distinct(),
    )

    private fun <T : Keymap> register(manager: KeymapManagerEx, keymap: T): T {
        assertNull(manager.getKeymap(keymap.name))
        manager.schemeManager.addScheme(keymap)
        return keymap
    }

    private fun withManager(block: (KeymapManagerEx) -> Unit) {
        val manager = KeymapManagerEx.getInstanceEx()
        val original = manager.activeKeymap
        val schemes = manager.schemeManager.allSchemes.toList()
        try {
            block(manager)
        } finally {
            manager.setActiveKeymap(original)
            manager.schemeManager.allSchemes.toList().filter { candidate -> schemes.none { it === candidate } }
                .forEach(manager.schemeManager::removeScheme)
        }
        assertSame(original, manager.activeKeymap)
        assertEquals(schemes, manager.schemeManager.allSchemes)
    }

    private fun settingsFixture(
        manager: KeymapManagerEx,
        confirm: (ShortcutRestorePlan) -> Boolean = { true },
    ): SettingsFixture {
        val settings = CopySelectionSettings()
        val configurable = CopySelectionConfigurable(
            settings = settings,
            trimOpenProjectHistory = {},
            analytics = CopySelectionAnalytics(),
            openMarketplaceReviewPage = {},
            confirmAnalyticsReset = { false },
            openKeymap = {},
            prepareShortcutRestore = { CopySelectionShortcuts.prepareRestore(manager, confirm, showConflicts = {}) },
            applyShortcutRestore = { CopySelectionShortcuts.applyRestore(it, manager) },
        )
        return SettingsFixture(settings, configurable, configurable.createComponent())
    }

    private class SettingsFixture(
        val settings: CopySelectionSettings,
        val configurable: CopySelectionConfigurable,
        component: JComponent,
    ) : AutoCloseable {
        private val components = descendants(component)
        val restore = components.filterIsInstance<JButton>().single {
            it.text == CopySelectionBundle.message("shortcuts.restore.button")
        }
        val editor = components.filterIsInstance<JTextArea>().single { it.isEditable }
        val format = components.filterIsInstance<JComboBox<*>>().single { it.selectedItem is OutputFormatOption }

        override fun close() {
            configurable.disposeUIResources()
            settings.dispose()
        }

        private fun descendants(component: Component): List<Component> = buildList {
            add(component)
            if (component is Container) component.components.forEach { addAll(descendants(it)) }
        }
    }

    private fun shortcutSnapshot(keymap: Keymap, actionIds: Collection<String>): Map<String, List<Shortcut>> =
        actionIds.associateWith { actionId -> keymap.getShortcuts(actionId).toList() }

    private fun Element.replaceAction(actionId: String, vararg shortcuts: KeyboardShortcut) {
        getChildren("action").firstOrNull { it.getAttributeValue("id") == actionId }?.let(::removeContent)
        addContent(Element("action").setAttribute("id", actionId).apply {
            shortcuts.forEach { shortcut ->
                addContent(
                    Element("keyboard-shortcut")
                        .setAttribute("first-keystroke", shortcut.firstKeyStroke.toString()),
                )
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

    companion object {
        private const val PARENT_NAME = "Synthetic plugin defaults"
        private const val COPY = "CopySelectionContext.Copy"
        private const val HISTORY = "CopySelectionContext.ShowHistory"
        private const val ADD_TO_COLLECTION = "CopySelectionContext.AddToCollection"
        private const val SHOW_COLLECTION = "CopySelectionContext.ShowCollection"
        private const val COPY_ALL_COLLECTION = "CopySelectionContext.CopyAllCollection"
        private const val REMOVED_IDE_ACTION = "IntroduceConstant"
        private const val UNRELATED_ACTION = "Unrelated.Action"
    }
}
