# Shortcut audit runbook

This is the reproducible procedure for #132. Preparing a profile, querying a
distribution, running a fixture and sending real keys are separate activities.
Only observed results belong in the evidence sections of the
[audit](../plans/shortcut-keymap-audit.md) and
[migration contract](shortcut-migration-contract.md).

## Scope and verdicts

The user limited real keyboard, UI and upgrade execution to **macOS** on
2026-09-08. Windows and Linux GNOME/KDE real execution is **excluded by user
scope**, never PASS or N/A. Windows/macOS/Linux automated CI and the effective
keymaps for every OS family remain required.

Use `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`, `N/A (specific unavailable combination)`
or `EXCLUDED BY USER SCOPE`. A missing distribution, license or GUI driver is
BLOCKED/NOT RUN, not evidence that its keymap is absent. An absent Rider family
can be N/A only after recording the loaded distribution's full keymap inventory.
Any outstanding required macOS row prevents issue/milestone completion.

The final candidate must be the same integrated main SHA and ZIP SHA-256 used
by the final CI/Verifier run, after #128–#131 have merged and their required CI
has passed. The plugin version alone does not identify that candidate: this
work does not bump the version or publish a release. Re-record affected results
when code or ZIP changes. Never present earlier draft or v1.6.0 probe results as
candidate verification.

## Distribution inventory

Refresh versions at execution time. Record the official source URL and UTC
retrieval time, downloaded file hash, `product-info.json`, actual IDE build,
loaded plugin inventory, architecture and profile paths.

