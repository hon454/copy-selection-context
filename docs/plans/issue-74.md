# feat(context): add context collection management UI

Source: https://github.com/hon454/copy-selection-context/issues/74
Issue updated: 2026-09-04T07:46:13Z
Retrieved: 2026-09-04

## Context and outcome

Give users a project tool window beside the editor where they can inspect,
organize and copy captured selections without recapturing them. Keep the window
open while moving between files. Display capture-time content and current source
status separately so edits do not silently alter what will be sent to an AI.

## Dependencies and delivery order

Depends on #73's collection model/mutations/subscriptions and #75's pure output,
size and copy-command contracts. Implement after both. Do not duplicate capture,
formatting, byte-counting, limit checks, publication or confirmation logic in UI
event handlers.

## Confirmed scope

- IDE tool window, not a popup. It supports keyboard and mouse use.
- Per-item path, inclusive line range and code preview, with individual removal,
  move up/down, confirmed clear-all and copy-all.
- Closing the management UI retains items. Project close disposes them.
- Opening the UI does not mutate editor selection or clipboard.
- Include-code checkbox is collection-specific and session-only, initially on.
  Switching it changes output, never raw snapshots or single-copy preferences.
- Capture-time content stays available after source edit, rename or deletion.
- Copy retains items and never puts collection output into persistent history.

## UI contract

Register a lazy tool window named `Context Collection`, initially anchored on the
right, with one non-closeable content tab. The IDE may let the user relocate or
hide the window. Register a localized `CopySelectionContext.ShowCollection`
action in Find Action and the existing submenu, enabled for a live project even
with no editor and no items. Do not auto-focus or auto-open it on every add.
Do not assign new default global shortcuts in v1.5.0; actions remain assignable
through IDE Keymap.

Layout:

1. Toolbar: Copy All, Remove, Move Up, Move Down and Clear All, with visible
   accessible names/tooltips and an include-code checkbox.
2. Summary: item count, retained raw-code UTF-8 size and final-output UTF-8 size.
   Label the two byte counts distinctly. Show the selected output format and a
   route to existing formatting settings rather than adding a second template
   editor. Display capacity/warning states from #73/#75.
3. Single-selection ordered list: fixed capture number/time, captured path and
   range, bounded one-line code preview and textual source status. Use path, not
   filename alone, as identity in the presentation. Two versions of one range
   remain separate rows with their original capture numbers after reordering.
   Display time in the UI locale and expose the full date/time/timezone in
   accessible details so equal short time labels do not imply equal captures.
4. Focusable read-only details: full captured code for the selected item, with
   captured metadata. Provide a separate final-output preview using the same
   ready payload as #75 so a custom template can be inspected before copying.

The list preview is bounded, Unicode-safe and markup-escaped through the existing
`CopyPreview` approach. A read-only plain-text viewer shows actual text, not
escaped HTML or an interpreted Markdown/browser view. Preview truncation never
truncates stored code or copied output. Rendering/full-output computation must be
bounded by the agreed item/payload limits and run away from the UI thread where
necessary. Ignore out-of-date computed results using revision/input checks.

Source statuses are separate from the captured path/code and may include changed
since capture, renamed/moved and unavailable. They are informative, not blockers.
Do not imply that an unchanged indicator proves source content is identical. No
automatic source navigation or refresh is necessary for this release; the full
captured-content viewer is the reliable inspection surface.

## Interaction details

- Arrow keys select rows. Tab/Shift-Tab reaches toolbar, checkbox, list and
  details/output viewers. Delete removes the selected item when the list owns
  focus. Copying selected text from a read-only viewer follows normal text-copy
  behavior and must not accidentally invoke Copy All.
- Move Up/Down moves the selected item one position through #73, preserving its
  identity and focus. Disable Up for the first item and Down for the last.
- After removal, select the item now at the removed index, otherwise the previous
  last item. When empty, move focus to the empty-state guidance or toolbar.
