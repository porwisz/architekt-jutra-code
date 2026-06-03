# Gap Analysis: Footprint Calculation Engine

**Date**: 2026-05-20
**Task**: `2026-05-20-footprint-calc-engine`
**Inputs**: codebase-analysis.md, clarifications.md (4 binding decisions), design-context/{brief,feature-spec,design-decisions,personas,INDEX}.md, research-context/high-level-design.md

---

## Summary

The Footprint Calculation Engine is a net-new BACKEND service that will sit as a regular domain package (`pl.devstyle.aj.footprint`) inside the existing `pl.devstyle:aj` Spring Boot 4.0.5 monolith, reusing every cross-cutting facility already in place (REST, JPA + BaseEntity, Liquibase, JOOQ codegen, JWT permission auth, TestContainers PG18) and adding four net-new patterns scoped to the footprint slice (archetype-lite primitives, `@TransactionalEventListener` AFTER_COMMIT, Problem+JSON, CSV export). All four scope/approach decisions surfaced in Phase 1 are already resolved (clarifications.md) — no critical decisions remain open for the orchestrator.

- **Risk level**: medium
- **Effort estimate**: medium-high
- **Detected characteristics**: creates_new_entities, involves_data_operations (audit), backend-only

---

## Task Characteristics

| Characteristic | Value | Evidence |
|---|---|---|
| has_reproducible_defect | false | Greenfield feature; no defect signals in inputs. |
| modifies_existing_code | true (narrow) | `SecurityConfiguration` requestMatchers, Liquibase master `includeAll`, JOOQ codegen pickup, optional new `Permission` enum entries. No behavior change in existing modules. |
| creates_new_entities | true | `FootprintAuditEntity` + entire `pl.devstyle.aj.footprint` package + `pl.devstyle.aj.archetype.pricing` package + `pl.devstyle.aj.footprint.export`. |
| involves_data_operations | true | CREATE (audit row written async after each non-dryRun calc) and READ (CSV exporter reads by correlationId). No UPDATE/DELETE. |
| ui_heavy | false | Engine is backend; HTML mockups are server-side previews handed off to a future frontend team — out of scope for this task. |

---

## Desired vs Current State

