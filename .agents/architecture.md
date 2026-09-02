# Architecture Details

## Toolchain

| Component | Version |
|-----------|---------|
| Kotlin | 2.4.10 |
| Gradle wrapper | 9.7.1 |
| IntelliJ Platform Gradle Plugin | 2.18.1 |
| Detekt | 2.0.0-alpha.6 |
| Kover | 0.9.8 |
| IntelliJ IDEA Community test platform | 2024.3 |
| JVM toolchain and target | 21 |
| Minimum IDE build | 243 (2024.3) |
| JUnit Jupiter | 6.1.3 |
| MockK | 1.14.11 |

`build.gradle.kts` is the source of truth for plugin and test dependencies, while `gradle/wrapper/gradle-wrapper.properties` pins Gradle. `gradle.properties` keeps `kotlin.stdlib.default.dependency=false` so the plugin uses the IDE-bundled Kotlin standard library. The plugin intentionally omits `untilBuild` for forward compatibility.

## Standard Copy Flow

1. The user invokes `CopySelectionContextAction` with `Ctrl+Alt+C` / `Cmd+Alt+C`, or chooses an explicit path/code action from the editor context menu.
2. `CopySelectionBaseAction` resolves the project, editor, and virtual file. `CopySelectionUtils` reads each caret exactly once into an immutable `SelectionContext` containing the captured path, file, filename, selected text or current line, 1-based inclusive line range, and Markdown language tag.
3. A non-empty selection uses `selectionEnd - 1` as its last included offset, so a selection ending at the next line's start does not include that line. With multiple carets, each captured context is formatted independently and the blocks are joined with a blank line; formatting and post-copy highlighting never re-read mutable editor selection state.
4. `OutputFormatterFactory` selects the configured Claude Code, Path:Line, or custom template formatter. `CopySelectionContextAction` includes code only when enabled; `CopyWithCodeContentAction` always includes it.
5. `CopyPasteManager` writes the complete formatted result to the clipboard.
6. When analytics are enabled, `CopySelectionAnalytics` increments total, selected-format, and detected-language counters exactly once per standard copy action, including multi-caret copies.
7. `CopySelectionHighlighter` replaces the previous gutter markers for the active editor ranges, and the project-level `CopyHistoryService` prepends the result using the configured entry-count limit plus UTF-8 content budgets of 256 KiB per entry and 2 MiB per project. A consecutive duplicate refreshes the newest entry's timestamp instead of adding another row. Oversized results remain on the clipboard but are not persisted.
8. `CopySelectionNotifier` shows a bounded, single-line, markup-escaped preview when notifications are enabled.
9. `CopySelectionStatusBarWidget` stores the full result, displays a safe preview capped at 40 characters including its prefix, and copies the full value again when clicked.
10. `CopySelectionReviewService` counts an eligible successful standard copy action once, regardless of caret count or analytics preference. Notifications must be enabled and the context must have a supported plugin version and be non-test and non-headless before the session counter can change. On exactly the tenth eligible copy in the IDE session, one localized review balloon may appear.

`ShowCopyHistoryAction` opens the current project's history popup. Each row combines a bounded, single-line `CopyPreview` with a localized timestamp while retaining the full stored content for re-copy. Choosing the clear-all item requires explicit confirmation before deleting history.

## Git Permalink Flow

`CopyGitPermalinkAction` extends `AnAction` directly rather than using the standard copy pipeline. It captures the editor ranges and Git root on the UI thread, sorts multiple carets by selection start, and resolves repository metadata on a pooled thread. Only the latest request may publish a result back on the UI thread. Resolution uses typed success/failure results so VCS mapping, metadata, remote-host, path-boundary, I/O, and unexpected failures receive distinct localized guidance. Safe diagnostics record only the failure category, operation, sanitized remote host, and exception class.

`GitRepositoryMetadataResolver` uses NIO and supports normal repositories and linked worktrees. It follows `.git` and `commondir`, resolves symbolic or detached HEADs from loose or packed refs, and prefers the current branch's tracked remote before `origin` or an unambiguous single remote. `GitPermalinkGenerator` accepts supported GitHub/GitLab HTTPS and SSH forms and percent-encodes every repository-relative path segment.

Successful resolution copies one commit-pinned URL per caret, separated by a blank line, and shows a notification. Any missing VCS root, unsupported remote, unresolved commit, out-of-root path, or stale request leaves the clipboard unchanged; current failures show a localized error. Permalink copies do not enter standard history, update the status widget, add gutter markers, or increment standard copy analytics.

