# Non-sensitive context collection sample

Copy Timeout.kt and Request.py into a new empty project under `src/demo/`. These files contain no credentials, personal data or external calls. Keep English IDE UI and use relative paths when taking #74 screenshots.

1. In Timeout.kt, select exactly `val timeout = 10` on line 3 and invoke Add to Context Collection. Capture #1 contains 16 ASCII/UTF-8 bytes, range 3–3.
2. Select `val retries = 3` on line 4 and add. Capture #2 contains 15 bytes, range 4–4.
3. In Request.py, select lines 1–2 without the trailing newline and add. Capture #3 contains 36 bytes, range 1–2. The collection has 3 items and 67 raw UTF-8 bytes.
4. Return to Timeout.kt and change line 3 to `val timeout = 30` without saving. Select that line again and add. Capture #4 adds 16 bytes, bringing the total to 83. Capture #1 stays `10` and capture #4 contains `30` with the same captured source location/range. #75 will label both in built-in output.
5. Re-add the unchanged line: expect 0 added, 1 duplicate and the same count/bytes. Switch to absolute paths and re-add again: still a duplicate with frozen relative display path. Actual file rename followed by recapture creates a separate new-location snapshot.
6. Rename/delete the source after collection: inspect separate source state in #74 while captured content remains fixed. Close/reopen the management panel: the collection persists. Close/reopen the project: it starts empty with collection code inclusion enabled.

#75 supplies the final output-size/warning sample after its formatter exists. #74 owns actual screenshot production and records the exact plugin head/version, IDE build, theme, locale and viewport. This guide is reproducible source material, not evidence that those screenshots have been taken.
