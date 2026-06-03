# Gap Analysis: Footprint Calculator Engine — Frontend UI

**Date**: 2026-05-20
**Task**: `.maister/tasks/development/2026-05-20-footprint-calc-engine-ui`
**Inputs**: `analysis/codebase-analysis.md`, `analysis/clarifications.md` (Phase 1 decisions), `analysis/design-context/` (3 mockups + brief), project docs (`.maister/docs/`)

---

## Summary

This is a **UI-only, additive** task that scaffolds three new pages (detail, comparison, historical timeline) against an already-shipped backend, plus thin cross-cutting helpers (Problem+JSON parser, blob download, CO₂e formatters, recharts integration). Risk is **low–medium**: templates exist for two of three screens and Phase 1 has already locked the most contentious decisions (chart lib, nav placement, CSV feedback, problem helper location); the residual unknowns are scoped to information-architecture choices (route names, comparison-selection UX, timeline access path, detail-page integration mode).

- **Risk Level**: Medium-Low
- **Effort Estimate**: Medium (≈10–12 new files, 5–6 modified)
- **Detected Characteristics**: `ui_heavy`, `modifies_existing_code`, `involves_data_operations` (READ-only, plus a download "operation")

## Task Characteristics

| Flag | Value | Justification |
|---|---|---|
| `has_reproducible_defect` | **false** | Greenfield UI feature, no defect description in inputs. |
| `modifies_existing_code` | **true** | `router.tsx`, `Sidebar.tsx`, `MobileDrawer.tsx`, `Header.tsx` (`getBreadcrumbs`), `Icons.tsx` (`ICON_MAP`), `utils/format.ts` all edited additively. |
| `creates_new_entities` | **false** | No new domain entities; the data model is owned by the backend. New *files* are scaffolding, not new entities in the product sense. |
| `involves_data_operations` | **false** | UI only consumes two READ endpoints + triggers a CSV blob download. No CREATE/UPDATE/DELETE; no new persisted entities owned by the frontend. Full CRUD module not warranted. |
| `ui_heavy` | **true** | Entire task is screens, components, nav, charts, formatters. |

---

## Gaps Identified

### Missing Capabilities (UI scaffolding — net new)

| Capability | Evidence of absence | Notes |
|---|---|---|
| Footprint API module | No `src/api/footprints.ts` | Mirror `src/api/products.ts` (typed module with `URLSearchParams`). |
| `useProductFootprint` hook | Not present | Mirror `useProducts.ts` shape: `{ data, loading, error, refetch }`. |
| `useFootprintComparison` hook | Not present | Orchestrates N parallel GETs, sets `X-Comparison-Group` header. |
| `useProductFootprintHistory` hook | Not present | N GETs over a date range (each datapoint = independent recompute via `asOf=`). |
| `ProductFootprintPage` (detail) | Not present | Template: `ProductDetailPage.tsx`. Adds context bar, summary card, scope-bar chart, hierarchical breakdown table. |
| `FootprintComparisonPage` | Not present | Template: `ProductListPage.tsx` structure (cards + delta table). 1–4 scenarios. |
| `ProductFootprintTimelinePage` | Not present | Line chart (recharts) with factor-version event log + datapoints table + batch CSV. |
| `src/api/problem.ts` (RFC 7807 helper) | `ApiError.body` is raw; no normaliser found | Settled in Phase 1: shared helper exporting `ProblemDetail`, `isProblemDetail`, `extractProblemMessage`. |
| `src/utils/download.ts` blob helper | No blob/`a[download]` utility found | Used for CSV export. |
| `src/utils/format.ts` extensions | Only `formatDate` exists | Add `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`; opportunistic move of duplicated `formatPrice`. |
| `recharts` dependency | Not in `package.json` | Phase 1 locked the choice; add as dep. |
| Footprint nav entry + icon | No item in `Sidebar.tsx` / `MobileDrawer.tsx`; no `FootprintIcon` in `Icons.tsx` `ICON_MAP` | lucide `Leaf` candidate. |
| Breadcrumb mapping for new routes | `getBreadcrumbs` in `Header.tsx` is pathname-based | Must learn new path segments. |

### Behavioural / Visual Gaps Discovered from Mockups

