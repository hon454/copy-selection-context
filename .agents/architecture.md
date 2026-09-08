# Architecture Details

## Toolchain

| Component | Version |
|-----------|---------|
| Kotlin | 2.4.10 |
| Gradle wrapper | 9.7.1 |
| IntelliJ Platform Gradle Plugin | 2.18.1 |
| Detekt | 2.0.0-alpha.6 |
| Kover | 0.9.9 |
| IntelliJ IDEA Community test platform | 2024.3 |
| Plugin Verifier targets | IC 2024.3, IU 2026.2.2, RD 2026.2.1 |
| JVM toolchain and target | 21 |
| Minimum IDE build | 243 (2024.3) |
| JUnit Jupiter | 6.1.3 |
| MockK | 1.14.11 |

`build.gradle.kts` is the source of truth for plugin and test dependencies, while `gradle/wrapper/gradle-wrapper.properties` pins Gradle. `gradle.properties` keeps `kotlin.stdlib.default.dependency=false` so the plugin uses the IDE-bundled Kotlin standard library. The plugin intentionally omits `untilBuild` for forward compatibility.

## Standard Copy Flow

1. The user invokes `CopySelectionContextAction` with `Ctrl+Alt+C` / `Cmd+Alt+C`, or chooses an explicit path/code action from the editor context menu.
2. The dumb-aware `CopySelectionBaseAction` evaluates project/editor/file availability on the background action update thread, then resolves those same inputs on EDT when invoked. It snapshots settings and fixes the action's code-capture policy before visiting carets. `CopySelectionUtils` captures path, file, filename, 1-based inclusive line range and Markdown language tag for every caret, but reads the selected/current-line payload exactly once only when that policy includes code. The immutable `SelectionContext` carries an empty code value for path-only formatting and never retains an editor, document, caret or lazy reader. This flow uses editor, document, VFS and settings state without an index-backed query.
3. A non-empty selection uses `selectionEnd - 1` as its last included offset, so a selection ending at the next line's start does not include that line. With multiple carets, each captured context is formatted independently and the blocks are joined with a blank line; formatting and post-copy highlighting never re-read mutable editor selection state.
4. `OutputFormatterFactory` selects the configured Claude Code, Path:Line, or custom template formatter using the same settings snapshot and include-code decision used for capture. `CopySelectionContextAction` includes code only when enabled; `CopyWithCodeContentAction` always includes it; explicit relative/absolute path actions never acquire code payloads. A custom template's `{code}` continues to follow the main include-code setting.
5. The project-scoped `CopyResultPublisher` obtains an application `ClipboardRequestCoordinator` token at invocation and applies the explicit `STANDARD` policy only while current. The same application sequence covers async permalinks, collection copies, history and status re-copy across projects.
6. `CopyPasteManager` writes the complete formatted result to the clipboard.
7. When analytics are enabled, `CopySelectionAnalytics` increments total, selected-format, and detected-language counters exactly once per standard copy action, including multi-caret copies.
8. `CopySelectionHighlighter` replaces the previous gutter markers for the active editor ranges, and the project-level `CopyHistoryService` prepends the result using the configured entry-count limit plus UTF-8 content budgets of 256 KiB per entry and 2 MiB per project. A consecutive duplicate refreshes the newest entry's timestamp instead of adding another row. Oversized results remain on the clipboard but are not persisted.
9. `CopySelectionNotifier` shows a bounded, single-line, markup-escaped preview when notifications are enabled.
10. `CopySelectionStatusBarWidget` uses the public custom-widget API, stores the full latest successful plugin result, displays a safe preview capped at 40 characters including its prefix, and copies the full value again when clicked.
11. `CopySelectionReviewService` counts an eligible successful standard copy action once, regardless of caret count or analytics preference. It reads the canonical plugin version from a build-expanded resource without relying on internal plugin-manager APIs. Notifications must be enabled and the context must have a supported plugin version and be non-test and non-headless before the session counter can change. On exactly the tenth eligible copy in the IDE session, one localized review balloon may appear.

