# Architecture Details

## Component Interaction

1. **User triggers action** (`Ctrl+Alt+C` shortcut or context menu)
2. **CopySelectionContextAction** reads settings (path type + code content toggle)
3. **Action** extracts editor context (file path, line range, optionally code)
4. **CopyPasteManager** writes formatted text to clipboard
5. **CopySelectionNotifier** shows toast notification (BALLOON type)
6. **CopySelectionStatusBarWidget** updates (currently stub, prints to console)
7. **CopySelectionSettings** provides path type and code content preferences

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

- **CopySelectionContextAction.kt** (~105 lines): Main unified action. Reads path type + code content from `CopySelectionSettings`. Contains `resolvePath()`, `resolveLineRange()`, `getCodeContent()`, `detectLanguage()` (15 file type mappings). Only action with shortcut: `Ctrl+Alt+C` / `Meta+Alt+C`.

- **CopyRelativePathAction.kt** (~21 lines): Relative path from `project.basePath`. Handles files outside project. Context menu only.

- **CopyAbsolutePathAction.kt** (~11 lines): Returns `file.path` directly. Simplest impl. Context menu only.

- **CopyWithCodeContentAction.kt** (~104 lines): Path + line + markdown code block. Overrides `actionPerformed()` for custom format. Uses settings for path type. Includes `detectLanguage()` and `getCodeContent()`. Context menu only.

- **CopySelectionNotifier.kt** (~17 lines): Singleton. `NotificationGroupManager` BALLOON notifications with checkmark prefix. Group ID: `"CopySelectionContext"` (must match plugin.xml).

- **CopySelectionStatusBarWidget.kt** (~9 lines): Stub (console only). Full impl needs `StatusBarWidget` + `TextPresentation` + `getAlignment(): Float` + Factory class.

- **CopySelectionSettings.kt** (~39 lines): `@Service` + `@State`. Persists to `CopySelectionPlugin.xml`. Settings: `defaultPathType` (ABSOLUTE), `includeCodeContent` (false). `PathType` enum: RELATIVE, ABSOLUTE.

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
