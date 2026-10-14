package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class CopySelectionShortcutFixtureTest : BasePlatformTestCase() {
    fun testMainCopyActionOwnsTheOnlyDefaultShortcutWithoutConflicts() {
        val manager = KeymapManager.getInstance()
        val expected = mapOf(
            "\$default" to "control alt shift X",
            "Mac OS X 10.5+" to "meta alt shift X",
            "macOS System Shortcuts" to "meta alt shift X",
        )

        expected.forEach { (keymapId, keystroke) ->
            val keymap = requireNotNull(manager.getKeymap(keymapId)) { keymapId }
            val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke(keystroke), null)

            assertEquals(
                keymapId,
                listOf(shortcut),
                keymap.getShortcuts(COPY_ACTION_ID).filterIsInstance<KeyboardShortcut>(),
            )
            assertEquals(keymapId, listOf(COPY_ACTION_ID), keymap.getActionIdList(shortcut))
            UNBOUND_ACTION_IDS.forEach { actionId ->
                assertEmpty("$keymapId / $actionId", keymap.getShortcuts(actionId).toList())
            }
        }
    }

    private companion object {
        const val COPY_ACTION_ID = "CopySelectionContext.Copy"
        val UNBOUND_ACTION_IDS = listOf(
            "CopySelectionContext.ShowHistory",
            "CopySelectionContext.AddToCollection",
            "CopySelectionContext.ShowCollection",
            "CopySelectionContext.CopyAllCollection",
            "CopySelectionContext.CopyRelativePath",
            "CopySelectionContext.CopyAbsolutePath",
            "CopySelectionContext.CopyWithCodeContent",
            "CopySelectionContext.CopyGitPermalink",
        )
    }
}