The dumb-aware `ShowCopyHistoryAction` checks project availability on the background action update thread and opens the current project's history popup on EDT. Each row combines a bounded, single-line `CopyPreview` with a localized timestamp while retaining the full stored content for re-copy. Choosing the clear-all item requires explicit confirmation before deleting history.

## Git Permalink Flow

`CopyGitPermalinkAction` extends `DumbAwareAction` directly and uses background presentation updates. On EDT it obtains the shared application request token and captures the Git root, full bounded unsaved document, charset and sorted caret ranges. `GitPermalinkLifetime` owns disposable document/VFS listeners with weak editor/document/file references and a monotonic change latch, so undo or rename-back cannot restore an old approval. Metadata and original HEAD object checks run in a cancellable background task without indexes. Typed failures distinguish missing Git, execution/timeout/output limits, absent or unavailable paths, unsupported content, changed HEAD and existing metadata/remote failures. Diagnostics contain category, operation, sanitized host and exception class only.

`GitHeadTargetValidator` uses system Git only for this action: literal `ls-tree` checks the path and raw `cat-file` reads the original SHA's blob. Strict charset/BOM decoding and IDE newline normalization compare the current document with HEAD; `check-attr --source` accounts for Git's UTF-8 storage of `working-tree-encoding` paths. Absent paths are blocked. Equal content needs no confirmation; differing content requires one Cancel-default confirmation retaining the captured SHA/current ranges, without line mapping. `GitHeadSnapshot` re-resolves metadata identity/stamps/digests before the initial UI decision and again after approval. Final EDT publication verifies document/file identity, change latch, token and lifetime. External repository writes after the last background HEAD observation are outside that snapshot; no EDT filesystem reads or repository locks are added. See the [Git permalink contract](../docs/development/git-permalink-contract.md).

`GitProcessRunner` discovers system `git`/`git.exe` on each request and invokes argv with explicit root/SHA, no replacement refs, no lazy fetch, no optional locks and literal paths. The child environment removes inherited Git repository/object/config/helper controls and disables all transports. Repository-owned worktree/common-dir and local alternates remain intact. Both pipes drain with independent bounds, finite timeouts and owned process/stream cleanup. Unsupported local queries fail without fallback, helper/filter execution or network. Platform/coroutine cancellation propagates after cleanup through every pre-publication boundary.

`GitRepositoryMetadataResolver` uses NIO and supports normal repositories and linked worktrees. It follows `.git` and `commondir`, resolves symbolic or detached HEADs from loose or packed refs, and prefers the current branch's tracked remote before `origin` or an unambiguous single remote. `GitConfigIncludeResolver` expands includes inline, retaining the first URL for each remote while applying last-value-wins to each branch's tracked remote in actual read order. Include paths may be absolute, relative to the declaring config file, or `~/`-relative; `~user/` and `%(prefix)/` paths are not supported and fail explicitly rather than being misresolved.

Conditional includes support `gitdir`, case-insensitive `gitdir/i`, and `onbranch`, including Git's `*`, `?`, `**/`, `/**`, character-class, trailing-slash, config-relative `./`, and home-relative `~/` matching rules relevant to repository-local resolution. Git-directory matching checks both normalized and real paths. Other conditions, including `hasconfig:remote.*.url`, are deliberately unsupported and treated as non-matching. Recursive expansion detects active-file cycles and permits at most 10 include edges. Missing, unreadable, invalid-path, cyclic, and over-depth includes return dedicated typed failures; diagnostics retain only safe operation, reason, and exception-type metadata, never config values or filesystem paths. `GitPermalinkGenerator` accepts supported GitHub/GitLab HTTPS and SSH forms and percent-encodes every repository-relative path segment.

Successful resolution sends one commit-pinned URL per caret, separated by a blank line, through the publisher's explicit `GIT_PERMALINK` policy. That policy updates the clipboard, active-editor gutter markers, project history, optional notification, and status bar, but deliberately disables analytics and review-prompt accounting. Any missing VCS root, unsupported remote, unresolved commit, out-of-root path, or stale request produces no success-side effects and leaves the clipboard unchanged; current failures show a localized error. Because both policy paths share project ordering, the status bar always represents the latest successful plugin copy.


