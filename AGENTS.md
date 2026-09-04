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
| Kotlin | 2.4.10 |
| Gradle | 9.7.1 |
| IntelliJ Platform Plugin | 2.18.1 |
| Detekt | 2.0.0-alpha.6 |
| Kover | 0.9.9 |
| JVM Toolchain | 21 |
| Min IDE Version | 2024.3 |

## Build Commands

**Windows (this project's dev environment):**
```bash
cmd //c "gradlew.bat buildPlugin"    # Build plugin ZIP (build/distributions/)
cmd //c "gradlew.bat runIde"         # Run IDE with plugin installed
cmd //c "gradlew.bat verifyPlugin"   # Verify plugin structure
cmd //c "gradlew.bat detekt koverXmlReport koverHtmlReport" # Static analysis + coverage
cmd //c "gradlew.bat publishPlugin"  # Publish to Marketplace (requires PUBLISH_TOKEN)
```

> **Note**: `./gradlew` does not work in this environment. The shell is bash-on-Windows, so Gradle must be invoked via `cmd //c "gradlew.bat ..."` to run the Windows batch wrapper correctly.

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | Push/PR to `main` | Build verification + artifact upload |
| `release.yml` | Push `v*` tag | Build → GitHub Release (ZIP attached) → Marketplace publish (conditional) |

### Release Process

1. Update the `[Unreleased]` entries in `CHANGELOG.md`
2. Bump `version` in `build.gradle.kts`, then run `cmd //c "gradlew.bat patchChangelog"`
3. Commit the version and changelog, tag (`v<major>.<minor>.<patch>`), and push
4. `release.yml` uses `scripts/generate-release-notes.sh` to initialize Gradle separately, then captures only the matching `CHANGELOG.md` section from `getChangelog`
5. The GitHub Actions-built release ZIP is canonical: signed and signature-verified when both signing credentials are configured, otherwise explicitly unsigned; the workflow then verifies `SHA256SUMS` and GitHub provenance for that exact ZIP before upload
6. JetBrains Marketplace publishing runs only when `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty, and uploads the same canonical signed ZIP without rebuilding or re-signing it

**Version rule**: Tag version must match `build.gradle.kts` `version` — workflow fails on mismatch.

## Architecture Overview

Single flat package: `com.github.hon454.copyselectioncontext/`

| File | Role |
|------|------|
| `CopySelectionContextAction.kt` | Main unified action (`Ctrl+Alt+C` shortcut) |
| `CopySelectionBaseAction.kt` / `CopyResultPublisher.kt` | Standard formatting plus the project-scoped, policy-driven publisher for clipboard, analytics, highlighting, history, notifications, status, review accounting, and cross-action ordering |
| `OutputFormatter.kt` / `TemplateFormatter.kt` | Built-in Claude Code and Path:Line formats plus custom templates |
| `ContextCollectionService.kt` / `ContextCollectionStore.kt` / `ContextCollectionItem.kt` | Session-only immutable captures, bounded atomic additions, revisions and mutations |
| `ContextCollectionSourceTracker.kt` / `ContextCollectionSubscriptions.kt` | Independent source-state revision and disposable listeners without retaining editors/documents |
| `ContextCollectionFormatter.kt` / `ContextCollectionOutputService.kt` | Bounded pure formatting and shared keyed background output state |
| `ContextCollectionToolWindowFactory.kt` / `ToolWindowFactoryAdapter.java` / `ContextCollectionPanel.kt` / `ContextCollectionPresentation.kt` / `ContextCollectionTextViewer.kt` | Lazy content-owned collection UI, keyboard organization and full read-only previews |
| `ShowContextCollectionAction.kt` | Localized no-editor tool-window open action without a default shortcut |
| `ContextCollectionCopyCommand.kt` / `CopyAllContextCollectionAction.kt` | Shared no-editor copy command, combined confirmation and final EDT input validation |
| `ClipboardRequestCoordinator.kt` | Application-wide request tokens and atomic final clipboard transaction; no retained payload/project state |
| `AddToContextCollectionAction.kt` | Add-only editor action; no publisher or copy side effects |
| `SelectionContext.kt` | Immutable per-caret snapshot of path, file, range, code, language, and filename inputs |
| `CopySelectionUtils.kt` | Path/language helpers and single-pass selection context capture |
| `CopySelectionHighlighter.kt` | Editor-scoped gutter highlighter lifecycle |
| `CopyRelativePathAction.kt` | Relative path (context menu only) |
| `CopyAbsolutePathAction.kt` | Absolute path (context menu only) |
| `CopyWithCodeContentAction.kt` | Path + markdown code block (context menu only) |
| `CopyGitPermalinkAction.kt` / `GitRepositoryMetadataResolver.kt` / `GitPermalinkGenerator.kt` | Async, worktree-safe GitHub/GitLab permalink generation published through the shared result boundary |
| `CopyHistoryService.kt` / `CopyHistoryPopup.kt` | Local non-roaming project history, migration, re-copy, and clear-all |
| `CopyPreview.kt` | Bounded, single-line, Unicode-safe, markup-escaped previews |
| `CopySelectionAnalytics.kt` | Thread-safe opt-in, local-only usage counters and immutable UI snapshots |
| `CopySelectionNotifier.kt` | Toast notifications (BALLOON) |
| `CopySelectionReviewService.kt` / `CopySelectionReviewNotifier.kt` | Session-threshold Marketplace review prompt, local suppression state, and balloon actions |
| `CopySelectionStatusBarWidget.kt` / `CopySelectionStatusBarWidgetFactory.kt` | Last-copy status display and click-to-copy behavior |
| `CopySelectionSettings.kt` | Settings persistence (`@Service` + `@State`) |
| `CopySelectionConfigurable.kt` | Settings UI with multiline template editor, preview, validation, passive review link, and analytics view/reset controls |

**Flow**: User trigger -> Action captures/formats a result or begins async permalink resolution -> project-scoped `CopyResultPublisher` applies explicit standard/permalink policy -> clipboard -> optional standard-only analytics -> gutter marker -> project history -> optional notification -> status bar -> optional standard-only review eligibility

Collection copy consumes the current immutable output key/result and validates content/settings revision, actual options and project lifetime on EDT immediately before the application coordinator writes. `COLLECTION` enables opt-in analytics and independent review accounting once, notification preference and status; history and gutter are disabled. `Published(feedbackFailures)` remains successful after optional feedback failure; no effect is retried. Standard, permalink, collection and clipboard-only history/status re-copy all acquire application request tokens. See `docs/development/context-collection-output-contract.md` for #74 integration.

## Conventions

- **Code Style**: Kotlin idiomatic, expression bodies, `?.`/`?:` null handling, avoid `!!`
- **Naming**: Actions=`Copy*Action.kt`, UI=`CopySelection*Widget/Notifier.kt`, Settings=`CopySelection*Settings/Configurable.kt`
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
