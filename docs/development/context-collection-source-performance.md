# Source tracking profile (#104)

Measured 2026-09-08 on macOS 26.6.2 (25G83) / Darwin 25.6.0, arm64, 12 CPU, 24 GiB RAM, IC 2024.3 (243.21565.193), JetBrains Runtime 21.0.5+8-b631.16. Baseline is `51a6155766e5e9400631851ad903e5b74e249f87`. Revised source SHA-256: `05aa8f8c183293be21eb46f7193f6bb72c7bbc198756e4ee8d8704e3fa622528`.

This is a headless platform/editor profile, not GUI input-latency measurement. One fixture creates one or two real Project instances and a tracker owned by each project lifetime. Both implementations are temporary test-only copies of their source, with identical primitive counters added at document callback, observe, update, status visit/map/object/snapshot construction and EDT enqueue boundaries. No counters ship in the plugin; the optional production observer is null. Source files/paths/content are synthetic and are not logged.

For each 0/1/100-capture, 1/2-project condition, baseline and revised implementations alternate, with two warmups and five measured samples, each delivering 1,000 events. Captures use distinct live files except the related-file case, where all captures share the edited source. Actual document edits pass through FileDocumentManager/EditorFactory and WriteCommandAction. Structural cost uses an unknown/null-file structural event at the tracker boundary; separate regression fixtures perform actual file and ancestor rename/move/delete. Background-unrelated cost uses pooled callbacks and drains the EDT queue after timing; reported time/allocated bytes for that case excludes the later queue drain, while operation counts include it. Structural scans remain conservative.

All conditions run inside one worker JVM and one exclusive heavy-resource lease. JFR uses `settings=profile`; exact counter samples are the deterministic evidence, while allocation/JFR/time measurements include surrounding IDE fixture work and are not CI thresholds. Status-map creation is counted separately from published immutable snapshots: baseline unrelated events allocate maps and status objects even though they publish no new snapshot.

## Deterministic unrelated-event work

Per 1,000 events, for both EDT document events and background source callbacks:

| Active projects | Captures per project | Visits before → after | State maps before → after | Status objects before → after | Published snapshots before → after | Background EDT enqueues before → after |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 → 0 | 1,000 → 0 | 0 → 0 | 0 → 0 | 1,000 → 0 |
| 1 | 1 | 1,000 → 0 | 1,000 → 0 | 1,000 → 0 | 0 → 0 | 1,000 → 0 |
| 1 | 100 | 100,000 → 0 | 1,000 → 0 | 100,000 → 0 | 0 → 0 | 1,000 → 0 |
| 2 | 0 | 0 → 0 | 2,000 → 0 | 0 → 0 | 0 → 0 | 2,000 → 0 |
| 2 | 1 | 2,000 → 0 | 2,000 → 0 | 2,000 → 0 | 0 → 0 | 2,000 → 0 |
| 2 | 100 | 200,000 → 0 | 2,000 → 0 | 200,000 → 0 | 0 → 0 | 2,000 → 0 |

Revised update invocations are zero in all unrelated/empty conditions. EDT document callbacks have zero enqueues in both versions; the baseline performs the work synchronously. Repeated related events visit only their matching captures; each tracker creates one map/snapshot on the first newly changed state and none for subsequent identical states. Nonempty structural events still visit all retained captures and allocate a candidate map/status objects; unchanged final state publishes no snapshot. This preserves detection rather than omitting it.

## Time and allocation samples

Median / maximum milliseconds per 1,000 events; allocation is median current-thread bytes. These are descriptive measurements with no speedup or typing-latency guarantee.

| Event | Projects | Captures | Baseline ms median / max | Revised ms median / max | Baseline / revised bytes |
|---|---:|---:|---:|---:|---:|
| unrelated | 1 | 0 | 218.764 / 272.004 | 214.760 / 249.696 | 118,333,112 / 118,105,864 |
| unrelated | 1 | 1 | 163.755 / 165.654 | 156.738 / 163.855 | 118,622,560 / 117,947,840 |
| unrelated | 1 | 100 | 171.656 / 177.428 | 157.301 / 166.275 | 139,708,160 / 117,812,504 |
| unrelated | 2 | 0 | 159.434 / 165.927 | 158.871 / 166.852 | 120,578,232 / 120,223,672 |
| unrelated | 2 | 1 | 159.991 / 168.654 | 161.982 / 168.310 | 123,199,600 / 122,371,272 |
| unrelated | 2 | 100 | 180.223 / 184.279 | 160.415 / 166.287 | 168,662,992 / 124,595,304 |
| related | 1 | 0 | 172.581 / 198.082 | 176.626 / 182.839 | 118,102,600 / 117,931,584 |
| related | 1 | 1 | 156.149 / 165.010 | 157.564 / 256.787 | 120,396,680 / 120,248,216 |
| related | 1 | 100 | 171.962 / 177.145 | 162.381 / 166.799 | 153,768,936 / 126,864,048 |
| related | 2 | 0 | 160.518 / 163.119 | 161.640 / 168.705 | 121,799,808 / 121,332,128 |
| related | 2 | 1 | 161.215 / 171.771 | 168.576 / 174.982 | 124,273,008 / 123,914,800 |
| related | 2 | 100 | 183.252 / 188.508 | 167.534 / 181.587 | 192,169,592 / 138,518,744 |
| structure | 1 | 0 | 0.183 / 0.202 | 0.152 / 0.154 | 208,024 / 56,024 |
| structure | 1 | 1 | 0.131 / 0.166 | 0.302 / 0.324 | 520,064 / 728,024 |
| structure | 1 | 100 | 5.546 / 8.483 | 5.219 / 8.347 | 22,080,048 / 22,144,024 |
| structure | 2 | 0 | 0.090 / 0.118 | 0.050 / 0.058 | 384,024 / 80,024 |
| structure | 2 | 1 | 0.168 / 0.189 | 0.191 / 0.201 | 1,008,104 / 1,152,024 |
| structure | 2 | 100 | 11.849 / 14.963 | 12.388 / 13.287 | 44,128,072 / 44,256,024 |
| background-unrelated | 1 | 0 | 0.529 / 0.569 | 0.057 / 0.083 | 904,024 / 48,024 |
| background-unrelated | 1 | 1 | 0.807 / 0.966 | 0.105 / 0.111 | 928,064 / 152,024 |
| background-unrelated | 1 | 100 | 0.734 / 0.892 | 0.027 / 1.932 | 936,048 / 112,024 |
| background-unrelated | 2 | 0 | 1.155 / 1.206 | 0.082 / 0.155 | 1,776,208 / 64,024 |
| background-unrelated | 2 | 1 | 1.385 / 1.452 | 0.096 / 0.102 | 1,824,288 / 192,024 |
| background-unrelated | 2 | 100 | 1.370 / 2.010 | 0.035 / 0.042 | 1,840,256 / 192,024 |

Raw evidence remains outside the product tree at `/private/tmp/goal-runs/copy-selection-context-milestone-8/issue-104/`: `profile-final.log` (exit 0), `profile-samples.csv` (240 rows), `source-profile.jfr`, the two instrumented source copies, `ContextCollectionSourceProfileFixtureTest.kt`, `prepare-profile.py`, and `profile.init.gradle`. The harness is temporary and is removed before normal tests/packaging. Earlier failed harness/filter setup logs are not measurement evidence. Reproduce by generating the temporary copies, restoring the supplied fixture and running `platformTest` with the supplied init script and `--tests '*ContextCollectionSourceProfileFixtureTest'` under the same JBR; remove the three temporary test files afterward.
