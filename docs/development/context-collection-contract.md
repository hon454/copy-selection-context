# Context collection contract (#73)

## Capture and output ownership

`ContextCollectionService.getInstance(project)` is a project light service with no persistent state. `capture(editor, file, pathType)` is the batch-add API. It accepts a live ordinary text editor belonging to that project and its associated VirtualFile. Each caret contributes its exact selection or complete current line, sorted by start offset and then end offset. Exclusive selection ends use `end - 1` for the inclusive line range. No live selection, document or editor object is stored by the service; only accepted bounded raw strings and captured metadata survive the call. No clipboard, publisher, history, status, gutter, analytics or review operation is performed by collection mutations.

An item contains stable session `id`/`captureNumber`, millisecond-capable `Instant capturedAt`, raw `code`/`codeBytes`, filename, language, inclusive start/end line, capture-time `absolutePath`, nullable `relativePath`, chosen `displayPath`, and `sourceLocation(sourceToken, url)`. IDs and capture numbers coincide but consumers should use `id` for mutations and `captureNumber` for labels. They increase only for accepted unique entries, remain unchanged by moves and duplicate adds, and are never reused after remove/clear. Equal timestamps do not imply equal captures. Relative paths are absent outside the applicable base; display path falls back to absolute.

Exact duplicate comparison uses source token, captured URL, filename, language, inclusive range and every raw UTF-16 code unit. Display paths are deliberately excluded. Relative/absolute preference changes alone therefore preserve the existing item, revision, order and display path. Changed code adds a new item with the current display preference. Actual rename changes the captured URL and permits a separate new-location capture. Overlaps are never merged.

#75 groups conflicts by captured source location and line range, uses the frozen `displayPath`, and formats `capturedAt` in fixed UTC millisecond precision. It must never resolve a current source path to format an item. Output, copy commands, computed-output state and application clipboard ordering remain #75's responsibility. #74 owns the tool window, confirmation dialogs, accessible presentation and real screenshots.

## Revisions and concurrency

`snapshot()` is safe for background readers: it returns a volatile, immutable `ContextCollectionSnapshot` with unmodifiable ordered items, `rawCodeBytes: Long`, session `includeCode` (initially true), and `revision: Long`. Add/remove/move/clear/include-code changes advance revision once per effective transaction. Duplicate-only, rejected, invalid and no-op calls do not advance it. New snapshots never mutate earlier snapshots.

All capture, mutation and subscription registration runs on EDT. `subscribe(parentDisposable) { snapshot -> ... }` emits no initial event; subscribe and read `snapshot()` in the same EDT turn. Callbacks receive only completed state, once per effective mutation. The subscription unregisters when either its parent or the service is disposed. Callback failures are isolated and log only an exception class, with no source fields. Callbacks may inspect state; schedule mutations for a later EDT turn instead of mutating reentrantly while a collection notification is being delivered.

`remove(id)`, `moveUp(id)`, `moveDown(id)`, `setIncludeCode(value)` and `clear(expectedRevision)` return whether state changed. #74 must confirm clear with its captured revision, then pass that revision; a changed collection invalidates clear instead of deleting newly added items. The model shows no dialogs. Closing a UI/content disposable only removes subscriptions. Project close/plugin unload disposes listeners and retained state. Copy outcomes have no collection mutation API and must retain items.

#75 freezes content revision and its own immutable output options. Final validation and publication must be on EDT, serialized with these mutations. Only content revision affects prepared output validity; source status has its own notification path.

## Source observation

`sourceTracker.snapshot()` returns an immutable `ContextCollectionSourceSnapshot(statuses, revision)`, keyed by item ID, with independent booleans `changed`, `relocated` and `unavailable`. Its `subscribe(parentDisposable)` contract matches content subscriptions. Source events never change content revision or item fields. A document event conservatively marks all captures of that live source observed before the event; delayed EDT delivery cannot mark newly accepted captures. Rename/move checks current VFS location, including ancestor directory moves. Delete makes a captured source unavailable. Flags latch observed changes; they are not an exact textual comparison, and undo does not prove equivalence.

A weak source registry assigns nonreused session tokens. The tracker strongly retains VirtualFiles referenced by currently retained captures so identity survives editor closure and cache collection. It does not retain editors/documents. Remove/clear releases those strong references; dispose clears both registries. A deleted VirtualFile and a subsequently recreated file at the same path receive different tokens. Source-only signals refresh #74's labels without triggering #75 output recalculation.

## Capacity and feedback

Limits are fixed: 100 items, 256 KiB UTF-8 raw code per item, 2 MiB total raw code. Equality is accepted; totals use Long. Candidate length is checked before reading selected text. UTF-8 is scanned with a bounded counter, including supplementary pairs and JVM's one-byte replacement for malformed UTF-16. Exact duplicates are skipped before resulting count/byte totals are evaluated. Only bounded unique candidates are materialized. Lazy batch iteration stops at the first rejection; no rejected batch consumes capture numbers or partially adds entries. No truncation or eviction occurs.

`ContextCollectionAddResult` is `Added(added, duplicates)`, `Rejected(limit)` or `InvalidContext`. The action localizes counts and rejection guidance without displaying paths/code. Success/duplicate feedback follows the existing notification preference; capacity rejection always explains the violated limit. `CopySelectionContext.AddToCollection` is available in the editor submenu and Find Action with no default shortcut, and does not open UI or move focus.

## Verification and handoff

`ContextCollectionStoreTest` covers pure transaction, identity, immutable snapshots, capacity and bounded-allocation behavior. `ContextCollectionFixtureTest` runs in the isolated `platformTest` partition and covers unsaved buffers, multiple editors/carets, exclusive ends, path-mode duplicates, rename/delete/recreation, disposal, separate source events, action side-effect exclusion and serialized plugin-state absence. `DocumentationSyncTest` and descriptor/bundle tests retain all existing checks and add collection markers and the Kover source-of-truth check.

Use the non-sensitive [sample guide](../samples/context-collection/README.md) for #74. Actual UI screenshot capture is deferred to #74; local plugin ZIPs are validation artifacts, not canonical releases.
