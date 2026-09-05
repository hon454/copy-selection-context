# feat(context): copy accumulated multi-file context with guardrails

Source: https://github.com/hon454/copy-selection-context/issues/75
Issue updated: 2026-09-04T07:46:12Z
Retrieved: 2026-09-04

## Context and outcome

Copy the project collection as one deterministic payload, using captured data
even when source editors are closed or files have changed. Format and validate
the complete result before publication. Share one output/size contract with #74
so the tool window describes exactly what its copy command will publish.

## Dependencies and delivery order

Depends on #73's immutable collection snapshot, revision and session options.
Implement this issue before #74. Register copy-all as a project action available
through Find Action, independent of an active editor or management UI. #74 later
invokes the same command and subscribes to the same formatting results.

## Confirmed product behavior

- Successful copy retains the collection. Cancellation, rejection and failure
  also retain it. There is no copy-and-clear command.
- Do not add collection results to persistent history, regardless of existing
  history settings or result size. Never clear or modify previous history as a
  side effect of a collection copy. No separate history preference is added.
- Collection include-code is session-only, defaults to true on project open and
  can be changed by #74 without changing the one-shot include-code preference.
- Always use captured code/path/filename/ranges, never reread source contents or
  refresh locations while formatting. Source changes do not block copying.
- Custom templates retain existing substitution semantics. Code appears only
  where the existing `{code}` variable occurs; do not append code outside it.

## Output contract

Add a pure `ContextCollectionFormatter` with immutable inputs consisting of a
collection snapshot and output options. Options snapshot the current shared
format key, custom template and code-trimming preference, plus the collection's
include-code flag. Captured display paths are fixed at add time.
Display-mode changes do not alter existing items or their duplicate identity.
Use each item's originally chosen captured display path, even though #73 retains
both capture-time absolute and project-relative representations internally.

Format each item in collection order using the existing built-in/custom semantics
and join item blocks with exactly `\n\n`. The collection-only snapshot annotations
below are the sole added built-in wrapper. Do not add a collection-wide header,
new template variable, XML wrapper or new template language. Changing shared
format/template/trimming options changes the next preview/copy, not raw snapshots.

### Distinguishing captures of the same location

Group items by capture-time source location and inclusive range, independent of
the chosen relative/absolute display representation. If a group contains more
than one item, annotate every member in built-in `claude` and `pathline` output
with a line of this exact shape before its otherwise unchanged formatted block:
`[Snapshot #<captureNumber> · <capturedAtUtc>]`. Render the immutable capture time
in ISO-8601 UTC with fixed millisecond precision, for example
`[Snapshot #2 · 2026-09-04T07:30:00.000Z]`. The annotation is independent of UI
locale, current clock and collection order. Include it even with code disabled.
Non-conflicting items and all one-shot action output remain unchanged. Removing
members until a group has one item removes its annotation on the next rendering.

Do not annotate or otherwise rewrite custom-template output. When conflict groups
are copied with a custom template, require explicit confirmation that no automatic
capture labels are included. Do not invent new template variables or silently
deduplicate rendered blocks. UI capture numbers/time remain available for review.

For any conflict group whose output omits a member's code, require confirmation
that location references do not carry or reconstruct the historical code versions.
This includes code disabled, a custom template without `{code}`, and effective
code suppressed as blank by existing formatting rules. The collection retains
all raw snapshots. A reference always describes the captured location, not a
guarantee that the current file contains that historical version.

Combine snapshot-label, missing-historical-code and size-warning reasons into
one confirmation for the same immutable prepared payload. Copy Anyway preserves
the full output; Cancel has no publication effects. Hard-limit/empty-output
failures remain non-copyable and do not become overridable warnings.

Built-in formats repeat the full path and inclusive range per item. Code-enabled
output uses the existing language-tagged dynamic backtick fence rules, including
the existing behavior that whitespace-only code produces a reference without a
fenced body. Code-disabled custom substitution receives empty `{code}`. Blank
templates keep the existing factory fallback to path/range. Unknown variables
and single-pass substitution remain backward compatible.

Example with code disabled, `pathline`:

```text
src/A.kt:10-20

src/B.kt:30-40
```

Example with code enabled, `pathline` (short example selections):

````text
src/A.kt:10
```kotlin
val timeout = 10
```

