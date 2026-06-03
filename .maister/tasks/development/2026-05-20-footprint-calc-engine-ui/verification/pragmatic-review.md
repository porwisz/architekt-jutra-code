# Pragmatic Review — Carbon Footprint Calculator UI

**Scope**: 14 new files + ~6 modifications under `src/main/frontend/src/`
**Project scale**: pre-alpha plugin platform; small Maven monolith + Vite SPA, ~75 frontend tests pre-existing.
**Verdict**: Appropriate. The slice is mostly proportional to the feature's scope. A few small over-extractions and one notable bit of duplicated boilerplate, but no enterprise-pattern smells, no speculative infrastructure, no intrusive automation.

---

## Executive Summary

| Severity | Count | Topics |
|---|---|---|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 3 | CSV-export code duplication; `useProductFootprint` query-identity footgun; ad-hoc Problem+JSON parser concentrating logic the rest of the codebase ignores |
| Low | 4 | `problem.ts` as separate module for ~30 LOC; inline `<input style={...}>` blocks duplicated 6×; `MINI_BAR_COMPONENTS` hard-coded; comparison page is 715 LOC — should be split |
| Note | 3 | Test coverage is reasonable (44 tests, ~4–7 per surface); `client.ts` opts extension is genuinely minimal; helper extraction in `format.ts` is a net win |

The code reads like normal feature work — no Redis, no factories, no CQRS, no abstract base classes. The biggest pragmatic concern is **localised duplication** (CSV download flow) rather than over-engineering.

---

## 1. Complexity vs Project Scale

The project is pre-alpha (per `project/architecture.md`). The feature itself is a sizeable two-page workflow (single-product + N-scenario compare with URL share, CSV export, mini-bars, delta table). For that scope, **14 files is on the low side, not the high side** — the comparable existing slice (products: `ProductListPage` + `ProductDetailPage` + `ProductFormPage` + `useProducts` + `api/products.ts`) is similarly granular.

Headline numbers:
- `FootprintComparisonPage.tsx`: 715 lines (largest page in the repo).
- `ProductFootprintPage.tsx`: 365 lines.
- `BreakdownTree.tsx`: 227 lines.
- Everything else is < 160 lines and single-purpose.

No premature optimisation, no caching layer, no state-management library introduced.

---

## 2. File-by-File Necessity Audit

### Necessary as separate files (8)

| File | LOC | Verdict |
|---|---|---|
| `src/api/footprints.ts` | 159 | Yes — DTO types + `tagBreakdown` + 2 endpoint wrappers; mirrors `api/products.ts`. |
| `src/hooks/useProductFootprint.ts` | 48 | Yes — mirrors `useProducts` pattern; refetch + correlationId surfacing belongs in a hook. |
| `src/hooks/useFootprintComparison.ts` | 104 | Yes — N independent fetches with per-scenario loading/error need real orchestration; inlining into the page would push it past 800 LOC. See §3. |
| `src/components/footprint/BreakdownTree.tsx` | 227 | Yes — recursive expand/collapse + a11y is non-trivial; reuse opportunity if any other surface ever shows a tree. |
| `src/pages/ProductFootprintPage.tsx` | 365 | Yes (route target). |
| `src/pages/FootprintComparisonPage.tsx` | 715 | Yes (route target) but **should be split** — see §6. |
| `src/pages/CarbonFootprintLandingPage.tsx` | — | Yes (route target). |
| `src/test/*` (8 files) | 1450 | Yes — co-located per file under test; matches `src/test/` convention. |

### Borderline (4)

| File | LOC | Verdict | Notes |
|---|---|---|---|
| `src/api/problem.ts` | 30 | **Borderline** — see Medium-3. | Used only by 4 callsites, all within this feature. Could live in `api/footprints.ts` or even `api/client.ts`. Extracting it implies cross-feature reuse that hasn't been demonstrated yet (the rest of the codebase still uses ad-hoc `err.message` strings). |
| `src/utils/download.ts` | 10 | OK | 10-line helper, single responsibility, used by 2 callsites (`CsvExportButton` + comparison page `onExportAll`). Cheap, no downside. |
| `src/utils/scenarioUrl.ts` | 28 | OK | Encode/decode pair, used only by `FootprintComparisonPage` — but keeping it out of the 715-LOC page is the right call, and it's directly unit-testable. |
| `src/components/footprint/ScopeChip.tsx` | 36 | OK | Used 4× (2× directly, 2× via `ScopeBar` and `BreakdownTree`). |

