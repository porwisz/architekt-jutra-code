# Phase 2 Scope Clarifications

All 9 decisions from `gap-analysis.md` resolved.

## Critical

| # | Decision | Choice |
|---|----------|--------|
| C1 | Comparison screen UX | **A. Same-product scenarios only.** `+ Add scenario` opens date/destination/storage-days form for the same SKU; backend re-computes per scenario. |
| C2 | Historical timeline access | **A. Per-product only.** Route `/products/:id/footprint/history`, reached from the product footprint detail page. |
| C3 | ProductDetailPage integration | **B. Separate route + CTA button.** Add `View carbon footprint` CTA on `ProductDetailPage.tsx`; navigates to `/products/:id/footprint`. Standalone full-pane layout. |

## Important (all defaults accepted)

| # | Decision | Choice |
|---|----------|--------|
| I1 | Route naming | Nested under product: `/products/:id/footprint`, `/products/:id/footprint/compare`, `/products/:id/footprint/history`. Top-level sidebar landing at `/carbon-footprint` redirects/links to a product picker. |
| I2 | Save-comparison button | Shareable URL — scenarios encoded as query params, copied to clipboard. No backend persistence. |
| I3 | Timeline factor-band overlay + factor-version event log | **Skipped for V1.** Both require non-existent backend endpoints. Chart + datapoints table only. |
| I4 | Batch CSV export from timeline | Sequential client-side downloads with inline status text (matches Phase 1 decision). |
| I5 | Detail context bar recalc mode | **Explicit `Recalculate` button.** No live re-fetch on input change. |
| I6 | X-Comparison-Group UUID source | Client-generated via `crypto.randomUUID()`, persisted in URL. |

## Out of scope (V1)

- Cross-product comparison.
- Cross-product timeline.
- Footprint as embedded tab inside `ProductDetailPage`.
- Toast/notification system.
- Server-side comparison persistence.
- Factor-band overlay + factor-version event log on timeline.
- Real Emission Factor / Product domain integration (still V1-stubbed in backend).

## Open for spec phase

- Final shape of `/carbon-footprint` sidebar landing (product picker layout). Likely a simple search-by-SKU input → routes to per-product detail.
- API path prefix convention verification when writing `src/api/footprints.ts`.
- TypeScript types for the backend response: derive manually from the Java DTO definitions OR generate from OpenAPI (defer the codegen decision; manual types for V1).
