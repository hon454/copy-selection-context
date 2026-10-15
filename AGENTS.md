# Copy Selection Context - Project Knowledge Base

> Hierarchical docs: detail files in `.agents/` directory. This root index is always loaded.
> Sub-files are loaded on-demand when deeper context is needed.

## Project Overview

JetBrains IDE plugin for copying code context (file path, line numbers, optionally code) to clipboard for AI assistant workflows.

- **Core Value**: One shortcut copies file path + line numbers + optional code, formatted for AI
- **Target Users**: Developers using AI coding assistants (Claude, ChatGPT, etc.)
- **IDE Scope**: Cross-IDE IntelliJ Platform plugin for 2024.3+; IntelliJ IDEA is the largest observed user base, while Rider is the original use case

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | `build.gradle.kts` |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` |
| IntelliJ Platform Plugin | `build.gradle.kts` |
| Detekt | `build.gradle.kts` |
| Kover | `build.gradle.kts` |
| JVM Toolchain | 21 |
| Min IDE Version | 2024.3 |

Dependabot updates dependency versions, so this table names the file that pins each one instead of copying the number.

## Build Commands

```bash
./gradlew buildPlugin    # Build plugin ZIP (build/distributions/)
./gradlew runIde         # Run IDE with plugin installed
./gradlew test           # Unit tests
./gradlew platformTest   # IntelliJ fixture tests
./gradlew allTests detekt verifyPlugin  # Full local check: tests, static analysis, Plugin Verifier
./gradlew detekt koverXmlReport koverHtmlReport  # Static analysis + coverage
```

A JDK 21 must be installed where Gradle toolchain detection finds it; no toolchain auto-provisioning is configured. On Windows, run `.\gradlew.bat ...` from PowerShell (as CI does) or `cmd //c "gradlew.bat ..."` from Git Bash.

## Testing

Keep automated tests pragmatic: add or change a test only where it guards a real regression risk (plugin.xml descriptor, output/contract rules, documentation sync). Verify UI and platform behavior with manual QA instead of new test suites.

Manual QA: run `./gradlew runIde` to launch the IDE with the plugin.

1. Select code → `Ctrl+Alt+Shift+Z` (`Cmd+Alt+Shift+Z` on macOS) → verify absolute path copied
2. Settings: enable "Include code content" → shortcut → verify markdown code block
3. Settings: switch to Relative path → shortcut → verify relative path
4. No selection → shortcut → verify current line number
5. Right-click → "Copy Selection Context" copies directly; "Copy Selection Context Tools ▸" lists the other eight actions
6. Verify toast notification on copy
7. Settings → Tools → Copy Selection Context → verify radio buttons + checkbox; "Configure shortcuts in Keymap…" opens Keymap filtered to the plugin actions
8. Settings → Keymap → no conflict warning on `Ctrl/Cmd+Alt+Shift+Z`
9. Before changing the default shortcut, run `python3 -I scripts/check-shortcut-conflicts.py <ide-dir>...` against IntelliJ IDEA Community, IntelliJ IDEA (Ultimate) and Rider distributions; bundled plugins such as Deployment declare shortcuts the Community build lacks

## Must NOT Do

**Scope**: No AI service integration (clipboard only), no persistence of collected selections (Context Collection stays session-only, in memory), no complex Settings UI, no file tree integration.

**API**: No Internal/Deprecated APIs, no `untilBuild` setting, no Kotlin stdlib bundling, no PNG icons (SVG only).

**Behavior**: No error dialogs for standard or permalink copies (toast only; modal dialogs are limited to confirmations and Context Collection copy validation), no blocking operations, no file writes.

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | Push/PR to `main` | Build verification + artifact upload |
| `release-validation.yml` | Called by release, manual dispatch, or `codex/verify-*` push | Three-OS tests plus one Linux IDE compatibility pass; never publishes |
| `release.yml` | Push `v*` tag | Release validation gate → build → optional signing → `SHA256SUMS` + attestation → GitHub Release → Marketplace publish (conditional) |

### Release Process

