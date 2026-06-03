# Work Log

## 2026-05-20T00:00:00Z — Implementation Started

**Total Steps**: ~60 across 9 task groups (A–I)
**Task Groups**: A (helpers), B (api/footprints), C (useProductFootprint), D (shared components), E (ProductFootprintPage), F (useFootprintComparison), G (FootprintComparisonPage), H (landing + nav + routes + CTA), I (test review)
**Wave plan**: W1 {A} → W2 {B} → W3 {C ∥ D} → W4 {E ∥ F} → W5 {G} → W6 {H} → W7 {I}
**Validation**: every group has `Files to Modify` and `Dependencies` — parallel waves enabled.

## Standards Reading Log

### Loaded Per Group
(Entries added as groups execute)

## Group A — Cross-cutting Helpers — COMPLETE
**Steps**: A.1–A.9 all completed
**Standards Applied**:
- From plan: global/minimal-implementation, global/error-handling, frontend/components, frontend/css, testing/frontend-testing
- From INDEX.md: global/coding-style, global/commenting
- Discovered: none
**Tests**: 6/6 passed (`vitest run src/test/footprint-helpers.test.ts`)
**Files Modified**:
- new: src/api/problem.ts, src/utils/download.ts, src/utils/scenarioUrl.ts, src/test/footprint-helpers.test.ts
- modified: src/api/client.ts (+RequestOpts, 3rd-positional opts, Bearer-wins merge), src/utils/format.ts (+formatCO2eKg/formatPer100g/formatRelativeDate/formatPrice extraction), src/components/shared/Icons.tsx (+FootprintIcon), src/theme/index.ts (+scope1/2/3/mixed semantic tokens), src/pages/ProductListPage.tsx + src/pages/ProductDetailPage.tsx (import shared formatPrice)
**Notes**:
- formatCO2eKg rounding required a roundHalfUp helper.
- Pre-existing failures in auth.test.tsx + extension-points.test.tsx (unrelated) flagged for Group I triage.

## Group B — api/footprints.ts — COMPLETE
**Steps**: B.1–B.7 all completed
**Standards Applied**:
- From plan: global/minimal-implementation, global/error-handling, frontend/components, testing/frontend-testing
- Discovered: global/commenting
**Tests**: 4/4 passed
**Files Modified**:
- new: src/api/footprints.ts, src/test/footprint-api.test.ts
**Notes**:
- Backend Unit enum verified: KG_CO2, KG_CO2_PER_100G match spec.
- tagBreakdown idempotent via in-place mutation.
- getFootprintCsvExport uses raw fetch (smaller change than extending client; Bearer-token read mirrors client.ts).

## Wave 3 — Groups C ∥ D — COMPLETE

### Group C — useProductFootprint hook
**Tests**: 4/4 passed
**Files**: new src/hooks/useProductFootprint.ts, src/test/useProductFootprint.test.ts
**Note**: Group E callers should `useMemo` the query object to avoid render loops.

### Group D — Shared footprint components
**Tests**: 6/6 passed
**Files**: new src/components/footprint/{ScopeChip,ScopeBar,BreakdownTree,CsvExportButton}.tsx + src/test/footprint-components.test.tsx
**Visual compliance**: detail mockup honoured. Deviation: BreakdownTree includes "% of total" column (spec FR-5 requires it, mockup omits) — plan supersedes.
**Standards**: components, css (no inline hex), accessibility (semantic table + aria-expanded button + role=status live region), testing
**Notes**: jsdom limitation on Space-on-button required test workaround; CsvExportButton timer uses spy-on-setTimeout pattern.

## Wave 4 — Groups E ∥ F — COMPLETE

### Group E — ProductFootprintPage
**Tests**: 4/4 passed
**Files**: new src/pages/ProductFootprintPage.tsx, src/test/ProductFootprintPage.test.tsx
**Visual compliance**: detail mockup honoured. Storage days field added per spec (mockup omits). Destination is numeric km input per spec (mockup shows dropdown but FR-2 makes name resolution out-of-scope). Share button + emoji thumb intentionally omitted.
**Notes**: dual state pattern (pending/applied) prevents live re-fetch; deriveTopLevelScope walks composite subtree to emit `1|2|3|"mixed"` per row; no URL state for context bar (deferred decision).

### Group F — useFootprintComparison hook
**Tests**: 5/5 passed
**Files**: new src/hooks/useFootprintComparison.ts, src/test/useFootprintComparison.test.ts
**Notes**: didMountRef pattern ensures one-shot initial fetch; per-scenario isolation verified; baseline guard returns prev array unchanged (no re-render).

## Group G — FootprintComparisonPage — COMPLETE
**Tests**: 5/5 passed (regression check: 9/9 across E+F+G)
**Files**: new src/pages/FootprintComparisonPage.tsx, src/test/FootprintComparisonPage.test.tsx
**Visual deviations** (intentional, flagged for Group I review):
- Added page-level `Export all as CSV` button (mockup shows only per-card links) to drive the sequential live-region status required by spec a11y section
- Omitted mockup's `View history` button (V1 has no history page) and `caveat` paragraph (winter factors note) — neither in plan/spec
**Notes**: useRef sentinel prevents re-parse loop; URL sync via replace-navigate; share URL built from `origin+pathname+searchParams` (jsdom-safe); leaf-totals defensive sum-by-componentId.

## Group H — Landing + Nav + Routes + CTA — COMPLETE
**Tests**: 4/4 passed; full-suite 71/73 (2 pre-existing failures: auth.test.tsx, extension-points.test.tsx — unrelated, predate this work)
**Files**: new src/pages/CarbonFootprintLandingPage.tsx, src/test/CarbonFootprintLandingPage.test.tsx; modified Sidebar.tsx, MobileDrawer.tsx, Header.tsx, router.tsx, ProductDetailPage.tsx
**Notes**:
- Debounce pattern reused from ProductListPage (300ms).
- Routes registered eagerly (matches existing convention).
- ProductDetailPage CTA additive in heading row.
- Chakra `asChild`-wrapped anchor + jsdom quirk: tests use container.querySelector('a[href=...]') instead of getByRole("link"). Candidate for standards-update note.

## Post-verification fix loop — 2026-05-21
**Fixed**:
- TS2322 type error in `src/test/footprint-helpers.test.ts:114` — typed `getItemSpy` as `ReturnType<typeof vi.spyOn<Storage, "getItem">>` (8/8 helpers tests still pass)
- Completeness warnings #7–9 (inline-hex CSS violations in 3 page files): replaced 14 hex values with Chakra theme tokens / CSS vars
  - `CarbonFootprintLandingPage.tsx` (7 occurrences): `#0F172A→gray.900`, `#64748B→gray.500`, `#E2E8F0→gray.200`, `#F8FAFC→gray.50`
  - `FootprintComparisonPage.tsx` (3 occurrences) + `ProductFootprintPage.tsx` (4 occurrences): `#d1d5db→var(--chakra-colors-gray-300)` on native input/select borders
- Re-run: 15/15 page tests pass (CarbonFootprintLandingPage 4 + FootprintComparisonPage 7 + ProductFootprintPage 4)

**Deferred** (per user choice — fix in follow-up PR):
- Code-review W1 (URL-sync exhaustive-deps), W3/W4 (hook contracts), W5 (tagBreakdown mutation), W6 (CSV via client), W7 (in-flight guards), I5 (per-100g unit suffix)
