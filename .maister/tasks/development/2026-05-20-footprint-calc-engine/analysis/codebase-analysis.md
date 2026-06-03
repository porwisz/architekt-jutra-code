# Codebase Analysis Report — Carbon Footprint Calculation Engine

**Date**: 2026-05-20
**Task**: Implement carbon footprint calculation engine as kernel core service in `aj` (Spring Boot 4.0.5 / Java 25 monolith). Stateless calculator + REST GET surface + async audit log + CSV exporter. Reuses (hypothetical) `com.softwarearchetypes.pricing` archetype. Dual surface (`FootprintFacade` Spring bean + `GET /api/products/{id}/footprint`). 13-node component tree. Strict vs lenient modes. Problem+JSON. Historical reproducibility via `versionAt(t)`. CSV export over audit log.
**Analyzer**: codebase-analyzer skill (4 Explore agents: File Discovery, Code Analysis, Pattern Mining — Pricing Archetype, Context Discovery)

---

## Executive Summary

The `aj` codebase is a Spring Boot 4.0.5 / Java 25 single-module Maven monolith in pre-alpha state with solid infrastructure (REST controllers, JPA + Liquibase, jOOQ codegen, TestContainers PG18, JWT auth with permission claims, MockMvc test scaffolding) but **none** of the footprint-specific building blocks. Two findings are load-bearing for planning:

1. **BLOCKING — `com.softwarearchetypes.pricing` is not present** in `pom.xml`, vendored sources, or git submodules. The research HLD and spec both assume it exists. Either Maven coordinates must be obtained, or archetype-lite primitives (Calculator, Component, Validity, Applicability) must be built in-house.
2. **DECISION — Problem+JSON conflicts with the existing `ErrorResponse` convention** used by `GlobalExceptionHandler`. Spec calls for a new `FootprintExceptionHandler` returning RFC 7807; this introduces a second error contract in the codebase.

Kernel/plugin separation is **not** implemented yet; the footprint engine will sit as a regular domain package `pl.devstyle.aj.footprint` alongside `category/`, `product/`, `user/`. Event-driven async audit logging (`@TransactionalEventListener` AFTER_COMMIT) and CSV export are **net-new patterns** for this codebase.

---

## Project Structure

**Coordinates**: `pl.devstyle:aj:0.0.1-SNAPSHOT`, single Maven module.
**Source root**: `/src/main/java/pl/devstyle/aj/`
**Test root**: `/src/test/java/pl/devstyle/aj/`
**Plugins folder** `/plugins/`: frontend modules, **not** Java plugins — out of scope.

Existing domain packages (per-feature, layered):
- `category/`, `product/`, `plugin/`, `health/`, `auth/`, `user/`
- Pattern: `XController`, `XService`, `XRepository`, `XEntity`, `CreateXRequest`/`UpdateXRequest`/`XResponse` records colocated.

**Recommended target package layout** (from feature-spec, consistent with existing conventions):
```
pl.devstyle.aj.footprint
  ├── api/        (FootprintFacade, ports: EmissionFactorPort, ProductAttributesPort)
  ├── internal/   (calculator wiring, component tree, versioning service)
  ├── audit/      (FootprintAuditEntity, repository, event listener)
  ├── web/        (FootprintController, FootprintExceptionHandler, Problem+JSON DTOs)
  ├── config/     (component-tree configuration, EF source wiring)
pl.devstyle.aj.footprint.export   (CSV slice — separate package per spec)
```

---

## Existing Patterns to Follow

### REST Controllers
- Base URL `/api/`, plural nouns, thin controllers delegating to `@Service`.
- DTOs as Java records with static `from(Entity)` mappers.
- Examples: `CategoryController`, `ProductController`, `PluginController`.

### JPA Entities
- Extend `BaseEntity` (`id` SEQUENCE, `@CreatedDate createdAt`, `@Version updatedAt`).
- Per-entity `@SequenceGenerator` with `allocationSize=1`.
- `EnumType.STRING` always; LAZY fetch always; `Set<>` collections; business-key `equals`/`hashCode` (never `id`).
- Lombok: `@Getter`/`@Setter`/`@NoArgsConstructor` only (no `@Data`, no `@EqualsAndHashCode`).
- JSONB via `@JdbcTypeCode(SqlTypes.JSON)` (precedent exists).

