# Shortcut Simplification and Context Menu Restructure

Date: 2026-10-14
Status: Approved design, pending spec review

## Problem

Since v1.7.0 every one of the nine plugin actions carries a two-stroke default
(`Ctrl+Alt+Shift+G`, then a letter; `Cmd+Alt+Shift+G` on macOS). The everyday
copy therefore always needs two key presses, and the editor context menu hides
the main copy action inside a submenu. Documentation never followed the v1.7.0
change: the plugin description, all READMEs, AGENTS.md, CLAUDE.md and
CONTRIBUTING.md still advertise `Ctrl+Alt+C` / `Cmd+Alt+C` and `Ctrl+Alt+H`,
and the CHANGELOG has no entry for the change.

## Goals

1. The main action `CopySelectionContext.Copy` gets one single-stroke default
   shortcut. Two-stroke defaults are not allowed.
2. The other eight actions have no default shortcut; users assign their own in
   Keymap.
3. The main action is directly runnable from the editor context menu, above the
   submenu.
4. Settings offers a link that opens Keymap filtered to the plugin's actions.
5. Documentation, CLAUDE.md and the CHANGELOG match the real shortcuts.

## Non-goals

- Post-update notification or toast about the shortcut change.
- An in-plugin shortcut editor.
- Version bump or release preparation.

## Decisions

### Default shortcut

`Ctrl+Alt+Shift+X` (Windows/Linux) and `Cmd+Alt+Shift+X` (macOS).

Verification done during design:

- Free in all 26 keymaps on IntelliJ Community `master` (2026-10-14): `$default`,
  Default for GNOME/KDE/XWin, Mac OS X, Mac OS X 10.5+, macOS System Shortcuts,
  Eclipse (+Mac), Emacs, NetBeans 6.5, QtCreator (+Mac), ReSharper (+OSX),
  Sublime Text (+Mac), VSCode (+OSX), Visual Assist (+OSX), Visual Studio,
  Visual Studio 2022, Visual Studio OSX, Visual Studio for Mac, Xcode. The check
  resolved parent inheritance, macOS Ctrl→Cmd conversion of `$default`, and
  two-stroke shortcuts that start with the candidate. The control candidate
  `Ctrl/Cmd+Alt+Shift+C` was correctly detected as `CopyReference` in all 26.