The JetBrains [release API](https://data.services.jetbrains.com/products/releases?code=IIU,RD,PY,WS,PS,GO,CL,DG,RM&latest=true&type=release)
reported these stable releases on 2026-09-08. Android's
[stable release page](https://developer.android.com/studio/releases) reported
Quail 4. These are distribution selection data, not runtime test results.

| Product | Stable version selected for preparation | Build from official release data | Required execution |
| --- | --- | --- | --- |
| IntelliJ IDEA Community minimum | 2024.3 | IC-243.21565.193 (installed distribution) | Effective keymaps and macOS real input/UI/upgrade |
| IntelliJ IDEA latest | 2026.2.2 | 262.10315.125 | Effective keymaps and macOS real input/UI/upgrade |
| Rider latest | 2026.2.1 | 262.9437.287 | Effective keymaps and macOS real input/UI/upgrade |
| PyCharm | 2026.2.2 | 262.10315.174 | Effective keymaps |
| WebStorm | 2026.2.2 | 262.10315.144 | Effective keymaps |
| PhpStorm | 2026.2.2 | 262.10315.130 | Effective keymaps |
| GoLand | 2026.2.2.1 | 262.10315.160 | Effective keymaps |
| CLion | 2026.2.2 | 262.10315.131 | Effective keymaps |
| DataGrip | 2026.2.5 | 262.10315.132 | Effective keymaps |
| RubyMine | 2026.2.2 | 262.10315.129 | Effective keymaps |
| Android Studio | Quail 4 / 2026.1.4 | AI-261.26222.65.2614.16204760 (downloaded distribution) | Effective keymaps |

For each product enumerate every loaded keymap, including Windows/Linux and
macOS variants; record ID, parent chain and bundled/selected third-party
contributions. In Rider explicitly locate IntelliJ, Visual Studio, Visual Studio
2022, ReSharper and VS Code families and their OS variants. Do not infer IDs from
the visible name or a reference card. Keep the exact raw inventory even when
summarizing equivalent inheritance chains.

## Isolated tooling

`scripts/shortcut-audit/audit.py` uses Python 3.9+ and a JDK 21 to package a small
**separate diagnostic plugin**. It does not build or change the product ZIP,
download IDEs, discover personal profiles, change shortcuts, call product
`actionPerformed`, or access accounts. Use fresh output paths. It refuses to
overwrite profiles, archives or evidence. Run only one Gradle/IDE process at a
time when the coordinator has assigned the execution slot.

The diagnostic reads registered actions including lazy action stubs, unions
their IDs with every keymap and ancestor's action IDs, then reads effective
shortcuts through the platform keymap API. It inspects the entire first stroke,
including single strokes and chords with different second keys. It dumps both
`control alt shift G` and `meta alt shift G`, and all Ctrl/Meta+Alt+C/H comparisons.
Only the nine exact product IDs are excluded when classifying external prefix
occupancy. Dormant mappings remain visible with `registered=false`. Owner plugin
IDs, bundled flags and the loaded plugin inventory distinguish contributions.

### Comparing replacement prefixes

The six original schema-2 probes are retained as the G/C/H baseline. They do
not establish that another prefix is free. Start a **fresh observation profile**
with `audit --stroke-inventory` or `launch-gui --stroke-inventory` to request a
complete companion named `EXPORT.tsv.strokes.tsv` from the same EDT capture.
The optional companion has its own schema 1 and binds the original export's
filename and SHA-256. It includes the registered-ID snapshot, each keymap and
ancestor's source IDs, and every effective shortcut list (including empty,
mouse, dormant, single-stroke and two-stroke entries) for their union. Per-map
and final counts are checked, as are the original nine/watched bindings and
all six original probe occupancies and ownership fields. A missing companion,
missing action/ancestor row or mismatched export is an error, never a zero.

Every companion action's `registered` flag must match the registered-ID snapshot
in both directions, and its `(registered, owner, bundled)` tuple must be identical
across keymaps, matching the exporter's per-action cache. The exporter resolves
an action/stub and derives its descriptor through the stub or platform class
lookup; absent actions therefore require both owner and bundled to be `unknown`.
For registered actions, complete-evidence acceptance requires a known owner in
the original `PLUGIN` inventory and the same bundled flag. Although the exporter
can represent a missing descriptor as `unknown`, such a registered contribution
cannot be attributed and is rejected for this audit. A registration snapshot
disagreement is likewise rejected, never repaired or silently marked dormant.
These checks apply to every effective action, including actions outside the
original six probes.

```bash
python3 scripts/shortcut-audit/audit.py launch-gui \
  --app IDE_APP --profile FRESH_OBSERVATION_PROFILE --project FIXTURE_PROJECT \
  --stroke-inventory
# Invoke Export CSC Keymap Audit in the GUI, then quit that owned IDE.
python3 scripts/shortcut-audit/audit.py compare-prefixes \
  --export IC_EXPORT.tsv --export IDEA_EXPORT.tsv --export RIDER_EXPORT.tsv \
  --output NEW_PREFIX_COMPARISON.json
```

The default comparison covers A–Z, F1–F24 and eleven punctuation key codes with
both Ctrl+Alt+Shift and Meta+Alt+Shift. Repeated `--key J --key SEMICOLON` narrows
it explicitly. Punctuation uses Swing names: `SEMICOLON`, `COMMA`, `PERIOD`,
`SLASH`, `BACK_SLASH`, `OPEN_BRACKET`, `CLOSE_BRACKET`, `MINUS`, `EQUALS`,
`BACK_QUOTE` and `QUOTE`. These identify pressed key codes with modifiers, not
the characters a layout/IME produces. Literal punctuation is rejected to avoid
confusing typed and pressed strokes. Every first
stroke match counts, regardless of its second key or whether the action is
currently registered. Only the exact nine product IDs are excluded from
external conflicts; their occupancies remain separately listed. Results retain
all source identities, loaded plugins, keymap chains, action IDs/owners and
full shortcut lists, so a zero is bounded to the recorded combinations. A
platform action may return duplicate shortcuts: the companion preserves every
entry and its counts, while comparison reports one occupancy per identical
stroke and records `occurrencesInApiList` rather than inflating conflicts. A
candidate's availability on physical keyboards, Fn/media handling, macOS or
desktop interception, input layout and Korean IME remain separate real-input
requirements. F13–F24 may be unoccupied yet unavailable on ordinary keyboards.
No candidate is selected or applied to the product by this tool.

To additionally compare the modifier the product chooses for each keymap, pass
`--defaults-source PATH_TO_CopySelectionShortcuts.kt`. This is an explicit,
source-pinned policy: the currently supported source is the #128 implementation
at commit `1909df4ec318fda5ebab33400ceca1e912fb7549` (the exact source hash is in
`lineage.py`; a test fixture preserves that file). Any source change is rejected
until the rule and mirror are reviewed and the pin is updated. This intentionally
includes unrelated changes; the tool must never guess compatibility.

The policy source is one bounded bytes snapshot, capped at 16 KiB before and
after reading. The same bytes supply both SHA-256 and UTF-8 policy parsing.
Only regular files are accepted: final-entry symlinks, directories and special
files are rejected before reading, with no-follow/nonblocking open flags guarding
replacement races. The opened file's identity and size are checked again, and
changes to size/timestamps during the read fail validation. Parent directory
aliases are allowed; provenance labels the absolute lookup path and records
the opened descriptor's device/inode and snapshot size, without resolving the
path or rereading it after validation. Hosts lacking the required open flags
are rejected. Use a preserved commit snapshot when a live worktree can change.

The mirror walks the recorded keymap chain from self to ancestors. The first
exact member of the source's `macKeymapIds` chooses Meta; the first `$default`
chooses Ctrl. If neither occurs, it uses the recorded host OS, rejecting unknown
hosts. It does not infer a family from substrings such as `Mac` or `OSX`, or use
an old action's shortcut as a proxy. Each decision retains the chain, decisive
ancestor or host fallback, while the report records product and comparator
source hashes. The existing both-modifier result remains intact; the additional
`productRuleComparison` separates selected and opposite modifier occupancies.
A zero there means only that the selected modifier has no external first-stroke
match in those recorded maps. It is not a product execution or physical-input
verdict, and opposite-modifier entries are never discarded.

If a product's `product-info.json` omits `javaExecutablePath`, inspect that
distribution's bundled runtime and pass its absolute path to `audit` using
`--java-executable`. The tool accepts only an executable inside the selected
IDE home, resolves symlinks, and rejects overriding an existing metadata path.
It records the explicit selection and executable hash in run evidence without
altering product metadata. Runtime resolution fails before installing the
diagnostic harness into a fresh profile. This option does not change licensing
or initialization behavior; a runtime license rejection remains a blocked run.

Preserve old harness directories, exports and accepted profiles unchanged.
Use their pinned exporter revision for revalidation: a newer exporter correctly
rejects their old source manifest. Collect replacement-prefix inventories with
the new reviewed harness in separate profiles; never install it into a frozen
v6 baseline or relabel old six-probe data as complete-inventory evidence.

`build-harness` creates an immutable `manifest.json` beside the diagnostic JAR.
It records the exact JAR SHA-256, plugin ID and Java/descriptor source hashes,
plus the available Git revision and dirty-state metadata. Source hashes are the
binding to the reviewed exporter content; a revision alone does not identify
uncommitted build inputs. Keep the entire `csc-keymap-audit` directory together.
The runners reject a missing manifest, stale exporter sources, wrong plugin ID,
modified JAR or unexpected files before launching. They record and revalidate
the installed artifact after execution. Launch records, raw exports and baseline
acceptance all carry the same JAR/manifest hashes. A legacy diagnostic JAR cannot
be made current by copying it into a new profile. Rebuild from reviewed sources
and regenerate observations after exporter changes; do not add manifests to old
JARs or rewrite existing acceptance records.

The headless application starter exports data after application startup. Because
project startup or optional plugins can register additional actions, repeat the
export in the GUI after the audit project finishes loading, using Find Action →
**Export CSC Keymap Audit**. The export action has no shortcut. Compare the
registered action counts and occupancy; retain both snapshots. A headless dump
does not certify project-dependent or real key behavior. Do not instantiate
every IDE action to force lazy initialization: registered stubs already carry
their keymap data and plugin identity.

Example (replace every uppercase placeholder with an absolute path/value):

```bash
python3 scripts/shortcut-audit/audit.py build-harness \
  --ide-home IDE_APP_CONTENTS --jdk JDK21_HOME --output NEW_HARNESS_DIRECTORY
python3 scripts/shortcut-audit/audit.py prepare \
  --zip PRODUCT_ZIP --sha256 PRODUCT_SHA256 --output NEW_AUDIT_PROFILE
python3 scripts/shortcut-audit/audit.py audit \
  --ide-home IDE_APP_CONTENTS --profile NEW_AUDIT_PROFILE \
  --harness NEW_HARNESS_DIRECTORY/csc-keymap-audit --timeout 120
```

The starter is invoked with the distribution's own JBR, boot classpath and
runtime arguments from `product-info.json`. Config, system, plugins, logs and
error dumps are scoped to the new profile. This runner currently launches
macOS arm64 distributions; it still enumerates their installed OS-family
keymaps. Run other host distributions separately if their bundled contributions
differ. Never label macOS-hosted keymap data as Windows/Linux runtime evidence.

Output includes the exact launch argv and product identity (`audit-command.json`),
`audit-console.log`, PID/start/exit records, the complete `keymaps.tsv` and
`summary.json`. Schema 2 exports record profile/run/PID identity, open projects,
harness JAR/manifest hashes, initialized JSON parent chains, all nine command and watched-action bindings
(including mouse shortcuts and explicit empty lists), and all six probe strokes.
The parser rejects missing/duplicate rows, invalid schema/columns, mismatched
completion counts, incomplete per-keymap bindings and missing occupancy for a
recorded shortcut. A nonzero IDE exit also fails. Interrupted or failed launches
terminate their own process group and save the exit/cleanup record. Old schema 1
data requires `summarize --allow-legacy`; it is inspection-only and cannot certify
parents or become baseline/candidate acceptance evidence. A complete export
containing external G-prefix occupancy is evidence
of a conflict, not a passing audit. Report it for a product decision; do not
choose replacement keys or unbind external actions.

For GUI export, install only the product and the audit harness in a fresh test
plugin directory, then use `launch-gui --app IDE_APP --profile TEST_PROFILE
--project FIXTURE_PROJECT`. The runner creates a unique `gui-run-UUID` directory,
private VM options and `gui-command.json`; its VM options set the export location
and profile/run identities. Save screenshots and accessibility text inside that
same run directory. Use the GUI export action after the fixture project opens.
The nine-key behavioral run should also be repeated without
the diagnostic harness. A harness action invocation is only a data export.

## Native GUI launch and input precautions

Use an intact signed `.app` from the official distribution. Do not rewrite
Info.plist, replace the native launcher, or reconstruct a bundle around the
Gradle Java process. Pass the test `idea.properties` through the product's
`IDEA_PROPERTIES`/`RIDER_PROPERTIES` environment variable and use a private
VM-options file for crash/heap dumps. All four paths must point at the test
profile. Confirm them in `idea.log` before any UI changes.

In this host's restricted execution boundary, the native IC launcher failed at
`DirectoryLock` with `UnixDomainSockets.bind: Operation not permitted`, followed
by SIGABRT/134. The same native bundle and explicit profile started successfully
outside that boundary. An empty older log and reported exit137 do not establish
the same cause. The earlier Gradle Java GUI log ended with SIGTERM/143.

The GUI tool can automatically start a stopped app. Confirm the prepared process
is alive first, then target its bundle identifier; never let an inspection call
silently start the app with default user paths. Do not access or import personal
IDE settings, accounts, licenses or keymaps. Finish by terminating only the
process started for the allocated test profile.

Use actual separate key events: press the prefix, release **all** modifiers,
then press a plain second letter. Record US/QWERTY and Korean input source IDs
and actual delivered modifier behavior. A driver that cannot hold/release keys,
switch focus or observe OS interception cannot certify those timing/global
cases. Record that precise limitation and obtain operator evidence for those
rows. Clipboard verification must inspect the actual result, including spaces
and newlines, rather than rely only on the success balloon.

## macOS real execution matrix

Run these rows for IC 2024.3, the selected latest IDEA and latest Rider, with
US/QWERTY and Korean input. In Rider repeat across every installed required
keymap family. Prefix expectations follow the **active keymap lineage**, so a
Windows-style keymap selected on macOS keeps Ctrl+Alt+Shift+G. A native macOS
keymap uses Cmd+Option+Shift+G.

Prepare a local committed Git repository containing `example.txt` with exactly
`alpha\nbeta\ngamma\n`, an HTTPS GitHub fixture remote and a recorded HEAD SHA.
No remote writes or network Git operations are necessary. Use a valid project
base and select exactly line 2 without its following newline. Start with default
Claude output, absolute path and include-code disabled. Record the absolute
fixture path as `FILE`.

| Row | Real action/steps | Expected observation |
| --- | --- | --- |
| K-C | Prefix, release, C | Clipboard is exactly ` @FILE#L2 `; history/status update once |
| K-R | Prefix, release, R | Clipboard is exactly ` @example.txt#L2 `, including leading/trailing spaces |
| K-P | Prefix, release, P | Clipboard is exactly ` @FILE#L2 `, including leading/trailing spaces |
| K-B | Prefix, release, B | Exact code payload `B` defined below; UTF-8 clipboard bytes match |
| K-L | Prefix, release, L on clean committed text | Commit-pinned fixture URL with `#L2`; unchanged existing permalink publication contract |
| K-L-dirty | Change line 2, run L, cancel; repeat and confirm | Cancel preserves clipboard; confirm uses captured HEAD/range; no duplicate write |
| K-A | Seed a known clipboard value, prefix/release/A | One collection capture, clipboard/history/status unchanged |
| K-H | In a fresh profile, execute K-C once, close every editor, prefix/release/H | History opens with the C item; re-copy yields exactly ` @FILE#L2 ` |
| K-O | Close every editor, prefix/release/O | Collection tool window opens with the saved capture |
| K-F | Clear the test collection, K-A once, set collection Include Code off, close all editors, prefix/release/F | Exactly ` @FILE#L2 ` copied once; repeat with Include Code on and expect payload `B` |
| K-release | Prefix with modifiers held, varied release timing, then letter | Record platform behavior and verify the documented all-released/plain-letter path |
| K-Escape | Prefix, Escape, then type a sentinel | No product action; normal typing resumes |
| K-timeout | Prefix, wait beyond observed platform timeout, then type | No stuck chord state or unintended product action; record actual timeout |
| K-wrong | Prefix, unused second letter, then type | Record platform handling; subsequent normal input works |
| K-disabled | No selection/editor/project as applicable | Disabled editor actions do not copy; no-editor actions retain their contracts |
| K-focus | Prefix, move focus between editor/tool window/settings/another app | No stale context action or trapped typing after cancellation |
| K-indexing | Trigger while indexing; cancel and resume typing | Dumb-aware action behavior is preserved; no index dependency exception |

The exact code payload `B`, expressed as a JSON string after replacing `FILE`
with the absolute fixture path, is:

```json
" @FILE#L2 \n```text\nbeta\n```"
```

There is no trailing newline after the closing fence. K-F uses one capture to
avoid duplicate-source snapshot labels. For a separate two-capture check at the
same path/range, record each capture number and UTC timestamp and build the
expected string as `[Snapshot #N · YYYY-MM-DDTHH:mm:ss.SSSZ]\n` plus that capture's
formatted payload, joined with exactly `\n\n`. Compare against this independently
constructed string; the UI preview alone is not an oracle.

## Settings matrix

Run each row in the three required macOS IDEs. Use independent cloned profiles
for scenarios that mutate bindings. Capture dialog bounds and actual displayed
current/default shortcuts, including multi-shortcut, long Unicode labels and
unassigned rows. Check EN/KO/JA/zh-CN/zh-TW at normal and increased IDE scale;
record the scale and any unavailable localization combination.

| Row | Scenario | Expected observation |
| --- | --- | --- |
| S-list | Open plugin settings on default/custom/unassigned keymaps | Nine current/default rows and Keymap navigation reflect the active scheme |
| S-confirm | Restore then decline/close confirmation | No pending plan or active keymap change |
| S-pending | Confirm restore, inspect before Apply | Pending state visible; source identity/shortcuts unchanged |
| S-apply | Apply pending restore | One uniquely named derived scheme active; original preserved |
| S-ok | Confirm then OK | Same transaction as Apply and settings close |
| S-repeat | Apply again; reopen settings | No duplicate derivation; displayed current values refreshed |
| S-cancel | Confirm then Cancel; repeat with Reset/disposal | Pending plan discarded; no keymap mutation |
| S-template | Invalid custom template with pending restore, Apply/OK | Validation fails before keymap transaction |
| S-prefix-single | Give an external action the first stroke alone | Restore blocked with action ID; external action unchanged |
| S-prefix-chord | Give an external action same prefix with different second key | Restore blocked, even when all nine exact chords are free |
| S-stale-map | Confirm, switch active scheme, Apply | Stale plan rejected; no derived scheme |
| S-stale-binding | Confirm, change any of the nine keyboard/mouse lists, Apply | Stale plan rejected |
| S-stale-conflict | Confirm, introduce new external prefix occupant, Apply | New conflict rejected |
| S-preserve | Apply with unrelated edits, mouse shortcut, explicit IDE deletion | Only nine keyboard lists replaced; source and other assignments unchanged |
| S-return | Restart, then select original scheme | Derived scheme persists; original values restored by selecting original |
| S-layout | Long/multiple shortcuts, translations, increased scale | Rows and confirmation buttons remain readable and reachable |

Some stale-plan scenarios may require an independent test session or a second
settings window. Record exactly how the intervening change was made. Automated
fixtures cover concurrency boundaries separately and do not fill a real-UI row.

## Six v1.6.0 upgrade profiles

Use the official released v1.6.0 ZIP, verify its release-asset digest and prepare
each case with `prepare --case CASE --native-mac --parent OBSERVED_KEYMAP_ID`.
For `removed-ide`, supply `--removed-action ID` from an actual baseline collision
dump. For `explicit-old`, supply `--old-copy KEYSTROKE --old-history KEYSTROKE`
from its effective runtime bindings. The tool writes **seed XML**, not a record of IDE acceptance. Start v1.6.0,
verify the selected keymap/values in UI and runtime export, and quit cleanly.
`launch-gui` records the config snapshot after process exit. Then explicitly
record the observation with `accept-baseline` as below. Seed preparation or a
boolean acceptance marker cannot authorize a clone.

| Case | Baseline | Required candidate result before explicit restore |
| --- | --- | --- |
| pristine | Inherited defaults, no custom scheme | Only inherited defaults move to B |
| unrelated-only | Derived scheme edits another action only | Plugin defaults follow inheritance; unrelated edit remains |
| explicit-old | Explicit old Copy/History bindings | Explicit C/H choices remain; no automatic aliases added |
| custom | Different Copy/History keys and a mouse binding | Custom keyboard and mouse shortcuts remain |
| unassigned | Explicit empty Copy/History entries | Commands stay unassigned |
| removed-ide | Explicitly remove observed conflicting IDE action | IDE removal remains before/after restore; no full reset |

v1.6.0 declares History only on `$default` (`control alt H`), but IC 243's actual
`Mac OS X 10.5+` runtime maps it to **`meta alt H`** through platform inheritance.
The same dump maps Copy to `meta alt C`, while the IDE's CallHierarchy remains
`control alt H`. Thus descriptor text alone does not establish the effective old
binding or a collision. Use the observed values for each Rider/IDE lineage.

```bash
python3 scripts/shortcut-audit/audit.py accept-baseline \
  --profile BASELINE_PROFILE --run-directory BASELINE_PROFILE/gui-run-UUID \
  --export BASELINE_PROFILE/gui-run-UUID/keymaps-gui-TIMESTAMP.tsv \
  --observed-keymap OBSERVED_KEYMAP_ID --performer OPERATOR_NAME \
  --notes OBSERVATION_DESCRIPTION --confirm-observed \
  --gui-evidence BASELINE_PROFILE/gui-run-UUID/keymap.png \
  --gui-evidence BASELINE_PROFILE/gui-run-UUID/keymap.txt
python3 scripts/shortcut-audit/audit.py clone-upgrade \
  --source BASELINE_PROFILE --output NEW_CANDIDATE_PROFILE \
  --zip CANDIDATE_ZIP --sha256 CANDIDATE_SHA256 --commit INTEGRATED_MAIN_SHA
```

Acceptance checks that the GUI loaded v1.6.0 in the intended four profile paths,
had the expected project and keymap open, exported complete bindings from the
recorded PID/run within its lifetime, and exited normally. It verifies each seed's
keyboard/mouse/empty/deletion contract and binds the raw export, launch records,
PNG or native JPEG screenshot, accessibility text and unchanged exit config
snapshot by hashes. It also requires the installed harness to match both the
recorded artifact and the current exporter source hashes.
The operator attests to what the screenshot and UI actually showed; these hashes
provide integrity checks, not independent attestation against a fabricated set
of artifacts. Unit tests use explicitly synthetic evidence, never real GUI PASS.

Accepted baselines are frozen: the launcher refuses to reopen them, and cloning
revalidates all evidence and current config/product/harness hashes. Source and target
must be disjoint canonical paths (neither equal nor an ancestor of the other),
including symlink aliases. A target nested under the source is rejected before
any writes. Preserve the accepted original and work only in a new sibling tree.

The clone copies only the baseline config and installs the candidate product ZIP
in a new plugin directory; mutable system/cache/log directories are new. Preserve
the original baseline. Run S-preserve/S-return in each case and compare exact
source keymap XML and effective shortcuts before/after restore. Repeat the six
cases across required macOS IC/IDEA/Rider, recording the selected lineage.

For each fresh/candidate profile, record these introduction scenarios separately
from shortcut restoration. A second project is a separate local fixture project.

| Row | Scenario | Expected observation |
| --- | --- | --- |
| I-new | Fresh candidate profile, first valid project | One introduction; wording does not claim an upgrade |
| I-upgrade | Each of the six accepted v1.6.0 baselines, first candidate project | One introduction describing actual current bindings |
| I-custom | Explicit old/custom bindings, including long/markup-like display text | Bounded escaped display, no claim that defaults are active |
| I-unassigned | Explicit empty Copy/History | Current unassigned state shown accurately |
| I-projects | Open a second project immediately and after initial notification | No second introduction in the same application profile |
| I-restart | Quit cleanly and restart with introduced state | No repeated introduction |
| I-copy-notification | Disable success-copy notifications before first startup | Introduction policy remains independent |
| I-group | Disable the IDE notification group in a fresh profile | Normal IDE notification controls remain respected |
| I-settings | Introduction's settings button | Opens the nine-shortcut settings list |
| I-keymap | Introduction's Keymap button | Opens IDE Keymap settings without changing assignments |
| I-dismiss | Close/dismiss introduction | Balloon expires; no repeat on next project/restart |
| I-disposed | Close originating project before queued display/action | No display/claim or settings access through a disposed project |
| I-state | Inspect persisted state and service fixture evidence | Local `copySelectionShortcuts.xml` state, copied state/atomic claim and `RoamingType.DISABLED` accounted for separately |
| I-sync | Dedicated test account/profile Settings Sync, if available | Record original/derived keymap synchronization independently from non-roaming introduction state |

Settings Sync is exercised only with a dedicated test account/profile when
available. If unavailable, record the missing environment and leave this
scenario NOT RUN; never use a personal account or treat local restart as sync.

## Evidence and operator handoff

Each executed row needs: row ID, performer, UTC timestamp, main SHA, candidate
ZIP SHA-256, plugin version, IDE product/build, OS/build/desktop, architecture,
IME/input source, keymap ID/parent chain, baseline/profile identity, procedure,
expected result, actual result, verdict, logs and required screenshots. Include
clipboard bytes or an escaped exact value when relevant. Keep executable argv,
exit codes, actual test counts and CI URLs/artifact identity alongside the run.
Do not commit local profiles, logs or personal data to the product repository.

If GUI access fails, provide the operator the prepared profile, candidate hash,
native launcher command, project fixture and remaining row IDs. Ask for the
actual result and screenshot/clipboard evidence for each pending row, including
IME and modifier release details. Preserve the failure log and exact failed
operation. Leave those rows BLOCKED/NOT RUN until evidence arrives; a fixture,
Verifier result or success balloon alone is not a replacement.
