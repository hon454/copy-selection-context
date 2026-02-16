# Copy Selection Context - Project Knowledge Base

## Project Overview

Copy Selection Context is a JetBrains IDE plugin that streamlines sharing code context with AI assistants. When working with AI tools, developers frequently need to provide file paths, line numbers, and code snippets. This plugin makes that workflow instant.

**Core Value**: One keyboard shortcut copies everything needed for AI context: file path, line numbers, and optionally the code itself, formatted and ready to paste.

**Target Users**: Developers using AI coding assistants (Claude, ChatGPT, etc.) who need to quickly share code context from their IDE.

**Primary IDE**: Rider, but compatible with all IntelliJ Platform IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.) version 2024.3+.

## Architecture

### Package Structure
```
com.github.hon45.copyselectioncontext/
├── CopySelectionContextAction.kt    # Main unified action (settings-driven, has shortcut)
├── CopySelectionBaseAction.kt       # Abstract base with clipboard logic
├── CopyRelativePathAction.kt        # Relative path from project root (context menu only)
├── CopyAbsolutePathAction.kt        # Absolute filesystem path (context menu only)
├── CopyWithCodeContentAction.kt     # Path + line + markdown code block (context menu only)
├── CopySelectionNotifier.kt         # Toast notification utility
├── CopySelectionStatusBarWidget.kt  # Status bar widget (stub)
├── CopySelectionSettings.kt         # Settings persistence (@Service + @State)
└── CopySelectionConfigurable.kt     # Settings UI (Configurable interface)
```

All source files are in a single flat package structure with no subdirectories.

### Component Interaction
1. **User triggers action** (`Ctrl+Alt+C` shortcut or context menu)
2. **CopySelectionContextAction** reads settings (path type + code content toggle)
3. **Action** extracts editor context (file path, line range, optionally code)
4. **CopyPasteManager** writes formatted text to clipboard
5. **CopySelectionNotifier** shows toast notification (BALLOON type)
6. **CopySelectionStatusBarWidget** updates with last copied path (currently stub implementation)
7. **CopySelectionSettings** provides path type and code content preferences

### Output Formats
- **Plain text**: `src/main/kotlin/MyFile.kt:15-23`
- **Markdown code block**: 
  ````
  src/main/kotlin/MyFile.kt:15-23
  ```kotlin
  fun example() {
      // code here
  }
  ```
  ````

## Tech Stack

- **Language**: Kotlin 2.1.0
- **Build Tool**: Gradle 9.3.1
- **Plugin Framework**: IntelliJ Platform Gradle Plugin 2.11.0
- **JVM**: Toolchain 21
- **Target Platform**: IntelliJ Platform SDK 2024.3+
- **IDE Compatibility**: All IntelliJ Platform IDEs (Rider, IDEA, PyCharm, WebStorm, etc.)

## Build Commands

```bash
# Build the plugin (creates ZIP in build/distributions/)
./gradlew buildPlugin

# Run IDE with plugin installed (for manual testing)
./gradlew runIde

# Verify plugin structure and compatibility
./gradlew verifyPlugin

# Publish to JetBrains Marketplace (requires token)
./gradlew publishPlugin
```

## Key Patterns

### AnAction Inheritance
All copy actions extend `CopySelectionBaseAction`, which extends `com.intellij.openapi.actionSystem.AnAction`. Actions override `actionPerformed(AnActionEvent)` to handle user triggers.

```kotlin
// Pattern: Extract editor context from AnActionEvent
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

The base class provides:
- `buildPathString()`: Calculates line range and formats path with line numbers
- `copyToClipboard()`: Uses CopyPasteManager to write to system clipboard
- `update()`: Enables/disables action based on editor and file availability
- Abstract `getPath()`: Subclasses implement to return relative or absolute path

### CopyPasteManager Usage
```kotlin
// Pattern: Write to clipboard
val content = StringSelection(formattedText)
CopyPasteManager.getInstance().setContents(content)
```

### Notification Pattern
```kotlin
// Pattern: Show toast notification (CopySelectionNotifier.kt)
NotificationGroupManager.getInstance()
    .getNotificationGroup("CopySelectionContext")
    .createNotification("✓ Copied: $message", NotificationType.INFORMATION)
    .notify(project)
