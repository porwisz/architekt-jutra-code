# Product Brief — Footprint Calculation Engine

**Status**: Design approved (Phase 8 complete) · **Date**: 2026-05-20 · **Source**: research task `2026-04-17-footprint-calc-engine`

This brief is a summary document for development handoff. Full detail lives in `analysis/`.

---

## Layer 0 — Core Brief

### Problem Statement

System produktowy **aj** musi dostarczać deterministyczne, audytowalne obliczenie **carbon footprint produktu** w zadanym kontekście (timestamp, destynacja, atrybuty produktu). Wynik musi być reprodukowalny historycznie (regulacja) i pełni rozbity na komponenty (audyt). Silnik jest **core service** w platformie aj, służy zarówno frontendowi (REST) jak i innym backend services (in-process Spring bean) z identyczną semantyką.

### Target Users

Four personas (full detail in [`analysis/personas.md`](../analysis/personas.md)):

- **Marta** — Sustainability Manager / ESG Officer. Needs trustworthy per-product footprints for CSRD reports, supplier scorecards, what-if comparisons.
- **Tomek** — Online grocery shopper. Wants a quick "is this greener?" answer with hoverable explanations.
- **Anna** — Internal auditor. Needs **CSV export** of breakdowns to reconcile in Excel/BI tools.
- **Piotr** — Backend developer integrating the engine via Spring bean and REST.

### Feature Overview

Stateless calculation engine + thin REST surface + audit log + CSV exporter.

- `FootprintFacade.calculateTotal(params, options)` — full-product kg CO₂ breakdown.
- `FootprintFacade.calculateUnit(params, options)` — per-100g normalised view (adapter over `calculateTotal`).
- `GET /api/products/{id}/footprint?asOf=&...` — pure-GET REST surface; query params for all overrides.
- `GET /api/footprints/calculations/{correlationId}/export?format=csv` — CSV export of a past breakdown (separate slice).
- 13-node component tree: materials, transport, packaging, cold-storage (applicability: refrigerated only).
- Sealed exception hierarchy → Problem+JSON (RFC 7807) for strict mode.
- Async audit write via `@TransactionalEventListener(AFTER_COMMIT)`; comparison linkage via `X-Comparison-Group` header.

### Constraints

1. **Pure-math API** — engine receives pre-resolved numeric inputs (distances in km, weight in kg). Destination-name resolution stays in the application layer.
2. **Pricing Archetype reuse** — `com.softwarearchetypes.pricing` provides Calculator / Component / Validity / Applicability. Not rebuilt.
3. **Dual surface, identical semantics** — REST + Spring bean.
4. **V1 includes both methods** — `calculateTotal` and `calculateUnit` first-class.
5. **No caching V1** — compute every call. Acceptable given tree size and indexed factor reads.
6. **Configurable strictness per call** — `STRICT` (fail-fast typed exception → 422) or `LENIENT` (partial breakdown + warnings).
7. **Engine writes audit log** — async, via Spring event listener (`AFTER_COMMIT`).
8. **Core-service placement** — engine is part of the kernel, not a plugin.

### Success Criteria

| # | Criterion |
|---|---|
| 1 | Determinism — same params → byte-identical breakdown |
| 2 | Historical reproducibility — past `t` uses past factor versions |
| 3 | Full breakdown — kgCO₂, scope, factor info on every leaf |
| 4 | Applicability — non-refrigerated products exclude cold-storage subtree |
| 5 | Context sensitivity — different destination/season → different result without code change |
| 6 | Audit guarantee (eventually consistent) — every non-dryRun call → audit row within 5 s |
| 7 | Strict mode fails loudly — missing factor → 422 Problem+JSON with precise code |
| 8 | Lenient mode degrades gracefully — partial breakdown + warnings, frontend-renderable |
| 9 | UI breakdown surface — three mockups (detail/comparison/historical) acceptable to frontend team |
| 10 | CSV export — Anna downloads flattened breakdown by `correlationId` |

### Acceptance Criteria (Test Scenarios)