### Liquibase
- YAML changelogs under `/src/main/resources/db/changelog/2026/`.
- Master uses `includeAll` → new YAML auto-discovered.
- Next free index: **011** (existing 001–010 cover categories, products, users, oauth2, plugins, plugin_objects, MCP server seed).

### jOOQ
- Codegen via `testcontainers-jooq-codegen-maven-plugin` (`pom.xml` lines 149–213), output `target/generated-sources/jooq`, package `pl.devstyle.aj.jooq`.
- Triggered on `mvn compile` (Liquibase migrations → generated classes).
- Standards: type-safe DSL, bind variables, MULTISET for N+1, EXISTS over COUNT, explicit joins/ordering.

### Security
- Stateless JWT; `JwtTokenProvider` issues `{sub, permissions: List<String>, iat, exp}`.
- `JwtAuthenticationFilter` maps each permission `p` → `SimpleGrantedAuthority("PERMISSION_" + p)`.
- Authorization centralized in `SecurityConfiguration` via `requestMatchers().hasAnyAuthority(...)` — **no `@PreAuthorize` on controllers**.
- Custom `AuthenticationEntryPoint` / `AccessDeniedHandler` return JSON 401/403.
- Plain claim strings observed: `READ`, `EDIT`, `PLUGIN_MANAGEMENT`, `mcp:read`, `mcp:edit`.

