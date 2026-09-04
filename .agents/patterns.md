# Code Patterns

## AnAction Inheritance

### Shared copy-pipeline actions

`CopySelectionBaseAction` extends `AnAction`, captures and formats standard results, then publishes through the project-scoped `CopyResultPublisher` with the explicit `STANDARD` policy. Four registered actions inherit that shared behavior:

- `CopySelectionContextAction`
- `CopyRelativePathAction`
- `CopyAbsolutePathAction`
- `CopyWithCodeContentAction`

These subclasses implement `getPath()` and may override `buildContent()`.

### Specialized direct actions

Four registered actions extend `AnAction` directly because their workflows do not use the standard copy pipeline:

- `CopyGitPermalinkAction` resolves repository metadata on a pooled thread and publishes with `GIT_PERMALINK` only when its application request token is still current. Any later managed plugin copy invalidates the token across projects; current resolution failure reports an error and leaves the clipboard unchanged.
- `CopyAllContextCollectionAction` invokes the shared `ContextCollectionCopyCommand` without requiring an editor and implements `DumbAware`. The command consumes `ContextCollectionOutputService` state and confirms all warnings together.
- `AddToContextCollectionAction` captures a bounded atomic batch through `ContextCollectionService`, implements `DumbAware`, and never invokes the publisher.
- `ShowCopyHistoryAction` opens the project history popup.

The specialized actions override `actionPerformed(AnActionEvent)` themselves, while shared-pipeline actions inherit that implementation from `CopySelectionBaseAction`.

Editor-backed copy actions use the following event-data extraction pattern; `ShowCopyHistoryAction` only requires the project.

```kotlin
// Extract editor context from AnActionEvent
val project = e.getData(CommonDataKeys.PROJECT) ?: return
val editor = e.getData(CommonDataKeys.EDITOR) ?: return
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
```

The publisher's `STANDARD` policy enables clipboard writes, optional analytics, gutter highlighting, project history, notifications, status-bar updates, and one successful-copy signal to the session-only review policy regardless of caret count. `GIT_PERMALINK` enables clipboard, highlighting, history, notifications, and status while explicitly disabling analytics and review accounting.

## Clipboard

All successful standard, permalink and collection results reach `CopyResultPublisher`; its production side-effect adapter performs the clipboard write inside `ClipboardRequestCoordinator.writeIfCurrent`. The coordinator owns only application request identity and the atomic final check/write. Acquire the request at invocation, before async work, and keep dialogs outside its monitor. Final collection validation must run on EDT with content/settings mutations. Use `ClipboardRequestCoordinator.recopy` for clipboard-only history/status actions. Never write before ordering is checked. `COLLECTION` disables history/gutter, attributes analytics to prepared actual format and reduced language, and counts review independently. `Published(feedbackFailures)` means the clipboard succeeded even if an optional effect failed; never retry a token or an attempted effect.

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

Review-prompt decisions use a separate application service with `RoamingType.DISABLED`. Gate notification-disabled, unit-test, headless, unsupported-version, and already-suppressed contexts before changing the session counter or persisted state. Keep the eligible-copy counter outside persisted `State`; store only the last prompted plugin version and permanent suppression signals. Mark the version before notifying so closing the balloon or choosing `Later` cannot repeat the prompt in that version.

Read the current plugin version from `META-INF/copy-selection-context-version.properties`, which `processResources` expands from the canonical Gradle project version. Do not use `PluginManagerCore` for plugin metadata; its lookup methods are internal in newer IDE builds.

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
class CopySelectionStatusBarWidget : CustomStatusBarWidgetAdapter()
```

The Java `CustomStatusBarWidgetAdapter` implements the public `CustomStatusBarWidget` API without Kotlin generating compatibility bridges for deprecated `StatusBarWidget` default methods. The Kotlin widget extends that adapter instead of implementation-only editor widget classes or the obsolete IntelliJ `Consumer` callback. Its Swing label is created lazily, stores the full last-copied content in an `AtomicReference`, shows a bounded single-line preview with a total 40-character budget including its prefix, and copies the full value again when clicked. `CopyPreview` preserves Unicode code points, escapes notification/tooltip markup, and never cuts an escape entity. `CopySelectionStatusBarWidgetFactory` registers the widget through `statusBarWidgetFactory` in `plugin.xml`; standard path/code actions call `update()` after copying.

## Collection service integration

Subscribe on EDT using a content-owned disposable, then read the immutable snapshot in the same EDT turn. Read-only background formatting uses that snapshot; mutations and final publication validation stay on EDT. Schedule mutations after notification callbacks return. Content revision changes only for effective list/order/include-code changes; subscribe separately to source status for changed/renamed/unavailable labels. Confirm clear with its original revision and pass it to `clear(expectedRevision)`. See [the shared contract](../docs/development/context-collection-contract.md) for capacity, identity and lifetime rules.

Use `ContextCollectionOutputService.subscribe` plus `snapshot()` for final preview and bytes; disable Copy All and clear stale output claims on `Calculating`. Never calculate output or warnings in the panel. Invoke `ContextCollectionCopyCommand.execute()` for both Find Action and tool-window copies. Call `CopySelectionSettings.outputSettingsCommitted()` after successful Apply, never UI Reset; load replacement also signals. Current tuple checks defend publication from callers omitting the signal. See [the output/copy contract](../docs/development/context-collection-output-contract.md) for typed states and combined confirmation ownership.
