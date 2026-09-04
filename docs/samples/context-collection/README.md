# Non-sensitive context collection sample

Copy Timeout.kt and Request.py into a new empty project under `src/demo/`. These files contain no credentials, personal data or external calls. Keep English IDE UI and use relative paths when taking #74 screenshots.

1. In Timeout.kt, select exactly `val timeout = 10` on line 3 and invoke Add to Context Collection. Capture #1 contains 16 ASCII/UTF-8 bytes, range 3–3.
2. Select `val retries = 3` on line 4 and add. Capture #2 contains 15 bytes, range 4–4.
3. In Request.py, select lines 1–2 without the trailing newline and add. Capture #3 contains 36 bytes, range 1–2. The collection has 3 items and 67 raw UTF-8 bytes.
4. Return to Timeout.kt and change line 3 to `val timeout = 30` without saving. Select that line again and add. Capture #4 adds 16 bytes, bringing the total to 83. Capture #1 stays `10` and capture #4 contains `30` with the same captured source location/range. #75 will label both in built-in output.
5. Re-add the unchanged line: expect 0 added, 1 duplicate and the same count/bytes. Switch to absolute paths and re-add again: still a duplicate with frozen relative display path. Actual file rename followed by recapture creates a separate new-location snapshot.
6. Rename/delete the source after collection: inspect separate source state in #74 while captured content remains fixed. Close/reopen the management panel: the collection persists. Close/reopen the project: it starts empty with collection code inclusion enabled.

## Ready output and snapshot preview

For the first three captures, choose a custom template consisting of `{path}:{range}`, one newline, then `{code}` (no trailing newline). Leave collection code enabled and trimming off. The output is the three captured blocks separated by exactly two newlines. No conflict confirmation is required until capture #4 is added. Use the same template's exact substitution output for preview/copy comparison. For capture #4, switch to Path:Line to show stable built-in Snapshot #1/#4 UTC millisecond annotations. UI capture times vary with the actual capture; do not fake fixed screenshot timestamps.

## Reproducible above-warning confirmation

Run `python3 docs/samples/context-collection/generate-warning.py /tmp/context-collection-demo` and open that disposable demo project. The script creates `src/demo/Warning.txt` and `expected-warning-output.txt`; it performs no network calls. Begin with an empty project collection, select the entire Warning.txt file, and invoke Add to Context Collection once. This single captured line is exactly 262144 ASCII/UTF-8 bytes, the allowed per-item boundary. Do not collect the expected-output file.

In the existing formatting settings, choose Custom Template. Enter `{code}` followed by exactly one newline (place the caret on the empty second line), Apply, and leave collection code enabled. Invoke Copy All Context Collection via Find Action or the #74 toolbar. The shared Ready result is exactly **262145 output UTF-8 bytes**, **1 item**, and warning set `{SIZE}`. The raw-code summary remains **262144 bytes**. The one confirmation displays the exact output count and item count, Copy Anyway and Cancel; its default is Cancel. Copy Anyway must match `expected-warning-output.txt` byte-for-byte (including its final newline). Cancel retains the collection and previous clipboard. A `{code}` template without the newline produces exactly **262144 bytes** and no size warning, so confirm the template carefully before photographing.

`ContextCollectionFormatterTest` verifies both values and all threshold boundaries. This custom-template sample avoids dependence on IDE language detection, absolute paths or capture clock values. For a built-in confirmation, Path:Line with code enabled also exceeds 256 KiB; use the service's actual byte result instead of reusing the custom sample's number.

#74 owns actual screenshot production and records the exact plugin head/version, IDE build, theme, locale and viewport. Use the [output/copy contract](../../development/context-collection-output-contract.md). This guide is reproducible source material, not evidence that screenshots have been taken. Local ZIPs remain validation artifacts, not canonical releases.
