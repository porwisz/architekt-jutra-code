# Workflow Summary — Footprint Calculator UI (V1)

**Status**: ✅ Completed (passed with non-blocking issues)
**Started**: 2026-05-20 (handoff from product-design `2026-05-20-footprint-calc-engine`)
**Completed**: 2026-05-21
**Risk level at intake**: medium-low → resolved at verification
**Backend dependency**: completed task `2026-05-20-footprint-calc-engine` (V1 backend already shipped)

## What was built

V1 frontend for the carbon footprint calculation engine. Three new screens were originally scoped; the historical timeline was deferred to V1.1 (no backend history endpoint), leaving:

- **Product Footprint Detail** (`/products/:id/footprint`) — product card sidebar + context bar (Date / Destination km / Storage days / Unit / explicit `Recalculate` button) + summary card (total kgCO₂e + per-100g + strict/lenient badge + monospace correlationId) + 4 top-level scope bars + hierarchical breakdown table + CSV export with inline status.
- **Footprint Comparison** (`/products/:id/footprint/compare`) — same-product, multi-scenario (1–4 cards). URL-driven (`?cg=<uuid>&s=<base64>...`), `+ Add scenario (up to 4)`, best-of-N highlight + `−X% vs A` delta. Delta table with `±X.XX` colour-coded values. `Save comparison` copies shareable URL to clipboard. Sequential per-scenario CSV export with `Stopped at n/N: <detail>` halt-and-announce live region.
- **Carbon Footprint landing** (`/carbon-footprint`) — debounced product picker (300 ms) → routes to per-product detail.
- **Cross-cutting helpers** — `src/api/problem.ts` (RFC 7807 normaliser), `src/utils/download.ts` (blob → file), `src/utils/format.ts` extensions (`formatCO2eKg`, `formatPer100g`, `formatRelativeDate`, extracted `formatPrice`), `src/utils/scenarioUrl.ts` (base64 encode/decode), `FootprintIcon` (lucide `Leaf`).
- **Shared footprint components** — `ScopeChip`, `ScopeBar`, `BreakdownTree` (semantic `<table>` + `aria-expanded`/`aria-controls`), `CsvExportButton` (live region status).
- **Navigation wiring** — Sidebar + MobileDrawer entry, Header breadcrumb mappings, ProductDetailPage `View carbon footprint` CTA.
- **`api/client.ts` minimal extension** — optional 3rd-positional `opts?: { headers? }` on all verbs with **bearer-wins** filter (caller-supplied `Authorization` always stripped — security improvement).

## Phase trail

| Phase | Output | Outcome |
|---|---|---|
| 1 Codebase analysis | `analysis/codebase-analysis.md` + `analysis/clarifications.md` | risk medium-low; 4 technical decisions resolved (recharts→deferred, top-level sidebar, inline status, shared problem.ts) |
| 2 Gap analysis | `analysis/gap-analysis.md` + `analysis/scope-clarifications.md` | UI-only additive, ui_heavy=true, 3 critical + 6 important IA decisions resolved |
| 4 UI mockup gen | **SKIPPED** — 3 external mockups already in `design-context/` from product-design handoff | n/a |
| 5 Specification | `implementation/spec.md` (450L, 9 FRs) + `analysis/requirements.md` | implementation-ready; 13 binding decisions captured |
| 6 Spec audit | `verification/spec-audit.md` | pass-with-concerns; 0 critical / 2 high / 4 medium / 3 low — all High + 3 Medium fixed inline before planning |
| 7 Implementation plan | `implementation/implementation-plan.md` (327L, 9 task groups, 7 waves) + `implementation/visual-coverage.md` (2/3 screens covered; timeline deferred) | plan green-lit |
| 8 Implementation | code + `implementation/work-log.md` | 9/9 task groups complete (parallel waves W3 {C∥D}, W4 {E∥F}); 44 feature tests, 0 regressions; inline fix during Group I (CSV sequential-error wording corrected to spec-compliant "Stopped at n/N: <detail>") |
| 11 Verification (4 reviewers + completeness) | `verification/implementation-verification.md` + 4 sub-reports | passed-with-issues; 0 critical / 10 warnings (all fixable). Post-verification fix loop applied: TS type fix + 14 inline-hex → Chakra tokens |
| 12/13 E2E + user docs | **SKIPPED** per user choice at Phase 10 | n/a |
| 14 Finalisation | this `SUMMARY.md` | task closed |

## Test results

```
cd src/main/frontend && npx vitest run
→ 77 passed, 2 failed (auth.test.tsx, extension-points.test.tsx)
```

The 2 failures are **pre-existing**, dating to commit `ba41674` (Module 03 / Module 02 implementations), and were already failing before this task — flagged for separate triage. All 44 feature tests (8 test files) pass: `footprint-helpers` (8), `footprint-api` (5), `useProductFootprint` (4), `footprint-components` (7), `useFootprintComparison` (5), `ProductFootprintPage` (4), `FootprintComparisonPage` (7), `CarbonFootprintLandingPage` (4).

## Files modified or created

- **New code** (under `src/main/frontend/`):
  - `src/api/problem.ts`, `src/api/footprints.ts`
  - `src/utils/download.ts`, `src/utils/scenarioUrl.ts`
  - `src/hooks/useProductFootprint.ts`, `src/hooks/useFootprintComparison.ts`
  - `src/components/footprint/{ScopeChip,ScopeBar,BreakdownTree,CsvExportButton}.tsx`
  - `src/pages/{CarbonFootprintLandingPage,ProductFootprintPage,FootprintComparisonPage}.tsx`
  - `src/test/{footprint-helpers,footprint-api,useProductFootprint,footprint-components,useFootprintComparison,ProductFootprintPage,FootprintComparisonPage,CarbonFootprintLandingPage}.test.{ts,tsx}` (8 test files)
