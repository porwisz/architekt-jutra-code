# Code Review Report — Footprint Calculator UI

**Date**: 2026-05-21
**Scope**: all (quality, security, performance, best practices)
**Files analysed**: 14 new + 8 modified frontend files
**Status**: Issues Found (no critical security issues)

## Summary
| Severity | Count |
|----------|-------|
| Critical | 0 |
| Warning | 7 |
| Info | 8 |

## Warnings

### W1 — Scenario URL state can desynchronise from comparison group hook
- **Location**: `src/main/frontend/src/pages/FootprintComparisonPage.tsx:198-211`
- **Issue**: URL-sync `useEffect` disables `react-hooks/exhaustive-deps` and omits `searchParams`/`setSearchParams` from deps. Other code mutating `searchParams` (back/forward nav) causes URL/state drift.
- **Recommendation**: Compute next URL from `cg` + `scenarios` only; compare against `window.location.search` once before `setSearchParams`. Re-enable the lint rule.

### W2 — Conditional state mutation during render
- **Location**: `FootprintComparisonPage.tsx:165-180`
- **Issue**: `initialParseRef.current` populated inside render body; also calls `crypto.randomUUID()` and `searchParams.getAll()` during render.
- **Recommendation**: Move into `useState(() => ...)` or `useMemo(..., [])` for clearer lazy-init intent.

### W3 — `useProductFootprint` re-fetches when caller doesn't memoise `query`
- **Location**: `src/main/frontend/src/hooks/useProductFootprint.ts:22-38`
- **Issue**: `load` depends on the `query` object reference. Today `ProductFootprintPage` memoises correctly; the hook offers no defence for future callers.
- **Recommendation**: Document the contract OR depend on a JSON-serialised key.

### W4 — `useFootprintComparison` initial-fetch effect lists `scenarios` as dep but ref-gates
- **Location**: `src/main/frontend/src/hooks/useFootprintComparison.ts:65-72`
- **Issue**: Misleading dep list — works only because of `didMountRef` guard. Removal of the guard would re-issue all N fetches on every state update.
- **Recommendation**: `useEffect(..., [])` with comment, or iterate `initialScenarios` captured in a ref.

### W5 — `tagBreakdown` mutates server response in place
- **Location**: `src/main/frontend/src/api/footprints.ts:87-98`
- **Issue**: In-place tag injection violates the `readonly` discriminated-union intent; latent footgun if a cache is added.
- **Recommendation**: Return a new object: `{ ...node, type, children: node.children.map(tagBreakdown) }`.

### W6 — CSV export bypasses central `api` client (auth + 401 duplication)
- **Location**: `src/main/frontend/src/api/footprints.ts:140-158`
- **Issue**: Duplicates token retrieval and header building; does NOT redirect to `/login` on 401 like `request()` does. No `Accept: text/csv` header.
- **Recommendation**: Extend `client.ts` with a `getBlob(path, opts)` helper. Add `Accept: text/csv`.

### W7 — Comparison page allows duplicate fire-and-forget exports / clipboard writes
- **Location**: `FootprintComparisonPage.tsx:247-273`, `:234-243`
- **Issue**: `onExportAll`/`exportSingle` have no in-flight guard. `onSaveComparison` ignores the clipboard promise — shows "Copied!" even on failure.
- **Recommendation**: Add an `exporting` ref to disable buttons during work; await clipboard promise.

## Informational

| # | Location | Note |
|---|----------|------|
| I1 | `utils/scenarioUrl.ts:16-27` | `decodeScenario` casts to `ScenarioInput` without per-field validation |
| I2 | `utils/scenarioUrl.ts:12-14` | `btoa` throws on non-Latin1 — safe today (no string fields) |
| I3 | `components/footprint/BreakdownTree.tsx:34-37` | Warnings list uses `code+message` as React key — collision possible |
| I4 | `ProductFootprintPage.tsx:228,240,252,262`; `FootprintComparisonPage.tsx:463-513` | Native `<input style={{...}}>` blocks bypass theme — use Chakra `Input`/`NativeSelect` |
| I5 | `utils/format.ts:46-48`, `FootprintComparisonPage.tsx:549,588-590,644,679` | Per-100g unit suffix not reflected on comparison page — totals show "kg CO₂" even when scenario is PER_100G |
| I6 | `FootprintComparisonPage.tsx:214-224` | Best-of-N comparison ignores unit consistency |
| I7 | `ProductFootprintPage.tsx:25-41`; comparison page | `deriveTopLevelScope` and `collectLeafTotals` walk subtree on every render — memoise |
| I8 | `BreakdownTree.tsx:21-23` | `safeId` collapses `/` and `_` to same `aria-controls` ID — use `useId` |

## Security

| Area | Status |
|------|--------|
| Auth header collision | PASS — `mergeHeaders` strips caller `Authorization`; bearer-wins enforced |
| XSS | PASS — no `dangerouslySetInnerHTML`, no innerHTML, no eval |
| CSRF | N/A — Bearer header, not cookies |
| Token in localStorage | Pre-existing; not introduced |
| Blob URL leaks | PASS — `URL.revokeObjectURL` called synchronously after `click()` |

## Performance

- No N+1 patterns; effects cancel via `cancelled` flag.
- `useFootprintComparison` issues ≤4 parallel requests on mount (`MAX_SCENARIOS = 4`).
- `leafTotalsFor` called repeatedly inside render — memoise candidate (see I7).
- Re-render storm risk in `useProductFootprint` if caller skips `useMemo` (W3).

## Metrics

- Largest file: `FootprintComparisonPage.tsx` (716 LOC) — candidate for `ScenarioCard` + `DeltaTable` extraction.
- Max nesting depth: 4 (acceptable).
- Hardcoded secrets: 0.
- ESLint `react-hooks/exhaustive-deps` disables: 1 (W1).

## Prioritised Recommendations

1. **W1** — fix URL-sync deps and re-enable lint.
2. **W6** — consolidate CSV export through `client.ts` for uniform 401 behaviour.
3. **W5** — make `tagBreakdown` non-mutating (one-line change).
4. **W7** — guard concurrent exports; await clipboard promise.
5. **I5** — fix unit-suffix rendering on comparison page when PER_100G is selected.
6. **W3, W4** — clarify hook contracts (`useMemo` on `query`, ref-iterate initial scenarios).
7. **I3, I7, I8** — small polish.