## Output Formats

- **Claude Code (`claude`, default)**: ` @src/main/kotlin/MyFile.kt#L15-23 `. When code is included, a language-tagged fenced block follows; the formatter lengthens the fence when leading backtick runs in the code require it.
- **Path:Line (`pathline`)**: `src/main/kotlin/MyFile.kt:15-23`, with the same optional code-block behavior.
- **Custom template (`template`)**: substitutes `{path}`, `{line}`, `{range}`, `{code}`, `{lang}`, and `{filename}`. `FormatContext.filename` is populated from `VirtualFile.name` for standard copy actions.
- **Git permalink action**: produces a commit-pinned `github.com` or `gitlab.com` URL such as `https://github.com/.../blob/<sha>/path#L15-L23`.

Paths use forward slashes in formatted output. Without a selection, the current line and its content are used. The settings UI provides a six-row multiline template editor and six-row read-only, focusable live preview, localized Path and Range / Claude Reference / With Code Block preset labels, accessible labels, and unknown-variable validation on input and apply. Preset keys and template bodies remain stable internal values independent of localized labels.

## Settings and Local State

`CopySelectionSettings` is an application service persisted to `CopySelectionPlugin.xml`.

| State property | Default | Behavior |
|----------------|---------|----------|
| `defaultPathType` | `ABSOLUTE` | Uses an absolute path or a project-relative path when possible |
| `includeCodeContent` | `false` | Adds selected code/current-line content to the main action |
| `enableNotification` | `true` | Enables success balloon notifications |
| `outputFormat` | `claude` | Selects `claude`, `pathline`, or `template` output |
| `codeTrimming` | `false` | Trims leading and trailing whitespace from included code |
| `copyHistorySize` | `10` | Retains 0 through 100 entries; zero disables and clears history |
| `customFormatTemplate` | empty | Stores the multiline template used by the `template` output format |
| `analyticsEnabled` | `false` | Enables local-only application usage counters |

`CopySelectionReviewService` keeps its exact eligible-copy counter only in memory. Notification-disabled, unit-test, headless, unsupported-version, already-prompted-version, and permanently suppressed paths return before changing that counter or prompt state. Its separate local, non-roaming `copySelectionReview.xml` state contains only `lastPromptedVersion`, `neverAskAgain`, and `marketplacePageOpened`. The prompt records the version before it appears, so `Later` suppresses the rest of that version; opening the official Marketplace review page or choosing `Don't ask again` suppresses every future version. `Review on Marketplace` and the Settings link open the same exact review URL on explicit user action. The feature neither reads nor writes analytics, copied content, file information, or review outcomes, and performs no automatic network request.

`CopyHistoryService` persists history per project in the IDE's local, non-roaming workspace storage. Copied code is never written to shareable project settings. Each entry is limited to 256 KiB of UTF-8 content and total project history to 2 MiB; oversized results are still copied but skipped by history. Reducing the count limit trims the oldest entries immediately, and load-time normalization applies the count and byte limits while collapsing consecutive duplicates in existing or legacy state. Clearing through the popup is confirmation-gated, and clearing directly or setting zero persists an empty history. The deprecated `copySelectionHistory.xml` storage entry migrates to workspace storage and is cleaned up. `CopySelectionAnalytics` separately persists opt-in counters at application scope in `copySelectionAnalytics.xml` and does not transmit them. Counter mutation, reset, persistence snapshots, and immutable UI snapshots are synchronized. Settings displays total, format, and language usage and offers a confirmation-gated reset that persists an empty state.

User-facing action, history, notification, status, gutter, and settings strings are resolved through `CopySelectionBundle`. English is the default resource bundle, while Korean, Japanese, Simplified Chinese, and Traditional Chinese override the same active key set; output-format options and template presets resolve their display names through message keys rather than persisted keys or template contents.

## Source Files

The implementation uses the flat package `com.github.hon454.copyselectioncontext`.

