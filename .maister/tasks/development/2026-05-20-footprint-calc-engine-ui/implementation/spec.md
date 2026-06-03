# Specification: Footprint Calculator UI (V1)

**Date**: 2026-05-20
**Task**: `.maister/tasks/development/2026-05-20-footprint-calc-engine-ui`
**Scope**: Frontend UI for the already-shipped carbon footprint calculation engine. V1 = **2 pages** (detail + same-product scenario comparison), cross-cutting helpers, nav wiring. **Historical timeline page is deferred to V1.1.**

---

## Goal

Give internal authenticated host-app users (PERMISSION_READ) a read-only, audit-grade UI to inspect a single product's carbon footprint breakdown and to compare multiple "what-if" calculation scenarios for the same product, with CSV export of any computed result. The UI consumes two existing backend endpoints without any backend change.

## User Stories

- **As Marta (ESG officer)**, I want to see one product's full kgCO₂e breakdown in a hierarchical view with scope chips so I can validate factor sources for CSRD reports.
- **As Marta**, I want to add up to 4 same-product calculation scenarios (varying date / destination / storage days) and see a delta table vs the baseline so I can pick the lowest-impact logistics setup.
- **As Anna (auditor)**, I want a one-click CSV export of any computed result so I can reconcile values in Excel — the file must use the backend-issued `correlationId` for traceability.
- **As Tomek (developer/PM)**, I want a "View carbon footprint" CTA on the product detail page so I can jump straight from a product to its footprint.
- **As any user**, I want a shareable URL that re-creates the comparison set so I can hand off a what-if to a colleague without re-typing inputs.

## Core Requirements

### FR-1 — Sidebar landing (`/carbon-footprint`)
Top-level "Carbon Footprint" sidebar entry. The landing page shows a product picker (search-by-SKU/name reusing `getProducts({ search })` from `src/api/products.ts`); clicking a result navigates to `/products/:id/footprint`. Empty state when no search term.

### FR-2 — Product Footprint Detail (`/products/:id/footprint`)
Implements `screen:footprint-detail` (mockup `analysis/design-context/mockups/product-footprint-detail.html`, binding for layout, copy, field order, scope chip colours).

Layout (desktop ≥1024px): 2-column grid `280px 1fr`. Sidebar product card collapses **above** the main pane on viewports <768px.

- **Topbar breadcrumb**: `Products › <sku>` plus `Compare scenarios` button → `/products/:id/footprint/compare`. ("View history" button from the mockup is **omitted in V1**.)
- **Product card (left)**: thumb/emoji (reuse `product.photoUrl` if present, else neutral placeholder; no emoji), name, SKU, key attributes (`Material weight` from `materialWeightKg`, `Supplier distance` from `supplierDistanceKm`, `Refrigerated` from `requiresRefrigeration`). Source: existing `getProduct(id)`.
- **Context bar (top of main pane)**: form fields — `Date` (`<input type="date">`, default = today), `Destination` (numeric km input — V1 uses raw `destinationDistanceKm` since destination-name resolution is backend application-layer concern and out of scope), `Storage days` (numeric input, default 0), `Unit` (select: `Per product` / `Per 100g` → `Normalisation.TOTAL` / `PER_100G`), `Recalculate` primary button. **No live re-fetch** — fetch only on explicit button click, on mount, and on URL param change.
- **Summary card**: large kgCO₂e total, unit suffix (`kg CO₂` or `kg CO₂ / 100g`), strict/lenient mode badge, `Calculated <ISO ts>` timestamp, monospace `correlationId: <uuid>`.
- **Top-level bars**: one row per top-level composite (`Materials`, `Transport`, `Packaging`, `Cold storage`), each with label · proportional bar (`width = kgCo2 / total * 100%`) · numeric value · scope chip. Cold storage row is omitted when the response tree lacks it (refrigerated=false).
- **Breakdown table**: hierarchical expand/collapse rows. Top-level composites expanded by default; leaves indented; clicking a composite toggles. Columns: `Component`, `kg CO₂`, `% of total`, `Scope` chip (leaves only), `Factor` value, `Valid from` (formatted date). Per-leaf hover surfaces `factorVersionId` via Chakra `Tooltip`. Warnings on a leaf render as inline small text under the component name.
- **Export CSV button**: triggers `GET /footprints/calculations/{correlationId}/export?format=csv`, downloads via blob helper. Inline status text right of button: `Exporting...` → `Downloaded footprint-<id>.csv` (3 s auto-clear) or `Export failed: <message>`.