src/B.kt:30
```kotlin
val retries = 3
```
````

For `claude`, retain the existing leading/trailing spaces around each reference,
for example ` @src/A.kt#L10 `, followed by the existing optional fenced block.
For custom `{filename}: {code}`, the item output is exactly that substitution,
even though the user-chosen template omits the full path/range. Do not claim
custom templates guarantee unambiguous file boundaries. The tool window must
show the selected format and give access to the final output before copying.

Treat an empty collection or any item producing only whitespace as non-copyable.
Report the item/format problem without silently omitting that item. This prevents
a `{code}`-only template with code disabled from producing a misleading success.

### Shared computed output state

#75 also owns a project-scoped `ContextCollectionOutputService`. It observes #73's
content revision and the relevant shared format/template/trimming settings and
exposes an immutable input key plus `Calculating` or the typed formatting result.
#74 subscribes through a content-owned disposable and does not run its own formatter.
Calculate on immutable inputs in background, cancel/discard superseded work and
publish only results matching the current input key. Keep at most the current
prepared output, not an unbounded cache of source-containing payloads.

Add a settings-change signal after `CopySelectionConfigurable.apply()` has
successfully committed the shared settings. Settings load/replacement must also
invalidate existing output state where applicable. UI-only Reset does not change
committed output options until Apply. Advance an output-settings revision when
the format/template/trimming tuple changes. The session include-code flag is
already represented by #73's content revision. Path-type changes affect future
captures only and do not reformat existing captured paths. Check the actual
current options again at copy invocation and publication as a defensive guard.

The copy command freezes the same input key/result contract even when invoked
from Find Action with no tool window. Preview computation does not start a
publisher copy request or produce clipboard/accounting side effects. New output
input immediately invalidates displayed bytes and signals Calculating, before a
fresh result arrives. Source-status changes have a separate subscription and do
not trigger output invalidation.

## Capacity and warnings

Fixed limits: warning above 256 KiB final UTF-8 output, hard failure above
4 MiB. At or below 256 KiB there is no size warning, but snapshot/reference warning
reasons still require confirmation. Exactly 4 MiB is allowed after confirmation.
Count paths, language labels, fences, template literals/repetitions and `\n\n`
separators, not only captured code. Use KiB = 1024 bytes and MiB = 1024 KiB.

Represent formatting as a typed result: ready payload plus exact byte count,
warning-reason set, empty/blank-item error, or above-hard-limit result. Reuse this
contract in #74. Count snapshot annotation lines in the final output budget.
An above-hard-limit result need not materialize the full payload or compute its
unbounded exact size: show that it exceeds the documented maximum.

Preflight size or use bounded output construction so a custom template repeating
`{code}` cannot allocate an enormous result before the check. Byte calculation
must match actual UTF-8 encoding, including supplementary characters and malformed
UTF-16 replacement behavior. Use overflow-safe arithmetic.

Above the size-warning threshold, show byte size and item count and require
explicit Copy Anyway or Cancel in the same dialog as any snapshot warnings. Hard-limit failures offer guidance to remove items, change
format or disable code. Never publish a truncated payload or silently drop items.

## Publication and concurrency contract

Extend the existing project `CopyResultPublisher` with an explicit collection
policy. Make publication possible without an `Editor`; do not create/open a dummy
editor or assign all collected ranges to the currently focused editor.

### Application-wide plugin clipboard ordering

The current publisher's project-owned `latestRequestId` cannot suppress a late
copy from project A after a newer copy in project B. Introduce an application-
scoped `ClipboardRequestCoordinator` shared by every project publisher in the same
IDE process. It owns only monotonic request identity and atomic current-request
checks, not collection contents, prepared payloads, history, feedback or strong
project references. Keep project lifetime/input validation and side effects in
the originating project's publisher.

Route ordinary copy, async permalink and collection copy requests through this
coordinator. Route the existing direct clipboard writes in `CopyHistoryPopup`
and `CopySelectionStatusBarWidget` through its synchronous copy transaction as
well. A re-copy becomes the newest request and invalidates older async work but
retains its previous clipboard-only behavior: no new history, analytics, review,
notification or marker side effects are introduced for these paths.