### Desired (per spec + clarifications)
1. Archetype-lite primitives under `pl.devstyle.aj.archetype.pricing` (Calculator, SimpleFixedCalculator, Component, SimpleComponent, CompositeComponent, Validity (half-open, REJECT_OVERLAPPING), Applicability enum, ComponentVersion, QuantityExtractor, ParameterValue).
2. `pl.devstyle.aj.footprint` engine: api / internal / audit / web / config sub-packages per spec §1.
3. Public API types: `FootprintFacade`, `FootprintParameters`, `CalculationOptions`, sealed `BreakdownNode` (`LeafNode`/`CompositeNode`), `FootprintBreakdown`, `FootprintWarning`, `FactorVersionRef`, `Strictness`, `Normalisation`, sealed exception hierarchy.
4. 13-node `ComponentTreeRegistry` (4 composites + 9 leaves) with `REFRIGERATED_ONLY` applicability on `cold-storage`.
5. Two SPI port interfaces (`EmissionFactorPort`, `ProductAttributesPort`) + in-memory stub adapters (per clarification 3) seeded for the 9 acceptance scenarios.
6. `DefaultFootprintFacade` with deterministic DFS post-order calculation, HALF_UP @ 4 decimals, composite-sum invariant, strict/lenient failure semantics, FUTURE/ANCIENT timestamp warnings.
7. `BreakdownScaler` for `calculateUnit` (PER_100G); shares audit row with TOTAL.
8. Async audit: `FootprintCalculatedEvent` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Retryable(3, exponential)` + `@Recover` terminal handler + failure metric.
9. `footprint_audit_log` table via Liquibase changelog (id, correlation_id UUID unique, comparison_group_id, caller_id, product_id, timestamp_param, computed_at, strictness, normalisation, total_kg_co2 numeric(18,6), warnings_count, factor_version_ids text[], breakdown jsonb, params jsonb; 3 indexes incl. GIN on factor_version_ids).
10. `GET /api/products/{productId}/footprint` (10 query params, 3 X- headers) returning JSON envelope per spec §3.
11. `FootprintExceptionHandler` (`@RestControllerAdvice` package-scoped) returning `application/problem+json` per RFC 7807 with `code` extension property — leaving `GlobalExceptionHandler`/`ErrorResponse` intact for all other modules (per clarification 2).
12. `GET /api/footprints/calculations/{correlationId}/export?format=csv` flattening audit row → one row per leaf (12 columns, dot-delimited `component_path`, JSON-literal `warnings`).
13. Security: register both routes in `SecurityConfiguration` requestMatchers under `PERMISSION_READ` (and optional `PERMISSION_FOOTPRINT_EXPORT` if introduced) using single-segment `*` for the `{productId}` PathPattern.
14. Tests: 9 integration tests (Scenarios A–I) using TestContainers PG18 + MockMvc + stub port fixtures, plus ~3 pure-JUnit unit tests for `BreakdownScaler` invariants and the deterministic facade contract.

### Current
- Spring Boot 4.0.5 / Java 25 single Maven module; domain packages for category/product/plugin/user/auth exist.
- BaseEntity, JPA conventions, Liquibase pipeline (master `includeAll` → `db/changelog/2026/`, next index **011**), JOOQ codegen (`pl.devstyle.aj.jooq.*`), TestContainers config, JWT auth with permission claims, `SecurityConfiguration`, `GlobalExceptionHandler`/`ErrorResponse`, MockMvc test scaffolding — all present and reusable.
- **None** of items 1–14 above exist.

---

## Gaps Identified (Ordered Build List)

Sequencing reflects dependency order; group labels are work-package suggestions for the planner.

### Group 1 — Archetype-lite primitives (foundation)
1. `pl.devstyle.aj.archetype.pricing.Calculator` interface.
2. `SimpleFixedCalculator` — `rate × quantity`, BigDecimal HALF_UP @ 4 decimals.
3. `Component` sealed interface + `ComponentId`, `CalculatorId`, `ComponentVersion` records.
4. `SimpleComponent` (leaf), `CompositeComponent` (sum aggregation, skips inactive children).
5. `Validity` — half-open `[validFrom, validTo)`, `versionAt(componentId, t)` returning `Optional<ComponentVersion>`, REJECT_OVERLAPPING enforcement.
6. `Applicability` enum (V1: `ALWAYS`, `REFRIGERATED_ONLY`) + `QuantityExtractor` functional interface + `ParameterValue` record.
7. Pure-JUnit unit tests for primitives (rounding, half-open boundary, overlap rejection, composite sum invariant) — no Spring, no mocks.

### Group 2 — Engine domain types (public API)
8. `pl.devstyle.aj.footprint.api.FootprintFacade` interface.
9. `FootprintParameters` record with compact-constructor invariants (timestamp/productId non-null; weight > 0; distances/storageDays ≥ 0).
10. `ProductId` value record with blank guard.
11. `CalculationOptions` record + 3 static factories (`strictTotal`, `lenientTotal`, `strictPer100g`).
12. `Strictness`, `Normalisation` enums.
13. Sealed `BreakdownNode` (`LeafNode`, `CompositeNode`); `FootprintBreakdown`; `FootprintWarning`; `FactorVersionRef`.
14. Sealed `FootprintCalculationException` hierarchy (5 subclasses: `MissingFactorException`, `MissingProductAttributeException`, `InvalidParametersException`, `ApplicabilityResolutionException`, `FactorVersionOverlapException`) each carrying `code` + `details` map.

### Group 3 — SPI ports + in-memory stub adapters
15. `internal.ports.EmissionFactorPort` interface + `EmissionFactorVersion` record.
16. `internal.ports.ProductAttributesPort` interface + `ProductAttributes` record.
17. In-memory stub `EmissionFactorPort` adapter (seeded summer/winter factors for Scenario A, gap for Scenario D, missing-last-mile for Scenario E).
18. In-memory stub `ProductAttributesPort` adapter (seeded OFB-330 refrigerated and CAW-042 non-refrigerated).
19. Wire stubs via `@Component` (or `@Configuration` in `config/`) — fail-fast `@Autowired(required=true)` per spec §1.

### Group 4 — Engine internals
20. `ComponentTreeRegistry` — immutable static map of 13 `ComponentDefinition`s built at bean init.
21. `EmissionCalculator` (wraps `SimpleFixedCalculator`).
22. `BreakdownTreeBuilder` — DFS post-order walk; applicability pre-pass; leaf factor resolution; strict/lenient branching; FUTURE/ANCIENT timestamp warning emission; INACTIVE_SUBTREE warning emission.
23. `BreakdownScaler` — recursive recompute of composites from scaled leaves (preserves sum invariant).
24. `RoundingPolicy` static utility (HALF_UP, scale=4).
25. `DefaultFootprintFacade` (`@Service`) — `calculateTotal` (publishes event unless dryRun) and `calculateUnit` (calls calculateTotal once, scales, no second event).
26. `FootprintModuleConfig` — bean wiring for registry + calculator + facade.

### Group 5 — Audit subsystem
27. `FootprintCalculatedEvent` record.
28. `FootprintAuditEntity` extending `BaseEntity` — fields per Liquibase columns; JSONB via `@JdbcTypeCode(SqlTypes.JSON)`; `String[]` for `factor_version_ids`; Lombok minimal subset; `@SequenceGenerator allocationSize=1`; business-key equals on `correlation_id`.
29. `FootprintAuditRepository` (Spring Data JPA).
30. `FootprintAuditEntityMapper` — breakdown → JSON, params → JSON, warning count.
31. `FootprintAuditListener` (`@Component`) with `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)` + `@Retryable(3, exponential backoff 200ms ×2)` + `@Recover` terminal handler.
32. Micrometer counter `footprint_audit_failure_total` (or equivalent).
33. **New pattern note**: Enable Spring `@EnableRetry` (verify config); confirm `spring-retry` + `spring-aspects` are on the classpath (add to `pom.xml` if absent — neither is currently present per Phase 1).

### Group 6 — Persistence migration
34. `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml` (or `.xml` — spec example is XML, project convention is YAML; align with existing convention → use YAML) creating `footprint_audit_log` with unique constraint on `correlation_id`, defaults, and the three indexes (b-tree on `(product_id,timestamp_param)`, b-tree on `comparison_group_id`, GIN on `factor_version_ids`).
35. JOOQ generator picks up new table automatically on `mvn compile` — verify generated class appears under `pl.devstyle.aj.jooq.tables`. No manual codegen change.

### Group 7 — Web layer (engine)
36. `FootprintController` — `GET /api/products/{productId}/footprint`; binds 10 query params + 3 `X-*` headers (via `@RequestHeader(required=false)`); calls facade; maps to `FootprintResponseDto` envelope.
37. `FootprintQueryRequest` + `FootprintResponseDto` records (Jackson-friendly; static `from(...)` mappers per project convention).
38. `FootprintExceptionHandler` (`@RestControllerAdvice(basePackages = "pl.devstyle.aj.footprint")`) — one `@ExceptionHandler` per sealed exception subclass, returning `ProblemDetail` with `code`, `componentId`, `correlationId`, `field` extension properties; correct HTTP statuses (400, 409, 422).
39. Register problem `type` URI base (e.g., `https://aj.example.com/problems/{kebab-code}`) — confirm placeholder host is acceptable for V1.