| Mockup signal | Gap |
|---|---|
| Detail: scope-coloured horizontal bars per top-level component (4 bars, scope chips) | No reusable scope-bar component; needs Chakra+token implementation. |
| Detail: hierarchical breakdown table with collapsible composites (`▼ Materials` → leaves) | No tree-table primitive; build inline with `useState<Set<string>>` for expanded set. |
| Detail: per-leaf hover-tooltip ("factor source") | No tooltip pattern in codebase beyond Chakra defaults; spec must define what is surfaced (`factorRef`, `validFrom`, `scope`). |
| Detail: scope badges (Scope 1/2/3 + "mixed") | New semantic chip; colour tokens for 3 scopes + mixed must be added to theme or chip component. |
| Detail: context bar (date / destination / unit) with `Recalculate` button | Today the page would simply re-fetch on param change — UX decision: live re-fetch vs explicit button. Mockup shows explicit. |
| Detail: `Per product` ↔ `Per 100g` toggle | API has both endpoints/responses; UI must wire toggle to per-100g view. |
| Comparison: 1–4 scenario cards + delta table + best-of-N highlight | Requires **scenario-add UX**: how does a user pick the comparison context (date/destination/storage-days)? Mockup pre-populates; real UX requires a clear "+ Add scenario" affordance with form. |
| Comparison: "Save comparison" button | Backend has no persistence for comparisons. Either drop, or implement client-side bookmarkable URL with encoded group state. Flag for decision. |
| Timeline: factor-band overlay (summer/winter dashed line) on chart | Requires a second chart series / reference area; recharts supports `ReferenceArea`. Source of band data unclear — backend exposes no "factor band" endpoint. Spec must decide: derive from datapoints, or skip overlay for V1. |
| Timeline: factor-version event log (e.g. "Truck factor updated 0.00058 → 0.00062") | No backend endpoint for factor version events is documented in the task description. Likely **out of V1 scope** or stubbed. |
| Timeline: batch CSV export of selected datapoints | Backend export takes a single `correlationId`. Batch = N sequential downloads or zipped client-side. Mockup implies N. Needs decision. |
| Timeline: warnings column (`FUTURE_TIMESTAMP`) | Backend returns warnings in the breakdown response; must surface in list and tooltip. |
| Comparison header shows `X-Comparison-Group: 4f8e-...` | UI must generate this header per session (UUIDv4 in the browser) and attach to every call in the group. |

### Integration Surface Gaps

- `Header.tsx::getBreadcrumbs` must learn three new path prefixes.
- `Sidebar.tsx` and `MobileDrawer.tsx` are siblings — sync risk; both must add the same entry in one change.
- `router.tsx` registers three new routes under existing protected `Layout`.
- `Icons.tsx::ICON_MAP` extended with `FootprintIcon` (lucide `Leaf`).
- `ApiError` is the foundational error type; `extractProblemMessage` must compose over it cleanly without changing existing callers.

---

## User Journey Impact Assessment

| Dimension | Current | After | Assessment |
|---|---|---|---|
| Reachability — footprint detail | None (no link, no page) | Top-level "Carbon Footprint" sidebar + link from `ProductDetailPage` | Good (mockup shows both entry points). |
| Reachability — comparison | None | Sidebar → Comparison; "Compare scenarios" button from detail | Good. |
| Reachability — timeline | None | Sidebar → Timeline (cross-product picker) **and/or** "View history" from detail | **Decision needed** — see D2. |
| Discoverability (sidebar entry, `Leaf` icon) | n/a | 8/10 | Standard pattern, matches other top-level entries (Products, Categories, Plugins). |
| Flow integration with existing product detail | Not integrated | Add "View footprint" CTA on `ProductDetailPage` | **Decision needed** — see D3 (new tab vs separate route vs inline section). |
| Multi-persona | n/a | Marta (comparison), Tomek (detail), Anna (timeline+CSV) all covered by the three screens. Piotr is API-only — out of UI scope. | Good. |

**Discoverability concern**: if comparison requires picking arbitrary products (not just scenarios of one product), the entry from a single `ProductDetailPage` is asymmetric. The mockups frame comparison as **same-product, different scenarios** (date/destination/storage-days). This is binding — see D1.

---

## Defect Analysis

Not applicable.

---

## Issues Requiring Decisions

### Critical (must decide before spec)

#### D1 — Comparison selection UX

The mockup unambiguously shows **same-product, multi-scenario** comparison (one SKU, varying date/destination/storage-days). There is no cross-product picker in the mockup. The user request, however, mentions "comparison selection UX (drawer vs search-modal vs add-to-cart)" implying cross-product was considered.

