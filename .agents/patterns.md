# Code Patterns

## AnAction Inheritance

All actions extend `CopySelectionBaseAction` -> `AnAction`. Override `actionPerformed(AnActionEvent)`.

```kotlin
// Extract editor context from AnActionEvent
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

Base class provides: `buildPathString()`, `copyToClipboard()`, `update()`, abstract `getPath()`.

## Clipboard

```kotlin
val content = StringSelection(formattedText)
CopyPasteManager.getInstance().setContents(content)
```

## Notifications

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("CopySelectionContext")  // No spaces, must match plugin.xml
    .createNotification("✓ Copied: $message", NotificationType.INFORMATION)
    .notify(project)
```

## Settings Persistence

```kotlin
@Service
@State(name = "CopySelectionSettings", storages = [Storage("CopySelectionPlugin.xml")])
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>
```

## No Selection -> Current Line

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

## Path Normalization

```kotlin
val normalizedPath = path.replace("\\", "/")
```

## Language Detection

`detectLanguage(file: VirtualFile): String` maps `file.fileType.name` to markdown language identifiers.

15 mappings: kotlin, java, csharp, javascript, typescript, python, ruby, go, rust, php, swift, html, css, xml, sql. Returns `""` for unknown types.

## Status Bar Widget (Stub)

```kotlin
object CopySelectionStatusBarWidget {
    fun update(message: String) {
        println("Status: $message")
    }
}
```

Full implementation would need: `StatusBarWidget` interface with `TextPresentation`, `getAlignment()` returning Float (e.g., 0.0f), `AtomicReference<String>` for thread-safe storage, Factory class registered via `statusBarWidgetFactory` in plugin.xml.