### Group 8 — CSV export slice
40. `pl.devstyle.aj.footprint.export.FootprintExportController` — `GET /api/footprints/calculations/{correlationId}/export?format=csv`; 404 if missing; 400 if `format != csv`.
41. `BreakdownCsvFlattener` — walks JSONB `breakdown`, emits one row per leaf, dot-delimited `component_path` (root excluded), JSON-literal `warnings`, locale-invariant decimal `.`, no BOM, 12-column header.
42. `FootprintExportConfig` — wires flattener + repo reader. Choose: Apache Commons CSV vs hand-rolled. Recommend hand-rolled (minimal-implementation standard; no new dependency).

### Group 9 — Security wiring
43. Update `SecurityConfiguration.SecurityFilterChain` to add two `requestMatchers`:
    - `GET /api/products/*/footprint` (single-segment `*`) → `hasAuthority("PERMISSION_READ")`.
    - `GET /api/footprints/calculations/*/export` → `hasAuthority("PERMISSION_READ")` (or new `PERMISSION_FOOTPRINT_EXPORT` — see decisions_needed.important).
44. If new permission introduced, add value to `pl.devstyle.aj.user.Permission` enum and seed in `@WithMockEditUser` for tests.

### Group 10 — Tests (per backend-testing standard)
45. `pl.devstyle.aj.footprint.DefaultFootprintFacadeContractTests` — determinism contract (Scenario I helpers); pure unit / `@SpringBootTest` slice as appropriate.
46. `FootprintControllerIntegrationTests` — Scenarios A, C, G, H (success paths + comparison group + dryRun).
47. `FootprintControllerValidationTests` — Scenarios D (strict 422), E (lenient 200 with warnings), invalid-param 400.
48. `FootprintAuditListenerIntegrationTests` — Scenario B (eventually consistent audit write within 5s; assert JSONB matches; assert `warnings_count`).
49. `FootprintCsvExportIntegrationTests` — Scenario F (csv header + 9 leaf rows refrigerated / 4 non-refrigerated; Σ kg_co2 matches total within tolerance).
50. `BreakdownScalerTests` (~3 unit tests) — composite-sum invariant under scaling; HALF_UP rounding; PER_100G total matches expected.
51. All controller tests `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})` + `@WithMockEditUser`.
52. Fixture: test-only `EmissionFactorPort` / `ProductAttributesPort` seeders (or use the in-memory production stubs configured for the scenarios — confirm in planning).