## Session Context Collection

`ContextCollectionService` is a project light service with no persistence. Additions retain exact unsaved code and frozen capture paths/ranges, with stable capture numbers/time, source identity separate from display paths, and all-or-nothing UTF-8 budgets (100 items, 256 KiB per item, 2 MiB total). Immutable snapshots and disposable notifications have a content revision independent of source status. Source-only document/VFS events never change frozen output or invalidate a prepared payload. Capture does not call `CopyResultPublisher` and leaves clipboard, history, status, gutter, analytics and review accounting alone.

`ContextCollectionFixtureTest` joins the isolated `platformTest` partition. The full [shared service contract](../docs/development/context-collection-contract.md) defines EDT mutation, subscription lifetime, source tracking, stable identity and #75/#74 ownership. The [non-sensitive sample](../docs/samples/context-collection/README.md) provides deterministic multi-file selections for later real screenshots.

## Output Formats

Collection output uses `ContextCollectionFormatter`, `ContextCollectionOutputService` and `ContextCollectionCopyCommand`. The shared [output contract](../docs/development/context-collection-output-contract.md) defines immutable content/settings keys, background cancellation/discard, one current payload, exact bounded UTF-8 construction, fixed UTC snapshot labels and one combined confirmation. `CopyAllContextCollectionAction` works without an editor. The `COLLECTION` publisher policy has opt-in actual-format/language analytics and independent review accounting, notification preference and status, with no history or gutter changes. Final content/settings/lifetime validation and coordinator write are contiguous on EDT. `Published(feedbackFailures)` isolates optional failures after a successful clipboard write; a token never retries an attempted write or effect. Clipboard-only history/status re-copy participates in ordering without adding feedback. The application coordinator retains no project, code or history. OS/external clipboard history and native Copy are outside its scope.

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
| `ContextCollectionPresentation.kt` | Bounded escaped rows, localized source/time metadata and stable selection helpers |
| `ContextCollectionTextViewer.kt` | Content-owned native text documents and cancellable preparation requests, cleared on supersession/content or project disposal |
| `ContextCollectionTextLayout.kt` | Immutable paragraph bidi/shaping geometry and binary-search line/cell indexes prepared off EDT |
| `ContextCollectionTextView.kt` | Native text area with a custom Swing View for visible fragments, logical/visual caret mapping and selection painting |
| `ContextCollectionTextRaster.kt` | Background glyph masks, caret coordinates and indexed native mouse hits for oversized indivisible shaping clusters |
| `ContextCollectionTextNavigation.kt` | Cached line-boundary actions that avoid native per-character Home/End and line-selection scans |
| `ContextCollectionPanel.kt` | Keyboard-accessible collection management consuming shared snapshots/output/copy |
| `ContextCollectionToolWindowFactory.kt` / `ToolWindowFactoryAdapter.java` | Lazy right tool window with one non-closeable content and its disposable; public Java adapter avoids Kotlin bridges to internal platform defaults |
| `ShowContextCollectionAction.kt` | No-editor, assignable localized open action using the shared G-prefix default |
| `ContextCollectionItem.kt` | Immutable captures, source locations, snapshots and typed add results |
| `ContextCollectionStore.kt` | Pure bounded capture transaction and session mutation engine |
| `ContextCollectionService.kt` | Project-only session service and editor capture adapter |
| `ContextCollectionSourceTracker.kt` | Stable live-source identity, immutable file-to-capture index, unrelated-document fast path and conservative structural VFS checks |
| `ContextCollectionSubscriptions.kt` | Disposable snapshot listeners with safe callback failure isolation |
| `AddToContextCollectionAction.kt` | Localized add-only action without copy effects |
| `CustomStatusBarWidgetAdapter.java` | Public custom-widget bridge that avoids Kotlin-generated deprecated status API methods |
| `CopyAbsolutePathAction.kt` | Context-menu action with an absolute path |
| `CopyGitPermalinkAction.kt` | Dumb-aware, BGT-updated, asynchronous latest-request-wins Git permalink action |
| `CopyHistoryPopup.kt` | History chooser, re-copy, and clear-all behavior |
| `CopyHistoryService.kt` | Local, non-roaming project history, migration, and retention |
| `CopyPreview.kt` | Bounded, single-line, Unicode-safe, markup-escaped previews |
| `CopyResultPublisher.kt` | Project-scoped copy-result ordering plus explicit standard/permalink side-effect policies |
| `CopyFailureReporter.kt` | Project-owned clipboard failure reporting, identity/lifetime checks and clipboard-only re-copy |
| `CopyRelativePathAction.kt` | Context-menu action with a project-relative path |
| `CopySelectionAnalytics.kt` | Thread-safe opt-in application-local counters and immutable snapshots |
| `CopySelectionBaseAction.kt` | Dumb-aware shared standard-copy lifecycle with BGT updates and EDT post-copy integrations |
| `CopySelectionBundle.kt` | Localized message lookup through the public class-aware `DynamicBundle` constructor |
| `CopySelectionConfigurable.kt` | Tools settings UI, template validation, analytics, current/default shortcut list, and confirmation staged until Apply/OK; Reset, Cancel and disposal discard pending restoration |
| `CopySelectionContextAction.kt` | Settings-driven primary action |
| `CopySelectionShortcuts.kt` | Shared nine-command defaults and active-keymap lineage selection, effective shortcut display, prefix-conflict checks, and source-preserving keyboard restoration through a validated derived keymap |
| `CopySelectionGutterIconRenderer.kt` | Gutter icon and safe tooltip preview |
| `CopySelectionHighlighter.kt` | Editor-scoped multi-range gutter marker lifecycle |
| `CopySelectionNotifier.kt` | Settings-aware success, guarded clipboard-failure and localized permalink-failure balloons |
| `CopySelectionReviewNotifier.kt` | Localized honest-review balloon and Review on Marketplace / Later / Don't ask again actions |
| `CopySelectionReviewService.kt` | Session-only threshold, build-resource version and environment policy, non-roaming suppression state, and Marketplace opening |
| `CopySelectionSettings.kt` | Persistent application settings and path enum |
| `CopySelectionStatusBarWidget.kt` | Public custom status widget with safe last-copy preview and click-to-copy interaction |
| `CopySelectionStatusBarWidgetFactory.kt` | Status-bar widget registration lifecycle |
| `CopySelectionUtils.kt` | VFS paths, language detection, exclusive-end ranges, and policy-aware single-pass caret context capture |
| `CopySelectionWebHelpProvider.kt` | README help-topic URLs |
| `CopyWithCodeContentAction.kt` | Context-menu action that always includes code |
| `GitPermalinkGenerator.kt` | GitHub/GitLab remote parsing and encoded URL construction |
| `GitConfigIncludeResolver.kt` | Ordered, bounded Git config include and supported conditional-include expansion |
| `GitPermalinkResult.kt` | Typed permalink results, failure categories, and redacted diagnostic formatting |
| `GitRepositoryMetadataResolver.kt` | Standard and linked-worktree metadata/ref resolution |
| `GitProcessRunner.kt` | System Git argv/environment boundary with finite process/output limits and cancellation cleanup |
| `GitHeadTargetValidator.kt` / `GitHeadSnapshot.kt` / `GitSourceSnapshot.kt` | Original HEAD tree/blob/document comparison and immutable Git metadata/local source revalidation evidence |
| `GitWindowsFileIdentity.kt` | Windows volume/file ID lookup through bundled JNA, using a short metadata-only handle on BGT when NIO has no file key |
| `GitPermalinkLifetime.kt` | Request-scoped weak owners, document/VFS ABA latches and final EDT identity checks |
| `OutputFormatOption.kt` | Localized output-format setting options |
| `OutputFormatter.kt` | Format context, built-in formatters, and formatter factory |
| `SelectionContext.kt` | Immutable single source of truth for per-caret path, file, range, code, language, and filename inputs |
| `ShowCopyHistoryAction.kt` | Dumb-aware direct action with BGT updates that opens project copy history on EDT |
| `TemplateFormatter.kt` | Custom variable substitution, presets, and validation |
| `ClipboardRequestCoordinator.kt` | Application request identity and atomic clipboard transaction |
| `ContextCollectionFormatter.kt` | Immutable output keys/options/results and bounded pure collection formatting |
| `ContextCollectionOutputService.kt` | Latest background calculation and disposable computed-state subscriptions |
| `ContextCollectionCopyCommand.kt` | Shared confirmation, input validation and no-editor collection publication |
| `CopyAllContextCollectionAction.kt` | Localized no-editor Copy All action |

