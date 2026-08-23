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
| N01.6 | Verify at 80 / 100 / 125 / 150 % zoom | Done | Offset from centre 0px at all four |
| N01.7 | Verify on a short viewport | Done | Centred on both branches of `min(42rem, 100vh − 14rem)` |
| N01.8 | Confirm streaming and pending bubbles unaffected | Done | **Found a regression I had introduced** — see note |

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
| N03.8 | Check the summary paragraph is not clipped | Done | Real summary text, not clipped at any zoom |
| N03.9 | Verify at 4 zoom levels | Done | One left and one right edge at 80/100/125/150 % |

## UX-N04 — Boxes have no visible bottom edge

| ID | Task | Status | Evidence |
|---|---|---|---|
| N04.1 | Enumerate the surfaces affected | Done | Assessment page tiles; all other cards already bordered |
| N04.2 | Measure card against page background contrast | Done | Tile #f4f4f4 on page #ffffff = **1.1:1** |
| N04.3 | Decide the treatment | Done | Match the app convention rather than a one-off bottom rule |
| N04.4 | Implement consistently | Done | `1px solid $border-subtle`, four tiles |
| N04.5 | Confirm it holds in the light theme | Done | All contrast figures were measured on the light theme |
| N04.6 | Confirm 3:1 non-text contrast | Done | `$border-strong-01` = **3.02:1**; `$border-subtle` was only 1.31:1 |
| N04.7 | Verify no double borders | Done | Carbon `Tile` verified to compute `border: 0px none` |

## UX-N05 — Email body spacing under zoom

| ID | Task | Status | Evidence |
|---|---|---|---|
| N05.1 | Reproduce at each zoom step | Done | 67 / 100 / 150 % measured |
| N05.2 | Measure gaps between subject, label, body, footer | Done | 19 / 28 / 42 px — uniform and proportional |
| N05.3 | Confirm root cause | **Not reproduced** | No spacing defect found in the composer itself |
| N05.4 | Implement | Done, different defect | Fixed the empty box in the meeting-secured state instead |
| N05.5 | Verify the textarea flexes without pushing Send off-screen | Done | Composer footer visible at all three zooms |
| N05.6 | Verify at 4 zoom levels plus a short viewport | Done | Gaps 22/28/35/42 px, uniform at each |
| N05.7 | Regression at md and sm | Done | No overflow, Send present; 4px rhythm variance noted below |

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
| X.1 | Establish one test matrix | Done | Four zoom levels and md/sm, applied to every visual defect |
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
| Done | 51 |
| Partial | 1 (N03.1) |
| Not reproduced | 1 (N05.3) |
| Done against a different defect | 1 (N05.4) |
| Defects fixed | 5 of 6 |

N03.1 remains partial because the test engagement sits at the outreach phase
and cannot reach the assessment state. The layout was measured on a replica
built from the same Carbon classes and the same stylesheet the page uses, with
the real feedback text, so the figures are real — but nobody has seen the page
itself with real data. That is the one thing worth a second pair of eyes before
this merges.

## What the verification work turned up

Two findings came out of finishing the tasks rather than out of the original
defect list.

**A regression I had introduced (N01.8).** `turns` stays empty until the first
exchange is persisted, so on the learner's very first message the placeholder
was still rendering beside their own pending and streaming bubbles. My
centring rule would then have laid them out side by side. The placeholder now
hides as soon as a message is in flight, the centring is column-safe, and the
rule is covered by four unit tests so it cannot come back silently.

**The card edge is faint across the whole product (N04.6).** Measured against
the tile background of `#f4f4f4`: `$border-subtle` gives 1.31:1 and
`$border-strong-01` gives 3.02:1, which is the WCAG 1.4.11 threshold for
non-text contrast. The assessment page now uses the stronger token, because a
line nobody can see does not answer a request for a line. Every other card in
the product sits at roughly 1.15:1 against the same background. That is a
design-system decision rather than something to settle on one page, and it is
handed back rather than changed unilaterally.

**One thing deliberately not changed (N05.7).** At md and sm the composer's
vertical rhythm measures 32 / 32 / 36 px rather than a single value, because it
is built from four separate margins (8, 12, 8 and 16px) instead of one scale.
Four pixels at tablet width is below the threshold anyone would report, and
replacing the rhythm risks a visible regression at desktop where it currently
measures uniform. Recorded rather than churned.