- **Options**:
  - **A. Honour mockup**: same-product scenarios only. "+ Add scenario" opens a small form (date, destination, storage days). Route: `/products/:id/footprint/compare`.
  - **B. Extend to cross-product**: allow picking 1–4 different SKUs; each card shows its own product header. Adds a product picker (drawer or search-modal).
  - **C. Both**: same-product is the default flow from product detail; a separate `/footprints/compare` entry-point allows cross-product.
- **Recommendation**: **A** for V1. Mockups are binding; cross-product comparison is a meaningful scope expansion with backend implications (per-product weight/distance/refrigeration differs and complicates the delta table). Defer B/C to a follow-up.
- **Rationale**: stays inside the design brief, keeps URL state simple, lowest implementation risk.

#### D2 — Timeline access path

- **Options**:
  - **A. Per-product only**: route `/products/:id/footprint/history`, reached from detail page "View history" button. No sidebar sub-entry.
  - **B. Cross-product picker**: route `/footprints/history` requires a product picker first; per-product deep link still works.
  - **C. Both**: sidebar shows "Footprint history" which opens a product-picker landing page; deep link from detail page also works.
- **Recommendation**: **A** for V1.
- **Rationale**: each datapoint is anchored to a single SKU (mockup title: "Footprint history — OFB-330"). A cross-product timeline has no clear visualisation. Sidebar "Carbon Footprint" can point at the comparison/landing page; history reached via detail.

#### D3 — Detail page integration into existing `ProductDetailPage`

How does footprint appear from an existing product?

- **Options**:
  - **A. New tab on `ProductDetailPage`** (the page already has a tabs structure per codebase analysis). Footprint becomes a tab; URL stays at `/products/:id`.
  - **B. Separate route only**: `/products/:id/footprint`, reached via a CTA button on `ProductDetailPage`. No tab.
  - **C. Both**: tab embeds the footprint page; a deep link exists at `/products/:id/footprint` that renders the same content full-pane.
- **Recommendation**: **B** for V1.
- **Rationale**: the mockup is a standalone full-pane layout (sidebar product card + main pane with context bar). Cramming it into a `ProductDetailPage` tab loses the layout; the standalone page also fits the sidebar IA decided in Phase 1. Lower coupling to the existing tabs/plugin extension point machinery.

### Important (should decide; sensible defaults exist)

#### D4 — Route naming

Phase 1 deferred this. Proposed:

- `/products/:id/footprint` (detail)
- `/products/:id/footprint/compare` (scenarios for that product — pairs with D1.A)
- `/products/:id/footprint/history` (timeline — pairs with D2.A)
- `/carbon-footprint` (sidebar landing → either a product picker or a redirect to the most-recent product)

**Default**: as listed above.
**Rationale**: nests product-scoped views under `/products/:id` (consistent with `products/:id/edit`); reserves `/carbon-footprint` for cross-product landing.

#### D5 — Comparison context-state persistence ("Save comparison" button in mockup)

Backend does not persist comparison groups; the mockup shows a "Save comparison" CTA.

- **Options**:
  - **A. Drop the button** for V1.
  - **B. Make it produce a shareable URL** — encode scenarios into query params (`?s=...&s=...`), copy-to-clipboard.
- **Default**: **B** — URL-encoded scenarios. Cheap and matches user expectation of "save = come back to it".
- **Rationale**: avoids new persistence; consistent with `X-Comparison-Group` being a client-generated UUID per session.

#### D6 — Timeline factor-band overlay and event log

Backend has no "factor band" or "factor version event" endpoint surfaced in the task description.

- **Options**:
  - **A. Skip both for V1**: render the line + selected-datapoint dot + datapoints table only.
  - **B. Derive band visually from datapoint variance** (heuristic).
  - **C. Block on a backend endpoint** for factor events.
- **Default**: **A** — skip overlay and event log for V1; render an empty-state hint.
- **Rationale**: keeps the timeline shippable; band overlay is decorative; the events log requires a backend surface that does not exist.

#### D7 — Batch CSV export from timeline

Backend `GET /api/footprints/calculations/{correlationId}/export?format=csv` is single-correlationId.

- **Options**:
  - **A. Sequential client-side downloads** (one file per selected datapoint).
  - **B. Single combined client-side concat** (parse and merge into one CSV).
  - **C. Disable the button for V1**, allow per-row export only.
- **Default**: **A** — N downloads, with inline status text "Exporting 3 of 5...".
- **Rationale**: matches Phase-1 "inline status text" decision; no backend change; users can re-merge in Excel.

#### D8 — Detail context-bar UX: live re-fetch vs explicit "Recalculate"