## Plugin Registration

`src/main/resources/META-INF/plugin.xml` declares `messages.CopySelectionBundle`, registers the `CopySelectionContext` BALLOON notification group, application settings configurable, project history, application analytics, and review-prompt services, status-bar widget factory, web help provider, primary copy action, history action, three explicit path/code actions, and Git permalink action under `EditorPopupMenu`. Action and group presentations omit descriptor text and descriptions so IntelliJ resolves exact `action.<id>.*` and `group.<id>.*` bundle keys. The only platform dependency is `com.intellij.modules.platform`.

## Verification Architecture

Unit tests cover formatters, `{filename}`, exclusive selection ends, multi-caret joins and highlighting, safe previews, publisher policy/cardinality/ordering, five-locale key parity and descriptor presentations, locale-independent persisted format/preset data, worktree-safe Git metadata and bounded config includes, history privacy/migration/retention, template editor behavior, and review threshold/version/suppression/environment policy. `CopySelectionActionFixtureTest` exercises real IntelliJ editor, action-event, clipboard, history, highlighter, async permalink success and failure clipboard preservation, cross-action stale suppression, review cardinality, and missing-context flows. `CopySelectionDumbModeFixtureTest` enters dumb mode through the public task API, resolves the actual `ActionManager` registrations, checks action-system eligibility and BGT update declarations, executes standard actions through `ActionUtil`, and verifies history re-copy plus Git metadata/publication ordering before restoring smart mode. `DocumentationSyncTest` derives toolchain values from build sources, checks all localized README structures and feature markers, lists every Kotlin source file, and verifies registered action inheritance. `CiWorkflowTest` keeps PR validation on Linux, parses the reusable three-OS release matrix and its final gate, rejects missing/permissive release dependencies with negative YAML fixtures, and ensures the explicit three-IDE Plugin Verifier plus single Linux canonical publication path remain fail-closed. Detekt intentionally enables only four reviewed defect rules and uses no baseline; Kover publishes diagnostics without enforcing a coverage percentage.

