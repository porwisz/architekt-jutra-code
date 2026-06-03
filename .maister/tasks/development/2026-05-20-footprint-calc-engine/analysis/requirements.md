# Requirements — Footprint Calculation Engine

## Initial Description

Implement carbon footprint calculation engine as a core service in the aj kernel. Stateless calculator
+ thin REST GET surface + async audit log + CSV exporter. Pricing-Archetype-lite (in-house) reused
across a 13-node component tree (materials, transport, packaging, cold-storage). Dual surface:
Spring bean `FootprintFacade` + REST `GET /api/products/{id}/footprint`. Strict mode → 422
Problem+JSON; lenient mode → partial breakdown + warnings. Historical reproducibility via
`versionAt(t)`. Comparison via `X-Comparison-Group` header. CSV export as separate slice over
the audit log.

## Source Documents (binding inputs)

- `analysis/design-context/feature-spec.md` — implementation-ready spec; 8 sections; binding.
- `analysis/design-context/brief.md` — product brief (layers 0–3).
- `analysis/design-context/design-decisions.md` — 9 design areas with rationale.
- `analysis/design-context/personas.md` — Marta, Tomek, Anna, Piotr.
- `analysis/design-context/INDEX.md` — 3 HTML mockups (frontend handoff, not built here).
- `analysis/research-context/high-level-design.md` — research HLD (consistent with spec).
- `analysis/codebase-analysis.md` — repo state, conventions, integration points.
- `analysis/clarifications.md` — Phase 1 critical decisions (4).
- `analysis/scope-clarifications.md` — Phase 2 important decisions (5).

## User Journey

- **Marta** (ESG Officer): drives Comparison view by calling REST N times with shared
  `X-Comparison-Group` UUID header; uses CSV export for CSRD reports.
- **Tomek** (Consumer): receives detail breakdown (kg CO₂ per leaf + factor + validFrom)
  rendered by frontend.
- **Anna** (Internal Auditor): downloads CSV breakdown by `correlationId` to reconcile in Excel.
- **Piotr** (Backend developer): integrates engine in-process via `FootprintFacade` Spring bean,
  catches typed sealed exceptions, uses `dryRun=true` for tests.

## Existing Code Reuse

- Domain layout mirrors `pl.devstyle.aj.product` / `category`.
- Persistence: `BaseEntity` (SEQUENCE id, `@CreatedDate`, `@Version`).
- Liquibase YAML under `db/changelog/2026/` (next free index `011`).
- JOOQ codegen automatic; package `pl.devstyle.aj.jooq`.
- Security: centralized `SecurityConfiguration` — add 2 `requestMatchers` (no `@PreAuthorize`).
- Tests: `TestcontainersConfiguration`, `SecurityMockMvcConfiguration`, `@WithMockEditUser`.
- JSONB pattern via `@JdbcTypeCode(SqlTypes.JSON)`.

## Visual Assets

3 HTML mockups in `analysis/design-context/mockups/` are server outputs consumed by a separate
future frontend task. The engine ships the REST + facade surface only; no UI build here.

## Binding Decisions (Phase 1 + 2)

1. Pricing Archetype: **build in-house** under `pl.devstyle.aj.archetype.pricing`.
2. Error format: **Problem+JSON via Spring `ProblemDetail`**, scoped to footprint package only.
3. SPI ports `EmissionFactorPort` + `ProductAttributesPort`: **in-memory stub adapters** in V1.
4. CSV export slice: **in scope** as `pl.devstyle.aj.footprint.export`.
5. CSV export authorization: **reuse PERMISSION_READ**.
6. Problem URI base: **externalized** via `app.footprint.problem-base-uri` config property.
7. Liquibase format: **YAML**.
8. CSV library: **Apache Commons CSV** dependency.
9. Audit retry: **spring-retry + spring-aspects + `@EnableRetry`** with `@Retryable`/`@Recover`.

## Functional Requirements (summary)

- `FootprintFacade.calculateTotal(params, options)` — full kg CO₂ breakdown tree.
- `FootprintFacade.calculateUnit(params, options)` — per-100g normalised breakdown (adapter over
  `calculateTotal`); preserves `sum(children) == parent` after scaling.
- `GET /api/products/{id}/footprint` — query params for all overrides (asOf, destination,
  comparisonGroupId, strictness, normalisation, dryRun). Pure GET.
- `GET /api/footprints/calculations/{correlationId}/export?format=csv` — flatten audit row to CSV.
- 13-node component tree with `Applicability` (cold-storage excluded for non-refrigerated).
- Async audit via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Retryable`.
- Strict mode → sealed exception → 422 Problem+JSON. Lenient mode → partial breakdown + warnings list.
- Historical reproducibility: `EmissionFactorPort.versionAt(componentId, t)` returns version where
  `validFrom ≤ t < validTo`. `REJECT_OVERLAPPING` invariant.
- Determinism: same params → byte-identical breakdown (HALF_UP @ 4 decimals).

## Out of Scope (deferred)

- Real Emission Factor Management module + storage of factor versions.
- Real ProductAttributes resolution from Product entity (V1 = stub).
- Frontend UI (mockups handed to FE team).
- Result caching.
- Retention/archival policy for audit log.
- Kernel/plugin module split.

## Acceptance Criteria (9 scenarios, copy from feature-spec.md §8)

Historical reproducibility; audit persistence; applicability; strict missing-factor 422;
lenient missing-factor partial+warnings; dryRun no-audit-no-side-effects; comparison group
correlation; CSV export flattening; calculateUnit invariant.

All 9 implemented as integration tests (`@SpringBootTest` + TestContainers PG18 + MockMvc).

## Technical Considerations

- Java 25 features acceptable (records, sealed types, pattern matching).
- Lombok per project convention (`@Getter/@Setter/@NoArgsConstructor` only on entities).
- No `CascadeType.ALL`; no `@PreAuthorize`; LAZY default; business-key equals.
- No `*` in middle of Spring Security 7 path patterns; use single-segment `*` correctly.
- jOOQ for any complex audit-log read; JPA for CRUD.