### Probably-too-thin (2)

| File | LOC | Verdict |
|---|---|---|
| `src/components/footprint/ScopeBar.tsx` | 47 | **Borderline thin**. Only used inside `ProductFootprintPage`'s top-level bars loop. Pulls its weight only because `ScopeChip` is reused inside it. If `ScopeChip` were inlined too, both could collapse into one ~50-LOC block on the page. Not worth changing now; flag if reuse never materialises. |
| `src/components/footprint/CsvExportButton.tsx` | 70 | **Carries weight on `ProductFootprintPage` but is duplicated on `FootprintComparisonPage`** — see Medium-1. The page-level `Export all as CSV` flow reimplements the same status/timer/error pattern inline (lines 246–273 of comparison page) instead of looping over `CsvExportButton`. |

---

## 3. `useFootprintComparison` Scoping Decision

**Verdict**: Appropriately scoped as a single hook.

The alternative — composing N `useProductFootprint` calls in the page — is **not viable in React** because hook count must be stable across renders. The team would have had to either:
1. Render N `<ScenarioRow useHook={useProductFootprint} />` components (each owning its own hook) — workable but pushes per-scenario state across a component boundary, making the delta table awkward.
2. Maintain an internal `scenarios` array in the page and write the same `fetchScenario` reducer logic inline — that's the hook, just inlined.

The current hook is ~100 LOC and only exposes 4 functions. It's correctly bounded. The `didMountRef` pattern (lines 65–72) is a known one-shot-effect workaround and is commented. No over-engineering here.

**Minor critique**: `removeScenario`'s guard `prev[0].id === id` (line 97) silently no-ops when removing scenario A. This is fine but undocumented — would be a 1-line comment, not a structural issue.

---

## 4. `client.ts` opts Extension

**Verdict**: Genuinely the smallest possible change.

Diff summary:
- Added 1 interface (`RequestOpts`, 3 lines).
- Added 1 third positional parameter to 5 verbs.
- Added `mergeHeaders` helper to strip caller `Authorization` so the Bearer always wins.

This is the right design. Two real considerations:

a) Only `getProductFootprint` actually uses the opts parameter (for `X-Comparison-Group` and `X-Correlation-Id`). Adding it to all 5 verbs (not just `get`) is **mild over-reach** — but it's symmetrical and costs ~15 LOC, so it's fine.

b) `getFootprintCsvExport` deliberately bypasses `client.ts` (footprints.ts:140) because the client returns JSON. The comment explaining the trade-off (footprints.ts:135–139) is exactly right — extending the client with a `responseType: 'blob'` switch for one callsite would have been worse.

---

## 5. `src/api/problem.ts` — Premature Centralisation?

**Verdict**: Mildly premature, low cost to leave, would consolidate well if standardised across the codebase.

Evidence:
- `problem.ts` is ~30 LOC, used by exactly 4 files in this feature (`useProductFootprint`, `useFootprintComparison`, `CsvExportButton`, `FootprintComparisonPage`).
- No other feature in the codebase consumes Problem+JSON — they use `err.message` directly.
- The backend's `error-handling.md` standard implies Problem+JSON is the convention, so this is **forward-aligned** — but no standards-update was filed to promote the parser as the canonical pattern.

**Recommendation**: Either (a) inline these 30 lines into `api/client.ts` and re-export, so the next feature finds it automatically, or (b) raise a standards-update so existing pages migrate. As-is, it's a single-feature helper masquerading as shared infrastructure.

---

## 6. Page Size and Local Duplication

### Medium-1: CSV export logic duplicated 3 ways

The same conceptual flow ("fetch blob → download → status text → reset") exists in three places:
- `CsvExportButton.tsx` (full component with timer reset).
- `FootprintComparisonPage.tsx:247–263` — sequential loop with status string.
- `FootprintComparisonPage.tsx:266–273` — per-card single export, swallows errors.

The per-card export (266–273) should just render `<CsvExportButton correlationId={...} />` — it already exists, with proper error surfacing. The sequential `onExportAll` is genuinely different (page-level live region with "Exporting 2 of 4…" text the spec calls for) and is fine as-is.

**Fix**: replace the per-card `<Button onClick={() => void exportSingle(...)}>` (598–608) with `<CsvExportButton correlationId={...} />`. Drops ~15 lines and unifies error UX.

