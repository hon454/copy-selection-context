# feat(context): add a session-scoped multi-file context collection

Source: https://github.com/hon454/copy-selection-context/issues/73
Issue updated: 2026-09-04T07:46:11Z
Retrieved: 2026-09-04

## Context and outcome

Users need to collect related selections from different files before handing
them to an AI assistant. Add a project-owned, in-memory collection of immutable
capture-time snapshots. Adding an item must not copy it or affect one-shot copy
behavior. This issue owns the model, capture action and service contracts used by
#75 (output/copy) and #74 (management tool window).

## Confirmed behavior

- Capture the selected text from each caret, or that caret's complete current
  line when there is no selection. Include unsaved document edits. Store raw code
  even when the collection's output toggle excludes code.
- Store capture-time path, filename, language, code and 1-based inclusive line
  range. A selection ending at the next line's start excludes that next line.
- Editor changes, file rename/move or deletion never rewrite the snapshot.
  Source-state changes are displayed separately and do not prevent copying.
- An exact unchanged recapture adds no duplicate. A changed recapture of the
  same source/range adds a separate item and preserves the previous snapshot.
- Collection state survives copy success, cancellation, failure and management
  UI closure. It is discarded at project disposal or plugin unload. It is never
  serialized or exported automatically. Captured code, paths, filenames and item
  contents are never logged or included in analytics. Numeric copy accounting,
  if enabled by the agreed #75 policy, is a separate concern.
- The collection's session-only include-code option starts `true` for each new
  project session and is independent of the existing single-copy preference.
- An explicit clear-all command requires confirmation in its UI owner. The model
  exposes a clear mutation without showing dialogs. There is no copy-and-clear
  or refresh-from-source feature in this release.

## Shared contracts

Keep production classes in the existing flat package. Suggested responsibilities:

- `ContextCollectionItem`: immutable item ID, source token, captured source
  location (independent of display mode), capture-time absolute and optional
  project-relative paths, chosen display path/name, line range, raw code,
  language, fixed capture sequence/time and cached UTF-8 code byte count.
- `ContextCollectionSnapshot`: immutable ordered items, raw code byte total,
  include-code option and monotonically increasing content revision.
- `ContextCollectionService`: project service with batch add, remove by stable
  ID, move up/down, clear, set-include-code, immutable snapshot and change
  subscription. Snapshot callers cannot mutate service-owned lists.
- `AddToContextCollectionAction`: capture adapter and localized add-result
  feedback, without invoking `CopyResultPublisher`.
- `ContextCollectionSourceTracker`: session-owned source identity and immutable
  status map with a separate status revision and disposable subscriptions. Do not
  retain editors or documents for the session. Source-only status changes notify
  #74 without advancing content revision or invalidating #75's prepared payload.

Use a service-owned disposable lifetime for listeners and pending work. Run
mutations on EDT and publish safely readable immutable snapshots for background
formatting/action updates. Notify listeners after a
completed mutation, never while exposing partially updated collections.

Deterministic rules:

1. Process one multi-caret capture batch by ascending selection/current-line
   start, then end. Append accepted new items to the current collection order.
   Assign an immutable monotonically increasing project-session capture number
   and capture timestamp to each accepted unique item. Reorder, duplicate add
   and source-state changes never change them. Do not reuse capture numbers
   after remove/clear within the session. Rejected batches consume no numbers.
2. Deduplicate by session source identity plus captured source location,
   filename, language, line range and exact raw code. The source location uses
   the source's capture-time VFS identity/location representation, not the
   relative/absolute display-path string. Changing only the path-type preference
   does not create a new item. Do not deduplicate by hash alone, trim content
   before comparison, move duplicate rows or implicitly merge overlapping ranges.
3. Source identity must survive a rename of the same live source and distinguish
   a deleted source from a different file later created at the same path.
4. Capture both the source location and its absolute/project-relative path
   representations at add time. A project-relative representation is absent when
   no applicable project base exists or the source lies outside it, retaining the
   existing absolute fallback. Freeze the chosen display path using the path-type
   preference at that time. Later preference changes apply only to genuinely new
   items; an unchanged recapture neither duplicates the item nor changes its
   original display path. Never re-resolve output paths during copy. Actual
   rename/move changes the captured source location: explicit recapture may add
   a new item while the old item's original location/output remains unchanged.
5. Source status means observed changes since capture, not proof of exact textual
   divergence. A document modification may conservatively mark all its captured
   items as changed. Rename/move and unavailable-source states are independent
   of captured output. Do not load file contents or open editors to copy snapshots.
6. Missing project, disposed editor/project, unavailable source file or invalid
   capture context produces no mutation. Support ordinary text editors with a
   project and `VirtualFile`, including unsaved buffers. No project-tree bulk add,
   directory scanning, binary capture or synthetic diff aggregation in v1.5.0.

