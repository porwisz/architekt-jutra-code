# Phase 1 Clarifications

Answers binding for downstream phases.

| # | Question | Decision |
|---|----------|----------|
| 1 | Chart library for historical timeline | **Recharts** — declarative, React 19 compatible. Add `recharts` to `src/main/frontend/package.json`. |
| 2 | Footprint nav placement | **Top-level sidebar entry** ("Carbon Footprint"). Add to `Sidebar.tsx` + `MobileDrawer.tsx` + `Header.tsx` breadcrumbs. Comparison + global timeline live under this entry; per-product detail also linked from the existing product detail page. |
| 3 | CSV export feedback | **Inline status text** near the export button — matches existing inline-error pattern. No global toast system. |
| 4 | Problem+JSON parser location | **Shared helper at `src/api/problem.ts`** — exports `ProblemDetail` type, `isProblemDetail(body)`, and `extractProblemMessage(error)`. Reusable by all hooks; first consumer is the footprint feature. |

## Deferred (not blocking)

- API path prefix convention (`/api/...` vs `/...`) — verify when writing `src/api/footprints.ts`; follow whatever existing modules do.
- Route naming (`/products/:id/footprint`, `/footprints/compare`, `/products/:id/footprint/history`) — finalise during spec phase based on IA review.