Request-token validation and the clipboard write must be one atomic, serialized
operation shared across projects. Use a single lock/serialization order rather
than independent project locks with an unchecked gap before writing. Release all
long-lived work and warning dialogs outside the coordinator's critical section.
Never hold its lock while waiting for user input or background formatting. Keep
preview computation and collection editing outside the global copy sequence.

Newest request wins, not latest completion. A later request that is cancelled,
fails or belongs to a subsequently closed project still leaves earlier tokens
stale; do not reactivate an older operation. Disposing a project does not reset
the application sequence or affect another project's valid data. Tokens reset
only with the coordinator's own application/plugin lifetime. Stale completions
and stale failure callbacks produce no clipboard or project feedback changes.

The guarantee covers this plugin's managed copy paths in one IDE process. It
does not monitor native editor/text-viewer Copy, other plugins/apps or a second
IDE process, and does not claim to prevent those external clipboard writes.

Collection publication policy:

| Effect | Collection behavior |
| --- | --- |
| Clipboard | Exactly one complete payload on success |
| Persistent history | Disabled — confirmed Q1 |
| Notification | Existing notification preference, safe bounded preview |
| Status bar | Complete last-copy value plus existing bounded display |
| Gutter | No new markers, no automatic opening of source editors |
| Analytics | Existing opt-in, one total/actual-format/language record per successful Copy All |
| Review accounting | Once per successful Copy All, subject to existing eligibility/suppression gates |

Reduce captured nonblank language keys to one key for the entire copy: use the
sole distinct key when there is one, `mixed` when there are several, or blank
when every language is unknown (retaining the existing no-language-counter
behavior for blank keys). Do not increment counters per item or store source
paths, item IDs, code, byte sizes or preview content in analytics. Localize the
display label for `mixed` in the existing analytics view while keeping its stored
key locale-independent. Attribute the format to the prepared payload's actual
formatter, not mutable settings reread after preparation. Review counting remains
independent of analytics opt-in and uses its existing notification/environment/
version/suppression checks. No new collection marker means existing editor gutter
markers remain untouched.

Existing STANDARD and GIT_PERMALINK success-side-effect policies keep their
behavior. Their ordering guarantee is extended across projects by the same
application coordinator used for collection copies and managed re-copy actions.
An earlier delayed permalink or collection result must not overwrite a newer
managed copy in any project in this IDE process.

Consistency rules:

1. At copy invocation, capture content revision and immutable output settings
   and acquire the shared publisher request token. Expensive formatting operates
   on this captured input and does not block the UI.
2. The combined warning confirmation refers to this exact prepared payload and warning-reason set. On every path, both
   below the warning threshold and after confirmation, revalidate project
   lifetime, content/settings revisions, actual shared options and request
   currency immediately before writing. Serialize final validation and clipboard
   publication on EDT with #73/settings mutations, and use the application
   coordinator's atomic request check/write for concurrent tokens. No mutation may pass
   between the input check and write. If inputs changed, do not silently
   substitute another payload; report that the user must copy again.
3. Rejected, cancelled, invalidated and stale requests produce no clipboard,
   history, success notification, status, analytics or review update. As with the
   existing latest-request policy, beginning a newer copy request supersedes an
   older pending request even if the new attempt is later cancelled.
4. Source-state changes alone do not change the captured output. Closing source
   editors does not invalidate a copy prepared from immutable items.
5. Return a typed publication outcome distinguishing `NotPublished(reason)` from
   `Published(feedbackFailures)`. A clipboard write failure is NotPublished and
   prevents every success-side effect. After a successful write, attempt each
   enabled optional effect once in deterministic order, isolating nonfatal
   failures so a failed notification does not prevent status/accounting effects.
   Return Published even if some feedback fails, and never retry clipboard or
   any already-attempted counter. Only sanitized effect/failure categories may
   be logged, without source text or full paths. Keep existing policy behavior
   unchanged outside the new collection path. Retain collection in every case.

## Acceptance criteria

- [ ] Mixed files/languages and duplicate filenames produce deterministic output
  with full built-in file boundaries, collection ordering and exact separators.
- [ ] Multiple captures of one source location/range receive stable number/time
  annotations in built-in output, including code-off mode, while unrelated items
  and existing single-copy output are unchanged. Reordering preserves labels.
