## Problem

The user approves the Context Collection feature set and overall composition, but the current tool window feels cramped and visually unbalanced. The equally weighted 2-by-3 button grid, tightly stacked status text, weak section separation, and uneven list/preview proportions make the workflow harder to scan.

This is a user-requested v1.5.0 refinement of #74, implemented and merged through PR #92. Keep the existing collection, frozen-capture, formatting, copy, limit, and persistence contracts.

## Design direction

Use a restrained native JetBrains tool-window design: consistent insets and alignment, readable density, minimal borders, and clear primary/secondary actions. Organize the working surface into collection, selected capture, and final output. Preserve editor focus behavior and existing keyboard access; avoid decorative motion or additional product features.

## Acceptance criteria

- [x] Replace the equally weighted six-button grid with a clear primary Copy All action and a compact, logically grouped set of collection-management actions. Preserve access to Remove, Clear All, Move Up, Move Down, and Format Settings, with accessible names/tooltips and clear disabled states.
- [x] Apply consistent native spacing and alignment between controls, headings, summaries, and content. Visually distinguish collection, captured code, and final output without stacking heavy borders or adding decorative cards.
- [x] Make collection rows easy to scan by path/range and capture identity; give timestamp and source state secondary emphasis while keeping them accessible. Preserve distinct same-location captures and stable snapshot labels.
- [x] Place raw collection size and final formatted output size near the content they describe. Keep exact UTF-8 byte counts, relevant limits, include-code state, active format, and warning/calculating states discoverable and unambiguous.
- [x] Improve initial list/captured-code/final-output proportions and splitter appearance. Resizing must keep all operations reachable and previews usable in narrow and normal-width tool windows, with scrolling where necessary.
- [x] Verify actual IDE empty, populated, selected, and large-output warning states, plus keyboard focus and longer localized text. Use theme-aware native colors/fonts and check light/dark readability without changing shared behavior.
- [x] Preserve existing functional tests and add focused regression coverage only where control wiring, focus, selection, or lifecycle behavior changes. Required #74 gates and final independent review must pass on the final head.
- [ ] Record actual before/after UI evidence, viewport/theme/locale/source SHA, and design rationale. Recapture all three final English screenshots after the design change; synchronize the capture guide and five README/listing materials with the final UI.

## Execution and review

Continue in the existing #74 implementation task/worktree and PR #92, since this polishes the same tool-window feature before merge. The implementation owner retains exclusive IDE/browser access until handback. Reuse the independent reviewer for the final code and visual evidence; preliminary approval of an earlier source head is not final acceptance.

The earlier overview PNG and preview-design-baseline JPEG are intermediate QA evidence, not final release assets. Publication remains subject to the existing separate user approval boundary.



## Final functional verification — 2026-09-05

PR #92 merged at `b70f8e27227f278adee6a30d60982542ad32b660` after exact-head independent repository approval of `39edac08552a8e7dd6fecd4cc4c3af60be0b269b` and successful required CI. The integrated main-equivalent run passed all required gates, 402 tests (360 pure + 42 platform), and all three configured Plugin Verifier runs.

The coordinator subsequently executed the actual IDE functional matrix: keyboard/read-only viewers, raw/output boundaries, template/blank/combined warnings, legacy actions, history/status/analytics, same-process project close/reopen, and two live project windows. Final independent functional audit PASS verified 169 manual evidence file sizes/hashes, unchanged previous evidence, and 32 owner visual originals. Actual UI observations, registered controlled failure/order fixtures and prior owner narrow/localized/light/dark evidence are explicitly separated. No additional functional execution or product defect remains in this reviewed scope.

The final screenshot/material checkbox remains open: actual before/after and three English images exist, but the native warning modal exports at 370×179 while overview/preview are 1204×768. A consistent full-frame warning capture and its final provenance/material review remain unresolved. Supported native capture attempts timed out; no generated/composited substitute or public listing change was made. This is not full release readiness. Keep the in-progress label and the owner/reviewer available for this asset follow-up.

Evidence and limitations: [integrated verification at coordination431a07c](https://github.com/hon454/copy-selection-context/blob/431a07cba0ad2ce59601e08a9db633a07c2b910a/docs/releases/v1.5.0-integrated-verification.md), plus local `build/evidence/integrated-b70f8e2/independent-review/manual-final-20260904/`. Publication remains unexecuted and requires separate approval.


Earlier all-checkbox completion was rejected by automatic approval review before actual integrated verification. That operation was not executed or bypassed. The current partial update follows the subsequently completed actual matrix and final independent functional PASS, and deliberately retains the unresolved screenshot criterion.
