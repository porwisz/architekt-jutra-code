# Visual Coverage Matrix

Source: `analysis/design-context/INDEX.md`

| Screen/Component ID | Covered By Task Group(s) | Mockup | Status |
|---------------------|--------------------------|--------|--------|
| screen:footprint-detail | Group D (shared components — ScopeChip, ScopeBar, BreakdownTree, CsvExportButton), Group E (ProductFootprintPage — page layout, product card, context bar, summary card, top-level bars, breakdown table, export wiring) | `analysis/design-context/mockups/product-footprint-detail.html` | Covered |
| screen:footprint-comparison | Group D (shared components reused — ScopeChip, ScopeBar via mini-bars, CsvExportButton per card), Group G (FootprintComparisonPage — scenario card grid, best-of-N highlight, delta table, Save comparison, Add scenario controls) | `analysis/design-context/mockups/product-footprint-comparison.html` | Covered |
| screen:footprint-historical-timeline | — | `analysis/design-context/mockups/product-footprint-historical-timeline.html` | DEFERRED V1.1 (intentionally uncovered) |

## Uncovered Items

- **screen:footprint-historical-timeline** — explicitly marked `DEFERRED V1.1` in `INDEX.md`. Requires a new backend `GET /api/products/{productId}/footprint/history` endpoint that does not exist. Spec §"Out of Scope (V1)" lists this as deferred with a separate V1.1 follow-up task. The mockup is retained in `design-context/` for future reference but is not implemented in this task — by design, not omission.

All in-scope screens (2/2 active screens) are covered by at least one task group with explicit `Visual References` bindings to their mockup files.