### Medium-2: `useProductFootprint` query identity footgun

`useProductFootprint(productId, query?)` calls `setLoading(true)` on every render where `query` is a new object reference (hook.ts:34, the `[productId, query]` dep). The callsite already discovered this — work-log entry for Group C says "Group E callers should `useMemo` the query object to avoid render loops" — and `ProductFootprintPage.tsx:90` does memoise.

This is a **lurking footgun**: the contract is "pass a stable reference" but it's not in the type signature. Two practical options:

a) Stringify-key the query internally:
```ts
const queryKey = JSON.stringify(query ?? {});
const load = useCallback(async () => { ... }, [productId, queryKey]);
```
b) Document the contract in a JSDoc `@remarks` so the next caller doesn't trip.

Option (a) is ~2 lines, removes the footgun, and is what the comparison hook effectively does (closure captures stable input per scenario).

### Medium-3: Problem+JSON parser scope

Covered in §5.

---

## 7. Low-Severity Findings

### L-1: 715-LOC comparison page

`FootprintComparisonPage.tsx` packs: URL parse/sync, hook wiring, 4 callbacks, scenario card grid, mini-bars, delta table, and copy-to-clipboard. Natural splits:

- `ScenarioCard` component (cards loop, lines 407–610) — ~200 LOC, currently inline.
- `DeltaTable` component (lines 615–711) — ~95 LOC, currently inline.
- `useScenarioUrlSync(scenarios, cg)` hook (lines 160–211) — ~50 LOC.

After splits the page would be ~370 LOC, in line with `ProductFootprintPage`. **Not urgent**, but the page is the largest in the repo and will only grow if filters/sharing get richer.

### L-2: Inline `<input style={...}>` blocks duplicated

`<input>` elements appear 6 times across the two pages with near-identical inline style objects (border + radius + font-size + padding). Chakra has `Input` for this. Either standardise on `<Input>` or extract a `<TextField>` helper. Currently bypasses the design system inconsistently.

### L-3: `MINI_BAR_COMPONENTS` hard-coded labels

`FootprintComparisonPage.tsx:125` declares `["Materials", "Transport", "Packaging", "Cold storage"]`. If the backend ever ships a 5th top-level component (or renames "Cold storage" to "Refrigeration"), the mini-bars silently show 0. Should be derived from `scenarios[0].data.breakdown.children.map(c => c.componentId)`. ~3 LOC change.

### L-4: `scenarioInputToQuery` / `queryToScenarioInput` are mirror images

The two helpers (comparison page lines 80–105) repeat 8 `if (x !== undefined) y = x` lines each. A single `pickDefined` helper would halve them. Minor.

---

## 8. Test Suite Pragmatism

**44 feature tests** for: 1 API module, 2 hooks, 4 shared components, 2 pages, 1 landing page, plus cross-cutting helpers (URL codec, Problem parser, download, format, client opts).

| Surface | Tests | Verdict |
|---|---|---|
| `footprint-helpers.test.ts` | 8 | Reasonable — covers 4 utility units. |
| `footprint-api.test.ts` | 5 | Reasonable — endpoint URL building, tagBreakdown, CSV blob path, header forwarding, error path. |
| `footprint-components.test.tsx` | 7 | Reasonable — 4 components × ~2 cases each. |
| `useProductFootprint.test.ts` | 4 | Tight. |
| `useFootprintComparison.test.ts` | 5 | Tight. |
| `ProductFootprintPage.test.tsx` | 4 | **Possibly thin** — 4 tests for a 365-LOC page with form state, dual-state recalc, error/retry, summary, breakdown, CSV. |
| `FootprintComparisonPage.test.tsx` | 7 | Reasonable for a 715-LOC page. |
| `CarbonFootprintLandingPage.test.tsx` | 4 | Reasonable. |

**Conclusion**: 44 tests for ~2000 LOC of feature code is **appropriate, not bloated**. Existing comparators in the repo (`plugins.test.tsx` has 309 LOC, `extension-points.test.tsx` has 299 LOC for fewer surfaces) suggest the bar is similar. No tests for Lombok-equivalent trivialities; no over-mocked unit tests; no parallel duplication of integration coverage.

If anything, `ProductFootprintPage.test.tsx` is slightly **light** given the dual-state pending/applied invariant — a single test exercising "type in field → no refetch → click Recalculate → refetch with new params" would be valuable.

