# Copy Selection Context - Claude Context

Quick reference for Claude when working on this project.

## Quick Commands

```bash
# Build the plugin (creates ZIP in build/distributions/)
./gradlew buildPlugin

# Run IDE with plugin installed (for manual testing)
./gradlew runIde

# Verify plugin structure and compatibility
./gradlew verifyPlugin

# Publish to JetBrains Marketplace (requires token)
./gradlew publishPlugin

# Clean build artifacts
./gradlew clean
```

## Project Structure

```
copy-selection-context/
├── src/main/
│   ├── kotlin/com/github/hon45/copyselectioncontext/
│   │   ├── actions/           # Action implementations
│   │   ├── ui/                # UI components (notifications, status bar)
│   │   └── settings/          # Settings persistence and UI
│   └── resources/
│       └── META-INF/
│           └── plugin.xml     # Plugin descriptor
├── build.gradle.kts           # Build configuration
├── gradle.properties          # Gradle properties
└── settings.gradle.kts        # Gradle settings
```

## Frequently Modified Files

- `src/main/kotlin/com/github/hon45/copyselectioncontext/actions/*.kt` — Action implementations
- `src/main/kotlin/com/github/hon45/copyselectioncontext/ui/*.kt` — UI components
- `src/main/kotlin/com/github/hon45/copyselectioncontext/settings/*.kt` — Settings
- `src/main/resources/META-INF/plugin.xml` — Action registration, extensions, notification groups
- `build.gradle.kts` — Build configuration, dependencies, plugin metadata
- `gradle.properties` — Gradle properties (kotlin.stdlib.default.dependency=false is critical)

## Core Components

### Actions (Keyboard Shortcuts)
- **CopyRelativePathAction** (`Ctrl+Shift+Alt+C`) — Copy relative path + line numbers
- **CopyAbsolutePathAction** (`Ctrl+Shift+Alt+A`) — Copy absolute path + line numbers
- **CopyWithCodeContentAction** (`Ctrl+Shift+Alt+V`) — Copy path + line + markdown code block

### UI Components
- **CopySelectionNotifier** — Toast notifications on successful copy
- **CopySelectionStatusBarWidget** — Status bar widget showing last copied path

### Settings
- **CopySelectionSettings** — Persists default path type preference (relative vs absolute)
- **CopySelectionConfigurable** — Minimal settings UI with radio buttons

## Key IntelliJ Platform APIs

```kotlin
// Extract editor context
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

// Copy to clipboard
val content = StringSelection(formattedText)
CopyPasteManager.getInstance().setContents(content)

// Show notification
NotificationGroupManager.getInstance()
    .getNotificationGroup("Copy Selection Context")
    .createNotification(message, NotificationType.INFORMATION)
    .notify(project)

// Settings persistence
@Service
@State(name = "CopySelectionSettings", storages = [Storage("CopySelectionPlugin.xml")])
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>
```

## Testing

- **No automated tests** — Manual QA only
- **Manual testing**: Run `./gradlew runIde` to launch IDE with plugin installed
- **Test scenarios**:
  1. Select code in editor, press `Ctrl+Shift+Alt+C` → verify relative path copied
  2. Select code, press `Ctrl+Shift+Alt+A` → verify absolute path copied
  3. Select code, press `Ctrl+Shift+Alt+V` → verify markdown code block copied
  4. No selection, press any shortcut → verify current line number copied
  5. Check status bar widget updates with last copied path
  6. Check toast notification appears on copy
  7. Open Settings → Tools → Copy Selection Context → verify UI works

## Output Format Examples

### Relative Path (Ctrl+Shift+Alt+C)
```
src/main/kotlin/MyFile.kt:15-23
```

### Absolute Path (Ctrl+Shift+Alt+A)
```
C:/Users/username/project/src/main/kotlin/MyFile.kt:15-23
```

### With Code Content (Ctrl+Shift+Alt+V)
````
src/main/kotlin/MyFile.kt:15-23
```kotlin
fun example() {
    println("Hello")
}
```
````

## Must NOT Do

### Scope Constraints
- **No AI service integration** — This plugin only copies to clipboard, no API calls
- **No multi-selection accumulation** — One selection at a time
- **No complex Settings UI** — Keep settings minimal (just path type preference)
- **No file tree integration** — Editor context only

### API Constraints
- **No Internal/Deprecated APIs** — Use only public IntelliJ Platform APIs
- **No untilBuild setting** — Must remain unset for forward compatibility
- **No Kotlin stdlib bundling** — IDE provides its own (kotlin.stdlib.default.dependency=false)
- **No PNG icons** — SVG only (40×40px, <3KB)

### Behavior Constraints
- **No error dialogs** — Use toast notifications only
- **No blocking operations** — All clipboard operations are instant
- **No file writes** — Clipboard only, no persistence of copied content

## Common Pitfalls

1. **Forgetting null checks**: Always null-check `CommonDataKeys` results
2. **Line number indexing**: Editor uses 0-indexed lines, display as 1-indexed
3. **No selection handling**: Copy current line number when no selection exists
4. **Notification group registration**: Must register in plugin.xml before use
5. **Project base path**: Can be null in default project, handle gracefully

## Version Compatibility

- **Minimum IDE version**: 2024.3
- **Kotlin version**: 2.1.0
- **Gradle version**: 9.3.1
- **JVM toolchain**: 21
- **Target IDEs**: All IntelliJ Platform IDEs (Rider, IDEA, PyCharm, WebStorm, etc.)

## Useful Gradle Tasks

```bash
# Development
./gradlew buildPlugin          # Build plugin ZIP
./gradlew runIde              # Run IDE with plugin
./gradlew verifyPlugin        # Verify plugin structure

# Debugging
./gradlew --info buildPlugin  # Verbose build output
./gradlew --stacktrace        # Show full stack traces

# Publishing
./gradlew publishPlugin       # Publish to JetBrains Marketplace (requires PUBLISH_TOKEN)
```

## Plugin Metadata

- **ID**: `com.github.hon45.copy-selection-context`
- **Name**: Copy Selection Context
- **Vendor**: hon45
- **Description**: Copy file path, line numbers, and code content to clipboard for AI context sharing
- **Category**: Tools
