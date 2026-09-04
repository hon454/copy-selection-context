## Problem

The user approves the Context Collection feature set and overall composition, but the current tool window feels cramped and visually unbalanced. The equally weighted 2-by-3 button grid, tightly stacked status text, weak section separation, and uneven list/preview proportions make the workflow harder to scan.

This is a user-requested v1.5.0 refinement of #74, currently implemented in draft PR #92. Keep the existing collection, frozen-capture, formatting, copy, limit, and persistence contracts.

## Design direction

Use a restrained native JetBrains tool-window design: consistent insets and alignment, readable density, minimal borders, and clear primary/secondary actions. Organize the working surface into collection, selected capture, and final output. Preserve editor focus behavior and existing keyboard access; avoid decorative motion or additional product features.

## Acceptance criteria

- [ ] Replace the equally weighted six-button grid with a clear primary Copy All action and a compact, logically grouped set of collection-management actions. Preserve access to Remove, Clear All, Move Up, Move Down, and Format Settings, with accessible names/tooltips and clear disabled states.
- [ ] Apply consistent native spacing and alignment between controls, headings, summaries, and content. Visually distinguish collection, captured code, and final output without stacking heavy borders or adding decorative cards.
- [ ] Make collection rows easy to scan by path/range and capture identity; give timestamp and source state secondary emphasis while keeping them accessible. Preserve distinct same-location captures and stable snapshot labels.
- [ ] Place raw collection size and final formatted output size near the content they describe. Keep exact UTF-8 byte counts, relevant limits, include-code state, active format, and warning/calculating states discoverable and unambiguous.
- [ ] Improve initial list/captured-code/final-output proportions and splitter appearance. Resizing must keep all operations reachable and previews usable in narrow and normal-width tool windows, with scrolling where necessary.
- [ ] Verify actual IDE empty, populated, selected, and large-output warning states, plus keyboard focus and longer localized text. Use theme-aware native colors/fonts and check light/dark readability without changing shared behavior.
- [ ] Preserve existing functional tests and add focused regression coverage only where control wiring, focus, selection, or lifecycle behavior changes. Required #74 gates and final independent review must pass on the final head.
- [ ] Record actual before/after UI evidence, viewport/theme/locale/source SHA, and design rationale. Recapture all three final English screenshots after the design change; synchronize the capture guide and five README/listing materials with the final UI.

## Execution and review

Continue in the existing #74 implementation task/worktree and PR #92, since this polishes the same tool-window feature before merge. The implementation owner retains exclusive IDE/browser access until handback. Reuse the independent reviewer for the final code and visual evidence; preliminary approval of an earlier source head is not final acceptance.

The earlier overview PNG and preview-design-baseline JPEG are intermediate QA evidence, not final release assets. Publication remains subject to the existing separate user approval boundary.


## Post-merge verification status

PR #92 merged at `b70f8e27227f278adee6a30d60982542ad32b660` after independent repository source/assets/docs approval of `39edac08552a8e7dd6fecd4cc4c3af60be0b269b` and CI33864526229 SUCCESS. The issue was automatically closed by that merge.

Final integrated-main actual IDE verification is still pending. Preserve unchecked acceptance items and the in-progress label until that evidence is recorded. The native warning modal upload suitability also remains unverified. Automatic approval review rejected the attempted all-checkbox completion update for overstating completion while these checks remain outstanding; the remote issue body was not changed by that rejected operation.
