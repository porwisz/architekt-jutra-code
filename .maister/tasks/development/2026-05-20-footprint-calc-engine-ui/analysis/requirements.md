# Requirements — Footprint Calculator UI

## Initial description
"Implement UI" for the footprint calculator. Backend (task `2026-05-20-footprint-calc-engine`) is complete and exposes:
- `GET /api/products/{productId}/footprint` returning total kgCO₂e + per-100g intensity + 13-node breakdown tree, Problem+JSON 400/409/422 errors.
- `GET /api/footprints/calculations/{correlationId}/export?format=csv` (12-column CSV, RFC 4180).

## Binding inputs

### Mockups (`analysis/design-context/mockups/`)
1. `product-footprint-detail.html` — product card sidebar + main pane with context bar, breakdown tree, recalculate button, export CTA.
2. `product-footprint-comparison.html` — same-product multi-scenario comparison (Add scenario / Save comparison).
3. `product-footprint-historical-timeline.html` — single-SKU footprint history chart + datapoints table.

### Product brief
`analysis/design-context/brief.md` — internal-user persona, product-management workflow integration.

## Q&A summary

### Phase 1 (technical)
| Topic | Decision |
|-------|----------|
| Chart library | recharts |
| Sidebar nav placement | top-level "Carbon Footprint" entry |
| CSV export feedback | inline status text (no toast system) |
| Problem+JSON parser | shared `src/api/problem.ts` |

### Phase 2 critical (IA)
| Topic | Decision |
|-------|----------|
| Comparison UX | same-product scenarios only (`+ Add scenario` form) |
| Timeline access | per-product only at `/products/:id/footprint/history` |
| ProductDetailPage integration | separate route + CTA button (no embedded tab) |

### Phase 2 important
| Topic | Decision |
|-------|----------|
| Route naming | nested `/products/:id/footprint/*` + `/carbon-footprint` landing |
| Save-comparison | shareable URL (query params + copy-to-clipboard) |
| Factor-band overlay + event log on timeline | **skipped V1** (no backend endpoint) |
| Batch CSV export | sequential client-side downloads with inline status |
| Recalc trigger | explicit `Recalculate` button (no live re-fetch) |
| Comparison-group UUID | client-generated `crypto.randomUUID()`, in URL |

### Phase 5 (this round)
| Topic | Decision |
|-------|----------|
| TypeScript types | hand-written in `src/api/footprints.ts`, mirroring Java DTOs |
| Persona | internal authenticated host-app user (PERMISSION_READ) |
| Breakdown UI | expand-collapse nested rows: 4 top-level groups → child components with kgCO₂e + % of total. Cold-storage omitted when backend gate hides it. |

## Similar features identified
- `pages/ProductDetailPage.tsx` (211L) → template for footprint detail
- `pages/ProductListPage.tsx` (375L) → template for comparison (table + selection)
- `hooks/useProducts.ts` (78L) → template for `useProductFootprint`, `useFootprintComparison`, `useProductFootprintHistory`
- `api/products.ts` (70L) → template for `api/footprints.ts`

## Visual assets & insights
3 HTML mockups + brief at `analysis/design-context/`. INDEX.md enumerates screens with stable IDs (`screen:footprint-detail`, `screen:footprint-comparison`, `screen:footprint-historical-timeline`). Mockups are binding: layout, copy, field order, and explicit states must match.

## Functional requirements

### FR-1 — Carbon Footprint sidebar landing (`/carbon-footprint`)
Top-level sidebar entry. Renders a simple product picker (search-by-SKU/name input → list → click navigates to `/products/:id/footprint`).

### FR-2 — Product Footprint Detail page (`/products/:id/footprint`)
Per `screen:footprint-detail` mockup. Reads `GET /api/products/{productId}/footprint`. Shows: product card, context bar (date/destination/storage-days inputs + explicit `Recalculate` button), totals (kgCO₂e + per-100g + strict/lenient badge), breakdown tree (expand-collapse, 4 groups → child components, kgCO₂e + %), export CSV button (with inline status text).