### Testing
- TestContainers: `@TestConfiguration TestcontainersConfiguration` exposes `@Bean @ServiceConnection PostgreSQLContainer("postgres:18")`.
- Per integration test class: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`, `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})`. No abstract base class.
- Test custom annotations: `@WithMockEditUser` (READ + EDIT), `@WithMockAdminUser` (READ + EDIT + PLUGIN_MANAGEMENT).
- Naming: `*Tests` suffix, package-private, same package as production. Methods: `action_condition_expectedResult`.
- Helpers: private `createAndSave*()` with `saveAndFlush()`. Split `*IntegrationTests` vs `*ValidationTests`. 2–8 tests per feature.

### Error Handling (current)
- `GlobalExceptionHandler` (`@RestControllerAdvice`) emits custom record `ErrorResponse(status, error, message, fieldErrors, timestamp)`.
- Custom exceptions: `EntityNotFoundException` → 404, `BusinessConflictException` → 409.
- **Not** RFC 7807 / Problem+JSON.

---

## Pricing Archetype Status — BLOCKING

- **No `com.softwarearchetypes.pricing` dependency** in `pom.xml`.
- **Not vendored** anywhere under `/src/`.
- **No git submodule**.
- Research HLD and spec both assume availability of `Calculator`, `Component`, `Validity`, `Applicability` primitives.

Reference-only training materials (NOT production-grade) exist in:
- `/week10/2-demo/04-single-module-new-concepts/calculation-engine/src/ComponentBreakdown.java` — aggregation/composite pattern.
- `/week10/4-demo/02-stateful-object-level/product-pricing/src/ProductPricing.java` — `PriceEntry` temporal versioning record.
- `/week7/5-znanewzorce-demo/pricing-archetype-mapper/SKILL.md` — comprehensive mapper guide.

Expected primitives (per spec):
| Primitive | Shape |
|-----------|-------|
| `SimpleFixedCalculator` | pure `rate × quantity`, `BigDecimal` `HALF_UP` @ 4 decimals |
| `SimpleComponent` | leaf: `ComponentId`, `CalculatorId`, `QuantityExtractor`, `Applicability`, `Validity` |
| `CompositeComponent` | node: `ComponentId`, children, `aggregation=sum`, `Applicability` |
| `Validity` | half-open `[validFrom, validTo)`, `versionAt(componentId, t) → Optional<ComponentVersion>`, REJECT_OVERLAPPING |
| `Applicability` | V1 enum `{ALWAYS, REFRIGERATED_ONLY}` — inactive subtree **excluded** (not zero-valued) |

Archetype anti-patterns to avoid: string-based context leakage (`contextParameters.get("MROZONKI")`), business logic in `Calculator`, mocking pure functions in tests, scaling composites directly, overlapping validity windows.

---

## Integration Points

| Surface | Mechanism | Status |
|---------|-----------|--------|
| REST controller registration | `@SpringBootApplication` component scan | Automatic, no `@Import` needed |
| JPA entity registration | Convention scan (no `@EntityScan`) | Just extend `BaseEntity` |
| Liquibase changelog | `includeAll` of `./2026` | Drop `011_footprint_*.yaml` |
| jOOQ codegen | Automatic on `mvn compile` | New `footprint_audit` table → generated classes appear in `pl.devstyle.aj.jooq.*` |
| Security authorization | `SecurityConfiguration` `requestMatchers()` | Add `/api/products/*/footprint` and `/api/footprints/**` rules with new permission strings |
| Async audit log | `ApplicationEventPublisher` + `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)` | **NEW pattern** — no existing usage in codebase |
| `ProductAttributesPort` adapter | Read-only call into existing `product/` domain | Existing `ProductService` available |
| `EmissionFactorPort` SPI | Production wiring TBD (in-memory seed acceptable for V1) | New |
| CSV export slice | Spring MVC + Apache Commons CSV (or custom flattener) | **NEW** — no existing data-export code |
| Problem+JSON | New `FootprintExceptionHandler` (`@RestControllerAdvice(basePackages = "pl.devstyle.aj.footprint")`) | **NEW** — conflicts with existing `ErrorResponse` convention |

---

## Standards That Apply

From `.maister/docs/standards/`:
- **Backend models** (`backend/models.md`): SEQUENCE id, EnumType.STRING, LAZY, Set<>, business-key equals, BaseEntity, Lombok minimal subset.
- **Backend API** (`backend/api.md`): plural nouns, limited nesting.
- **Backend migrations** (`backend/migrations.md`): reversible, small, zero-downtime aware.
- **Backend jOOQ** (`backend/jooq.md`): type-safe DSL, bind variables, MULTISET, explicit joins.
- **Backend security** (`backend/security.md`): centralized `SecurityFilterChain`, no `@PreAuthorize`, JSON 401/403.
- **Backend queries** (`backend/queries.md`): parameterized, N+1 avoidance, strategic indexing.
- **Testing — backend** (`testing/backend-testing.md`): integration-first, TestContainers PG18, `MockMvc` + `jsonPath`, 2–8 tests per feature, `*IntegrationTests` vs `*ValidationTests`, custom `@WithMockEditUser` / `@WithMockAdminUser`, Spring Security 7 PathPattern (`*` single-segment vs `**` multi-segment).
- **Global error handling** (`global/error-handling.md`): typed exceptions, centralized handling, fail-fast — **currently realized via `ErrorResponse`, not Problem+JSON**.
- **Global validation** (`global/validation.md`): server-side, allowlists, specific messages.
- **Global minimal implementation** (`global/minimal-implementation.md`): build only what is needed, no speculative abstractions — directly relevant to the "import vs build archetype-lite" decision.
- **Global commenting / coding style / conventions**: standard application.

---

## Gaps Requiring Decision

### 1. Pricing Archetype acquisition (BLOCKING)
**Options**:
- **(a) Import published artifact** — requires Maven coordinates; unknown today.
- **(b) Vendor / build archetype-lite in-house** — build only the V1 subset (SimpleFixedCalculator, SimpleComponent, CompositeComponent, half-open Validity, Applicability enum). Aligns with `minimal-implementation.md`. Reference patterns exist in `/week10/` training materials.
- (c) Defer and stub — incompatible with task scope (calculator is the core).

**Recommendation**: **(b) archetype-lite** unless Maven coordinates are confirmed within the planning phase.

### 2. Problem+JSON vs existing `ErrorResponse` convention
**Options**:
- **(a) Scope Problem+JSON to footprint package** via `@RestControllerAdvice(basePackages = "pl.devstyle.aj.footprint")`. Spec-aligned but introduces a second error contract in the codebase.
- **(b) Align with existing `ErrorResponse`** to keep one error convention. Diverges from spec.
- **(c) Migrate the whole codebase to Problem+JSON** — out of scope for this task.

**Recommendation**: **(a)**, with an explicit note in the work-log that error convention divergence is a known temporary state and a candidate for a future standards-update (`/maister:standards-update`).

### 3. `EmissionFactorPort` production wiring
V1 acceptable as in-memory seed; spec does not mandate a particular source. Decide whether a Liquibase-seeded `emission_factors` table is part of V1 or whether config-driven in-memory map is sufficient.

### 4. `ProductAttributesPort` adapter scope
Need to confirm which Product attributes (mass, refrigerated flag, category code) the existing `ProductEntity` already exposes and whether new columns are needed for V1.

---

## Recommended Approach

1. **Resolve archetype acquisition** before starting implementation (Decision 1).
2. **Build the footprint package** under `pl.devstyle.aj.footprint` mirroring `category/` / `product/` shape.
3. **Add Liquibase changelog `011_footprint_audit.yaml`** for `footprint_audit` table (id, correlation_id UUID, product_id, calculated_at, mode, total_kg_co2e, component_breakdown JSONB, evaluation_time, created_at) — let jOOQ codegen pick it up automatically.
4. **Wire async audit log** with `ApplicationEventPublisher` + a `@Component` listener annotated `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)`. Establishes a new pattern — document it briefly in code or candidate standard.
5. **REST surface**: `GET /api/products/{id}/footprint` (read-only, `PERMISSION_READ`), `GET /api/footprints/calculations/{correlationId}/export?format=csv` (`PERMISSION_READ` or new `PERMISSION_FOOTPRINT_EXPORT`).
6. **Problem+JSON scoped to footprint package** (Decision 2). Map strict-mode rule violations → `400 application/problem+json` with stable `type` URIs.
7. **Testing per standards**: `FootprintFacadeIntegrationTests`, `FootprintControllerIntegrationTests`, `FootprintControllerValidationTests`, `FootprintAuditEventListenerIntegrationTests`, `FootprintCsvExportIntegrationTests`. Pure-function calculator tests as plain JUnit (no Spring context, no mocks per archetype guidance).
8. **Security**: add `requestMatchers` in `SecurityConfiguration` — use `*` (single-segment) for `/api/products/*/footprint` per Spring Security 7 PathPattern rules.
9. **No kernel/plugin split** for now — footprint sits as a domain package; revisit when microkernel architecture lands.

---

## Risk Assessment

| Factor | Value | Level |
|--------|-------|-------|
| Net-new code volume | ~15–20 files (domain pkg + audit + export + tests) | Medium |
| Net-new patterns | Problem+JSON, `@TransactionalEventListener` AFTER_COMMIT, CSV export, archetype-lite primitives | High |
| External dependency unknowns | `com.softwarearchetypes.pricing` availability | High |
| Existing infra reuse | REST/JPA/Liquibase/jOOQ/Security/TestContainers all already in place | Low (positive) |
| Consumer blast radius | Read-only adapter into `product/`, no mutations to existing domains | Low |
| Test coverage path | TestContainers + MockMvc patterns proven; calculator pure-fn easy to test | Low |

### Overall Risk: **Medium-High**

**Justification**: Infrastructure reuse is strong and the surface (GET-only + async audit) is conservative, but three converging unknowns drive the rating up:
- Pricing archetype acquisition is unresolved and load-bearing.
- Problem+JSON introduces a second error convention against existing patterns.
- `@TransactionalEventListener` AFTER_COMMIT is a brand-new pattern in the codebase, with subtle correctness/testing implications (event published inside transaction, listener runs after commit, `fallbackExecution=true` semantics under no-transaction tests).

Risk drops to **Medium** if archetype-lite is chosen up front and Problem+JSON scope is confirmed in the planning phase.

---

## Next Steps

Hand off to **gap-analyzer** with focus on:
1. Confirm/resolve the three Gaps Requiring Decision before specification phase.
2. Validate `ProductEntity` already exposes the attributes the calculator needs (mass, refrigerated flag, category code).
3. Confirm V1 component tree shape (13 nodes) is enumerated in spec inputs.
