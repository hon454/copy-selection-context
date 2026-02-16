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
├── actions/
│   ├── CopySelectionBaseAction.kt       # Abstract base with clipboard logic
│   ├── CopyRelativePathAction.kt        # Relative path from project root
│   ├── CopyAbsolutePathAction.kt        # Absolute filesystem path
│   └── CopyWithCodeContentAction.kt     # Path + line + markdown code block
├── ui/
│   ├── CopySelectionNotifier.kt         # Toast notification utility
│   └── CopySelectionStatusBarWidget.kt  # Status bar widget
└── settings/
    ├── CopySelectionSettings.kt         # Settings persistence (@Service + @State)
    └── CopySelectionConfigurable.kt     # Settings UI (Configurable interface)
```

### Component Interaction
1. **User triggers action** (keyboard shortcut or context menu)
2. **Action class** (extends CopySelectionBaseAction) extracts editor context
3. **CopyPasteManager** writes formatted text to clipboard
4. **CopySelectionNotifier** shows toast notification
5. **CopySelectionStatusBarWidget** updates with last copied path
6. **CopySelectionSettings** provides default path type preference

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
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

### CopyPasteManager Usage
```kotlin
// Pattern: Write to clipboard
val content = StringSelection(formattedText)
CopyPasteManager.getInstance().setContents(content)
```

### Notification Pattern
```kotlin
// Pattern: Show toast notification
NotificationGroupManager.getInstance()
    .getNotificationGroup("Copy Selection Context")
    .createNotification(message, NotificationType.INFORMATION)
    .notify(project)
```

### Status Bar Widget
Implements `StatusBarWidget` interface, registered as `statusBarWidgetFactory` extension in plugin.xml.

### Settings Persistence
```kotlin
// Pattern: Service + PersistentStateComponent
@Service
@State(name = "CopySelectionSettings", storages = [Storage("CopySelectionPlugin.xml")])
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>
```

### No Selection Handling
When no text is selected, actions copy the current line number instead of failing.

## File Organization

### Source Files (src/main/kotlin/com/github/hon45/copyselectioncontext/)

- **CopySelectionBaseAction.kt**: Abstract base class containing shared clipboard logic, editor context extraction, and line number calculation. All copy actions inherit from this.

- **CopyRelativePathAction.kt**: Copies path relative to project root + line numbers. Keyboard shortcut: `Ctrl+Shift+Alt+C`.

- **CopyAbsolutePathAction.kt**: Copies absolute filesystem path + line numbers. Keyboard shortcut: `Ctrl+Shift+Alt+A`.

- **CopyWithCodeContentAction.kt**: Copies path + line numbers + markdown code block with actual code content. Keyboard shortcut: `Ctrl+Shift+Alt+V`.

- **CopySelectionNotifier.kt**: Utility class for showing toast notifications when copy succeeds.

- **CopySelectionStatusBarWidget.kt**: Status bar widget displaying the last copied path (updates on each copy action).

- **CopySelectionSettings.kt**: Settings service using `@Service` and `@State` annotations. Persists user preference for default path type (relative vs absolute).

- **CopySelectionConfigurable.kt**: Settings UI page implementing `Configurable` interface. Minimal UI with radio buttons for path type preference.

### Configuration Files

- **plugin.xml** (src/main/resources/META-INF/): Plugin descriptor defining actions, keyboard shortcuts, notification groups, status bar widget factory, and settings page.

- **build.gradle.kts**: Gradle build configuration with IntelliJ Platform plugin setup, Kotlin configuration, and publishing settings.

- **gradle.properties**: Gradle properties including `kotlin.stdlib.default.dependency=false` (critical for IDE compatibility).

### Naming Conventions
- Actions: `Copy*Action.kt`
- UI components: `CopySelection*Widget.kt`
- Settings: `CopySelection*Settings.kt` / `*Configurable.kt`
- Package prefix: `com.github.hon45.copyselectioncontext`

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
       <notificationGroup id="Copy Selection Context" displayType="BALLOON"/>
   </extensions>
   ```

5. **Plugin Naming**: Plugin name cannot contain "Plugin" or "IntelliJ" (JetBrains Marketplace requirement).

### IntelliJ Platform API Quirks

6. **No Selection Behavior**: When `editor.selectionModel.hasSelection()` returns false, actions should copy the current line number, not fail silently or show an error.

7. **CommonDataKeys Nullability**: Always null-check `e.getData(CommonDataKeys.*)` results. Data keys can return null in unexpected contexts (e.g., when action is triggered outside an editor).

8. **Project-Relative Paths**: Use `virtualFile.path` and `project.basePath` to calculate relative paths. Handle cases where `project.basePath` is null (e.g., default project).

9. **Line Numbers Are 0-Indexed**: Editor line numbers are 0-indexed internally but displayed as 1-indexed to users. Always add 1 when formatting for clipboard.

10. **StatusBarWidget Lifecycle**: Status bar widgets must implement `dispose()` properly to avoid memory leaks. Use `project.messageBus.connect()` for event subscriptions.

### Build and Deployment

11. **Gradle Daemon**: IntelliJ Platform Gradle Plugin can be memory-intensive. Increase Gradle daemon heap if builds fail: `org.gradle.jvmargs=-Xmx2048m` in gradle.properties.

12. **Plugin Verification**: Always run `./gradlew verifyPlugin` before publishing. It catches compatibility issues with target IDE versions.

13. **Marketplace Token**: Publishing requires a JetBrains Marketplace token set as `PUBLISH_TOKEN` environment variable or in `~/.gradle/gradle.properties`.
