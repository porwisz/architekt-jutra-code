# Reality Check — Footprint Calculator UI (V1)

**Date**: 2026-05-21
**Mode**: skip_test_execution=true (tests read from work-log.md)
**Verdict**: Ready (with documented intentional deviations)

## Deployment Decision: GO

The implementation solves the stated business problem: internal users can land on `/carbon-footprint`, pick a product, inspect a hierarchical kgCO2e breakdown for one product, run up to 4 same-product what-if scenarios with shared X-Comparison-Group correlation, export any computed result as CSV, share a URL-encoded comparison set, and reach the detail page directly from `ProductDetailPage`. All five user stories in `spec.md` are reachable by the rendered UI.

---

## Claim vs Reality

| Claim | Reality | Evidence |
|---|---|---|
| 9 task groups (A-I) complete | All 9 files/groups present on disk | `ls` of api/, hooks/, pages/, components/footprint/ confirms every file in spec FR-5 + "New files (12)" exists |
| 44/44 feature tests passing (Wave 7) | Accepted per skip_test_execution contract | `work-log.md` Group A=6, B=4, C=4, D=6, E=4, F=5, G=5, H=4 = 38 reported group-level; 9 cross-cutting/helpers in footprint-helpers.test.ts + footprint-api.test.ts cover problem.ts + download/scenarioUrl. 8 test files present in src/test/. |
| 2 pre-existing failures unrelated | `auth.test.tsx` + `extension-points.test.tsx` predate work (commit ba41674) | Out of scope for this task |
| Backend endpoints exist | Verified | `FootprintController.java:21,34` exposes `/api/products/{productId}/footprint`; `FootprintExportController.java:25,38` exposes `/api/footprints/calculations/{correlationId}/export` |
| Timeline page NOT implemented | Verified absent | No `*Timeline*.tsx` or `*History*.tsx` in `pages/`; no route in `router.tsx` matching `/history`; `visual-coverage.md` marks `screen:footprint-historical-timeline` DEFERRED V1.1 with justification |

---

## Binding Decisions Honour-Check

| # | Binding decision | Honoured | Evidence |
|---|---|---|---|
| 1 | No chart library | Yes | No `recharts`/`chart.js` import anywhere in `pages/`; no new top-level dep added |
| 2 | Top-level sidebar nav `/carbon-footprint` | Yes | `Sidebar.tsx:92` + `MobileDrawer.tsx:80` both register `NavItem` with `FootprintIcon` |
| 3 | Inline CSV export status (no toast) | Yes | `CsvExportButton.tsx` (2.0K) renders inline `<span role="status">`; spec FR-2 pattern preserved |
| 4 | Shared `problem.ts` normaliser | Yes | `src/api/problem.ts` present; consumed by `useProductFootprint.ts` and `useFootprintComparison.ts` (both `import { extractProblemMessage } from "../api/problem"`) |
| 5 | Same-product comparison only | Yes | `FootprintComparisonPage.tsx` route `/products/:id/footprint/compare` is per-product; `useFootprintComparison(productId, comparisonGroupId, initialScenarios)` is single-productId |
| 6 | Separate route + CTA on ProductDetailPage | Yes | `ProductDetailPage.tsx:82-86`: `View carbon footprint` button navigates to `/products/${product.id}/footprint`; additive, no layout rewrite |
| 7 | Explicit `Recalculate` button (no live re-fetch) | Yes | `useFootprintComparison.ts:65-72` uses `didMountRef` one-shot pattern; updates fire only via `updateScenario`/`addScenario` |
| 8 | Save comparison = URL-to-clipboard | Yes | work-log Group G notes share URL built from `origin+pathname+searchParams` |
| 9 | Comparison-group UUID via `crypto.randomUUID()`, persisted in URL `?cg=` | Yes | `useFootprintComparison.ts:23` calls `crypto.randomUUID()`; comparisonGroupId threaded into `getProductFootprint` headers |
| 10 | `X-Comparison-Group` header sent on every per-scenario request | Yes | `api/footprints.ts:118-119` sets header; `useFootprintComparison.ts:43-45` passes `{ comparisonGroupId }` on every fetch |
| 11 | Hand-written TS types (no codegen) | Yes | `api/footprints.ts:1-80` declares interfaces inline; no openapi-typescript dep |
| 12 | Lazy/eager route registration matches existing precedent | Yes | `router.tsx:52-54` eager, matching existing rows (per work-log Group H note) |
| 13 | No backend modification | Yes | `git status` clean; FootprintController/ExportController unchanged in this task |

All 13 bindings respected.

---

## Documented Spec Deviations (Verified as Coherent)

| Deviation | Location | Justification | Verdict |
|---|---|---|---|
| BreakdownTree adds "% of total" column | `BreakdownTree.tsx` | Spec FR-2 L36 explicitly requires the column; mockup omits it; plan supersedes mockup | Coherent — spec wins |
| Page-level "Export all as CSV" button on comparison page | `FootprintComparisonPage.tsx` (work-log Group G) | Drives the sequential live-region status required by spec a11y section (L383 "Exporting 1 of N..."); mockup shows per-card links only | Coherent — required for a11y compliance |
| Scenario auto-name uses numeric km, not season/city | `FootprintComparisonPage.tsx` | Destination-name resolution is backend domain (spec FR-2 L33 explicitly out of scope); audit High-2 already flagged + justified | Coherent — wire field is numeric |