Mockup shows an explicit `Recalculate` button.

- **Options**:
  - **A. Honour mockup** — only fetches on button click.
  - **B. Debounced live re-fetch** on change.
- **Default**: **A**.
- **Rationale**: each call is a backend compute; explicit button avoids accidental load.

#### D9 — `X-Comparison-Group` UUID source

- **Default**: client-generated UUIDv4 (`crypto.randomUUID()`), regenerated each time a comparison page is mounted; persisted in URL with D5.B so refresh keeps the same group.
- **Rationale**: matches "audit linkage" annotation in mockup; deterministic across page reloads.

---

## Recommendations

1. Build cross-cutting scaffolding **first** (`src/api/problem.ts`, `src/utils/download.ts`, `src/utils/format.ts` extensions, `recharts` install, `FootprintIcon` + sidebar entry + breadcrumbs). One small PR-shaped change before any screen.
2. Implement screens in order **Detail → Comparison → Timeline** (uncertainty increases; reuse compounds).
3. Keep the breakdown-tree, scope-chip, and scope-bar pieces as **small presentational components** (`BreakdownTree`, `ScopeChip`, `ScopeBar`) so all three screens can reuse them.
4. `useFootprintComparison` should accept a `scenarios: ScenarioInput[]` array and run `Promise.all` of `getProductFootprint` with the shared `X-Comparison-Group` header — single hook, easy to test.
5. Strip mockup CSS to Chakra tokens; do not port hex values.
6. For the timeline, render a minimal recharts `<LineChart>` (no `ReferenceArea`) for V1 per D6.
7. Tests: one happy-path + one Problem+JSON-422 path per page; one test that the comparison hook sets the `X-Comparison-Group` header; one test that the CSV blob download is triggered (mock `URL.createObjectURL`).
8. Confirm `/api/...` vs `/...` path convention by inspecting `src/api/products.ts` when writing `src/api/footprints.ts`; follow precedent (do not introduce a new style).

---

## Integration Points (precise file list)

**Modified**:
- `src/main/frontend/src/router.tsx` — register 3 new routes under protected `Layout`.
- `src/main/frontend/src/components/layout/Sidebar.tsx` — add "Carbon Footprint" `NavItem` (lucide `Leaf`).
- `src/main/frontend/src/components/layout/MobileDrawer.tsx` — mirror the above.
- `src/main/frontend/src/components/layout/Header.tsx` — extend `getBreadcrumbs` for new paths.
- `src/main/frontend/src/components/shared/Icons.tsx` — add `FootprintIcon` to `ICON_MAP`.
- `src/main/frontend/src/utils/format.ts` — add `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`; opportunistically extract `formatPrice` from pages.
- `src/main/frontend/package.json` — add `recharts`.
- `src/main/frontend/src/pages/ProductDetailPage.tsx` — add "View footprint" CTA (per D3.B).

**New**:
- `src/main/frontend/src/api/footprints.ts`
- `src/main/frontend/src/api/problem.ts`
- `src/main/frontend/src/utils/download.ts`
- `src/main/frontend/src/hooks/useProductFootprint.ts`
- `src/main/frontend/src/hooks/useFootprintComparison.ts`
- `src/main/frontend/src/hooks/useProductFootprintHistory.ts`
- `src/main/frontend/src/pages/ProductFootprintPage.tsx`
- `src/main/frontend/src/pages/FootprintComparisonPage.tsx`
- `src/main/frontend/src/pages/ProductFootprintTimelinePage.tsx`
- `src/main/frontend/src/components/footprint/BreakdownTree.tsx`
- `src/main/frontend/src/components/footprint/ScopeChip.tsx`
- `src/main/frontend/src/components/footprint/ScopeBar.tsx`
- `src/main/frontend/src/test/footprint.test.tsx`

---

## Risk Assessment

| Risk | Level | Notes |
|---|---|---|
| Complexity | **Low** | Templates exist; only timeline chart is novel. |
| Integration | **Low–Medium** | Sidebar/MobileDrawer/Header/Icons must move together; small sync risk. |
| Regression | **Low** | All host changes additive; existing pages untouched except `ProductDetailPage` CTA (additive). |
| Dependency | **Low** | One new dep (`recharts`); no Chakra/React upgrade. |
| Backend coupling | **Medium** | Timeline mockup shows factor-event log and band overlay with no backend surface — D6 defers this scope. |
| Design fidelity | **Medium** | Honouring mockups (D1) means *no cross-product comparison* in V1 — explicit user confirmation valuable. |
