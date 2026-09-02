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
| JVM Toolchain | 21 |
| Min IDE Version | 2024.3 |

## Build Commands

**Windows (this project's dev environment):**
```bash
cmd //c "gradlew.bat buildPlugin"    # Build plugin ZIP (build/distributions/)
cmd //c "gradlew.bat runIde"         # Run IDE with plugin installed
cmd //c "gradlew.bat verifyPlugin"   # Verify plugin structure
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
4. `release.yml` generates GitHub Release notes from the matching `CHANGELOG.md` section with `getChangelog`
5. JetBrains Marketplace publishing runs only when `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty

**Version rule**: Tag version must match `build.gradle.kts` `version` — workflow fails on mismatch.

## Architecture Overview

Single flat package: `com.github.hon454.copyselectioncontext/`

| File | Role |
|------|------|
| `CopySelectionContextAction.kt` | Main unified action (`Ctrl+Alt+C` shortcut) |
| `CopySelectionBaseAction.kt` | Shared copy pipeline: formatting, clipboard, analytics, highlighting, history, notifications, and status updates |
| `OutputFormatter.kt` / `TemplateFormatter.kt` | Built-in Claude Code and Path:Line formats plus custom templates |
| `CopySelectionUtils.kt` | Path, line range, selected code, multi-caret, and language helpers |
| `CopySelectionHighlighter.kt` | Editor-scoped gutter highlighter lifecycle |
| `CopyRelativePathAction.kt` | Relative path (context menu only) |
| `CopyAbsolutePathAction.kt` | Absolute path (context menu only) |
| `CopyWithCodeContentAction.kt` | Path + markdown code block (context menu only) |
| `CopyGitPermalinkAction.kt` / `GitRepositoryMetadataResolver.kt` / `GitPermalinkGenerator.kt` | Async, worktree-safe GitHub/GitLab permalink generation |
| `CopyHistoryService.kt` / `CopyHistoryPopup.kt` | Local non-roaming project history, migration, re-copy, and clear-all |
| `CopyPreview.kt` | Bounded, single-line, Unicode-safe, markup-escaped previews |
| `CopySelectionAnalytics.kt` | Opt-in, local-only usage counters |
| `CopySelectionNotifier.kt` | Toast notifications (BALLOON) |
| `CopySelectionStatusBarWidget.kt` / `CopySelectionStatusBarWidgetFactory.kt` | Last-copy status display and click-to-copy behavior |
| `CopySelectionSettings.kt` | Settings persistence (`@Service` + `@State`) |
| `CopySelectionConfigurable.kt` | Settings UI with multiline template editor, preview, and validation |

**Flow**: User trigger -> Action reads settings -> Extract editor context -> Format output -> CopyPasteManager -> optional local analytics -> gutter marker -> project history -> optional notification -> status bar update

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