---

## Integration Points

| Integration Point | File / Mechanism | Change Type |
|---|---|---|
| Component scan registration | `@SpringBootApplication` on `AjApplication` — `pl.devstyle.aj` base package covers new sub-packages | None (automatic) |
| JPA entity scan | Convention; `FootprintAuditEntity` extends `BaseEntity` | None (automatic) |
| Liquibase master | `src/main/resources/db/changelog/db.changelog-master.yaml` → `includeAll: 2026/` | Drop file at index **011** |
| JOOQ codegen | `testcontainers-jooq-codegen-maven-plugin` (pom.xml ~lines 149–213) | None (automatic on next `mvn compile`) |
| Security authorization | `pl.devstyle.aj.core.security.SecurityConfiguration` `requestMatchers()` | Add 2 matchers; single-segment `*` for `{productId}` per Spring Security 7 PathPattern |
| Permission catalog | `pl.devstyle.aj.user.Permission` enum | Optionally add `FOOTPRINT_EXPORT` |
| Error handling | `pl.devstyle.aj.core.error.GlobalExceptionHandler` | **Unchanged** — Problem+JSON is package-scoped to footprint; coexists with `ErrorResponse` (per clarification 2) |
| Product domain | `pl.devstyle.aj.product.ProductService` / `Product` | **No mutation** — V1 uses stub `ProductAttributesPort`; real adapter is a follow-up task |
| Async event infra | `ApplicationEventPublisher` (Spring) + new `@TransactionalEventListener` | New pattern in codebase; possibly add `spring-retry` + `spring-aspects` dependencies |
| Test security infra | `SecurityMockMvcConfiguration`, `@WithMockEditUser` annotation | Reuse as-is |
| TestContainers | `TestcontainersConfiguration` (PG18) | Reuse as-is |

---

## User Journey Impact

Not applicable in the traditional UI sense — engine is backend-only. **System-integration journey** assessment:

| Dimension | Assessment |
|---|---|
| Reachability (HTTP) | New routes mounted under existing `/api/` prefix; consistent with existing controllers. |
| Reachability (Spring) | `FootprintFacade` bean auto-discovered; any in-process caller can `@Autowired` it. |
| Discoverability | Two-surface design (bean + GET) matches HLD intent; documented in `FootprintFacade` Javadoc and spec §1. |
| Flow integration | Read-only; zero mutation of existing domains; no disruption to current workflows. |
| Persona coverage | Anna (CSV export) — covered by Group 8. Piotr (deterministic/dryRun integration tests) — covered by tests + dryRun semantics. Frontend team (mockups) — out of scope this task. |

---

## Data Lifecycle Analysis

**Entity**: `FootprintAuditEntity` (table `footprint_audit_log`)

| Operation | Backend | UI / API | User Access | Status |
|---|---|---|---|---|
| CREATE | `FootprintAuditListener.onCalculated` via Spring Data `repo.save(...)` (Group 5) | Implicit — written by listener after `FootprintCalculatedEvent` published from facade | Triggered by every non-dryRun engine call (REST or in-process) | Planned (no orphan) |
| READ (by correlationId) | `FootprintAuditRepository.findByCorrelationId(...)` (Group 5 / Group 8) | `GET /api/footprints/calculations/{correlationId}/export?format=csv` (Group 8) | Anna persona; `PERMISSION_READ` | Planned (no orphan) |
| UPDATE | Not specified | Not exposed | n/a | Out of scope V1 (audit rows immutable by design) |
| DELETE | Not specified | Not exposed | n/a | Out of scope V1 (retention policy deferred) |

**Completeness for V1 scope**: 100% (CREATE + READ end-to-end across all 3 layers; UPDATE/DELETE intentionally excluded).
**Orphaned operations**: **none**. Every CREATE has a READ path (CSV exporter + ad-hoc SQL/JOOQ).
**Missing touchpoints**: none required for V1. Possible V2 touchpoints (retention sweep, divergence detector, dashboard widget) explicitly deferred per spec §6 and §8 risk register.

