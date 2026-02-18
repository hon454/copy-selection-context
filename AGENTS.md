# Copy Selection Context - Project Knowledge Base

> Hierarchical docs: detail files in `.agents/` directory. This root index is always loaded.
> Sub-files are loaded on-demand when deeper context is needed.

## Project Overview

JetBrains IDE plugin for copying code context (file path, line numbers, optionally code) to clipboard for AI assistant workflows.

- **Core Value**: One shortcut copies file path + line numbers + optional code, formatted for AI
- **Target Users**: Developers using AI coding assistants (Claude, ChatGPT, etc.)
- **Primary IDE**: Rider (compatible with all IntelliJ Platform IDEs 2024.3+)

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.0 |
| Gradle | 9.3.1 |
| IntelliJ Platform Plugin | 2.11.0 |
| JVM Toolchain | 21 |
| Min IDE Version | 2024.3 |

## Build Commands

```bash
./gradlew buildPlugin    # Build plugin ZIP (build/distributions/)
./gradlew runIde         # Run IDE with plugin installed
./gradlew verifyPlugin   # Verify plugin structure
./gradlew publishPlugin  # Publish to Marketplace (requires PUBLISH_TOKEN)
```

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | Push/PR to `main` | Build verification + artifact upload |
| `release.yml` | Push `v*` tag | Build → GitHub Release (ZIP attached) → Marketplace publish (conditional) |

### Release Process

1. Bump `version` in `build.gradle.kts` to match the tag
2. Commit, tag (`v<major>.<minor>.<patch>`), push
3. `release.yml` auto-creates GitHub Release with commit-based release notes
4. JetBrains Marketplace publish activates when signing secrets are configured

**Version rule**: Tag version must match `build.gradle.kts` `version` — workflow fails on mismatch.

## Architecture Overview

Single flat package: `com.github.hon454.copyselectioncontext/`

| File | Role |
|------|------|
| `CopySelectionContextAction.kt` | Main unified action (`Ctrl+Alt+C` shortcut) |
| `CopySelectionBaseAction.kt` | Abstract base with clipboard logic |
| `CopyRelativePathAction.kt` | Relative path (context menu only) |
| `CopyAbsolutePathAction.kt` | Absolute path (context menu only) |
| `CopyWithCodeContentAction.kt` | Path + markdown code block (context menu only) |
| `CopySelectionNotifier.kt` | Toast notifications (BALLOON) |
| `CopySelectionStatusBarWidget.kt` | Status bar widget (stub) |
| `CopySelectionSettings.kt` | Settings persistence (`@Service` + `@State`) |
| `CopySelectionConfigurable.kt` | Settings UI (Tools menu) |

**Flow**: User trigger -> Action reads settings -> Extract editor context -> CopyPasteManager -> Notification

## Conventions

- **Code Style**: Kotlin idiomatic, expression bodies, `?.`/`?:` null handling, avoid `!!`
- **Naming**: Actions=`Copy*Action.kt`, UI=`CopySelection*Widget/Notifier.kt`, Settings=`CopySelection*Settings/Configurable.kt`
- **Package**: Flat `com.github.hon454.copyselectioncontext` (no subdirectories)
- **Plugin ID**: `com.github.hon454.copy-selection-context` (kebab-case)
- **Notification Group ID**: `"CopySelectionContext"` (PascalCase, no spaces)
- **PRs**: One feature per PR, include manual testing steps, update AGENTS.md if architecture changes
- **README**: Bilingual — `README.md` (English, primary) and `README.ko.md` (Korean). Any README change MUST be applied to both files. Keep section structure, order, and content in sync between the two.

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