| File | Responsibility |
|------|----------------|
| `CopyAbsolutePathAction.kt` | Context-menu action with an absolute path |
| `CopyGitPermalinkAction.kt` | Asynchronous, latest-request-wins Git permalink action |
| `CopyHistoryPopup.kt` | History chooser, re-copy, and clear-all behavior |
| `CopyHistoryService.kt` | Local, non-roaming project history, migration, and retention |
| `CopyPreview.kt` | Bounded, single-line, Unicode-safe, markup-escaped previews |
| `CopyRelativePathAction.kt` | Context-menu action with a project-relative path |
| `CopySelectionAnalytics.kt` | Thread-safe opt-in application-local counters and immutable snapshots |
| `CopySelectionBaseAction.kt` | Shared standard-copy lifecycle and post-copy integrations |
| `CopySelectionBundle.kt` | Localized message lookup |
| `CopySelectionConfigurable.kt` | Tools settings UI, multiline template editor, preview, validation, and local analytics controls |
| `CopySelectionContextAction.kt` | Settings-driven primary action |
| `CopySelectionGutterIconRenderer.kt` | Gutter icon and safe tooltip preview |
| `CopySelectionHighlighter.kt` | Editor-scoped multi-range gutter marker lifecycle |
| `CopySelectionNotifier.kt` | Settings-aware success and localized permalink-failure balloons |
| `CopySelectionReviewNotifier.kt` | Localized honest-review balloon and Review on Marketplace / Later / Don't ask again actions |
| `CopySelectionReviewService.kt` | Session-only threshold, version and environment policy, non-roaming suppression state, and Marketplace opening |
| `CopySelectionSettings.kt` | Persistent application settings and path enum |
| `CopySelectionStatusBarWidget.kt` | Safe last-copy preview and click-to-copy interaction |
| `CopySelectionStatusBarWidgetFactory.kt` | Status-bar widget registration lifecycle |
| `CopySelectionUtils.kt` | VFS paths, language detection, exclusive-end ranges, and single-pass caret context capture |
| `CopySelectionWebHelpProvider.kt` | README help-topic URLs |
| `CopyWithCodeContentAction.kt` | Context-menu action that always includes code |
| `GitPermalinkGenerator.kt` | GitHub/GitLab remote parsing and encoded URL construction |
| `GitPermalinkResult.kt` | Typed permalink results, failure categories, and redacted diagnostic formatting |
| `GitRepositoryMetadataResolver.kt` | Standard and linked-worktree metadata/ref resolution |
| `OutputFormatOption.kt` | Localized output-format setting options |
| `OutputFormatter.kt` | Format context, built-in formatters, and formatter factory |
| `SelectionContext.kt` | Immutable single source of truth for per-caret path, file, range, code, language, and filename inputs |
| `ShowCopyHistoryAction.kt` | Direct action that opens project copy history |
| `TemplateFormatter.kt` | Custom variable substitution, presets, and validation |

## Plugin Registration

`src/main/resources/META-INF/plugin.xml` declares `messages.CopySelectionBundle`, registers the `CopySelectionContext` BALLOON notification group, application settings configurable, project history, application analytics, and review-prompt services, status-bar widget factory, web help provider, primary copy action, history action, three explicit path/code actions, and Git permalink action under `EditorPopupMenu`. Action and group presentations omit descriptor text and descriptions so IntelliJ resolves exact `action.<id>.*` and `group.<id>.*` bundle keys. The only platform dependency is `com.intellij.modules.platform`.

## Verification Architecture

Unit tests cover formatters, `{filename}`, exclusive selection ends, multi-caret joins and highlighting, safe previews, five-locale key parity and descriptor presentations, locale-independent persisted format/preset data, worktree-safe Git metadata, history privacy/migration/retention, template editor behavior, and review threshold/version/suppression/environment policy. `CopySelectionActionFixtureTest` exercises real IntelliJ editor, action-event, clipboard, history, highlighter, async permalink, review cardinality, and missing-context flows. `DocumentationSyncTest` derives toolchain values from build sources, checks all localized README structures and feature markers, lists every Kotlin source file, and verifies registered action inheritance. `CiWorkflowTest` keeps Detekt, Kover XML/HTML coverage, test, project/structure verification, three-IDE Plugin Verifier, packaging, and diagnostic artifact gates ordered before publication, and structurally validates conservative Gradle Dependabot grouping. Detekt intentionally enables only four reviewed defect rules and uses no baseline; Kover publishes diagnostics without enforcing a coverage percentage.

Gradle separates reusable pure unit execution (`test`) from IntelliJ application and editor-fixture execution (`platformTest`). `CopySelectionActionFixtureTest` and `CopyHistoryPersistenceTest` run through `platformTest` with one class per JVM; `allTests` is the complete local and CI aggregate, and `check` depends on it. `buildPlugin` remains packaging-only because CI and release workflows gate it behind a separate `allTests` invocation. `CiWorkflowTest` keeps standard IntelliJ application/fixture markers synchronized with the explicit task partition so new platform-state tests cannot silently fall into the reusable worker. CI invokes the aggregate with `--continue` and uploads XML and HTML report directories for both test tasks.