---

## New Capability Analysis

### Integration points
See table above (10 integration points; only 1 — `SecurityConfiguration` requestMatchers — requires non-additive change in existing file).

### Patterns to follow
| Pattern | Reference in current codebase |
|---|---|
| Domain package layout | `pl.devstyle.aj.product/` (Controller + Service + Repository + Entity + DTO records) |
| REST controller shape | `ProductController`, `CategoryController` (thin delegation, record DTOs with `from(Entity)` mappers) |
| JPA entity shape | `Product`, `Category` (BaseEntity, SEQUENCE id, EnumType.STRING, business-key equals, Lombok minimal) |
| JSONB column mapping | Existing JSONB usage in `product/plugin_data` column (per Phase 1 — precedent confirmed) |
| Liquibase YAML | `2026/002-create-products-table.yaml` (column types, constraints, indexes) |
| MockMvc integration test | Existing `*IntegrationTests` (Phase 1 standard) |
| Security requestMatcher | Existing entries in `SecurityConfiguration` |
| Custom `@RestControllerAdvice` | `GlobalExceptionHandler` — **structural** reference only; Problem+JSON output shape diverges intentionally |

### Patterns to introduce (net-new)
1. `@TransactionalEventListener(AFTER_COMMIT)` + `@Retryable`/`@Recover` async write pipeline.
2. `application/problem+json` (RFC 7807) via Spring `ProblemDetail`.
3. Sealed exception hierarchy with structured `code` + `details` map.
4. Server-side CSV export (Spring MVC + manual flattener).
5. Half-open temporal validity model with REJECT_OVERLAPPING enforcement.

### Architectural impact
**Low-to-medium**. Two net-new top-level packages (`pl.devstyle.aj.footprint`, `pl.devstyle.aj.archetype.pricing`, `pl.devstyle.aj.footprint.export`); no kernel/plugin split (deferred); no module restructure; one existing file edited (`SecurityConfiguration`); possibly two new Maven dependencies (`spring-retry`, `spring-aspects`).

---

## Compatibility Requirements

**Strict**. The engine MUST NOT alter existing module behavior or error contracts:
- `GlobalExceptionHandler` + `ErrorResponse` remain authoritative for all non-footprint packages.
- `Product` / `Category` / `User` entities and APIs are read-only consumers (only via stub port in V1).
- Existing security filter chain semantics preserved (only additive `requestMatchers`).
- Existing Liquibase migrations unchanged; new file added at index 011 only.

---

## Risk Assessment