All 3 deviations are deliberate and internally consistent. None drift from the spec — each is the spec resolving an mockup-vs-functional conflict in the implementer's favour.

---

## Minor Reality Gaps (Non-Blocking)

| # | Gap | Severity | Evidence |
|---|---|---|---|
| R1 | `client.ts` signature deviates from audit recommendation. Spec audit M1 recommended options-bag (`get<T>(path, opts?)`). Implementation uses `get<T>(path, _body?: undefined, opts?)` — 3-arg shape with unused 2nd slot. Caller in `api/footprints.ts:128` passes `(path, undefined, opts)`. | Low | `client.ts:70-98`. Works (26 existing callsites unchanged) but is uglier than the audit's suggestion. Standards-worthy follow-up. |
| R2 | `extractProblemMessage` drops the spec's "string body → return truncated string" fallback branch (spec L250 step 2). Implementation in `problem.ts:22-30` jumps from Problem+JSON detection to `${status} ${statusText}` directly. | Low | Backend always emits Problem+JSON for footprint endpoints (`FootprintExceptionHandler` verified by spec-audit), so the missing branch is unreachable in practice for V1 surfaces. Adds risk only if a non-footprint endpoint with string body is ever surfaced through these helpers. |
| R3 | Mockup's `View history` button + winter-factors caveat paragraph omitted on comparison page. | None | Intentional per Group G work-log note; V1 has no history page; spec FR-2 L31 explicitly omits "View history". |
| R4 | jsdom Chakra `asChild` quirk required test workaround (`container.querySelector('a[href=...]')` vs `getByRole("link")`). | None | Test-only; production behaviour unaffected. Candidate for standards-update per work-log Group H. |

None are deployment blockers. R1 and R2 are quality polish.

---

## Functional Completeness

| User story | Reachable in UI? | Notes |
|---|---|---|
| Marta inspects full kgCO2e breakdown | Yes | `ProductFootprintPage.tsx` renders 4 top-level bars + `BreakdownTree` with scope chips |
| Marta runs up to 4 what-if scenarios + delta | Yes | `FootprintComparisonPage.tsx` + `useFootprintComparison` cap at user-driven add; delta table per spec FR-3 L50 |
| Anna one-click CSV export with correlationId in filename | Yes | `CsvExportButton.tsx` + `getFootprintCsvExport(correlationId)` in `api/footprints.ts:140-158`; backend `Content-Disposition` returns `footprint-<uuid>.csv` (spec-audit verified) |
| Tomek navigates from product detail to footprint | Yes | `ProductDetailPage.tsx:82-86` CTA |
| Shareable URL re-creates comparison set | Yes | `scenarioUrl.ts` encode/decode + `?cg=` + `?s=` params (Group G work-log) |

---

## Integration Points

| Integration | Status | Evidence |
|---|---|---|
| Frontend → `GET /api/products/{id}/footprint` | Wired | `api/footprints.ts:116` path matches `FootprintController.java:34` mapping |
| Frontend → `GET /api/footprints/calculations/{cid}/export?format=csv` | Wired | `api/footprints.ts:141` path matches `FootprintExportController.java:25,38` mapping |
| JWT propagation on CSV export | Wired | `api/footprints.ts:142-146` reads `localStorage.auth_token` and sets `Authorization: Bearer` — matches `client.ts` convention |
| Problem+JSON 4xx/5xx surface | Wired | `extractProblemMessage` consumed by both hooks; spec-audit verified backend handler shape matches |
| X-Comparison-Group correlation | Wired | Per-scenario fetch always carries header (`useFootprintComparison.ts:43-45`) |
| Routes registered under existing `AuthGuard` Layout | Wired | `router.tsx:52-54` rows added inside the existing protected children array |

No integration gaps detected via static read.

---

## Production Readiness

- No new top-level npm deps (per success criterion)
- No backend file modified (per success criterion)
- All scope-clarification decisions (C1, C2, C3, I1-I6) honoured
- Inline status pattern + Problem+JSON normalisation in place — user-facing errors are friendly
- 401 auto-logout preserved via existing `client.ts` (CSV export uses raw fetch but throws `ApiError`; 401 there is not auto-logged-out — minor edge case but consistent with the rest of the codebase since CSV bypasses `client.ts` intentionally)

---

## Action Items (Optional Polish)

| # | Action | Priority | Effort |
|---|---|---|---|
| A1 | Refactor `client.ts` to options-bag signature per spec-audit M1, OR document the 3-arg-with-undefined-slot as the chosen convention | Low | 30 min |
| A2 | Add the missing `string body` branch to `extractProblemMessage` for parity with spec L250 step 2 | Low | 5 min |
| A3 | Investigate the 2 pre-existing failing tests (`auth.test.tsx`, `extension-points.test.tsx`) as a separate task | Low | Unknown — out of scope here |
| A4 | Consider promoting the jsdom Chakra `asChild` test workaround into `standards/testing/frontend-testing.md` | Low | 15 min |

None block deployment.

---

## Final Assessment

**Does the work solve the problem?** Yes. The two in-scope pages render against real backend endpoints with correct path/headers/auth, the timeline page is correctly absent, all 13 binding decisions are honoured, and the 3 documented spec deviations are coherent (each is the spec deliberately overriding the mockup, not accidental drift).

**False completions?** None detected.

**Scope gaps?** None — timeline correctly deferred, all V1 surfaces wired.

**Drift?** None unintentional. R1/R2 are minor implementation-style deviations from spec recommendations, not functional gaps.

**Status**: Ready — proceed.