- **Modified**:
  - `src/api/client.ts` — `+RequestOpts`, optional 3rd-positional `opts`, bearer-wins `mergeHeaders`
  - `src/utils/format.ts` — `+formatCO2eKg`, `+formatPer100g`, `+formatRelativeDate`, `+formatPrice` (extracted from pages)
  - `src/components/shared/Icons.tsx` — `+FootprintIcon` in `ICON_MAP`
  - `src/theme/index.ts` — `+scope1/2/3/scopeMixed` semantic tokens (bg/fg)
  - `src/components/layout/{Sidebar,MobileDrawer,Header}.tsx` — nav entry + breadcrumb mappings
  - `src/router.tsx` — 3 routes registered under existing protected `Layout`
  - `src/pages/ProductDetailPage.tsx` — `View carbon footprint` CTA button
  - `src/pages/ProductListPage.tsx` — uses shared `formatPrice`
- **Untouched**: backend (Java, Liquibase, jOOQ), build pipeline, `package.json` (recharts dropped with timeline deferral — zero new runtime deps)

## Verification verdict

| Reviewer | Verdict | Findings |
|----------|---------|----------|
| Completeness | passed_with_issues → resolved | 0 critical / 3 warnings (inline-hex — fixed) / 1 info |
| Code review | passed_with_issues | 0 critical / 7 warnings / 8 info (deferred to follow-up PR) |
| Pragmatic review | passed | 0 critical / 0 high / 3 medium / 4 low |
| Production readiness | **GO** (Low risk) | 0 blockers / 2 concerns (both pre-existing platform gaps) |
| Reality check | **GO (Ready)** | All 5 user stories reachable; integration verified; 0 false completions |

## Known follow-ups (deferred — out of V1 scope)

1. **Historical timeline page** (V1.1) — requires new `GET /api/products/{productId}/footprint/history` endpoint.
2. **Code-review warnings** (cleanup PR): W1 (URL-sync exhaustive-deps), W3/W4 (hook contracts: stabilise `useProductFootprint` query dep, ref-iterate initial scenarios), W5 (`tagBreakdown` non-mutating, one-line fix), W6 (consolidate CSV export through `client.ts` for uniform 401 redirect), W7 (in-flight guards on exports + clipboard await), I5 (per-100g unit suffix on comparison page when scenario is PER_100G).
3. **Pragmatic simplifications**: replace per-card export with `<CsvExportButton>` (−15 LOC); decide on `problem.ts` location (fold into `client.ts` or formalise via standards-update).
4. **Cross-product comparison** — different SKUs in one comparison view (intentionally V1.1+).
5. **Frontend observability** — platform Sentry/RUM integration (pre-existing platform gap, not introduced here).
6. **Toast / notification system** — V1 chose inline status; revisit if multiple features need it.
7. **Named-destination dropdown** — requires backend address resolver; V1 uses raw numeric km.
8. **Mockup "View history" button on detail page** — re-enable when timeline ships.
9. **Pre-existing failing tests** (`auth.test.tsx`, `extension-points.test.tsx`) — predate this work; separate triage.

## Commit message template

```
feat(footprint-ui): add carbon footprint UI (detail + same-product comparison)

V1 frontend for the already-shipped carbon footprint calculation engine.
Two pages ship now (product footprint detail + same-product scenario
comparison) plus a top-level sidebar landing; the historical timeline
mockup is deferred to V1.1 pending a backend history endpoint.

- src/api/problem.ts: shared RFC 7807 Problem+JSON normaliser
- src/api/footprints.ts: typed FootprintResponse + getProductFootprint
  + getFootprintCsvExport + recursive structural tagBreakdown
- src/api/client.ts: additive optional opts.headers on get/post/put/patch/delete
  with bearer-wins (caller-supplied Authorization is stripped — security
  improvement)
- src/hooks/{useProductFootprint,useFootprintComparison}.ts: data hooks
  mirroring the existing useProducts shape; comparison hook orchestrates
  N parallel per-scenario fetches sharing one X-Comparison-Group UUID
- src/components/footprint/{ScopeChip,ScopeBar,BreakdownTree,CsvExportButton}
  .tsx: presentational primitives; BreakdownTree is a semantic <table>
  with <button aria-expanded aria-controls> for keyboard expand/collapse
- src/pages/{ProductFootprintPage,FootprintComparisonPage,
  CarbonFootprintLandingPage}.tsx: 3 pages, all behind existing AuthGuard
- src/components/layout/{Sidebar,MobileDrawer,Header}.tsx: Carbon Footprint
  nav entry + breadcrumb mappings
- src/router.tsx: 3 routes under existing protected Layout
- src/pages/ProductDetailPage.tsx: View carbon footprint CTA
- src/utils/{download,scenarioUrl}.ts + format.ts extensions
- src/theme/index.ts: scope1/2/3/scopeMixed semantic tokens
- src/test/footprint-*.test.{ts,tsx} (8 files, 44 tests)

Backend untouched; no Liquibase; no new runtime deps (recharts dropped
with timeline deferral).

Closes the V1 frontend portion of
.maister/tasks/development/2026-05-20-footprint-calc-engine.

Tests: 44/44 feature tests pass; full suite 77/79
(2 pre-existing failures in auth.test.tsx + extension-points.test.tsx
unrelated, dating to commit ba41674).
```

## Next steps

1. Review the spec/plan/work-log/verification reports under `.maister/tasks/development/2026-05-20-footprint-calc-engine-ui/` before commit.
2. Open a PR referencing this SUMMARY and `spec.md`.
3. File V1.1 follow-up tasks (timeline page; code-review warnings cleanup; pragmatic simplifications; platform Sentry; pre-existing failing-test triage).