| Factor | Assessment | Level |
|---|---|---|
| Pricing archetype availability | Resolved by clarification 1 (build in-house) | Low |
| Problem+JSON divergence | Resolved by clarification 2 (package-scoped) | Low (with technical-debt note for future standards-update) |
| Port wiring blockers | Resolved by clarification 3 (stub adapters) | Low |
| `@TransactionalEventListener` AFTER_COMMIT correctness | New pattern; subtle around `fallbackExecution=true` outside-tx semantics; `@Retryable` requires `@EnableRetry` + AOP | Medium — mitigate with dedicated `FootprintAuditListenerIntegrationTests` and Phase-7 planner verifying `spring-retry`/`spring-aspects` on classpath |
| Determinism contract under refactor | Spec §5 invariant must be enforced by contract test | Medium — explicit contract test (Group 10 #45) |
| JSONB serialization round-trip | Jackson + JSONB column round-trip must preserve breakdown fidelity for CSV export | Medium — Scenario F validates end-to-end |
| Spring Security 7 PathPattern (`*` vs `**`) | Easy to misconfigure (`/api/products/{id}/footprint` needs single-segment `*`) | Low — explicitly called out in standards |
| Numeric precision drift | HALF_UP @ 4 decimals must be applied per-node, not bulk-scaled | Low — covered by `RoundingPolicy` and `BreakdownScaler` tests |
| New Maven dependencies | `spring-retry`/`spring-aspects` (if not transitively present) | Low |

**Overall risk**: **medium** (down from medium-high after all 4 clarifications were resolved in Phase 1).

---

## Decisions Needed

### Critical
*(none — all 4 critical decisions were resolved in Phase 1 clarifications.md)*

### Important
1. **decision-id**: `audit-permission-scope`
   - Issue: Spec doesn't explicitly require a separate `PERMISSION_FOOTPRINT_EXPORT`; existing `PERMISSION_READ` would suffice but couples export to general read.
   - Options: (a) reuse `PERMISSION_READ` for both engine GET and CSV export; (b) introduce new `PERMISSION_FOOTPRINT_EXPORT` for the exporter only.
   - Default: (a) — minimal-implementation aligned; can split later without API break.
   - Rationale: V1 personas (Piotr, Anna) both already hold READ; splitting now is speculative.

2. **decision-id**: `problem-type-uri-base`
   - Issue: Spec uses placeholder `https://aj.example.com/problems/{code}` for RFC 7807 `type` URI; no real host owned by the project yet.
   - Options: (a) keep `https://aj.example.com/problems/{code}`; (b) use relative URI like `/problems/{code}`; (c) introduce config property `aj.footprint.problem.type-base`.
   - Default: (c) — externalize via `@Value("${aj.footprint.problem.type-base:https://aj.example.com/problems/}")` so QA/prod can override without code change.
   - Rationale: RFC 7807 permits relative; externalizing avoids hardcoded fake domain leaking into prod logs.

3. **decision-id**: `liquibase-format`
   - Issue: Spec §6 example is XML; project convention under `db/changelog/2026/` is YAML.
   - Options: (a) follow project convention — YAML at `011-create-footprint-audit-log.yaml`; (b) follow spec literally — XML.
   - Default: (a) YAML — consistent with all 10 existing changelogs.
   - Rationale: Convention beats spec example for syntactic style; semantics are identical.

4. **decision-id**: `csv-library-choice`
   - Issue: CSV export can use Apache Commons CSV or a hand-rolled flattener.
   - Options: (a) hand-rolled (no new dependency, 12 fixed columns, simple escaping); (b) Apache Commons CSV.
   - Default: (a) hand-rolled.
   - Rationale: Minimal-implementation standard; CSV shape is fixed and well-bounded.

5. **decision-id**: `spring-retry-dependency`
   - Issue: `@Retryable`/`@Recover` require `spring-retry` + `spring-aspects` + `@EnableRetry`. Phase 1 didn't confirm these are on the classpath.
   - Options: (a) add the two dependencies and `@EnableRetry`; (b) hand-roll retry loop in the listener.
   - Default: (a) add dependencies.
   - Rationale: Spec §6 explicitly uses `@Retryable`; native Spring support is idiomatic and well-tested.

*(All five are "important", not "critical" — the planner can adopt defaults and proceed; orchestrator should still surface them for explicit user confirmation before the specification phase.)*

---

## Recommendations

1. **Sequence implementation by Group 1→10**: archetype primitives → engine types → ports/stubs → engine internals → audit → migration → web → export → security → tests. Each group is independently testable.
2. **Pure-JUnit first for Group 1**: archetype primitives have zero Spring dependencies; fastest feedback loop.
3. **Add `spring-retry` + `spring-aspects` early** (Group 5 prerequisite) so async-audit work isn't blocked.
4. **Run `mvn compile` immediately after Group 6** to confirm JOOQ codegen picks up `footprint_audit_log`.
5. **Wire Problem+JSON handler scoped to `pl.devstyle.aj.footprint`** (not `pl.devstyle.aj.footprint.web` only — exception handler must catch from both `web/` and `export/`; recommend `basePackages = {"pl.devstyle.aj.footprint"}`).
6. **Surface a candidate standards-update at end of task**: document the AFTER_COMMIT audit pattern and the Problem+JSON divergence as candidates for `.maister/docs/standards/backend/` once stable.
7. **Defer**: real `EmissionFactorPort` adapter, Product-domain attribute integration, frontend UI, kernel/plugin split, audit retention, divergence detector, outbox — all explicitly tracked as follow-ups per clarifications.md and spec §6/§8.

---

## Report Locations Referenced

- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/.maister/tasks/development/2026-05-20-footprint-calc-engine/analysis/codebase-analysis.md`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/.maister/tasks/development/2026-05-20-footprint-calc-engine/analysis/clarifications.md`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/.maister/tasks/development/2026-05-20-footprint-calc-engine/analysis/design-context/feature-spec.md`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/.maister/tasks/development/2026-05-20-footprint-calc-engine/analysis/research-context/high-level-design.md`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/src/main/java/pl/devstyle/aj/core/BaseEntity.java`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/src/main/resources/db/changelog/db.changelog-master.yaml`
- `/Users/kuba/Projects/dna_ai/architekt-jutra-code/src/main/resources/db/changelog/2026/` (next free index: **011**)
