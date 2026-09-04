# Context collection output and copy contract (#75)

## Consumers and ownership

`ContextCollectionOutputService.getInstance(project)` is initialized on EDT. Subscribe with `subscribe(contentDisposable) { state -> ... }`, then read `snapshot()` in that same EDT turn. Subscriptions have no initial callback and release with either owner. Listeners only inspect completed state; schedule mutations and modal dialogs for a later EDT turn. #74 never formats, counts output bytes, asks copy confirmations, or writes the clipboard independently.

`ContextCollectionOutputKey` contains `contentRevision`, `settingsRevision`, immutable `options(format, template, trimCode)` and `includeCode`. Include-code belongs to the session content snapshot. Captured display paths stay fixed. `ContextCollectionOutputState` is `Calculating(key)` or `Computed(key, result)`. On a changed key the old payload/byte display is immediately invalidated and Calculating is emitted before background work. Cancelled/superseded work cannot replace current output. There is one current prepared payload, with no history/cache of outputs. Source-only state events do not change the key or start a calculation.

`CopySelectionSettings.outputSettingsCommitted()` signals after Configurable Apply commits successfully; UI Reset has no signal. `loadState()` restores synchronously under a short settings monitor and dispatches only observer delivery to EDT. It never waits for EDT during service initialization or a background read action. The output revision advances only for changes to format/template/trimming. Subscribers run on EDT. Existing direct writes to mutable settings must happen on EDT; `refresh()` and final copy validation also compare the actual tuple to catch callers that omitted the signal. Path type and one-shot include-code do not invalidate existing captures.

Final collection publication holds that same settings monitor around the coordinator transaction only (settings monitor → coordinator monitor), so even background state replacement cannot pass between actual-key validation and clipboard writing. Feedback runs after both monitors are released. Background reload never calls the coordinator or waits for EDT while holding the monitor. The immutable revision/tuple is read together; copy rejects changed settings even before their queued observer event arrives. See [JetBrains service initialization guidance](https://www.jetbrains.com/help/inspectopedia/PotentialDeadlockInServiceInitialization.html).

## Shared result

The pure `ContextCollectionFormatter.format(snapshot, options)` returns:

| Result | Consumer behavior |
| --- | --- |
| `Ready(payload, bytes, itemCount, actualFormat, language, warnings)` | Display this exact plain-text payload and exact UTF-8 count; enable Copy All |
| `Empty` | Show add guidance; disable Copy All |
| `BlankItem(captureNumber, actualFormat)` | Show capture/format error; disable Copy All |
| `AboveHardLimit` | Show exceeds 4 MiB, never a fabricated exact count; disable Copy All |

`Calculating` clears stale preview/byte claims and disables the tool-window button. A ready result with warnings remains copyable through the command. Limits are strict greater-than: warning above 262144 bytes, rejection above 4194304 bytes. All text, annotation lines and two-newline item separators count. No item is omitted and no output is truncated. Streaming template substitution stops once the hard bound is exceeded and retains the existing unknown-variable/single-pass/fallback semantics. Incremental UTF-8 counting includes supplementary pairs spanning substitution chunks and JVM one-byte malformed-surrogate replacements.

Conflicts group by captured source location and inclusive range, independent of display path. Built-ins prefix each conflict with `[Snapshot #N · YYYY-MM-DDTHH:mm:ss.SSSZ]` using its stable captured number/time. Custom templates are never annotated. `SNAPSHOT_LABELS_ABSENT`, `HISTORICAL_CODE_ABSENT` and `SIZE` are a combined immutable reason set. Blank effective code, code disabled, or custom output without `{code}` warns that references cannot reconstruct historical code. Small conflicting payloads can therefore require confirmation without a size warning.

## Copy command and publication

Call `ContextCollectionCopyCommand.getInstance(project).execute()` on EDT. The localized `CopySelectionContext.CopyAllCollection` action uses this same entry point, without an active-editor requirement or default shortcut. It is registered in the existing submenu and Find Action. The command acquires a global request token at invocation, freezes the current key and consumes the matching computed result (or waits for it). No preview operation acquires a copy token. Another invocation supersedes the previous command's pending subscription.

The command schedules completion after subscriptions return, verifies current inputs before showing its one combined confirmation, and publishes the same ready payload after Copy Anyway. Cancel is the dialog default. Every final path, including small output and post-dialog completion, rechecks lifetime, content/settings revision, include-code and the actual current tuple on EDT inside the coordinator's final transaction. Source-only changes and closed source editors remain valid. Input changes ask the user to copy again; they never substitute new output for an old confirmation. Stale requests show no success or failure feedback.

`CopyPublicationOutcome` distinguishes `NotPublished(reason)` from `Published(feedbackFailures)`. A token is attempted at most once, including clipboard failure. Clipboard failure prevents all success effects; after successful writing optional effects are attempted once in deterministic order, isolated from one another. Failure metadata contains effect and exception class only. No raw text or path is logged or recorded in analytics. The collection is retained for every outcome.

If a synchronous clipboard listener or an early feedback effect starts a newer copy after COLLECTION has already written successfully, its analytics and review adapter calls are still attempted once. Notification/status adapters are also attempted once but guard visible feedback with current request/lifetime checks, so an older successful copy cannot replace the newer status. Disposal suppresses actual project UI access. These guarded no-ops are not failures; thrown nonfatal exceptions are listed in `feedbackFailures`. STANDARD/GIT_PERMALINK keep their current-request guard before each effect. Requests stale before their clipboard transaction still perform zero effects.

| Policy | Analytics | Gutter | History | Notification | Status | Review |
| --- | --- | --- | --- | --- | --- | --- |
| STANDARD | opt-in | yes | yes | preference | yes | eligibility |
| GIT_PERMALINK | no | yes | yes | preference | yes | no |
| COLLECTION | opt-in | no | no | preference | yes | eligibility |
| History/status re-copy | no | no | no | no | no | no |

Collection analytics uses the ready result's actual formatter and one reduced nonblank language key: sole distinct key, `mixed` for several, or blank for all unknown. No per-item counter or extra source metadata is stored. Review eligibility is called once independently of analytics and preserves existing environment/version/notification/suppression gates.

`ClipboardRequestCoordinator` is an application light service owning only a sequence and attempted flag. Final request check, input validation and clipboard write run under its single monitor. Formatting, dialogs and feedback are outside that monitor. Publishers own project lifetime and feedback. Standard, async permalink, collection, history re-copy and status re-copy all participate. Later cancellation/failure/project disposal never revives earlier requests. Native Ctrl/Cmd+C, other processes/apps/plugins remain outside this guarantee. Plugin session retention does not control OS or third-party clipboard history.

## Verification and #74 handoff

`ContextCollectionFormatterTest` covers golden strings, conflict identity, fixed labels, warnings, every template variable, exact byte boundaries, malformed UTF-16 and bounded template amplification. `CopyResultPublisherTest` covers all policies, no editor, accounting cardinality, failure isolation, no retry, two publisher ordering and a latch-controlled final transaction. `ContextCollectionOutputFixtureTest` is registered in `platformStateTestClasses`; it covers real clipboard/history/gutter, settings/source invalidation, controlled stale calculations, confirmation mutation, disposal and two live projects including history/status re-copy. No timing sleeps are used.

Use [the sample guide](../samples/context-collection/README.md) for actual ready/above-warning output. #74 owns the tool window, accessible presentation and actual English IDE screenshots. This implementation does not create a panel or claim screenshot evidence. Version stays 1.4.1 until the separately authorized release stage.