### FR-3 — Footprint Comparison page (`/products/:id/footprint/compare`)
Per `screen:footprint-comparison` mockup. Same-product, multi-scenario. Starts with one baseline scenario (from URL query); `+ Add scenario` adds a new scenario row (date/destination/storage-days form). Each scenario invokes the backend independently with a shared `X-Comparison-Group` UUID. Delta column shows kgCO₂e diff vs baseline. `Save comparison` button copies a shareable URL to clipboard. Sequential per-scenario CSV export with inline status.

### FR-4 — Footprint History page — **DEFERRED to V1.1**
Backend has no history-list endpoint (only correlation-id-keyed CSV export). Deferred to a follow-up task gated on a new `GET /api/products/{productId}/footprint/history` endpoint. The `historical-timeline` mockup remains in `design-context/` for reference but is **out of V1 scope**. Update `design-context/INDEX.md` to mark `screen:footprint-historical-timeline` as deferred.

### FR-5 — Detail-page CTA on ProductDetailPage
Add a `View carbon footprint` button to `pages/ProductDetailPage.tsx` navigating to `/products/:id/footprint`.

### FR-6 — Cross-cutting helpers
- `src/api/problem.ts` — `ProblemDetail` type, `isProblemDetail(body)`, `extractProblemMessage(error)`.
- `src/utils/download.ts` — `downloadBlob(blob, filename)`.
- `src/utils/format.ts` extensions — `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`. Move duplicated `formatPrice` here too.
- `src/components/shared/Icons.tsx` — add `FootprintIcon` (lucide `Leaf`).

### FR-7 — Navigation wiring
Add `Carbon Footprint` nav entry to `Sidebar.tsx` + `MobileDrawer.tsx`. Extend `Header.tsx` `getBreadcrumbs()` for all new routes.

### FR-8 — Error handling
All hooks use the new Problem+JSON parser. 400/409/422 surface user-friendly `detail` text inline (matches existing inline-error pattern).

### FR-9 — Tests
Per the frontend testing standard: per-feature `renderWithProviders`, `vi.mock` API modules, ~2–8 tests per page covering happy path, loading, error (incl. 422 via Problem+JSON), CSV export trigger.

## Reusability opportunities

| Existing | Reuse for |
|----------|-----------|
| `api/client.ts` (`api`, `ApiError`) | All new endpoints |
| `useProducts.ts` hook shape | All 3 new hooks |
| `ProductDetailPage.tsx` page structure | All 3 new pages |
| `ConfirmDialog`, `EmptyState`, `PrimaryButton` | Compare delete-scenario, empty state, primary actions |
| Chakra theme tokens (`brand.*`, `accent.*`) | All UI styling |
| `formatDate` from `utils/format.ts` | History timeline + comparison metadata |
| `AuthGuard` wrapping | New routes (under existing protected Layout) |

## Scope boundaries

### In scope (V1)
**2 pages** (detail + comparison), 2 hooks, 1 API module, 4 shared helpers (problem parser, blob download, formatters, footprint icon), nav wiring, ProductDetailPage CTA, tests. **recharts dependency dropped** — V1 has no chart (timeline deferred).

### Out of scope (V1)
- **Historical timeline page** (deferred V1.1, requires new backend endpoint).
- Cross-product comparison or timeline.
- Embedded tab inside ProductDetailPage.
- Toast/notification system.
- Server-side comparison persistence.
- Factor-band overlay + factor-version event log.
- Real Emission Factor / Product domain integration (backend still stubbed via `app.footprint.adapters=in-memory`).
- OpenAPI codegen.
- react-query introduction.

## Open questions for spec phase

1. **API path prefix** — verify `api.get('/products/...')` vs `/api/products/...` convention by reading client.ts and matching `api/products.ts`.
2. `/carbon-footprint` landing layout details (mockup doesn't show it explicitly).

## Technical considerations

- **No new top-level dependencies** (recharts dropped with timeline deferral).
- All work in `src/main/frontend/`; backend untouched.
- jOOQ/Java tests untouched; only Vitest test suite gains new tests.
- Build pipeline (`vite build` → `../resources/static`) unchanged.
- Auth: relies on existing JWT in localStorage, AuthGuard.
- Accessibility: must hit Chakra's defaults; keyboard navigation on breakdown tree expand/collapse + tab order on comparison form. Color contrast 4.5:1 per global standard.
- Responsive: mobile-first per global standard; layout collapses sidebar product card under main pane on narrow viewports.