- Free among every `keyboard-shortcut` declared by bundled plugins in IntelliJ
  IDEA Community 2024.3 (the plugin's minimum platform).
- GitHub code search across JetBrains repositories found only `Alt+Shift+X`
  (Tasks "Clear Context", Eclipse keymap), which does not include Ctrl/Cmd.
- No default macOS, Windows, GNOME or KDE system shortcut uses it.
- Not verified: closed-source Ultimate-only plugins and third-party plugins.
  Manual QA covers this through the Keymap conflict view.

Rejected candidates: `Ctrl/Cmd+Alt+C` (Introduce Constant), `Ctrl/Cmd+Alt+Shift+C`
(Copy Reference), `Ctrl/Cmd+Shift+C` (Copy Paths), `Alt+Shift+C` (Recent
Changes), `Ctrl/Cmd+Alt+K` (Git Log branch navigation, Claude Code plugin),
`Ctrl+Alt+Shift+K` (Convert Java to Kotlin), `Ctrl+Alt+Shift+J` (Select All
Occurrences), `Cmd+Alt+Shift+Q` (macOS immediate log out).

### Context menu

```
Copy Selection Context            Ctrl+Alt+Shift+X
Copy Selection Context Tools  ▸
    Show Copy History
    Open Context Collection
    Copy All Context Collection
    Add to Context Collection
    Copy Relative Path with Line Numbers
    Copy Absolute Path with Line Numbers
    Copy with Code Content
    Copy GitHub/GitLab Permalink
```

- The top-level item keeps the action's existing name, "Copy Selection
  Context", so the menu, Keymap, Find Action and documentation use one name. No
  `override-text`. A "Quick" prefix was considered and rejected because "Quick"
  in JetBrains IDEs means a popup (Quick Documentation, Quick Definition).
- The submenu's display name changes to "Copy Selection Context Tools". Its group
  ID `CopySelectionContextGroup` is unchanged.
- No action ID changes, so shortcuts users assigned themselves in Keymap survive.

### Keymap link in Settings

Settings → Tools → Copy Selection Context gets one link, "Configure shortcuts in
Keymap…", in the Behavior group next to the existing Marketplace link.

- When clicked, it takes `Settings.KEY` from the link's data context, then
  `find("preferences.keymap")`, then `select(keymapConfigurable, "Copy Selection
  Context")`. This navigates inside the open Settings dialog with the Keymap
  search prefilled. These APIs are public and carry no `ApiStatus` annotations in
  2024.3. Impl classes such as `KeymapPanel` are not used.
- If `Settings.KEY` or the Keymap configurable is unavailable, the click does
  nothing: no dialog and no exception.
- The link text is added to all five resource bundles.

## Changes

### plugin.xml

- Move `CopySelectionContext.Copy` out of `CopySelectionContextGroup` into a
  standalone `<action>` with
  `<add-to-group group-id="EditorPopupMenu" anchor="before" relative-to-action="CopySelectionContextGroup"/>`.
- Shortcuts on Copy: `$default` → `control alt shift X`; the eight Mac keymaps
  already listed (`Mac OS X`, `Mac OS X 10.5+`, `macOS System Shortcuts`,
  `Visual Studio OSX`, `ReSharper OSX`, `VSCode OSX`, `Visual Assist OSX`,
  `Sublime Text (Mac OS X)`) → `meta alt shift X` with `replace-all="true"`.
  Other Mac keymaps inherit from `Mac OS X 10.5+`.
- Remove every `<keyboard-shortcut>` from the other eight actions.
- Update `<description>`: single shortcut, Keymap customization note, no claim
  of `Ctrl+Alt+C` or `Ctrl+Alt+H`.

### Resource bundles (en, ja, ko, zh_CN, zh_TW)

- `group.CopySelectionContextGroup.text` → "Copy Selection Context Tools" (localized).
- New key for the Keymap link text.

### Kotlin

- `CopySelectionShortcuts.kt`: shrink to the main action ID, the two keystrokes
  and the Mac keymap ID list used by the descriptor test. Remove the nine-command
  table, the G prefix, the keymap lineage helpers and the unused
  `activeDefaultShortcuts`.
- `CopySelectionConfigurable.kt`: add the Keymap link.

### Tests (pragmatic scope)

Update existing tests only where they would break or where they guard a likely
regression. No new test suites.

- `PluginDescriptorLocalizationTest`: replace the G-prefix test with one test.
  It checks that only Copy declares shortcuts, with the `$default` and Mac
  keystrokes and Mac `replace-all`. It also checks that no shortcut has a
  `second-keystroke` and that the known conflicting keystrokes stay forbidden.
  Adjust any assertion about Copy's parent group.
- `CopySelectionShortcutFixtureTest`: reduce to one test. In `$default` and
  `Mac OS X 10.5+`, Copy has exactly the expected shortcut and no other action is
  bound to it (conflict regression guard). Delete the lineage and persisted-keymap
  tests; they re-verify platform behavior.
- `DocumentationSyncTest`: require the new shortcut in the READMEs instead of
  `Ctrl+Alt+H`, and add one check that the obsolete strings (`Ctrl+Alt+C`,
  `Cmd+Alt+C`, `Ctrl+Alt+H`) are absent. Doc drift caused this work.
- No automated test for the Settings link; manual QA covers it.

### Documentation

- README.md, README.ko.md, README.ja.md, README.zh-CN.md, README.zh-TW.md:
  shortcut table (one row), context menu description, how to assign shortcuts in
  Keymap, and the Settings link.
- AGENTS.md, CONTRIBUTING.md, `.agents/architecture.md`, `.agents/patterns.md`:
  shortcut references and the `CopySelectionShortcuts.kt` description.
- CLAUDE.md testing section:
  - Replace "No automated tests" with the actual policy. Automated tests exist
    (`./gradlew test`). Add or change them only where they guard a real
    regression risk such as descriptor or contract checks. Cover UI and platform
    behavior with manual QA.
  - Update the QA steps: `Ctrl+Alt+Shift+X` / `Cmd+Alt+Shift+X`, the new menu
    layout instead of "all 4 actions visible", the Settings Keymap link, and a
    Keymap conflict check.
- CHANGELOG `[Unreleased]`:
  - Changed: Copy Selection Context default shortcut is now
    `Ctrl+Alt+Shift+X` / `Cmd+Alt+Shift+X`. Copy Selection Context is shown
    directly in the editor context menu. The submenu is renamed to Copy Selection
    Context Tools.
  - Removed: the `Ctrl/Cmd+Alt+Shift+G` two-stroke defaults for all actions.
    Custom Keymap assignments are kept.
  - Added: Settings link to configure shortcuts in Keymap.
  - Fixed: documentation advertised `Ctrl+Alt+C` / `Ctrl+Alt+H`, which v1.7.0
    had replaced.

## User impact

- Users on bundled keymaps lose the nine `G` two-stroke shortcuts and get
  `Ctrl/Cmd+Alt+Shift+X` for Copy.
- Users who customized shortcuts keep them, because action IDs are unchanged.

## Manual QA

1. Select code → `Ctrl+Alt+Shift+X` (`Cmd+Alt+Shift+X` on macOS) → copied per settings.
2. No selection → shortcut → current line copied.
3. Right-click → "Copy Selection Context" runs directly; "Copy Selection Context
   Tools ▸" lists the other eight actions without shortcuts.
4. Settings → Tools → Copy Selection Context → "Configure shortcuts in Keymap…" →
   Keymap opens with the plugin actions filtered.
5. Settings → Keymap → search "Copy Selection Context" → no conflict warning on
   `Ctrl/Cmd+Alt+Shift+X`, and the old `G` two-stroke entries are gone.
6. Assign a custom shortcut to another action → it works and persists after restart.