Gradle separates reusable pure unit execution (`test`) from IntelliJ application and editor-fixture execution (`platformTest`). `CopySelectionActionFixtureTest`, `CopySelectionDumbModeFixtureTest` and `CopyHistoryPersistenceTest` run through `platformTest` with one class per JVM; `allTests` is the complete local and CI aggregate, and `check` depends on it. `buildPlugin` remains packaging-only because CI and release workflows gate it behind a separate `allTests` invocation. `CiWorkflowTest` keeps standard IntelliJ application/fixture markers synchronized with the explicit task partition so new platform-state tests cannot silently fall into the reusable worker. CI invokes the aggregate with `--continue` and uploads XML and HTML report directories for both test tasks.

## Collection tool window

The declarative right-hand Context Collection tool window initializes only when opened and owns one non-closeable content. Its disposable owns snapshot/source/output subscriptions and both read-only plain-text viewer documents. Detached documents and complete paragraph geometry are prepared on a pooled thread and installed only for the current viewer generation. Native JTextArea documents, TransferHandler and accessibility retain every original code unit. Line action keys use cached boundaries while preserving selection and other native actions. ContextCollectionTextView reads cached dimensions and indexes visible lines/fragments instead of invoking Swing whole-paragraph layout. Cancellation clears the payload and prepared document inside a request even while its EDT callback remains queued. Disposal clears documents, pending request resources and list models; session captures remain service-owned. Source-only updates repaint labels without replacing output. Stable IDs preserve list selection through additions/reordering; removal selects the nearest survivor. Clear confirmation defaults to Cancel and validates the captured revision. Output bytes, warnings and Copy All use the #75 service/command unchanged.