### FR-3 — Footprint Comparison (`/products/:id/footprint/compare`)
Implements `screen:footprint-comparison` (mockup `analysis/design-context/mockups/product-footprint-comparison.html`, binding for layout, copy, scenario card structure, delta table format).

**Scope: same-product, multi-scenario only.** No cross-product comparison.

- **Topbar breadcrumb**: `Products › <sku> › Compare`. Buttons: `Back to detail` (→ `/products/:id/footprint`), `Save comparison` (copy current URL to clipboard, inline status `Copied!` 2 s).
- **Header**: `Compare scenarios — <sku>`. Meta line: `Comparison group: <uuid>` (monospace) + `N audit rows linked via X-Comparison-Group header` (N = number of successfully computed scenarios).
- **Comparison group UUID**: `crypto.randomUUID()` on first mount when absent from URL; persisted in URL query param `?cg=<uuid>` so refresh / share preserves identity. Sent on every request as `X-Comparison-Group` header.
- **Scenarios**: 1–4 scenario cards in a CSS Grid (3 cols ≥1024px, 2 cols 768–1023px, 1 col <768px). Card contents: title (auto-generated, format `Scenario <Letter> — <YYYY-MM-DD> / <km> km` e.g. `Scenario A — 2026-05-20 / 1240 km`; **deviation from mockup intentional** — mockup shows season + city name (`Summer / Szczecin`), but destination-name resolution lives backend-side and is out of V1 scope; numeric km is the source-of-truth field on the wire), close `✕` (disabled when only 1 scenario remains), control form (Date, Destination distance km, Storage days), total kgCO₂e block (large value), 4 mini-bar rows (Materials / Transport / Packaging / Cold storage). Lowest-total card gets `best` style (Chakra green tokens) + relative `−X% vs A` delta on the others (A = first/baseline scenario, never deletable).
- **Add scenario button**: `+ Add scenario (up to N)` where N=4. Disabled at cap. Defaults new scenario from the baseline (clone date/destination/storage-days, user adjusts). Triggers a fresh per-scenario backend call.
- **Per-scenario fetch lifecycle**: each scenario tracks its own `{ loading, data, error }`. On Recalculate within a card (or on Add scenario), call backend once. Failures show inline red text in that card; do not block other scenarios.
- **Delta table** (below grid): `Component | A | B | Δ B | C | Δ C | D | Δ D` (columns appear as scenarios are added). Δ formatted `±X.XX` colour-coded (green = lower, red = higher, grey "—" = unchanged). Total row separated with top border.
- **URL state shape**: `?cg=<uuid>&s=<base64-json>&s=<base64-json>...` where each `s` decodes to `{ asOf, materialWeightKg, supplierDistanceKm, destinationDistanceKm, lastMileDistanceKm, storageDays, requiresRefrigeration, unit }`. Encode/decode in `src/utils/scenarioUrl.ts` (new helper). **Invalid `s=` params**: `decodeScenario` returns `null` → drop silently. If every `s=` decodes to `null` (or none are present), fall back to a single baseline scenario derived from the product's default parameters.
- **Per-scenario CSV export**: small "Export CSV" link per card (uses that card's `correlationId` once present). Sequential downloads if multiple triggered — inline status `Exporting 2 of 3...`.

### FR-4 — Detail-page CTA on `ProductDetailPage.tsx`
Add a Chakra `Button` labeled `View carbon footprint` (leaf icon) in the existing actions area, navigating to `/products/:id/footprint`. Additive only — no other layout changes.

### FR-5 — Cross-cutting helpers (build first)

| File | Purpose | Public surface |
|---|---|---|
| `src/api/problem.ts` | RFC 7807 normaliser over `ApiError.body` | `interface ProblemDetail`, `isProblemDetail(body): boolean`, `extractProblemMessage(err: unknown): string` |
| `src/api/footprints.ts` | Typed footprint endpoints | See "TypeScript Type Contracts" below |
| `src/utils/download.ts` | Blob → file download | `downloadBlob(blob: Blob, filename: string): void` |
| `src/utils/format.ts` (extend) | Numeric/date formatters | Add `formatCO2eKg(value: number): string` (3 decimals + " kg CO₂"), `formatPer100g(value: number): string` (3 decimals + " kg CO₂ / 100g"), `formatRelativeDate(iso: string): string` (e.g. "2 hours ago"). Also move duplicated `formatPrice` from `ProductDetailPage` + `ProductListPage` here. |
| `src/utils/scenarioUrl.ts` | Encode/decode scenarios for URL state | `encodeScenario(s: ScenarioInput): string`, `decodeScenario(s: string): ScenarioInput \| null` |
| `src/components/shared/Icons.tsx` | Add `FootprintIcon` (lucide `Leaf`) to `ICON_MAP` | additive |
| `src/hooks/useProductFootprint.ts` | Single-scenario hook | `(productId, params) => { data, loading, error, refetch, correlationId }` |
| `src/hooks/useFootprintComparison.ts` | N-scenario orchestrator | `(productId, scenarios, comparisonGroupId) => { scenarios: ScenarioState[], addScenario, removeScenario, updateScenario }` |
| `src/components/footprint/BreakdownTree.tsx` | Hierarchical expand/collapse table | props: `root: BreakdownDto`, `total: number` |
| `src/components/footprint/ScopeChip.tsx` | Scope 1/2/3/mixed chip | props: `scope: 1 \| 2 \| 3 \| 'mixed'` |
| `src/components/footprint/ScopeBar.tsx` | Top-level bar row | props: `label`, `kgCo2`, `total`, `scope` |
| `src/components/footprint/CsvExportButton.tsx` | Export button + inline status | props: `correlationId`, `onError?` |

### FR-6 — Navigation wiring
- `src/components/layout/Sidebar.tsx`: add `NavItem` "Carbon Footprint" with `FootprintIcon`, path `/carbon-footprint`.
- `src/components/layout/MobileDrawer.tsx`: mirror sidebar entry.
- `src/components/layout/Header.tsx::getBreadcrumbs`: add mappings for `/carbon-footprint`, `/products/:id/footprint`, `/products/:id/footprint/compare`.

### FR-7 — Routes (`src/router.tsx`)
Register under existing protected `Layout`:
- `/carbon-footprint` → lazy `CarbonFootprintLandingPage`
- `/products/:id/footprint` → lazy `ProductFootprintPage`
- `/products/:id/footprint/compare` → lazy `FootprintComparisonPage`

### FR-8 — Error handling
All hooks pipe `ApiError` through `extractProblemMessage`. Errors render inline at point of use (existing `<Text color="red.500">{msg}</Text>` pattern — no toast system, no error boundary). 401 keeps the existing auto-logout in `client.ts`. 400/409/422 surface the Problem+JSON `detail` field. Network errors surface `"Network error — check connection"`.

### FR-9 — Loading + empty + error states
- **Loading**: Chakra `Spinner` centered in main pane with `Loading footprint…` text. Per-scenario card: inline spinner overlay on the total block.
- **Empty (compare with 0 valid scenarios)**: cannot occur — baseline always present.
- **Empty (landing without search)**: existing `EmptyState` component with `Search a product to see its footprint.`
- **Error**: inline red text + `Retry` button calling `refetch()`.

---

## Visual Design

Two mockups in `analysis/design-context/mockups/` are **binding inputs** to layout, copy, field order, scope chip colours, and explicit interactive states. The implementation-planner will attach `Visual References` to the UI task groups for FR-2 and FR-3.

| Stable ID | Mockup file | Drives |
|---|---|---|
| `screen:footprint-detail` | `product-footprint-detail.html` | FR-2 — product card, context bar, summary card, 4 top-level bars, hierarchical breakdown table, hover-tooltip hint, export button copy |
| `screen:footprint-comparison` | `product-footprint-comparison.html` | FR-3 — scenario card structure, mini-bars, best-of-N highlight, delta table layout, copy ("Save comparison", "+ Add scenario (up to 4)", caveat footer) |

**Fidelity level**: structural / approximate. Mockups use raw hex (`#10b981`, `#ecfdf5`, etc.) and -apple-system fonts — implementation **must** use Chakra v3 design tokens (`brand.*`, `accent.*`, semantic tokens) rather than porting hex. Scope chip colour assignment:
- Scope 1 → amber (`yellow.100` bg / `yellow.800` fg)
- Scope 2 → blue (`blue.100` / `blue.800`)
- Scope 3 → indigo (`purple.100` / `purple.800`)
- mixed → grey (`gray.100` / `gray.700`)

Add semantic tokens to `src/theme/index.ts` if not already present; do not inline hex.

`screen:footprint-historical-timeline` is marked **DEFERRED V1.1** in `analysis/design-context/INDEX.md` — produce no spec sections, components, hooks, routes, or tests for it.

---

## TypeScript Type Contracts (`src/api/footprints.ts`)

Hand-written types mirroring `FootprintResponseDto` (Java). When backend DTOs evolve, update by hand — no codegen in V1.

```ts
export type Normalisation = "TOTAL" | "PER_100G";
export type Strictness = "STRICT" | "LENIENT";
export type Unit = "KG_CO2" | "KG_CO2_PER_100G";
export type Scope = 1 | 2 | 3;

export interface WarningDto {
  code: string;
  message: string;
  componentId: string | null;
}

export interface CompositeDto {
  type: "composite";          // discriminator: not on wire — add via runtime mapper (see below)
  componentId: string;
  kgCo2: number;              // wire shape verified: JSON number (Spring default Jackson BigDecimal → number)
  children: BreakdownDto[];
  warnings: WarningDto[];
}

export interface LeafDto {
  type: "leaf";
  componentId: string;
  kgCo2: number;
  scope: Scope;
  factorVersionId: string;
  factorRate: number;
  factorValidFrom: string;    // ISO instant
  quantity: number;
  warnings: WarningDto[];
}

export type BreakdownDto = CompositeDto | LeafDto;

export interface ParametersEcho {
  productId: string;
  materialWeightKg: number | null;
  supplierDistanceKm: number | null;
  destinationDistanceKm: number | null;
  lastMileDistanceKm: number | null;
  storageDays: number;
  requiresRefrigeration: boolean;
  timestamp: string;          // ISO instant
}

export interface OptionsEcho {
  strictness: Strictness;
  normalisation: Normalisation;
  dryRun: boolean;
}

export interface FootprintResponse {
  correlationId: string;      // UUID
  comparisonGroupId: string | null;
  computedAt: string;         // ISO instant
  total: number;
  unit: Unit;
  parametersEcho: ParametersEcho;
  options: OptionsEcho;
  breakdown: BreakdownDto;
}

export interface FootprintQueryInput {
  asOf?: string;              // ISO instant; default = now
  materialWeightKg?: number;
  supplierDistanceKm?: number;
  destinationDistanceKm?: number;
  lastMileDistanceKm?: number;
  storageDays?: number;
  requiresRefrigeration?: boolean;
  unit?: Normalisation;
  strictness?: Strictness;
  dryRun?: boolean;
}

export interface FootprintRequestHeaders {
  comparisonGroupId?: string; // → X-Comparison-Group
  correlationId?: string;     // → X-Correlation-Id (rarely used by UI; let backend mint)
}

// Functions (signatures only, no implementations in spec)
export function getProductFootprint(
  productId: string,
  query?: FootprintQueryInput,
  headers?: FootprintRequestHeaders
): Promise<FootprintResponse>;

export function getFootprintCsvExport(correlationId: string): Promise<Blob>;
```

**Note**: backend `BreakdownDto` is a sealed interface (no `type` discriminator on wire). The API module must add a runtime tag by structure (composites have `children`, leaves have `scope`/`factorVersionId`) before returning to callers — keeps consumer code type-narrow-able. Implement in `src/api/footprints.ts::tagBreakdown(node)`.

**Number parsing**: backend serialises `BigDecimal` as JSON numbers (default Spring Boot Jackson; no overrides in the footprint package). All TypeScript types declare `number` directly — **no boundary coercion needed**. If a field ever arrives as `string`, that is a backend regression to fix, not a frontend defensive concern.

**`tagBreakdown(node)` contract**: recursively walks the tree. Decides composite vs leaf by structure (`"children" in node` → composite, else leaf). Returns the same node with an added `type` literal. Recurses into `composite.children` so every nested node carries the tag.

**Header injection — signature pinned**: extend `src/api/client.ts` with an optional **third** positional parameter `opts?: { headers?: Record<string, string> }` on all methods. Final signatures:
```ts
get<T>(path: string, opts?: { headers?: Record<string, string> }): Promise<T>
post<T>(path: string, body?: unknown, opts?: { headers?: Record<string, string> }): Promise<T>
put<T>(path: string, body?: unknown, opts?: { headers?: Record<string, string> }): Promise<T>
patch<T>(path: string, body?: unknown, opts?: { headers?: Record<string, string> }): Promise<T>
delete<T>(path: string, opts?: { headers?: Record<string, string> }): Promise<T>
```
Headers are merged into the existing `headers` object inside `request()` (Bearer token wins on collision — never let callers override `Authorization`). All 26 existing call sites work unchanged.

**Implementer verification step (cheap)**: before writing types, open `src/main/java/pl/devstyle/aj/footprint/api/Unit.java` and confirm the enum constants are exactly `KG_CO2` and `KG_CO2_PER_100G`. If they differ, update the TS literal union to match.

---

## Error Handling Contract (`src/api/problem.ts`)

```ts
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;              // backend extension property (e.g. "MISSING_FACTOR")
  [key: string]: unknown;
}

export function isProblemDetail(body: unknown): body is ProblemDetail;
export function extractProblemMessage(err: unknown): string;
```

`extractProblemMessage` resolution order:
1. `err instanceof ApiError && isProblemDetail(err.body)` → return `body.detail ?? body.title ?? "Request failed"`.
2. `err instanceof ApiError && typeof err.body === "string"` → return that string (truncated to 200 chars).
3. `err instanceof ApiError` → return `${err.status} ${err.statusText}`.
4. Otherwise → return `"Network error — check connection"`.

Backend Problem+JSON shape (verified against `FootprintExceptionHandler.java`):
```
{ "type": "<base>/missing-factor", "title": "Missing emission factor",
  "status": 422, "detail": "...", "instance": "/api/products/.../footprint",
  "code": "MISSING_FACTOR", ...extra ex-specific properties }
```
Content-Type: `application/problem+json`.

---

## Route Table

| Route | Page | Auth | Source of `:id` |
|---|---|---|---|
| `/carbon-footprint` | `CarbonFootprintLandingPage` | `AuthGuard` (existing Layout) | product picker → navigate |
| `/products/:id/footprint` | `ProductFootprintPage` | `AuthGuard` | `useParams<{ id: string }>()` |
| `/products/:id/footprint/compare` | `FootprintComparisonPage` | `AuthGuard` | `useParams<{ id: string }>()` |
| (CTA on existing) `/products/:id` | `ProductDetailPage` (FR-4 mod) | existing | — |

All new routes use `path:` strings registered under the existing protected `Layout` child array in `router.tsx`. Lazy-load via React Router v7 `lazy` for code-splitting consistency with existing pages (or eager if existing pages are eager — match precedent).

---

## File-Level Breakdown

### New files (12)
- `src/main/frontend/src/api/footprints.ts`
- `src/main/frontend/src/api/problem.ts`
- `src/main/frontend/src/hooks/useProductFootprint.ts`
- `src/main/frontend/src/hooks/useFootprintComparison.ts`
- `src/main/frontend/src/pages/CarbonFootprintLandingPage.tsx`
- `src/main/frontend/src/pages/ProductFootprintPage.tsx`
- `src/main/frontend/src/pages/FootprintComparisonPage.tsx`
- `src/main/frontend/src/components/footprint/BreakdownTree.tsx`
- `src/main/frontend/src/components/footprint/ScopeChip.tsx`
- `src/main/frontend/src/components/footprint/ScopeBar.tsx`
- `src/main/frontend/src/components/footprint/CsvExportButton.tsx`
- `src/main/frontend/src/utils/download.ts`
- `src/main/frontend/src/utils/scenarioUrl.ts`
- `src/main/frontend/src/test/footprint.test.tsx`

### Modified files (7)
- `src/main/frontend/src/router.tsx` — add 3 routes
- `src/main/frontend/src/api/client.ts` — accept optional `{ headers }` on `get/post/put/patch/delete`
- `src/main/frontend/src/components/layout/Sidebar.tsx` — add Footprint nav entry
- `src/main/frontend/src/components/layout/MobileDrawer.tsx` — mirror
- `src/main/frontend/src/components/layout/Header.tsx` — extend `getBreadcrumbs`
- `src/main/frontend/src/components/shared/Icons.tsx` — register `FootprintIcon`
- `src/main/frontend/src/utils/format.ts` — add three formatters + opportunistic `formatPrice` extraction
- `src/main/frontend/src/pages/ProductDetailPage.tsx` — add `View carbon footprint` CTA
- `src/main/frontend/src/theme/index.ts` — add scope chip semantic tokens (if missing)

### Files NOT modified
- Backend (untouched)
- `src/main/frontend/package.json` (no new deps — recharts dropped with timeline)
- `src/main/frontend/vite.config.ts`
- `src/main/frontend/vitest.config.ts`

---

## Reusable Components

### Existing code to leverage
| Existing | File | Reuse for |
|---|---|---|
| `api` + `ApiError` | `src/api/client.ts` | All new endpoints (extend `client.ts` only to accept `{ headers }` on each method) |
| `useProducts` hook shape | `src/hooks/useProducts.ts` | Template for `useProductFootprint` — same `{ data, loading, error, refetch }` shape |
| `ProductDetailPage` structure | `src/pages/ProductDetailPage.tsx` | Template for `ProductFootprintPage` topbar/breadcrumb/main pane layout |
| `ProductListPage` table pattern | `src/pages/ProductListPage.tsx` | Template for landing page picker + comparison delta table |
| `getProducts({ search })` | `src/api/products.ts` | Landing page picker |
| `EmptyState` | `src/components/shared/EmptyState.tsx` | Landing empty state |
| `PrimaryButton` | `src/components/shared/PrimaryButton.tsx` | Recalculate / Save comparison |
| `ConfirmDialog` | `src/components/shared/ConfirmDialog.tsx` | Removing a scenario (skip if mockup's plain `✕` is sufficient — V1 uses plain `✕` per mockup, no confirm) |
| `formatDate` | `src/utils/format.ts` | Factor `validFrom`, computed-at timestamps |
| Chakra tokens (`brand.*`, `accent.*`, semantic) | `src/theme/index.ts` | All UI styling — no inline hex |
| `AuthGuard` + `Layout` | `src/router.tsx` | Wraps all new routes (no changes needed) |
| `lucide-react` `Leaf` | already a dep via `Icons.tsx` | `FootprintIcon` |
| Test infra `renderWithProviders`, `vi.mock` pattern | `src/test/pages.test.tsx` | New `footprint.test.tsx` |

### New components required (justification)
| New | Why no reuse |
|---|---|
| `BreakdownTree` | No tree-table primitive in codebase; expand/collapse with `useState<Set<string>>` of expanded composite IDs |
| `ScopeChip` | No status-chip component exists; small presentational primitive used in 3+ places (bars, breakdown rows, summary) |
| `ScopeBar` | No proportional bar component; trivial flex/grid composition |
| `CsvExportButton` | Reused across detail + each comparison card; encapsulates blob fetch + inline status state |
| `src/api/problem.ts` | `ApiError.body` is opaque — no normaliser exists; required across all new hooks |
| `src/utils/download.ts` | No blob/`a[download]` helper exists; needed for CSV export |
| `src/utils/scenarioUrl.ts` | Comparison URL state encoding is feature-specific; nothing equivalent exists |
| `useFootprintComparison` | `useProducts` shape covers single-resource fetch but does not orchestrate N parallel calls with a shared header — distinct concern |

---

## Technical Approach

### Data flow — Detail page
`useParams` → `useProductFootprint(productId, query)` hook:
1. Holds `{ data, loading, error, refetch, correlationId }`.
2. `loader = useCallback(async () => api wrapper → setData)`; effect triggers on mount + when `query` changes.
3. Calls `getProductFootprint(productId, query)` → tagged `FootprintResponse`.
4. On `ApiError` → store `extractProblemMessage(err)`.
5. Parallel `useProduct(productId)` for sidebar card (reuse existing hook).

### Data flow — Comparison page
`useParams` + parse URL → `useFootprintComparison(productId, scenarios, comparisonGroupId)`:
1. Internal `scenarios: ScenarioState[]` where `ScenarioState = { id: string, input: ScenarioInput, data?: FootprintResponse, loading: boolean, error?: string }`.
2. `addScenario(input)` pushes a new entry + fires `getProductFootprint(productId, input, { comparisonGroupId })`.
3. `updateScenario(id, input)` re-fires that scenario.
4. `removeScenario(id)` drops it; baseline (`id === scenarios[0].id`) is removal-protected.
5. URL sync via effect → `navigate(..., { replace: true })` with encoded scenarios + `cg`.
6. Best-of-N derived: `Math.min(...data.map(d => d.total))` selecting which card gets `best` style.

### Standards Compliance

Referencing `.maister/docs/standards/`:
- **`global/minimal-implementation.md`** — build exactly the helpers/components listed; no speculative props, no future-proof stubs (e.g. no cross-product comparison plumbing, no chart library install).
- **`global/error-handling.md`** — typed `ApiError`, centralised Problem+JSON normaliser, user-friendly inline messages.
- **`global/validation.md`** — client-side numeric validation on context bar / scenario form (positive numbers, integer storage-days); server-side errors surfaced as Problem+JSON `detail`.
- **`frontend/components.md`** — single-responsibility presentational components (`ScopeChip`, `ScopeBar`, `BreakdownTree`, `CsvExportButton`); container pages call hooks + render.
- **`frontend/css.md`** — Chakra v3 design tokens only; no inline hex; semantic tokens added to theme for scope chips.
- **`frontend/accessibility.md`** — see detailed a11y subsections below.

#### Accessibility — BreakdownTree (detail page)
Use **semantic `<table>`** (not `role="treegrid"`) for the breakdown. The composite "expand" affordance is a **`<button>` inside the first cell** of each composite row carrying `aria-expanded="true|false"` and `aria-controls="<id-of-the-child-row-group>"`. Child rows render inside a `<tbody>` with `hidden` attribute toggled by state — screen readers respect `hidden` and announce expanded/collapsed via `aria-expanded` on the button. Keyboard: Space/Enter on the focused button toggles; Tab moves between composite buttons (child rows are not focusable). Visual indent on child rows is decorative; the row grouping (`<tbody>`) provides programmatic structure.

#### Accessibility — Comparison form
Tab order per scenario card: `Close ✕ button → Date input → Destination distance input → Storage days input → Recalculate button → Export CSV link`. Required-field validation surfaces via Chakra's `Field.ErrorText` (announced by screen readers). The 4 mini-bar rows are decorative — duplicated as a visually-hidden `<table>` for screen readers (`role="presentation"` on the visual bars).

#### Accessibility — Live regions
- CSV single export: existing button → after click, render `<span role="status" aria-live="polite">{message}</span>` below the button. Messages: `Exporting…`, `Downloaded {filename}`, error `detail`.
- CSV sequential export (comparison batch): same pattern at the page-level. Messages: `Exporting 1 of N…`, …, `Exported N of N.`, error stops the loop and shows `Stopped at {n}/N: {detail}`.
- "Copied!" toast for Save comparison: same `role="status" aria-live="polite"` pattern, auto-clears after 2s.
- **`frontend/responsive.md`** — mobile-first: product card collapses above main pane <768px; comparison grid 3→2→1 cols; context bar wraps with `flex-wrap`.
- **`testing/frontend-testing.md`** — Vitest + `@testing-library/react`; per-file `renderWithProviders`; `vi.mock("../api/footprints")`; `vi.resetAllMocks()` in `beforeEach`; describe blocks named per page.

---

## Implementation Guidance

### Build order (suggested for planner)
1. Cross-cutting helpers — `problem.ts`, `download.ts`, `format.ts` extension, `scenarioUrl.ts`, `Icons.tsx`, `client.ts` headers extension. One small task group.
2. `api/footprints.ts` (types + functions + `tagBreakdown` runtime mapper).
3. Hook `useProductFootprint`.
4. Shared presentational components `ScopeChip`, `ScopeBar`, `BreakdownTree`, `CsvExportButton`.
5. `ProductFootprintPage` (FR-2).
6. Hook `useFootprintComparison`.
7. `FootprintComparisonPage` (FR-3).
8. `CarbonFootprintLandingPage` (FR-1).
9. Nav wiring: `Sidebar`, `MobileDrawer`, `Header`, `router.tsx`.
10. CTA on `ProductDetailPage` (FR-4).
11. Tests (FR-9 below; can be co-located with each page step).

### Testing approach
Per `.maister/docs/standards/testing/frontend-testing.md`: 2–8 focused tests per implementation step group, co-located in `src/test/footprint.test.tsx`. Test verification runs only the new test file, not the full suite.

**Detail page (FR-2)** — 4 tests:
- Happy path: mock `getProductFootprint` → assert total, top-level bars, breakdown table rows visible.
- Loading state: pending promise → spinner present.
- 422 Problem+JSON error: reject with `ApiError(422, ..., { detail: "Missing factor X" })` → assert `Missing factor X` rendered inline.
- CSV export: click button, mock `getFootprintCsvExport`, mock `URL.createObjectURL`, assert `downloadBlob` called with expected filename and status text transitions.

**Comparison page (FR-3)** — 4 tests:
- Mounts with one baseline scenario from URL params → renders one card with total.
- Add scenario → 2 cards present, both fetched with the same `X-Comparison-Group` header (assert via mock call args).
- Best-of-N highlight: 2 scenarios with different totals → lower-total card has `best` class/data attr.
- Save comparison: click → `navigator.clipboard.writeText` called with current URL (mock clipboard API), inline `Copied!` appears.

**Cross-cutting (problem parser)** — 2 tests:
- `isProblemDetail` true for `{ detail: "...", title: "...", status: 422 }`; false for strings/null.
- `extractProblemMessage` returns `detail` for Problem+JSON `ApiError`, fallback string for plain `ApiError`, network message for non-`ApiError`.

Total: **10 tests** across detail + comparison + cross-cutting, within the 2–8 per-group guideline.

### Acceptance Criteria
- AC-1: Navigating to `/products/123/footprint` for a known product renders the detail page with total, 4 top-level bars, and a hierarchical breakdown matching the response tree.
- AC-2: Clicking `Recalculate` after editing context bar fields fires exactly one `GET /api/products/123/footprint` with the updated query.
- AC-3: Clicking `Export CSV` downloads a file named `footprint-<correlationId>.csv` and shows inline `Downloaded` status that clears after 3 s.
- AC-4: A 422 response renders the `detail` text inline in red, with a working `Retry` button.
- AC-5: Comparison page generates a UUID, persists it in `?cg=<uuid>`, and includes it as `X-Comparison-Group` on every per-scenario request.
- AC-6: Adding a 4th scenario disables the `+ Add scenario` button; removing scenarios re-enables it.
- AC-7: `Save comparison` copies the current full URL (with `cg` + all `s` params) to clipboard.
- AC-8: Sidebar (desktop) and MobileDrawer (mobile) both show a `Carbon Footprint` entry navigating to `/carbon-footprint`.
- AC-9: Breadcrumbs render correctly on all three new routes.
- AC-10: All 10 tests in `footprint.test.tsx` pass.

### Success Criteria
- No new top-level npm dependencies installed.
- No backend file modified.
- Zero inline hex colour values in new files (Chakra tokens only).
- `extractProblemMessage` is the single normaliser used by all 2 new hooks.
- The 2 V1 pages render correctly on viewports 360px, 768px, 1024px, 1440px.
- Keyboard navigation works on breakdown tree (Tab to composite row → Space/Enter expands).
- Lighthouse/axe baseline: no critical accessibility violations on detail and comparison pages.

---

## Out of Scope (V1)

| Item | Status | Why |
|---|---|---|
| **Historical timeline page** (`/products/:id/footprint/history`) | **Deferred V1.1** | No backend `GET .../footprint/history` endpoint exists; mockup retained in `design-context/` marked DEFERRED. Requires backend follow-up task first. |
| recharts (or any chart library) | Not installed | Dropped with timeline deferral; V1 has no chart. |
| Cross-product comparison | Not in V1 | Mockup is binding and scopes comparison to same-product scenarios only. |
| Embedded `Footprint` tab inside `ProductDetailPage` | Not in V1 | D3 chose separate route + CTA; full-pane layout doesn't fit a tab. |
| Toast / notification system | Not in V1 | Inline status text per Phase-1 decision. |
| Server-side comparison persistence | Not in V1 | URL-encoded shareable state instead. |
| Factor-band overlay, factor-version event log on timeline | Not in V1 | Timeline deferred; no backend surface. |
| Per-leaf custom hover tooltip beyond Chakra default `Tooltip` | Not in V1 | Default Tooltip with `factorVersionId` text is sufficient. |
| OpenAPI codegen / generated types | Not in V1 | Hand-written types in `api/footprints.ts`. |
| react-query introduction | Not in V1 | Manual `useState`/`useEffect` pattern matches existing codebase. |
| Editable scenario titles in comparison | Not in V1 | Auto-generated `Scenario A — <date> / <destination>` only. |
| Strict/lenient mode toggle in UI | Not in V1 | Backend default `STRICT` is used; toggling adds UX complexity without a clear user request. Can be added later as a select in the context bar. |
| Pagination of breakdown rows | Not applicable | Tree is bounded (13 leaves max). |
| Real Emission Factor / Product domain integration | Backend concern | Backend currently runs with `app.footprint.adapters=in-memory`; UI is decoupled. |

### V1.1 follow-ups (separate tasks)
1. **Backend**: add `GET /api/products/{productId}/footprint/history?from=&to=&step=` returning aggregated datapoint list.
2. **Frontend**: implement `ProductFootprintTimelinePage` (chart library decision deferred to that task; recharts the default candidate).
3. **Frontend**: add `View history` button to detail-page topbar (currently omitted in V1 since target route doesn't exist).
4. Optional UX upgrades: editable scenario titles, strict/lenient toggle, destination name resolution (requires backend lookup), batch CSV zip.
