# Copy Selection Context - Claude Instructions

> **Source of truth**: `AGENTS.md` — project overview, architecture, conventions, tech stack.
> **Deep reference**: `.agents/` directory — architecture details, code patterns, gotchas.
> Do NOT duplicate information already in AGENTS.md. Refer there first.

## Testing

Automated tests exist: `./gradlew test` (unit) and `./gradlew platformTest` (IntelliJ fixture tests). Keep them pragmatic — add or change a test only where it guards a real regression risk (plugin.xml descriptor, output/contract rules, documentation sync). Verify UI and platform behavior with manual QA instead of new test suites.

Manual QA: run `./gradlew runIde` to launch the IDE with the plugin.

1. Select code → `Ctrl+Alt+Shift+X` (`Cmd+Alt+Shift+X` on macOS) → verify absolute path copied
2. Settings: enable "Include code content" → shortcut → verify markdown code block
3. Settings: switch to Relative path → shortcut → verify relative path
4. No selection → shortcut → verify current line number
5. Right-click → "Copy Selection Context" copies directly; "Copy Selection Context Tools ▸" lists the other eight actions
6. Verify toast notification on copy
7. Settings → Tools → Copy Selection Context → verify radio buttons + checkbox; "Configure shortcuts in Keymap…" opens Keymap filtered to the plugin actions
8. Settings → Keymap → no conflict warning on `Ctrl/Cmd+Alt+Shift+X`

## Must NOT Do

**Scope**: No AI service integration (clipboard only), no multi-selection accumulation, no complex Settings UI, no file tree integration.

**API**: No Internal/Deprecated APIs, no `untilBuild` setting, no Kotlin stdlib bundling, no PNG icons (SVG only).

**Behavior**: No error dialogs (toast only), no blocking operations, no file writes.