- [ ] Capture code A with relative paths, switch to absolute paths and capture
  changed code B at the same source/range: the two chosen display paths stay
  distinct but both belong to one conflict group and receive stable annotations.
- [ ] Custom-template output is byte-for-byte unchanged by snapshot-label policy.
  Conflict groups require the agreed confirmation; missing historical code and
  size reasons share one dialog. Cancellation preserves all prior state.
- [ ] Conflict warnings still require confirmation for a small payload below or
  exactly at 256 KiB even though its size-warning flag is false.
- [ ] Removing the final conflict recalculates labels, warning reasons and exact
  UTF-8 bytes. Annotation overhead is covered at both size thresholds.
- [ ] Raw snapshots are unchanged by output toggles, trimming and formatting.
- [ ] All existing custom variables, fallback and single-pass substitution remain
  compatible, including repeated `{code}` and text resembling template variables.
- [ ] Empty collection/blank item outputs cannot report successful copy.
- [ ] UTF-8 final output below/at/above both thresholds gets the specified result.
- [ ] Oversize confirmation copies the same payload it described, or invalidates
  explicitly when inputs change. Cancel leaves clipboard and collection intact.
- [ ] Below-warning asynchronous copies also revalidate at publication. A change
  between computation and dispatch cannot publish stale input, and validation
  plus publication is serialized against content/settings mutations.
- [ ] Hard overflow never constructs or publishes an unbounded/truncated result.
- [ ] Copy works with no open editor and with renamed/deleted sources.
- [ ] Collection results never enter history and do not modify existing entries.
- [ ] In successful execution, every enabled effect occurs once, independent of
  item count. Each optional effect is attempted at most once even if it fails.
- [ ] Analytics-on copies increment total and actual-format once, with one
  same-language/mixed/blank-language result. Analytics-off copies do not record.
  Review eligibility is evaluated once with its existing independent gates.
- [ ] A clipboard exception invokes no optional effects. Injecting a nonfatal
  failure in any optional effect still attempts later enabled effects once and
  returns Published with failure metadata, without another clipboard write.
- [ ] Committed format/template/trimming changes invalidate output in every open
  project. Old calculations cannot overwrite newer results. Source-status-only
  changes refresh source labels without invalidating output or pending copy.
- [ ] Later ordinary/permalink/collection requests suppress older completions
  across action instances and project publishers through the shared application
  coordinator. Managed history/status re-copy also suppresses pending old copies.
- [ ] In an A-starts/B-copies/A-finishes sequence, the clipboard and B's feedback
  remain B's result and A has no stale success/failure feedback. Cover A as a
  delayed permalink or collection and B as each managed synchronous/async path.
- [ ] A later cancelled/failed request does not revive an older request. Closing
  either project does not reset ordering or leak its data into another project's
  history/status. Application coordination stores no payloads or source snapshots.
- [ ] Re-copy remains clipboard-only after adopting global ordering, with no
  additional analytics, history, review or notification effects.
- [ ] Success/failure/cancellation all retain the collection.

## Verification and documentation

Pure tests: golden strings for three formats, mixed languages, duplicate names,
multi-line code fences, Unicode/UTF-8 boundaries, code flag, trimming, template
repetition, blank outputs and safe overflow. Publisher tests cover every policy
flag, no editor, effect cardinality and failure ordering.

IDE fixtures: real clipboard, no active editor, history exclusion, retention,
oversize confirmation/cancel, changed inputs during confirmation, project close
and interleaving two live projects with delayed permalink/collection completion.
Use controlled completion barriers/fakes instead of sleep-based race tests; cover
history and status re-copy invalidation and independent project feedback. Add new platform-state test
classes to the isolated Gradle test partition. Keep existing single-copy and
permalink tests passing and run the applicable aggregate/verification gates.

Update five bundles and READMEs, CHANGELOG Unreleased, architecture/pattern docs,
publisher policy documentation/tests and registered action inheritance checks.
Document that session-only plugin retention does not control OS or third-party
clipboard history. No network service, token estimator, export format framework,
automatic refresh, persistent collection storage or configurable template DSL is
part of this issue.

Provide reproducible ready-output and above-warning sample scenarios for #74's
real UI screenshots. #74 owns the final screenshot assets, synchronized README
image sections, store-facing description and Marketplace media release checklist.