Nine integration test scenarios (TestContainers PostgreSQL, MockMvc) covering historical reproducibility, audit persistence, applicability, strict/lenient modes, dryRun, comparison groups, CSV export, calculateUnit invariant. See `analysis/feature-spec.md` Section 8 for full Given/When/Then.

---

## Layer 1 — Persona Cards (summary)

| Persona | Drives |
|---|---|
| Marta (ESG Officer) | Comparison view; multi-context what-if calls |
| Tomek (Consumer) | Detail view; hoverable per-component explanations |
| Anna (Internal Auditor) | **CSV export** by correlationId; historical lookup |
| Piotr (Developer) | Clean dual-surface API; typed exceptions; dryRun for tests |

Full detail in [`analysis/personas.md`](../analysis/personas.md).

---

## Layer 2 — Design Decisions (summary)

| Area | Choice |
|---|---|
| Parameters shape | Flat Java record + separate `CalculationOptions` |
| `calculateUnit()` | Adapter (post-divide breakdown tree) |
| Audit schema | Single row per call, breakdown stored as JSONB |
| Audit write | **Async** via `@TransactionalEventListener(AFTER_COMMIT)` |
| Errors | Per-node warnings + Problem+JSON for strict errors |
| REST contract | **Pure GET** with query params |
| CSV export | Separate `footprint-export` slice, reads audit log |
| Comparison | N client-side GETs + `X-Comparison-Group` header |
| Historical lookup | **Always recompute** via `versionAt(t)`; no verbatim audit retrieval endpoint |

Trade-offs accepted: audit guarantee becomes eventually-consistent; URL length caps comparison flexibility; historical accuracy depends on Emission Factor Management's REJECT_OVERLAPPING immutability.

Full rationale and alternatives:
- [`analysis/design-decisions.md`](../analysis/design-decisions.md)
- [`analysis/alternatives.md`](../analysis/alternatives.md) (8 decision areas, 26 alternatives)

---

## Layer 3 — Mockup References

Three HTML/CSS wireframes generated by the visual companion, saved to `analysis/mockups/`:

| Screen | File | Notes |
|---|---|---|
| Product Footprint Detail | [`mockups/product-footprint-detail.html`](../analysis/mockups/product-footprint-detail.html) | Product card, context bar, big total card, scope-coloured bar chart, hierarchical breakdown table with factor + validFrom per leaf |
| Product Footprint Comparison | [`mockups/product-footprint-comparison.html`](../analysis/mockups/product-footprint-comparison.html) | 3-column scenario cards with shared `X-Comparison-Group` ID, delta table, best-of-N highlight |
| Product Footprint Historical Timeline | [`mockups/product-footprint-historical-timeline.html`](../analysis/mockups/product-footprint-historical-timeline.html) | Line chart with seasonal factor-band overlay, factor-version events log, datapoints table with batch CSV export |

Cross-screen navigation: clickable `data-screen` links wired between all three. Annotations embedded explain integration points (correlationId, factor lookup, comparison-group header).

---

## References

- **Specification (implementation-ready)**: [`analysis/feature-spec.md`](../analysis/feature-spec.md) — 8 sections covering module structure, domain model, REST API, component tree, calculation flow, audit logging, error taxonomy, acceptance criteria
- **Design context**: [`analysis/design-context.md`](../analysis/design-context.md) — synthesis of project docs, research, tech stack
- **Problem statement (full)**: [`analysis/problem-statement.md`](../analysis/problem-statement.md)
- **Personas**: [`analysis/personas.md`](../analysis/personas.md)
- **Alternatives explored**: [`analysis/alternatives.md`](../analysis/alternatives.md)
- **Design decisions (full rationale)**: [`analysis/design-decisions.md`](../analysis/design-decisions.md)
- **Research input (HLD)**: [`context/research-context/high-level-design.md`](../context/research-context/high-level-design.md)
- **Project docs**:
  - [`.maister/docs/project/tech-stack.md`](../../../docs/project/tech-stack.md) — Java 25, Spring Boot 4.0.5, PostgreSQL, JPA+JOOQ, Liquibase
  - [`.maister/docs/project/architecture.md`](../../../docs/project/architecture.md) — microkernel target (pre-alpha)