1. Update the `[Unreleased]` entries in `CHANGELOG.md`
2. Bump `version` in `build.gradle.kts`, then hand-edit `CHANGELOG.md`: add `## [x.y.z] - YYYY-MM-DD` directly below `## [Unreleased]`, point the `[Unreleased]` compare link at `vx.y.z...HEAD`, and add `[x.y.z]: .../compare/v<previous>...vx.y.z`. Do not run `patchChangelog`; it reformats the whole file
3. Commit directly to `main` as `chore(release): prepare vx.y.z`, push, then push the tag `vx.y.z`
4. `release.yml` uses `scripts/generate-release-notes.sh` to initialize Gradle separately, then captures only the matching `CHANGELOG.md` section from `getChangelog`
5. The reusable release validation must pass unit/platform tests on Linux, macOS, and Windows plus Detekt, project/structure checks, and the explicit IC/latest IDEA/latest Rider Plugin Verifier targets once on Linux
6. The GitHub Actions-built release ZIP is canonical: the gated Linux release job signs and signature-verifies it when both signing credentials are configured, otherwise leaves it explicitly unsigned; the workflow then verifies `SHA256SUMS` and GitHub provenance for that exact ZIP before upload
7. JetBrains Marketplace publishing runs only when `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty, and uploads the same canonical signed ZIP without rebuilding or re-signing it

**Version rule**: Tag version must match `build.gradle.kts` `version` — workflow fails on mismatch.

## Architecture Overview

Single flat package: `com.github.hon454.copyselectioncontext/`

| File | Role |
|------|------|
| `CopySelectionContextAction.kt` | Main unified action (`Ctrl+Alt+Shift+Z` / `Cmd+Alt+Shift+Z`, the only default shortcut) |
| `CopySelectionBaseAction.kt` / `CopyResultPublisher.kt` | Dumb-aware standard formatting that fixes the code-capture policy before reading caret payloads, with BGT presentation updates plus the project-scoped, policy-driven publisher for clipboard, analytics, highlighting, history, notifications, status, review accounting, and cross-action ordering |
| `OutputFormatter.kt` / `TemplateFormatter.kt` | Built-in Claude Code and Path:Line formats plus custom templates |
| `ContextCollectionService.kt` / `ContextCollectionStore.kt` / `ContextCollectionItem.kt` | Session-only immutable captures, bounded atomic additions, revisions and mutations |
| `ContextCollectionSourceTracker.kt` / `ContextCollectionSubscriptions.kt` | Independent source-state revision, immutable live-file reverse index, document fast path and conservative structural VFS checks without retaining editors/documents |
| `ContextCollectionPanel.kt` / `ContextCollectionSourceTracker.kt` | Collection Go to Source resolves retained live-file identity for Enter, row double-click, and the explicit button; captured paths remain frozen and unavailable sources never fall back to path lookup |
| `ContextCollectionFormatter.kt` / `ContextCollectionOutputService.kt` | Bounded pure formatting and shared keyed background output state |
| `ContextCollectionToolWindowFactory.kt` / `ToolWindowFactoryAdapter.java` / `ContextCollectionPanel.kt` / `ContextCollectionPresentation.kt` / `ContextCollectionTextViewer.kt` | Lazy content-owned collection UI, keyboard organization and full read-only previews |
| `ContextCollectionTextLayout.kt` / `ContextCollectionTextView.kt` / `ContextCollectionTextRaster.kt` | Worker-prepared paragraph geometry, indexed viewport painting and oversized shaping-cluster masks; native text documents and selection/copy remain intact |
| `ContextCollectionTextNavigation.kt` | Cached line-boundary actions that preserve selection without native per-character row scans |
| `ShowContextCollectionAction.kt` | Localized no-editor tool-window open action without a default shortcut |
| `ContextCollectionCopyCommand.kt` / `CopyAllContextCollectionAction.kt` | Shared no-editor copy command, combined confirmation and final EDT input validation |
| `ClipboardRequestCoordinator.kt` | Application-wide request tokens and atomic final clipboard transaction; no retained payload/project state |
| `CopyFailureReporter.kt` | Project-owned, localized clipboard errors with one claim per failed request and final token/project/widget lifetime checks |
| `AddToContextCollectionAction.kt` | Add-only editor action; no publisher or copy side effects |
| `SelectionContext.kt` | Immutable per-caret snapshot of path, file, range, code, language, and filename inputs |
| `CopySelectionUtils.kt` | Path/language helpers and policy-aware single-pass selection context capture without unused text reads |
| `CopySelectionHighlighter.kt` | Editor-scoped gutter highlighter lifecycle |
| `CopyRelativePathAction.kt` | Relative path (context menu only) |
| `CopyAbsolutePathAction.kt` | Absolute path (context menu only) |
| `CopyWithCodeContentAction.kt` | Path + markdown code block (context menu only) |
| `CopyGitPermalinkAction.kt` / `GitRepositoryMetadataResolver.kt` / `GitPermalinkGenerator.kt` | Dumb-aware async, worktree-safe GitHub/GitLab permalink generation with BGT presentation updates, published through the shared result boundary |
| `GitHeadTargetValidator.kt` / `GitHeadSnapshot.kt` / `GitSourceSnapshot.kt` / `GitWindowsFileIdentity.kt` / `GitProcessRunner.kt` / `GitPermalinkLifetime.kt` | Original local HEAD path/content verification through bounded system Git, immutable prepared results, background HEAD revalidation and request-scoped document/VFS ABA guards; one Cancel-default dirty confirmation before publication |
| `ShowCopyHistoryAction.kt` / `CopyHistoryService.kt` / `CopyHistoryPopup.kt` | Dumb-aware history opening with BGT presentation updates plus local non-roaming project history, migration, re-copy, and clear-all |
| `CopyPreview.kt` | Bounded, single-line, Unicode-safe, markup-escaped previews |
| `CopySelectionAnalytics.kt` | Thread-safe opt-in, local-only usage counters and immutable UI snapshots |
| `CopySelectionNotifier.kt` | Toast notifications (BALLOON) |
| `CopySelectionReviewService.kt` / `CopySelectionReviewNotifier.kt` | Session-threshold Marketplace review prompt, local suppression state, and balloon actions |
| `CopySelectionStatusBarWidget.kt` / `CopySelectionStatusBarWidgetFactory.kt` | Last-copy status display and click-to-copy behavior |
| `CopySelectionSettings.kt` | Settings persistence (`@Service` + `@State`) |
| `CopySelectionConfigurable.kt` | Settings UI with multiline template editor, preview, validation, passive review link, and analytics view/reset controls |

**Flow**: User trigger -> Action captures/formats a result or begins async permalink resolution -> project-scoped `CopyResultPublisher` applies its explicit standard/permalink/collection policy -> clipboard -> optional analytics (standard and collection) -> gutter marker (standard and permalink) -> project history (standard and permalink) -> optional notification -> status bar -> optional review eligibility (standard and collection)

Permalinks require system Git only for this action. HEAD-absent targets are blocked, current document equality skips confirmation, and differing text requires one Cancel-default confirmation using the captured SHA/current ranges. Git checks run in the background; final EDT validation rejects changed document/file identity, expired lifetime or superseded application requests. See `docs/development/git-permalink-contract.md` for local-only process safety, cancellation and the external-HEAD synchronization boundary.

Collection copy consumes the current immutable output key/result and validates content/settings revision, actual options and project lifetime on EDT immediately before the application coordinator writes. `COLLECTION` enables opt-in analytics and independent review accounting once, notification preference and status; history and gutter are disabled. `Published(feedbackFailures)` remains successful after optional feedback failure; no effect is retried. Standard, permalink, collection and clipboard-only history/status re-copy all acquire application request tokens. See `docs/development/context-collection-output-contract.md` for #74 integration.

All five managed copy entry points report current clipboard write failure through `CopyFailureReporter`, regardless of success notification settings. Claims are bound to the coordinator's failed attempt; queued errors recheck token and project lifetime immediately before notification, plus widget lifetime for status re-copy. No retry or clipboard restoration is attempted. See `docs/development/clipboard-failure-contract.md`.

## Conventions

- **Code Style**: Kotlin idiomatic, expression bodies, `?.`/`?:` null handling, avoid `!!`
- **Naming**: Actions=`*Action.kt`, UI=`CopySelection*Widget/Notifier.kt`, Settings=`CopySelection*Settings/Configurable.kt`
- **Package**: Flat `com.github.hon454.copyselectioncontext` (no subdirectories)
- **Plugin ID**: `com.github.hon454.copy-selection-context` (kebab-case)
- **Notification Group ID**: `"CopySelectionContext"` (PascalCase, no spaces)
- **PRs**: One feature per PR, include manual testing steps, update AGENTS.md if architecture changes
- **README**: Multilingual — `README.md` (English, primary), `README.ko.md` (Korean), `README.zh-CN.md` (Simplified Chinese), `README.zh-TW.md` (Traditional Chinese), and `README.ja.md` (Japanese). Any README change MUST be applied to all five files. Keep section structure, order, and content in sync across them.

### Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/) with **mandatory body** for all non-trivial commits.

**Format:**
```
type[(scope)]: concise subject (imperative mood, lowercase, no period)