```

Note: Notification group ID is `"CopySelectionContext"` (no spaces) and must be registered in plugin.xml before use.

### Status Bar Widget
Currently a stub implementation (prints to console only). Full implementation would:
- Implement `StatusBarWidget` interface with `TextPresentation`
- Require `getAlignment()` method returning Float (e.g., 0.0f for left-aligned)
- Use `AtomicReference<String>` for thread-safe message storage
- Register via `statusBarWidgetFactory` extension in plugin.xml

Stub code:
```kotlin
object CopySelectionStatusBarWidget {
    fun update(message: String) {
        println("Status: $message")
    }
}
```

### Settings Persistence
```kotlin
// Pattern: Service + PersistentStateComponent
@Service
@State(name = "CopySelectionSettings", storages = [Storage("CopySelectionPlugin.xml")])
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>
```

### No Selection Handling
When no text is selected, actions copy the current line number instead of failing:

```kotlin
val lineRange = if (selectionModel.hasSelection()) {
    val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
    val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
    if (startLine == endLine) "$startLine" else "$startLine-$endLine"
} else {
    val currentLine = editor.caretModel.logicalPosition.line + 1
    "$currentLine"
}
```

### Path Normalization
All paths are normalized to use forward slashes for cross-platform compatibility:

```kotlin
val normalizedPath = path.replace("\\", "/")
```

### Language Detection
CopyWithCodeContentAction detects file type for markdown code blocks:

```kotlin
private fun detectLanguage(file: VirtualFile): String {
    val fileTypeName = file.fileType.name
    return when (fileTypeName.lowercase()) {
        "kotlin" -> "kotlin"
        "java" -> "java"
        "c#" -> "csharp"
        "javascript" -> "javascript"
        "typescript" -> "typescript"
        // ... 15 total mappings
        else -> "" // Empty string for unknown types
    }
}
```

## File Organization

### Source Files (src/main/kotlin/com/github/hon45/copyselectioncontext/)

- **CopySelectionBaseAction.kt** (55 lines): Abstract base class containing shared clipboard logic, editor context extraction, and line number calculation. Provides `buildPathString()` for line range formatting, `copyToClipboard()` for CopyPasteManager integration, and `update()` for action enablement. All copy actions inherit from this and implement abstract `getPath()` method.

- **CopySelectionContextAction.kt** (105 lines): Main unified action that reads settings to determine behavior. Resolves path type (absolute/relative) and code content inclusion from `CopySelectionSettings`. Contains `resolvePath()`, `resolveLineRange()`, `getCodeContent()`, and `detectLanguage()` (15 file type mappings). This is the only action with a keyboard shortcut: `Ctrl+Alt+C` (Windows/Linux), `Meta+Alt+C` (macOS).

- **CopyRelativePathAction.kt** (21 lines): Copies path relative to project root + line numbers. Implements `getPath()` by calculating relative path from `project.basePath`. Handles edge cases where file is outside project. Context menu only (no keyboard shortcut).

- **CopyAbsolutePathAction.kt** (11 lines): Copies absolute filesystem path + line numbers. Simplest implementation, returns `file.path` directly. Context menu only (no keyboard shortcut).

- **CopyWithCodeContentAction.kt** (104 lines): Copies path + line numbers + markdown code block with actual code content. Overrides `actionPerformed()` to build custom markdown format. Uses `CopySelectionSettings` to respect user's path type preference. Includes `detectLanguage()` for 15 file type mappings and `getCodeContent()` for text extraction. Context menu only (no keyboard shortcut).

- **CopySelectionNotifier.kt** (17 lines): Singleton object for showing toast notifications when copy succeeds. Uses `NotificationGroupManager` to display BALLOON-type notifications with checkmark prefix. Notification group ID is `"CopySelectionContext"` (must match plugin.xml registration).

- **CopySelectionStatusBarWidget.kt** (9 lines): Stub implementation that prints to console. Full implementation would require `StatusBarWidget` interface with `TextPresentation`, `getAlignment()` method, and Factory class for registration.

- **CopySelectionSettings.kt** (39 lines): Application-level settings service using `@Service` and `@State` annotations. Persists user preferences to `CopySelectionPlugin.xml` in IDE config directory. Implements `PersistentStateComponent<State>` for automatic load/save. Settings: `defaultPathType` (ABSOLUTE by default), `includeCodeContent` (false by default). Includes `PathType` enum with RELATIVE and ABSOLUTE values.

- **CopySelectionConfigurable.kt** (70 lines): Settings UI page implementing `Configurable` interface. Creates minimal Swing UI with radio buttons for path type preference and checkbox for code content inclusion. Appears under Settings → Tools → Copy Selection Context. Implements `isModified()`, `apply()`, `reset()`, and `disposeUIResources()` for proper settings lifecycle management.

### Configuration Files

- **plugin.xml** (src/main/resources/META-INF/, 81 lines): Plugin descriptor defining:
  - **Extensions**: `notificationGroup` (id="Copy Selection Context", displayType="BALLOON"), `statusBarWidgetFactory` (stub registration), `applicationConfigurable` (Settings UI under Tools menu)
  - **Actions**: 1 main action (`CopySelectionContextAction`) with keyboard shortcut `Ctrl+Alt+C` / `Meta+Alt+C`, plus 3 explicit actions (context menu only, no shortcuts)
  - **Context Menu**: All actions added to `EditorPopupMenu` with chained anchoring
  - **Metadata**: Description and change notes in CDATA blocks with HTML formatting
  - **Dependencies**: `com.intellij.modules.platform` only
  - **NO untilBuild**: Forward compatibility requirement

- **build.gradle.kts**: Gradle build configuration with IntelliJ Platform plugin setup (version 2.11.0), Kotlin configuration (2.1.0 with JVM toolchain 21), and publishing settings. Includes `pluginVerification` block for compatibility checking.

- **gradle.properties**: Gradle properties including `kotlin.stdlib.default.dependency=false` (critical for IDE compatibility - prevents classloader conflicts with IDE's bundled Kotlin stdlib).

### Naming Conventions
- Actions: `Copy*Action.kt` (e.g., CopyRelativePathAction, CopyAbsolutePathAction)
- UI components: `CopySelection*Widget.kt` or `CopySelection*Notifier.kt`
- Settings: `CopySelection*Settings.kt` / `CopySelection*Configurable.kt`
- Package: Single flat package `com.github.hon45.copyselectioncontext` (no subdirectories)
- Plugin ID: `com.github.hon45.copy-selection-context` (kebab-case with hyphens)
- Notification Group ID: `"CopySelectionContext"` (no spaces, PascalCase)

## Conventions

### Code Style
- Kotlin idiomatic style (follow IntelliJ IDEA defaults)
- Prefer expression bodies for single-expression functions
- Use `?.` safe calls and `?:` Elvis operator for null handling
- Avoid `!!` (non-null assertion) unless absolutely necessary

### Commit Messages
- Format: `type: description`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Example: `feat: add status bar widget showing last copied path`

### PR Guidelines
- One feature per PR
- Include manual testing steps in PR description
- Update AGENTS.md if architecture changes

## Gotchas

### Critical Configuration Issues

1. **untilBuild Must NOT Be Set**: The `untilBuild` property in plugin configuration must remain unset for forward compatibility. Setting it will break the plugin on future IDE versions.

2. **Kotlin Standard Library**: `kotlin.stdlib.default.dependency=false` must be set in gradle.properties. The IDE provides its own Kotlin stdlib; bundling another causes classloader conflicts.

3. **SVG Icons Only**: Plugin icons must be SVG format (40×40px, <3KB file size). PNG icons are not supported in modern IntelliJ Platform versions.

4. **Notification Group Registration**: Notification groups must be registered in plugin.xml before use:
   ```xml
   <extensions defaultExtensionNs="com.intellij">
       <notificationGroup id="CopySelectionContext" displayType="BALLOON"/>
   </extensions>
   ```
   Note: The ID in plugin.xml is `"CopySelectionContext"` (no spaces), but the display name can have spaces.

5. **Plugin Naming**: Plugin name cannot contain "Plugin" or "IntelliJ" (JetBrains Marketplace requirement).

### IntelliJ Platform API Quirks

6. **No Selection Behavior**: When `editor.selectionModel.hasSelection()` returns false, actions should copy the current line number, not fail silently or show an error.

7. **CommonDataKeys Nullability**: Always null-check `e.getData(CommonDataKeys.*)` results. Data keys can return null in unexpected contexts (e.g., when action is triggered outside an editor).

8. **Project-Relative Paths**: Use `virtualFile.path` and `project.basePath` to calculate relative paths. Handle cases where `project.basePath` is null (e.g., default project).

9. **Line Numbers Are 0-Indexed**: Editor line numbers are 0-indexed internally but displayed as 1-indexed to users. Always add 1 when formatting for clipboard.

10. **StatusBarWidget.TextPresentation**: When implementing status bar widgets, the `TextPresentation` interface requires `getAlignment()` method returning Float (e.g., 0.0f for left-aligned text). Missing this method causes compilation errors.

11. **Keyboard Shortcut Syntax**: Use `"control"` (not `"ctrl"`) in plugin.xml keyboard-shortcut elements. Invalid syntax causes plugin verification failures.

### Build and Deployment

12. **Gradle Daemon**: IntelliJ Platform Gradle Plugin can be memory-intensive. Increase Gradle daemon heap if builds fail: `org.gradle.jvmargs=-Xmx2048m` in gradle.properties.

13. **Plugin Verification**: Always run `./gradlew verifyPlugin` before publishing. It catches compatibility issues with target IDE versions. Configure with `pluginVerification { ides { recommended() } }` in build.gradle.kts.

14. **Marketplace Token**: Publishing requires a JetBrains Marketplace token set as `PUBLISH_TOKEN` environment variable or in `~/.gradle/gradle.properties`.

15. **Windows Build Environment**: On Windows with Git Bash, use PowerShell wrapper for Gradle commands: `powershell.exe -Command ".\gradlew.bat buildPlugin"`. Alternatively, run Gradle wrapper directly via Java: `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain buildPlugin`.

16. **Java Version Requirement**: Build requires Java 21 (jvmToolchain 21). Set `JAVA_HOME` to JDK 21 installation before building if system has multiple Java versions.
