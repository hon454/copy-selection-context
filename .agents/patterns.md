# Code Patterns

## AnAction Inheritance

All actions extend `CopySelectionBaseAction` -> `AnAction`. Override `actionPerformed(AnActionEvent)`.

```kotlin
// Extract editor context from AnActionEvent
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

Base class provides the shared action pipeline: path and content formatting, clipboard writes, optional analytics, gutter highlighting, project history, notifications, and status-bar updates. Subclasses implement `getPath()` and may override `buildContent()`.

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

`CopySelectionUtils.detectLanguage(file: VirtualFile): String` maps `file.fileType.name` to markdown language identifiers.

There are 39 explicit mappings, including Kotlin, Java, C#, JavaScript/TypeScript, Python, markup/config formats, shell, and common systems languages. Unknown file types fall back to the lowercase file extension.

## Status Bar Widget

```kotlin
class CopySelectionStatusBarWidget(project: Project) :
    EditorBasedWidget(project), TextPresentation
```

The widget stores the last copied content in an `AtomicReference`, shows a 40-character preview, and copies the full value again when clicked. `CopySelectionStatusBarWidgetFactory` registers it through `statusBarWidgetFactory` in `plugin.xml`; standard path/code actions call `update()` after copying.
