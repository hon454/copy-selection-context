# Code Patterns

## AnAction Inheritance

### Shared copy-pipeline actions

`CopySelectionBaseAction` extends `AnAction` and owns the standard copy pipeline. Four registered actions inherit that shared behavior:

- `CopySelectionContextAction`
- `CopyRelativePathAction`
- `CopyAbsolutePathAction`
- `CopyWithCodeContentAction`

These subclasses implement `getPath()` and may override `buildContent()`.

### Specialized direct actions

Two registered actions extend `AnAction` directly because their workflows do not use the standard copy pipeline:

- `CopyGitPermalinkAction` resolves repository metadata on a pooled thread and copies a permalink on the UI thread only when the request is still current. Resolution failure reports an error and leaves the clipboard unchanged.
- `ShowCopyHistoryAction` opens the project history popup.

The specialized actions override `actionPerformed(AnActionEvent)` themselves, while shared-pipeline actions inherit that implementation from `CopySelectionBaseAction`.

Editor-backed copy actions use the following event-data extraction pattern; `ShowCopyHistoryAction` only requires the project.

```kotlin
// Extract editor context from AnActionEvent
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

The base class pipeline covers path and content formatting, clipboard writes, optional analytics, gutter highlighting, project history, notifications, status-bar updates, and one successful-copy signal to the session-only review policy regardless of caret count.

## Clipboard

```kotlin
val content = StringSelection(formattedText)
CopyPasteManager.getInstance().setContents(content)
```

## Notifications

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("CopySelectionContext")  // No spaces, must match plugin.xml
    .createNotification(
        CopySelectionBundle.message("notification.copied", CopyPreview.notification(message)),
        NotificationType.INFORMATION,
    )
    .notify(project)
```

## Settings Persistence

```kotlin
@Service
@State(name = "CopySelectionSettings", storages = [Storage("CopySelectionPlugin.xml")])
class CopySelectionSettings : PersistentStateComponent<CopySelectionSettings.State>
```

Review-prompt decisions use a separate application service with `RoamingType.DISABLED`. Keep the session counter outside its persisted `State`; store only the last prompted plugin version and permanent suppression signals. Mark the version before notifying so closing the balloon or choosing `Later` cannot repeat the prompt in that version.

## Selection Range and Current-Line Fallback

```kotlin
val lines = if (selectionModel.hasSelection()) {
    val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
    val finalIncludedOffset = selectionModel.selectionEnd - 1
    val endLine = document.getLineNumber(finalIncludedOffset) + 1
    Pair(startLine, endLine)
} else {
    val currentLine = editor.caretModel.logicalPosition.line + 1
    Pair(currentLine, currentLine)
}
```

IntelliJ selection ends are exclusive, so line resolution must use `selectionEnd - 1`. Multi-caret actions resolve each caret independently and join formatted blocks with a blank line.

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

The widget stores the full last-copied content in an `AtomicReference`, shows a bounded single-line preview with a total 40-character budget including its prefix, and copies the full value again when clicked. `CopyPreview` preserves Unicode code points, escapes notification/tooltip markup, and never cuts an escape entity. `CopySelectionStatusBarWidgetFactory` registers the widget through `statusBarWidgetFactory` in `plugin.xml`; standard path/code actions call `update()` after copying.