- Clear All is disabled when empty. Otherwise confirm the count to be removed
  with Cancel as the safe default. Cancel leaves state unchanged. If the list
  changes while confirmation is open, do not clear newly added items silently:
  invalidate and require a fresh confirmation.
- Copy All invokes #75 with no active-editor requirement. Empty/invalid/overflow
  output cannot produce success. Warning-size outputs remain copyable through
  the shared confirmation command. A pending calculation shows Calculating and
  disables Copy All until a matching current result is available.
- New additions update the list and counts without taking focus from the editor
  or moving an existing selection unexpectedly. With no previous selection,
  select the first available row when the user enters the tool window.
- Collection include-code and relevant shared-format changes trigger a new output
  calculation. Do not temporarily label stale output bytes as current.

## Empty, source-change and oversized states

Empty state explains selecting editor text and using Add to Collection; no fake
success, automatic clipboard change or hidden empty popup. Hide/disable item
operations and clear any previously displayed captured content.

At capacity, show the violated capacity information returned by #73. Do not
evict previous items. For final output above the warning/hard threshold, show
#75's classification and guidance. If exact oversized output bytes are not
materialized, display an honest `exceeds maximum` state, not a fabricated count.

Accessibility must not rely on color/icon alone for statuses. Give list rows,
buttons, checkbox and both read-only viewers meaningful localized accessible
names. Keep user-supplied code/path plain or safely escaped as appropriate.

## Lifecycle and implementation guidance

