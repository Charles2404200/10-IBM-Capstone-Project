# UX Defect Round 3 — Task Breakdown and Status

| | |
|---|---|
| **Owner** | Vince Tran |
| **Source** | Christine's round 3 review list |
| **Branch** | `fix/christine-ux-round-3` |
| **Pull request** | #20 |
| **Prepared** | 23 August 2026 |

Six defects broken into 54 tasks. Status is recorded honestly: a task marked
**Partial** or **Not done** is one I did not complete, not one I completed
loosely. Three fixes could only be verified on replicas of the exact DOM and
CSS the pages produce, because the test engagement sits at the outreach phase
and cannot reach the assessment or meeting-secured states.

**Legend** — Done · Partial · Not done

---

## UX-N01 — Live Client Meeting: pre-start text not vertically centred

| ID | Task | Status | Evidence |
|---|---|---|---|
| N01.1 | Reproduce with zero turns | Done | Replica of the transcript panel |
| N01.2 | Measure panel height against text offset | Done | 441px panel, text centre 128px above panel centre |
| N01.3 | Confirm root cause | Done | Fixed `margin: 4rem 0` on the placeholder |
| N01.4 | Implement centring for the empty state only | Done | `:has()` scoped rule on the viewport |
| N01.5 | Regression: populated transcript stays top-aligned | Done | First turn at 16px, `scrollTop` 0 |
| N01.6 | Verify at 80 / 100 / 125 / 150 % zoom | Not done | Centring is height-independent, but not measured at each step |
| N01.7 | Verify on a short viewport | Partial | Replica used the real `min(42rem, 100vh − 14rem)` expression |
| N01.8 | Confirm streaming and pending bubbles unaffected | Not done | Requires a live meeting in progress |

## UX-N02 — Outreach: client monogram renders as an oval

| ID | Task | Status | Evidence |
|---|---|---|---|
| N02.1 | Reproduce with a long company name | Done | Christine's "AeroVector Aviation Atlas Operations Opportunity" |
| N02.2 | Measure the distortion | Done | 41.1 × 52 px, ratio 0.791 |
| N02.3 | Confirm root cause | Done | Flex item with fixed width and no shrink guard |
| N02.4 | Apply the shrink guard | Done | `flex: 0 0 auto` |
| N02.5 | Add `aspect-ratio` as a second guarantee | Done | Shape holds even if the box model changes |
| N02.6 | Verify against the longest catalogue name | Done | Plus one deliberately longer than any real name |
| N02.7 | Verify the two-line name case from the screenshot | Done | 52 × 52, ratio 1.000 |
| N02.8 | Audit sibling circular elements | Done | Six found across four files |
| N02.9 | Fix those sharing the defect | Done | `.sectionNumber` and `.hudDot` guarded; three already were |

## UX-N03 — Engagement Assessment: boxes not aligned

| ID | Task | Status | Evidence |
|---|---|---|---|
| N03.1 | Reproduce | Partial | Replica; engagement cannot reach the assessment state |
| N03.2 | Measure left and right edges of every card | Done | Direct child 80px, nested-grid card 96px |
| N03.3 | Confirm root cause | Done | Nested Carbon Grid re-applies its own 1rem gutter |
| N03.4 | Decide the correction | Done | Replace nesting rather than neutralise the gutter |
| N03.5 | Implement | Done | Plain CSS grid, `auto-fit` with a 20rem minimum |
| N03.6 | Re-measure for one left and one right edge | Done | All cards at left 80px, right 1360px |
| N03.7 | Verify collapse at md and sm | Done | Two columns to 660px, one column below |
| N03.8 | Check the summary paragraph is not clipped | Not done | Needs the live page with real feedback text |
| N03.9 | Verify at 4 zoom levels | Not done | Same blocker |

## UX-N04 — Boxes have no visible bottom edge

| ID | Task | Status | Evidence |
|---|---|---|---|
| N04.1 | Enumerate the surfaces affected | Done | Assessment page tiles; all other cards already bordered |
| N04.2 | Measure card against page background contrast | Partial | Page background computes transparent, so the ratio was not meaningful |
| N04.3 | Decide the treatment | Done | Match the app convention rather than a one-off bottom rule |
| N04.4 | Implement consistently | Done | `1px solid $border-subtle`, four tiles |
| N04.5 | Confirm it holds in the light theme | Partial | Uses a theme token, so it follows the theme by construction |
| N04.6 | Confirm 3:1 non-text contrast | Not done | Blocked by N04.2 |
| N04.7 | Verify no double borders | Done | Carbon `Tile` verified to compute `border: 0px none` |

## UX-N05 — Email body spacing under zoom

| ID | Task | Status | Evidence |
|---|---|---|---|
| N05.1 | Reproduce at each zoom step | Done | 67 / 100 / 150 % measured |
| N05.2 | Measure gaps between subject, label, body, footer | Done | 19 / 28 / 42 px — uniform and proportional |
| N05.3 | Confirm root cause | **Not reproduced** | No spacing defect found in the composer itself |
| N05.4 | Implement | Done, different defect | Fixed the empty box in the meeting-secured state instead |
| N05.5 | Verify the textarea flexes without pushing Send off-screen | Done | Composer footer visible at all three zooms |
| N05.6 | Verify at 4 zoom levels plus a short viewport | Partial | Three zoom levels measured, not four |
| N05.7 | Regression at md and sm | Not done | Not exercised |

One hypothesis was formed and then disproved by measurement. The item is
carried forward pending Christine's zoom level and a screenshot of the
composer itself. The defect visible in her screenshot — 504px of box around
88px of content in the meeting-secured state — is fixed.

## UX-N06 — Progress bar: stage labels wrap to a second line

| ID | Task | Status | Evidence |
|---|---|---|---|
| N06.1 | Reproduce and find the failing width band | Done | Labels revealed above 1440px viewport |
| N06.2 | Measure the natural width of all ten labels | Done | 1014px required, 814px available |
| N06.3 | Confirm root cause | Done | Viewport breakpoint was a guess and was wrong |
| N06.4 | Decide the correction | Done | Query the stepper's own container, not the viewport |
| N06.5 | Implement | Done | `container-type: inline-size` plus `flex-wrap: nowrap` |
| N06.6 | Sweep widths to prove no band wraps | Done | 400 → 1400px; labels switch on at 1024px |
| N06.7 | Verify the current stage stays identifiable | Done | Current label always shown |
| N06.8 | Verify bar height constant | Done | 21px at every width, and across all eight pages |

## Cross-cutting

| ID | Task | Status | Evidence |
|---|---|---|---|
| X.1 | Establish one test matrix | Partial | Applied per defect rather than uniformly |
| X.2 | Full-page regression sweep | Done | Eight pages render, no console errors |
| X.3 | Before/after evidence per item | Done | Measurements recorded in each commit message |
| X.4 | Lint, type-check, test type-check, tests, build | Done | All pass; 73 tests |
| X.5 | One commit per defect | Done | Six commits |
| X.6 | PR with evidence and handoff notes | Done | PR #20 |

---

## Summary

| | Count |
|---|---|
| Tasks | 54 |
| Done | 40 |
| Partial | 6 |
| Not done | 6 |
| Not reproduced | 1 (N05.3) |
| Done against a different defect | 1 (N05.4) |
| Defects fixed | 5 of 6 |

The twelve incomplete tasks fall into two groups: verification that needs an
account with a completed engagement or a live meeting, and zoom-level sweeps I
did not run to the full four steps. Neither group blocks the fixes; both would
strengthen the evidence.
