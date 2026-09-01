# Architecture Details

## Component Interaction

1. **User triggers action** (`Ctrl+Alt+C` shortcut or context menu)
2. **CopySelectionContextAction** reads settings (path type + code content toggle)
3. **Action** extracts editor context (file path, line range, optionally code)
4. **CopyPasteManager** writes formatted text to clipboard
5. **CopyHistoryService** stores the result in local, non-roaming workspace state when history is enabled
6. **CopySelectionHighlighter** replaces the gutter marker scoped to the active editor
7. **CopySelectionNotifier** shows toast notification (BALLOON type)
8. **CopySelectionStatusBarWidget** updates (currently stub, prints to console)
9. **CopySelectionSettings** provides formatting, behavior, and history preferences

## Output Formats

**Plain text**: `src/main/kotlin/MyFile.kt:15-23`

**Markdown code block**:
````
src/main/kotlin/MyFile.kt:15-23
```kotlin
fun example() {
    // code here
}
```
````

## Source File Details

- **CopySelectionBaseAction.kt** (~55 lines): Abstract base. `buildPathString()` (line range formatting), `copyToClipboard()` (CopyPasteManager), `update()` (action enablement). Subclasses implement abstract `getPath()`.

- **CopySelectionHighlighter.kt**: Stores the last gutter highlighter in each `Editor`'s user data and only removes it through that editor's `MarkupModel`.

- **CopySelectionContextAction.kt** (~105 lines): Main unified action. Reads path type + code content from `CopySelectionSettings`. Contains `resolvePath()`, `resolveLineRange()`, `getCodeContent()`, `detectLanguage()` (15 file type mappings). Only action with shortcut: `Ctrl+Alt+C` / `Meta+Alt+C`.

- **CopyRelativePathAction.kt** (~21 lines): Relative path from `project.basePath`. Handles files outside project. Context menu only.

- **CopyAbsolutePathAction.kt** (~11 lines): Returns `file.path` directly. Simplest impl. Context menu only.

- **CopyWithCodeContentAction.kt** (~104 lines): Path + line + markdown code block. Overrides `actionPerformed()` for custom format. Uses settings for path type. Includes `detectLanguage()` and `getCodeContent()`. Context menu only.

- **CopyGitPermalinkAction.kt**: Captures editor and VCS context, resolves Git metadata on a pooled thread, then copies a GitHub/GitLab permalink on the UI thread. Reports an error without changing the clipboard when resolution fails.

- **GitRepositoryMetadataResolver.kt**: NIO-based resolver for standard repositories and linked worktrees. Reads the common Git config, detached or symbolic HEAD, loose refs, packed refs, and the branch-tracking remote with deterministic fallbacks.

- **GitPermalinkGenerator.kt**: Normalizes supported GitHub/GitLab HTTPS and SSH remote forms and percent-encodes repository-relative file paths.

- **CopyHistoryService.kt**: Project-level history service persisted in the IDE's local workspace state with roaming disabled. Size zero disables and clears history; shrinking retains the newest entries deterministically. The deprecated `copySelectionHistory.xml` storage entry migrates and cleans up legacy shareable state.

- **CopyHistoryPopup.kt**: Popup chooser for re-copying local history entries, with a clear-all action.

- **CopySelectionNotifier.kt** (~17 lines): Singleton. `NotificationGroupManager` BALLOON notifications with checkmark prefix. Group ID: `"CopySelectionContext"` (must match plugin.xml).

- **CopySelectionStatusBarWidget.kt** (~9 lines): Stub (console only). Full impl needs `StatusBarWidget` + `TextPresentation` + `getAlignment(): Float` + Factory class.

- **CopySelectionSettings.kt**: `@Service` + `@State`. Persists to `CopySelectionPlugin.xml`. Includes output, notification, analytics, and copy history size preferences. `PathType` enum: RELATIVE, ABSOLUTE.

- **CopySelectionConfigurable.kt** (~70 lines): Settings UI under Tools menu. Swing radio buttons (path type) + checkbox (code content). Implements `Configurable` lifecycle: `isModified()`, `apply()`, `reset()`, `disposeUIResources()`.

## Configuration Files

- **plugin.xml** (src/main/resources/META-INF/, ~81 lines):
  - Extensions: `notificationGroup` (id="CopySelectionContext", BALLOON), `statusBarWidgetFactory` (stub), `applicationConfigurable` (Tools menu)
  - Actions: 1 main (`CopySelectionContextAction` with `Ctrl+Alt+C`/`Meta+Alt+C`) + 3 context-menu-only
  - All actions in `EditorPopupMenu` with chained anchoring
  - Dependency: `com.intellij.modules.platform` only
  - **NO untilBuild** (forward compatibility)

- **build.gradle.kts**: IntelliJ Platform plugin 2.11.0, Kotlin 2.1.0, JVM toolchain 21, `pluginVerification` block.

- **gradle.properties**: `kotlin.stdlib.default.dependency=false` (critical: prevents classloader conflicts with IDE's bundled Kotlin stdlib).