Use a declarative `com.intellij.toolWindow` registration and a lazy factory, with
content-owned disposables for UI listeners. The collection service outlives the
visible panel, while disposed panels must not retain snapshots/listeners. Prefer
the public 2024.3-compatible tool-window APIs and declare index-independent
behavior where no indexes are used. Follow [JetBrains tool-window guidance](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
and [disposal guidance](https://plugins.jetbrains.com/docs/intellij/disposers.html).

Suggested classes: `ContextCollectionToolWindowFactory`,
`ContextCollectionPanel`, `ShowContextCollectionAction`, plus testable list
presentation/selection helpers where needed. Keep the package flat. UI actions
delegate to #73/#75; they must not have their own clipboard, history or analytics
implementation.

## Acceptance criteria

- [ ] A live project's empty collection can be opened without an active editor.
- [ ] The user can keep the window visible while collecting from multiple files.
- [ ] Every captured item has inspectable original path/range and full code.
- [ ] Capture numbers/time distinguish versions in the list and details; the final
  output viewer shows #75's conditional annotations and warning classifications.
- [ ] Changed/renamed/deleted source states do not overwrite capture-time output
  or block copying an otherwise valid collection.
- [ ] Remove/reorder preserve item content and predictable keyboard selection.
- [ ] Clear All requires confirmation and cancellation preserves every item.
- [ ] Opening/hiding the window does not copy, recapture or clear anything.
- [ ] Raw-code bytes and final-output bytes are distinct and update with relevant
  state/settings changes using the shared contracts, including Unicode content.
- [ ] Stale calculations never replace current list/byte/preview state.
- [ ] Include-code is initially on, affects only collection output and survives
  tool-window hide/reopen but resets for a new project session.
- [ ] Empty, capacity, blank-template-output and final-overflow states are clear
  and cannot trigger a misleading successful copy.
- [ ] All operations, names and source states are keyboard-accessible and available
  in English, Korean, Japanese, Simplified Chinese and Traditional Chinese.
- [ ] Disposed content releases listeners and preview/editor resources while the
  service retains items until its owning project/plugin ends.

## Verification and documentation

### Real UI screenshots and listing deliverables

Real UI screenshots plus README and store-description updates are part of this
issue's completion scope. Capture the implemented plugin running
in an actual supported IDE after #73/#75/#74 behavior is available. Use a small
reproducible demo project with non-sensitive sample code and project-relative
paths. Record plugin commit/version, IDE build, theme, locale, viewport and steps
in a capture guide. Do not substitute a design mockup for a product screenshot.

Prepare three examples under the existing `docs/images/` directory:

- `context-collection-overview.png`: editor beside a populated collection with
  selections from multiple files, captured path/ranges, code-inclusion control
  and distinguishable raw/output byte summaries.
- `context-collection-preview.png`: a selected item's captured code and final
  formatted output, including two versions of the same location with their
  snapshot annotations, demonstrating what will be pasted into the AI assistant.
- `context-collection-size-warning.png`: the actual above-warning confirmation,
  with an honest matching item count and UTF-8 output size.

Confirmed asset locale: one consistent English-UI screenshot
set reused by all five READMEs with localized captions and alt text. Reuse the
same assets in the Marketplace media gallery. Prefer a consistent readable 16:10
viewport around the currently recommended 1280x800 size, preserving readable UI
text rather than shrinking an entire desktop capture. Record any required export
variation. Keep the existing settings screenshot unless its UI changes.

Add a synchronized collection workflow/screenshots section to all five READMEs.
Explain add, review/reorder, code toggle, copy/size warning, retained session-only
collection and exclusion from persistent copy history. Make the existing one-shot
shortcut distinct from assignable collection actions. Include correct links and
localized descriptions. Verify image paths, Markdown rendering and mobile-width
readability, as well as existing five-README structure checks.

Update the store-facing description in `src/main/resources/META-INF/plugin.xml`
to describe the collection workflow and its documented limitations. Prepare
Marketplace gallery ordering/captions and a versioned release checklist under
`docs/releases/` so uploading the ZIP is not mistaken for updating listing media.
At v1.5.0 release, verify the live Marketplace description and upload/verify the
actual screenshot gallery. Report listing publication separately from repository
asset readiness if access or release timing prevents it. Do not mark screenshot
publication complete merely because images were committed to the repository.

The existing release workflow publishes the canonical plugin archive but has no
screenshot upload step. See [Marketplace listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html)
and [screenshot approval guidance](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).
Verify whether Marketplace takes its description from the archive or a page-level
override, and update the effective source so the live description matches
`plugin.xml`. The listing supports an independently edited description, so a new
archive alone is not proof that the public text changed. Repository/PR completion
requires actual screenshots, README/descriptor changes and the prepared release
checklist; the v1.5.0 release checklist separately tracks live media/description
publication and verification to completion.

Additional acceptance criteria:

- [ ] Three real IDE screenshots are committed with reproducible capture steps
  and visually checked for readable, accurate implemented behavior.
- [ ] All five READMEs use the agreed screenshots with localized captions/alt
  text and synchronized collection instructions; image links render correctly.
- [ ] `plugin.xml` description documents the new workflow and privacy/size behavior.
- [ ] The release checklist names the Marketplace description/media update steps
  and records their actual publication status and verification evidence.

Unit tests cover row presentations, Unicode previews, selection after mutations,
empty states, count/byte display states and revision filtering. Platform fixtures
cover opening without an editor, listener cleanup and shared command integration.
Keep new platform-state classes in the isolated Gradle test partition.

Manual checks in a supported IDE: collect from two files, move and remove by
keyboard, cancel/confirm clear, compare original code with a changed/deleted
source, toggle code inclusion, inspect a custom template's final output, trigger
size warning/overflow and hide/reopen the window. Verify focus and accessible
names as well as a narrow tool-window layout.

Update all five bundles and READMEs together, CHANGELOG Unreleased, AGENTS.md,
architecture/pattern docs, plugin descriptor localization and relevant doc tests.
Run the applicable unit/platform aggregate, static analysis and Plugin Verifier
gates. Include manual testing steps in the PR.

## Out of scope

Persistent collections, multi-collection tabs, drag-and-drop, multi-row batch
editing, diff viewers, code editing, refresh/rebase of captured ranges, automatic
source navigation, token estimation, AI/network integration and a second custom
template editor.

