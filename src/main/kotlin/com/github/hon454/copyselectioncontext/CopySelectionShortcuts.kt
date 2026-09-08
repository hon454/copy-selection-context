package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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

    fun summary(keymap: Keymap? = KeymapManager.getInstance().activeKeymap): String {
        val defaults = defaultShortcuts(keymap)
        return commands.keys.joinToString("\n") { actionId ->
            summaryRow(actionId, keymap?.getShortcuts(actionId)?.toList().orEmpty(), defaults.getValue(actionId))
        }
    }

    fun shortcutText(shortcuts: List<Shortcut>): String = shortcuts
        .joinToString(" / ") { KeymapUtil.getShortcutText(it) }
        .ifEmpty { CopySelectionBundle.message("shortcuts.unassigned") }

    fun summaryRow(actionId: String, current: List<Shortcut>, default: KeyboardShortcut): String =
        CopySelectionBundle.message(
            "shortcuts.summary.row",
            CopySelectionBundle.message("action.$actionId.text"),
            shortcutText(current),
            KeymapUtil.getShortcutText(default),
        )

    fun openKeymap(project: Project? = null) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            { (it as? SearchableConfigurable)?.id == "preferences.keymap" },
            {},
        )
    }

    fun prepareRestore(
        manager: KeymapManager = KeymapManager.getInstance(),
        confirm: (ShortcutRestorePlan) -> Boolean = ::confirmRestore,
        showConflicts: (List<String>) -> Unit = { conflicts ->
            Messages.showWarningDialog(conflictMessage(conflicts), CopySelectionBundle.message("shortcuts.restore.title"))
        },
    ): ShortcutRestorePlan? {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val source = manager.activeKeymap
        val plan = ShortcutRestorePlan(source)
        val conflicts = plan.conflicts()
        if (conflicts.isNotEmpty()) {
            showConflicts(conflicts)
            return null
        }
        return plan.takeIf(confirm)
    }

    fun applyRestore(
        plan: ShortcutRestorePlan,
        manager: KeymapManagerEx = KeymapManagerEx.getInstanceEx(),
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        plan.validate(manager.activeKeymap)
        val baseName = "${plan.source.presentableName} (Copy Selection Context)"
        val names = manager.schemeManager.allSchemes.map { it.name }.toSet()
        val name = generateSequence(1) { it + 1 }
            .map { if (it == 1) baseName else "$baseName $it" }
            .first { it !in names }
        val restored = plan.derive(name)
        manager.schemeManager.addScheme(restored)
        manager.setActiveKeymap(restored)
    }

    fun conflictMessage(conflicts: List<String>): String =
        CopySelectionBundle.message("shortcuts.restore.conflicts", conflicts.joinToString("\n"))

    private fun confirmRestore(plan: ShortcutRestorePlan): Boolean = Messages.showDialog(
        CopySelectionBundle.message("shortcuts.restore.confirm", plan.source.presentableName, plan.summary()),
        CopySelectionBundle.message("shortcuts.restore.title"),
        arrayOf(CopySelectionBundle.message("shortcuts.restore.schedule"), Messages.getCancelButton()),
        1,
        Messages.getQuestionIcon(),
    ) == 0

    private const val DEFAULT_PREFIX = "control alt shift G"
    private const val MAC_PREFIX = "meta alt shift G"
}

/** A settings-session snapshot; confirmation alone never changes a keymap. */
internal class ShortcutRestorePlan(
    val source: Keymap,
) {
    private val defaults = CopySelectionShortcuts.defaultShortcuts(source)
    private val original = CopySelectionShortcuts.commands.keys.associateWith { source.getShortcuts(it).toList() }

    fun summary(): String = original.entries.joinToString("\n") { (actionId, shortcuts) ->
        CopySelectionShortcuts.summaryRow(actionId, shortcuts, defaults.getValue(actionId))
    }

    // Reserve the entire prefix: an external single stroke or a different chord can consume it too.
    fun conflicts(): List<String> = defaults.values.map { it.firstKeyStroke }.distinct()
        .flatMap { source.getActionIds(it).toList() }
        .filterNot { it in CopySelectionShortcuts.commands }
        .distinct()
        .sorted()

    fun validate(active: Keymap?) {
        if (active !== source || original.any { (id, shortcuts) -> source.getShortcuts(id).toList() != shortcuts }) {
            throw ConfigurationException(CopySelectionBundle.message("shortcuts.restore.changed"))
        }
        val conflicts = conflicts()
        if (conflicts.isNotEmpty()) {
            throw ConfigurationException(CopySelectionShortcuts.conflictMessage(conflicts))
        }
    }

    fun derive(name: String): Keymap = source.deriveKeymap(name).also { restored ->
        defaults.forEach { (actionId, shortcut) ->
            restored.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>().forEach {
                restored.removeShortcut(actionId, it)
            }
            restored.addShortcut(actionId, shortcut)
        }
    }
}
