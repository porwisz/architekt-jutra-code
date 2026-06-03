# Design Context Index

Source: product-design task `2026-05-20-footprint-calc-engine` (Phase 8 complete).

## Screens

| ID | Source Mockup | Description |
|---|---|---|
| screen:product-footprint-detail | mockups/product-footprint-detail.html | Single-product footprint detail view: product card, context bar (asOf/destination/season), big total kgCO₂ card, scope-coloured bar chart, hierarchical breakdown table with factor + validFrom per leaf. |
| screen:product-footprint-comparison | mockups/product-footprint-comparison.html | Three-column scenario cards sharing an `X-Comparison-Group` ID, delta table, best-of-N highlight. |
| screen:product-footprint-historical-timeline | mockups/product-footprint-historical-timeline.html | Line chart with seasonal factor-band overlay, factor-version events log, datapoints table with batch CSV export. |

## Brief & Specification

- `brief.md` — Layer 0–3 product brief (problem, personas, decisions, mockup refs).
- `feature-spec.md` — Implementation-ready spec (module structure, domain model, REST API, component tree, calculation flow, audit logging, error taxonomy, acceptance criteria).
- `design-decisions.md` — Full rationale for 9 design areas.
- `alternatives.md` — 8 decision areas, 26 alternatives explored.
- `problem-statement.md` — Full problem framing.
- `personas.md` — Marta, Tomek, Anna, Piotr persona cards.
