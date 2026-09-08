package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import javax.swing.KeyStroke

/** Shared source of truth for the plugin's assignable commands and defaults. */
internal object CopySelectionShortcuts {
    val commands: Map<String, String> = linkedMapOf(
        "CopySelectionContext.Copy" to "C",
        "CopySelectionContext.ShowHistory" to "H",
        "CopySelectionContext.AddToCollection" to "A",
        "CopySelectionContext.ShowCollection" to "O",
        "CopySelectionContext.CopyAllCollection" to "F",
        "CopySelectionContext.CopyRelativePath" to "R",
        "CopySelectionContext.CopyAbsolutePath" to "P",
        "CopySelectionContext.CopyWithCodeContent" to "B",
        "CopySelectionContext.CopyGitPermalink" to "L",
    )

    val macKeymapIds: Set<String> = linkedSetOf(
        "Mac OS X",
        "Mac OS X 10.5+",
        "macOS System Shortcuts",
        "Visual Studio OSX",
        "ReSharper OSX",
        "VSCode OSX",
        "Visual Assist OSX",
        "Sublime Text (Mac OS X)",
    )

    fun defaultShortcuts(mac: Boolean): Map<String, KeyboardShortcut> {
        val prefix = KeyStroke.getKeyStroke(if (mac) MAC_PREFIX else DEFAULT_PREFIX)
        return commands.mapValues { (_, secondKey) ->
            KeyboardShortcut(prefix, KeyStroke.getKeyStroke(secondKey))
        }
    }

    fun defaultShortcuts(
        keymap: Keymap?,
        macHost: Boolean = SystemInfo.isMac,
    ): Map<String, KeyboardShortcut> = defaultShortcuts(usesMacKeymap(keymap, macHost))

    fun activeDefaultShortcuts(
        keymapManager: KeymapManager = KeymapManager.getInstance(),
        macHost: Boolean = SystemInfo.isMac,
    ): Map<String, KeyboardShortcut> = defaultShortcuts(keymapManager.activeKeymap, macHost)

    fun usesMacKeymap(
        keymap: Keymap?,
        macHost: Boolean = SystemInfo.isMac,
    ): Boolean {
        val visited = mutableSetOf<Keymap>()
        var current = keymap
        while (current != null && visited.add(current)) {
            when (current.name) {
                in macKeymapIds -> return true
                KeymapManager.DEFAULT_IDEA_KEYMAP -> return false
            }
            current = current.parent
        }
        return macHost
    }

    private const val DEFAULT_PREFIX = "control alt shift G"
    private const val MAC_PREFIX = "meta alt shift G"
}