Body paragraph explaining WHY this change was made and WHAT it accomplishes.
Include context that isn't obvious from the diff alone.
```

**Allowed types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`, `style`, `build`, `asset`

**Title rules:**
- Imperative mood ("add" not "added"), lowercase after colon, no trailing period
- Max 72 characters
- Scope is optional; use module name when targeting a specific component (e.g., `feat(settings):`)

**Body rules:**
- Mandatory for all commits except trivial one-liners (typo fix, single-line config change)
- Separated from subject by a blank line
- Explain the motivation and summarize the approach — not a line-by-line diff narration
- Reference class/file names when helpful for future searchability

**Forbidden:**
- AI agent attribution in any form: `Co-authored-by` trailers, `Ultraworked with` footers, or any other AI tool credit lines
- Commit messages must read as if written by a human engineer — no boilerplate signatures

## Detail Files

Read these only when you need deeper context for a specific task:

| File | When to Read |
|------|-------------|
| [`.agents/architecture.md`](.agents/architecture.md) | Modifying components, adding features, understanding data flow or file internals |
| [`.agents/patterns.md`](.agents/patterns.md) | Writing new code, need API patterns for clipboard/notifications/settings/actions |
| [`.agents/gotchas.md`](.agents/gotchas.md) | Build issues, config problems, debugging platform API quirks |
