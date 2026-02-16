# Copy Selection Context - Claude Instructions

> **Source of truth**: `AGENTS.md` — project overview, architecture, conventions, tech stack.
> **Deep reference**: `.agents/` directory — architecture details, code patterns, gotchas.
> Do NOT duplicate information already in AGENTS.md. Refer there first.

## Testing (Manual QA Only)

No automated tests. Run `./gradlew runIde` to launch IDE with plugin.

1. Select code → `Ctrl+Alt+C` → verify absolute path copied
2. Settings: enable "Include code content" → `Ctrl+Alt+C` → verify markdown code block
3. Settings: switch to Relative path → `Ctrl+Alt+C` → verify relative path
4. No selection → `Ctrl+Alt+C` → verify current line number
5. Right-click → context menu → verify all 4 actions visible
6. Verify toast notification on copy
7. Settings → Tools → Copy Selection Context → verify radio buttons + checkbox

## Must NOT Do

**Scope**: No AI service integration (clipboard only), no multi-selection accumulation, no complex Settings UI, no file tree integration.

**API**: No Internal/Deprecated APIs, no `untilBuild` setting, no Kotlin stdlib bundling, no PNG icons (SVG only).

**Behavior**: No error dialogs (toast only), no blocking operations, no file writes.