---

## 9. Developer Experience

**Positives**:
- Patterns mirror existing slices (`useProducts` → `useProductFootprint`, `api/products.ts` → `api/footprints.ts`). New contributor will recognise the shape.
- DTOs are typed end-to-end; `BreakdownDto` discriminated union (`type: "composite" | "leaf"`) is ergonomic.
- Standards adherence noted in work-log: minimal-implementation, error-handling, components, accessibility — followed without ceremony.
- No new build steps, no new dependencies (no Redux, no React-Query, no Zod).

**Friction points**:
- `useProductFootprint` query-identity footgun (Medium-2).
- The 715-LOC comparison page is hard to navigate without folding.
- Inline `<input>` styles diverge from Chakra's `Input` — inconsistent for the next dev.
- The decision to read `localStorage.auth_token` directly inside `getFootprintCsvExport` (footprints.ts:142) duplicates auth coupling that `client.ts` owns. If auth ever moves to cookies, this callsite breaks silently.

---

## 10. Requirements Alignment

Per work-log and spec deviations called out by Groups E and G:
- "Storage days" field added (spec requires, mockup omits) — correct, spec wins.
- "View history" button and winter-factor caveat omitted (not in plan/spec) — correct, anti-scope-creep.
- Share button + emoji thumb omitted on detail page — correct, not in V1 scope.
- Page-level "Export all as CSV" added (spec a11y requires sequential live region) — correct, spec wins.

No requirement inflation observed. No speculative future-proofing (no plugin extension points, no permission scaffolds, no i18n keys).

---

## 11. Context Consistency

No dead code, no half-implemented patterns, no abandoned helpers. Two small inconsistencies:

- `useProductFootprint` uses `Promise<void>` refetch; `useFootprintComparison` has no refetch (only add/update/remove). Reasonable given different semantics, but worth a JSDoc note.
- `extractProblemMessage` is the canonical error string for this feature, while older pages (`ProductDetailPage`, etc.) still use `err.message`. Either migrate or document.

---

## 12. Top 3 Recommended Simplifications

### 1. Replace per-card CSV button with `<CsvExportButton>` (Medium-1)
**Before** (FootprintComparisonPage.tsx 266–273, 598–608):
```tsx
const exportSingle = useCallback(async (correlationId: string) => {
  try { const blob = await getFootprintCsvExport(correlationId); downloadBlob(blob, ...); }
  catch { /* surfaced via per-scenario error UI in a future iteration */ }
}, []);
// ...
<Button onClick={() => void exportSingle(scenario.data!.correlationId)} variant="ghost" size="xs">
  Export CSV
</Button>
```
**After**:
```tsx
<CsvExportButton correlationId={scenario.data.correlationId} />
```
**Impact**: −15 LOC, unifies error surfacing, removes the `/* future iteration */` TODO.

### 2. Stabilise `useProductFootprint(query)` via JSON-key (Medium-2)
**Before** (`useProductFootprint.ts:22-34`):
```ts
const load = useCallback(async () => { ... }, [productId, query]);
```
**After**:
```ts
const queryKey = JSON.stringify(query ?? {});
const load = useCallback(async () => { ... }, [productId, queryKey]); // eslint-disable-line — queryKey captures query
```
**Impact**: removes the "callers must `useMemo`" contract, eliminates a category of bugs.

### 3. Decide on Problem+JSON parser location (Medium-3 / L)
**Option A** (least disruptive): inline `problem.ts` into `client.ts` and re-export. Migrates other features for free.
**Option B**: file a `/maister:standards-update` for "all API error messages flow through `extractProblemMessage`" so existing pages adopt it.
**Impact**: avoids "centralised but unused" state; either spreads the pattern or owns its single-feature scope.

---

## 13. Conclusion

The feature is built **at the right altitude for the project's pre-alpha scale**. No infrastructure overkill, no enterprise patterns, no intrusive automation. The largest concern is the 715-LOC comparison page (splittable but not urgent) and one duplicated CSV-export flow (15-minute fix). The Problem+JSON parser sits in a grey zone — fine to keep, but should either spread or shrink within the next slice.

Estimated effort to address all top-3 simplifications: **~1 hour**.

Estimated LOC delta after simplifications: **−25 LOC, −1 file** (if `problem.ts` is folded into `client.ts`).
