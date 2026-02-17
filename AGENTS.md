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
- **Commits**: `type: description` (feat, fix, docs, refactor, test, chore)
- **PRs**: One feature per PR, include manual testing steps, update AGENTS.md if architecture changes

## Detail Files

Read these only when you need deeper context for a specific task:

| File | When to Read |
|------|-------------|
| [`.agents/architecture.md`](.agents/architecture.md) | Modifying components, adding features, understanding data flow or file internals |
| [`.agents/patterns.md`](.agents/patterns.md) | Writing new code, need API patterns for clipboard/notifications/settings/actions |
| [`.agents/gotchas.md`](.agents/gotchas.md) | Build issues, config problems, debugging platform API quirks |