## Capacity and transaction boundary

Fixed limits: 100 items, 256 KiB raw UTF-8 code per item and 2 MiB total raw
code. Exact equality is allowed. Deduplicate before evaluating resulting totals.

One user add invocation is atomic: either all new unique captures fit and are
appended, or none are added. Existing items and their order remain unchanged on
rejection. Report added/skipped-duplicate counts or the violated limit using
localized messages. No silent truncation, eviction or partial batch insertion.

Reject an obviously over-limit selection before materializing its full selected
text, and bound additional capture/encoding work while evaluating the batch.
UTF-8 byte limits describe retained code, not total JVM heap usage. Use overflow-
safe totals. Rejection must not change clipboard, history, status, review or
analytics.

## Action and integration boundaries

Register a localized `CopySelectionContext.AddToCollection` action in the existing
editor submenu and Find Action. Initial keymap behavior: no new default
shortcut, but the action is assignable in IDE Keymap. Do not steal focus from the
editor or auto-open a management surface on each addition. Provide bounded,
localized feedback even when every candidate was a duplicate.

The service must expose mutation results and collection revisions suitable for
#75 to freeze a payload and for #74 to refresh counts/list selection. Source
status tracking must not rewrite `ContextCollectionItem` output fields.

## Acceptance criteria

- [ ] Selections from two files and two editors enter one project collection.
- [ ] Multi-caret and current-line captures preserve exact text and correct
  inclusive line ranges, including an exclusive end at the following line start.
- [ ] Capture `timeout = 10`, edit to `timeout = 30`, then copy without recapture:
  the stored item still contains `timeout = 10`.
- [ ] Explicit recapture after that edit adds a second independent item.
- [ ] Capture number/time remain fixed through reorder and duplicate recapture,
  distinguish items even when capture times coincide, and are absent from
  persistent storage. Remove/clear does not recycle numbers in that session.
- [ ] Exact unchanged recapture does not increase count/bytes or reorder items.
- [ ] Capture a relative-path item, switch to absolute paths and recapture the
  same source/range/code: count, bytes, content revision, order and original
  display path are unchanged. The inverse sequence behaves identically.
- [ ] If the code changes before recapture under the new path preference, add a
  second item with its new chosen display path but the same captured source
  location/range, allowing #75 to identify the two as a conflict group.
- [ ] Renaming/moving the actual source and explicitly recapturing preserves a
  separate old-location snapshot rather than treating the rename as a mere
  display-mode change.
- [ ] Rename/delete after capture preserves original path, code and range and
  reports source state separately. Re-created paths do not impersonate old items.
- [ ] Source-only changes notify subscribers without changing content revision
  or invalidating a prepared collection payload.
- [ ] Remove/reorder/clear update immutable snapshots and notify listeners once
  per effective mutation. Invalid or no-op mutations preserve revision/state.
- [ ] One over-capacity multi-caret invocation leaves the entire prior collection
  unchanged and explains the rejection.
- [ ] UI closure and all copy outcomes retain items. Project close/reopen yields
  an empty collection with include-code enabled.
- [ ] Two projects are isolated. Disposing one releases its listeners and state.
- [ ] No collection source text or session toggle is persisted in project or
  application settings. Existing one-shot copy behavior remains unchanged.

## Verification and documentation

Pure tests: ordering, exact and changed recaptures, overlapping ranges, immutable
snapshots, mutations, notification cardinality, revision behavior, UTF-8 limits
at/below/above boundaries, duplicate-at-capacity and atomic batch rejection.

IDE fixtures: unsaved text, multiple carets, current-line fallback, rename/delete,
source re-creation, absent context, project isolation and disposal. Register new
platform-state test classes in `platformStateTestClasses` in `build.gradle.kts`.
Verify collection state is absent from serialized plugin settings/workspace state.

Update five resource bundles, the five READMEs together, CHANGELOG Unreleased,
AGENTS.md and relevant architecture/pattern docs. Keep new action inheritance and
source-file listings reflected in documentation tests. Run applicable pure and
platform tests plus the existing aggregate/static-analysis/verification gates
before merge. Include manual add/duplicate/project-close steps in the PR.
Supply the deterministic multi-file sample selections used by #74's actual IDE
screenshots; final image capture and store/README screenshot integration belong
to #74 after all collection behavior is implemented.

## Dependencies and exclusions

Implement first. #75 consumes its snapshot/options and #74 consumes its mutations
and subscriptions. This issue does not implement output formatting, copy-all,
the management tool window, persistent history, cross-session restore, AI service
integration, refresh, diff comparison or automatic range merging.

