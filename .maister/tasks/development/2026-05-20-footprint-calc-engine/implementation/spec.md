# Specification — Footprint Calculation Engine

> **Canonical functional spec**: `../analysis/design-context/feature-spec.md` (8 sections, 9 acceptance scenarios). This document is the **project adaptation layer** — it does not restate the feature spec; it maps it to concrete `aj` packages, files, conventions, and the resolved Phase 1 + Phase 2 decisions.

---

## Goal

Ship a stateless carbon-footprint calculation engine inside the `aj` Spring Boot 4.0.5 / Java 25 monolith as the domain package `pl.devstyle.aj.footprint`, exposing a Spring `FootprintFacade` bean plus two read-only REST endpoints, with async audit persistence and CSV export. V1 runs against in-memory stub SPI adapters and satisfies the 9 acceptance scenarios in feature-spec §8.

## User Stories

Source: `analysis/design-context/personas.md`. Adopted verbatim — engine behavior is the contract.

- **Marta (ESG Officer)** drives a Comparison view by sending N GET calls with a shared `X-Comparison-Group` UUID, then downloads CSV for CSRD reporting.
- **Tomek (Consumer)** reads a frontend-rendered detail breakdown (kg CO₂ per leaf + factor + validFrom).
- **Anna (Internal Auditor)** retrieves CSV by `correlationId` for Excel reconciliation.
- **Piotr (Backend Developer)** wires `FootprintFacade` in-process, catches typed sealed exceptions, uses `dryRun=true` for tests.

## Core Requirements

Authoritative list lives in `feature-spec.md` §§2–7. Summarized for traceability:

1. `FootprintFacade.calculateTotal(params, options)` — DFS post-order over the 13-node tree; HALF_UP @ 4 decimals; composite-sum invariant.
2. `FootprintFacade.calculateUnit(params, options)` — adapter that calls `calculateTotal` once and scales via `BreakdownScaler`. Shares ONE audit row with the underlying TOTAL call.
3. `GET /api/products/{productId}/footprint` — pure GET; 10 query params + 3 `X-*` headers per feature-spec §3.
4. `GET /api/footprints/calculations/{correlationId}/export?format=csv` — flattens audit row to 12-column CSV.
5. 13-node `ComponentTreeRegistry` with `REFRIGERATED_ONLY` applicability on `cold-storage` (inactive subtree excluded, not zero-valued).
6. Strict mode → sealed `FootprintCalculationException` → `application/problem+json` via Spring `ProblemDetail` (scoped to footprint package).
7. Lenient mode → partial breakdown + `FootprintWarning` list at leaf or root level.
8. `EmissionFactorPort.versionAt(componentId, t)` — half-open `[validFrom, validTo)`, REJECT_OVERLAPPING.
9. Async audit via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Retryable(3, exponential 200ms ×2)` + `@Recover`.
10. `dryRun=true` suppresses audit event publication; correlationId and breakdown are still returned.
11. Determinism: same `params` → byte-identical breakdown ignoring envelope fields {correlationId, computedAt}.
12. Timestamp sanity warnings: `FUTURE_TIMESTAMP` (>30d future), `ANCIENT_TIMESTAMP` (>10y past) — advisory only.

## Project Adaptation

Mapping from feature-spec abstractions to the actual `aj` codebase.

### Package & File Layout

| Spec construct | Concrete location in repo |
|---|---|
| Archetype-lite primitives | `src/main/java/pl/devstyle/aj/archetype/pricing/` |
| Engine public API | `src/main/java/pl/devstyle/aj/footprint/api/` |
| Engine internals | `src/main/java/pl/devstyle/aj/footprint/internal/` |
| SPI port interfaces | `src/main/java/pl/devstyle/aj/footprint/internal/ports/` |
| Stub SPI adapters | `src/main/java/pl/devstyle/aj/footprint/spi/stub/` (kept out of `internal/` so a future real adapter can replace them without package-private collisions) |
| Audit subsystem | `src/main/java/pl/devstyle/aj/footprint/audit/` |
| Web layer | `src/main/java/pl/devstyle/aj/footprint/web/` |
| Engine config | `src/main/java/pl/devstyle/aj/footprint/config/` |
| CSV export slice | `src/main/java/pl/devstyle/aj/footprint/export/` |
| Liquibase changelog | `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml` |
| jOOQ generated table | `pl.devstyle.aj.jooq.tables.FootprintAuditLog` (auto-generated on `mvn compile`) |
| Tests (production package mirror) | `src/test/java/pl/devstyle/aj/footprint/...` |

### Reused Infrastructure

| Concern | Reuse | File |
|---|---|---|
| Component scan root | `@SpringBootApplication` on `pl.devstyle.aj.AjApplication` covers all sub-packages | `AjApplication.java` |
| Audit entity base class | `pl.devstyle.aj.core.BaseEntity` (SEQUENCE id, `@CreatedDate createdAt`, `@Version updatedAt`) | `core/BaseEntity.java` |
| Liquibase master | `db.changelog-master.yaml` uses `includeAll` of `2026/` — drop file at index 011, no master edit | `db/changelog/db.changelog-master.yaml` |
| jOOQ codegen | `testcontainers-jooq-codegen-maven-plugin` auto-generates on `mvn compile` — no plugin edit | `pom.xml` ~lines 149–213 |
| JSONB column mapping | `@JdbcTypeCode(SqlTypes.JSON)` precedent in product/plugin_data | per `standards/backend/models.md` |
| Security entry point / access-denied | Reuse the existing `ErrorResponse` JSON 401/403 (Problem+JSON is for footprint domain errors only, not auth errors) | `core/security/SecurityConfiguration.java` |
| Test PG container | `@Import(TestcontainersConfiguration.class)` (PostgreSQL 18) | `src/test/java/.../TestcontainersConfiguration.java` |
| Test security | `@Import(SecurityMockMvcConfiguration.class)` + `@WithMockEditUser` (READ + EDIT permissions) | per `standards/testing/backend-testing.md` |
| Global error handler | `pl.devstyle.aj.core.error.GlobalExceptionHandler` — **unchanged**; Problem+JSON handler is package-scoped to footprint | `core/error/GlobalExceptionHandler.java` |
| Permission claim | `PERMISSION_READ` (existing) authorizes both engine GET and CSV export — no new Permission enum value | `pl.devstyle.aj.user.Permission` |

### New Maven Dependencies

| Group / Artifact | Why | Scope |
|---|---|---|
| `org.springframework.retry:spring-retry` | `@Retryable`/`@Recover` on audit listener | compile |
| `org.springframework:spring-aspects` | AOP runtime required by `@Retryable` | compile |
| `org.apache.commons:commons-csv` | CSV export writer (per scope-clarifications.md override) | compile |

`@EnableRetry` is declared on `FootprintModuleConfig`.

### New Configuration Property

`application.properties` (or yaml equivalent):

```properties
app.footprint.problem-base-uri=/problems
```

Consumed by `FootprintExceptionHandler` via `@Value("${app.footprint.problem-base-uri:/problems}")`. Relative URI by default (RFC 7807 permits relative).

---

## In-House Pricing Archetype

Built under `pl.devstyle.aj.archetype.pricing` per clarification 1. Minimal V1 surface — no speculative abstractions beyond what the 13-node tree and the 9 acceptance scenarios require.

All types are records or sealed interfaces unless noted. All primitives are **immutable** and **thread-safe**.

| Type | Kind | Purpose | Key methods / fields | Immutability |
|---|---|---|---|---|
| `Calculator` | interface | Pure function: `(rate, quantity) → BigDecimal`. | `BigDecimal calculate(BigDecimal rate, BigDecimal quantity)` | N/A (stateless) |
| `SimpleFixedCalculator` | final class implements `Calculator` | Single implementation in V1; `rate × quantity` HALF_UP @ 4 decimals. | implements `calculate` using `BigDecimal.multiply` then `setScale(4, HALF_UP)` | Stateless singleton |
| `CalculatorId` | record | Stable identifier so `SimpleComponent` can refer to a calculator by id rather than holding the instance. | `String value` — blank guard in compact constructor | Immutable |
| `ComponentId` | record | Stable identifier matching the 13 componentIds in the tree. | `String value` — blank guard | Immutable |
| `Component` | sealed interface permits `SimpleComponent`, `CompositeComponent` | Tree node abstraction. | `ComponentId id()`, `Applicability applicability()` | Immutable |
| `SimpleComponent` | record implements `Component` | Leaf: ties `ComponentId` to a `CalculatorId`, a `QuantityExtractor`, and `Applicability`. | fields: `ComponentId id`, `CalculatorId calculatorId`, `QuantityExtractor quantity`, `Applicability applicability`, `int scope` | Immutable |
| `CompositeComponent` | record implements `Component` | Composite: ordered child ids; aggregation is SUM of active children. | fields: `ComponentId id`, `List<ComponentId> childIds`, `Applicability applicability` (children inherit parent applicability) | Immutable (`List.copyOf` in compact constructor) |
| `Validity` | record | Half-open temporal window `[validFrom, validTo)`. `validTo == null` ⇒ open-ended. | `Instant validFrom`, `Instant validTo`; `boolean covers(Instant t)`; `static boolean overlaps(Validity a, Validity b)` | Immutable; compact constructor enforces `validFrom != null` and `validTo == null || validTo.isAfter(validFrom)` |
| `ComponentVersion` | record | Versioned (rate, validity) tuple tied to a `ComponentId` and a stable `factorVersionId` UUID owned externally. | fields: `ComponentId componentId`, `String factorVersionId`, `BigDecimal rate`, `Validity validity` | Immutable |
| `Applicability` | enum | V1 values: `ALWAYS`, `REFRIGERATED_ONLY`. `boolean isActive(ParameterValue params)`. | `REFRIGERATED_ONLY` returns `params.requiresRefrigeration()`. | Enum (singleton) |
| `QuantityExtractor` | functional interface | `(ParameterValue) → BigDecimal`. Each leaf carries its own lambda. | `BigDecimal extract(ParameterValue params)` | N/A (stateless lambda) |
| `ParameterValue` | record | Minimal context bag the archetype passes to `Applicability.isActive` and `QuantityExtractor.extract`. Adapter wraps `FootprintParameters` into this. | fields: `BigDecimal materialWeightKg`, `BigDecimal supplierDistanceKm`, `BigDecimal destinationDistanceKm`, `BigDecimal lastMileDistanceKm`, `int storageDays`, `boolean requiresRefrigeration` | Immutable |

**REJECT_OVERLAPPING enforcement**: lives in a static utility `Validity.assertNonOverlapping(List<Validity>)` — used by the in-memory stub seeder at startup and by an in-house unit test. The engine itself relies on the SPI port returning at most one version (FactorVersionOverlapException if violated).

**Rationale**: in-house build avoids external library uncertainty and aligns with `standards/global/minimal-implementation.md` ("build only what is needed").

---

## Problem+JSON Implementation

Per clarification 2: Problem+JSON is **scoped to footprint** — the existing `GlobalExceptionHandler` and `ErrorResponse` record remain authoritative for every other module.

### Handler

```java
@RestControllerAdvice(basePackages = "pl.devstyle.aj.footprint")
class FootprintExceptionHandler {

  private final String problemBaseUri;

  FootprintExceptionHandler(@Value("${app.footprint.problem-base-uri:/problems}") String problemBaseUri) {
    this.problemBaseUri = problemBaseUri.endsWith("/") ? problemBaseUri : problemBaseUri + "/";
  }

  @ExceptionHandler(MissingFactorException.class)
  ProblemDetail handleMissingFactor(MissingFactorException ex) { ... }
  @ExceptionHandler(MissingProductAttributeException.class)
  ProblemDetail handleMissingAttribute(...) { ... }
  @ExceptionHandler(InvalidParametersException.class)
  ProblemDetail handleInvalidParameters(...) { ... }
  @ExceptionHandler(ApplicabilityResolutionException.class)
  ProblemDetail handleInconsistentApplicability(...) { ... }
  @ExceptionHandler(FactorVersionOverlapException.class)
  ProblemDetail handleOverlappingVersions(...) { ... }
}
```

`basePackages = "pl.devstyle.aj.footprint"` covers `web/`, `export/`, and any future footprint sub-package. Each handler:

1. Constructs `ProblemDetail.forStatusAndDetail(status, ex.getMessage())`.
2. Sets `type = URI.create(problemBaseUri + kebabCode(ex.code()))`.
3. Sets `title` from a constant per subclass.
4. Copies `code`, `correlationId`, `componentId`, and other `details` map entries as `setProperty(...)`.

### Mapping Table

| Exception | HTTP | code | title | details keys |
|---|---|---|---|---|
| `MissingFactorException` | 422 | `MISSING_FACTOR` | "Emission factor not available" | `componentId`, `timestamp`, `correlationId` |
| `MissingProductAttributeException` | 422 | `MISSING_ATTRIBUTE` | "Required product attribute missing" | `attributeName`, `productId` |
| `InvalidParametersException` | 400 | `INVALID_PARAMETERS` | "Invalid calculation parameters" | `field`, `value` |
| `ApplicabilityResolutionException` | 409 | `INCONSISTENT_APPLICABILITY` | "Composite applicability rule failed" | `componentId` |
| `FactorVersionOverlapException` | 409 | `OVERLAPPING_FACTOR_VERSIONS` | "Overlapping factor versions for component" | `componentId`, `timestamp` |

### Response Examples

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "/problems/missing-factor",
  "title": "Emission factor not available",
  "status": 422,
  "detail": "No emission factor version valid for component 'raw-material' at 2019-01-01T00:00:00Z",
  "code": "MISSING_FACTOR",
  "componentId": "raw-material",
  "correlationId": "8a3f-..."
}
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "/problems/invalid-parameters",
  "title": "Invalid calculation parameters",
  "status": 400,
  "detail": "materialWeightKg must be > 0",
  "code": "INVALID_PARAMETERS",
  "field": "materialWeightKg",
  "value": "-1"
}
```

```http
HTTP/1.1 422 Unprocessable Entity
{
  "type": "/problems/missing-attribute",
  "title": "Required product attribute missing",
  "status": 422,
  "detail": "Product 'UNKNOWN-1' has no materialWeightKg",
  "code": "MISSING_ATTRIBUTE",
  "attributeName": "materialWeightKg",
  "productId": "UNKNOWN-1"
}
```

```http
HTTP/1.1 409 Conflict
{
  "type": "/problems/overlapping-factor-versions",
  "title": "Overlapping factor versions for component",
  "status": 409,
  "detail": "Two factor versions are valid for component 'transport' at 2026-07-15T00:00:00Z",
  "code": "OVERLAPPING_FACTOR_VERSIONS",
  "componentId": "transport",
  "timestamp": "2026-07-15T00:00:00Z"
}
```

```http
HTTP/1.1 409 Conflict
{
  "type": "/problems/inconsistent-applicability",
  "title": "Composite applicability rule failed",
  "status": 409,
  "detail": "Cannot resolve applicability for composite 'cold-storage'",
  "code": "INCONSISTENT_APPLICABILITY",
  "componentId": "cold-storage"
}
```

---

## Stub SPI Adapters

Per clarification 3. Live in `pl.devstyle.aj.footprint.spi.stub` and are registered as `@Component` beans so the engine's `@Autowired(required=true)` port injection succeeds at startup. They satisfy the 9 acceptance scenarios.

### `InMemoryProductAttributesPort`

Seeded with two products:

| productId | materialWeightKg | supplierDistanceKm (default) | destinationDistanceKm (default) | lastMileDistanceKm (default) | requiresRefrigeration |
|---|---|---|---|---|---|
| `OFB-330` | 0.5 | 2800 | 570 | 15 | true |
| `CAW-042` | 0.25 | 800 | 120 | 5 | false |

A third id `UNKNOWN-1` is **absent** so MissingProductAttribute scenarios can be exercised when needed.

### `InMemoryEmissionFactorPort`

Seeded `Map<ComponentId, List<ComponentVersion>>` with **non-overlapping** half-open validity windows. The seeder calls `Validity.assertNonOverlapping(...)` per componentId at startup; violation fails fast.

| componentId | window 1 (winter) | window 2 (summer) | gap (Scenario D) | leaf-specific gap (Scenario E) |
|---|---|---|---|---|
| `raw-material` | rate 0.90, `[2026-01-01, 2026-06-01)` | rate 0.95, `[2026-06-01, 2027-01-01)` | none before 2026 → MISSING_FACTOR at 2019 | — |
| `processing` | rate 0.30, `[2026-01-01, 2027-01-01)` | — | — | — |
| `supplier-to-warehouse` | rate 0.00012, `[2026-01-01, 2027-01-01)` | — | — | — |
| `warehouse-to-customer` | rate 0.00012, `[2026-01-01, 2027-01-01)` | — | — | — |
| `last-mile` | rate 0.00025, `[2026-01-01, 2026-04-01)` | — | — | **gap `[2026-04-01, ∞)`** → MISSING_FACTOR for Scenario E |
| `packaging` | rate 0.10, `[2026-01-01, 2027-01-01)` | — | — | — |
| `warehouse-refrigeration` | rate 0.0015, `[2026-01-01, 2027-01-01)` | — | — | — |
| `transport-refrigeration` | rate 0.00003, `[2026-01-01, 2027-01-01)` | — | — | — |
| `last-mile-cold-chain` | rate 0.00020, `[2026-01-01, 2027-01-01)` | — | — | — |

`factorVersionId` per row is a stable UUID literal (the seeder owns them so tests can assert exact ids when needed). Composites (`product-footprint`, `materials`, `transport`, `cold-storage`) carry no factor versions.

Both stub beans are pure read-only maps; thread-safety satisfied by immutability (`Map.copyOf`).

---

## Liquibase Migration

File: `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 011-create-footprint-audit-log
      author: footprint-engine
      changes:
        - createSequence:
            sequenceName: footprint_audit_log_id_seq
            startValue: 1
            incrementBy: 1
        - createTable:
            tableName: footprint_audit_log
            columns:
              - column:
                  name: id
                  type: bigint
                  defaultValueSequenceNext: footprint_audit_log_id_seq
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: correlation_id
                  type: uuid
                  constraints:
                    nullable: false
                    unique: true
                    uniqueConstraintName: uk_footprint_audit_correlation_id
              - column:
                  name: comparison_group_id
                  type: uuid
              - column:
                  name: product_id
                  type: varchar(100)
                  constraints:
                    nullable: false
              - column:
                  name: caller_id
                  type: varchar(100)
              - column:
                  name: requested_at
                  type: timestamp without time zone
                  constraints:
                    nullable: false
              - column:
                  name: total_kg_co2
                  type: numeric(12, 4)
                  constraints:
                    nullable: false
              - column:
                  name: strictness
                  type: varchar(16)
                  constraints:
                    nullable: false
              - column:
                  name: normalisation
                  type: varchar(16)
                  constraints:
                    nullable: false
              - column:
                  name: breakdown
                  type: jsonb
                  constraints:
                    nullable: false
              - column:
                  name: warnings
                  type: jsonb
                  constraints:
                    nullable: false
              - column:
                  name: factor_versions
                  type: jsonb
                  constraints:
                    nullable: false
              - column:
                  name: dry_run
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp without time zone
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp without time zone
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
        - createIndex:
            indexName: idx_footprint_audit_product_requested
            tableName: footprint_audit_log
            columns:
              - column:
                  name: product_id
              - column:
                  name: requested_at
        - createIndex:
            indexName: idx_footprint_audit_correlation_id
            tableName: footprint_audit_log
            columns:
              - column:
                  name: correlation_id
```

**Notes**

- Timestamp columns are `timestamp without time zone` to match Hibernate's mapping of
  `LocalDateTime` (inherited from `BaseEntity`) and the entity field for `requested_at`. The
  service stores all instants converted to UTC `LocalDateTime` (`LocalDateTime.ofInstant(i, UTC)`).
- `updated_at` populated by `BaseEntity` `@Version`; default is for direct SQL inserts and JPA optimistic locking baseline.
- Numeric precision `(12,4)` covers the realistic kg CO₂ range (≤ 99,999,999.9999 kg). Spec §6 example used `(18,6)` but `(12,4)` matches the HALF_UP @ 4 decimal rounding contract exactly.
- `factor_versions` is JSONB (not Postgres `text[]`) — keeps the engine database-portable and the column type uniform; queries by version are deferred to V2 (`->>` operators or a derived index).
- jOOQ codegen will pick this up automatically on the next `mvn compile`.

**Audit entity (FootprintAuditEntity) field bindings**

```java
@Entity
@Table(name = "footprint_audit_log")
@SequenceGenerator(name = "base_seq", sequenceName = "footprint_audit_log_id_seq", allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class FootprintAuditEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private UUID correlationId;            // business key — used in equals/hashCode

    @Column(name = "comparison_group_id")
    private UUID comparisonGroupId;

    @Column(nullable = false, length = 100)
    private String productId;

    @Column(name = "caller_id", length = 100)
    private String callerId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;     // UTC, matches "timestamp without time zone"

    @Column(name = "total_kg_co2", nullable = false, precision = 12, scale = 4)
    private BigDecimal totalKgCo2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Strictness strictness;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Normalisation normalisation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> breakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factor_versions", columnDefinition = "jsonb", nullable = false)
    private List<String> factorVersions;   // UUIDs as strings

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;
}
```

The `@SequenceGenerator(name = "base_seq", …)` declaration on the entity is **required** — the
inherited `@GeneratedValue(generator = "base_seq")` in `BaseEntity` is a reference that each
concrete entity must resolve with its own per-entity sequence (precedent: `Product.java`).

---

## REST Contract

Restated from feature-spec §3 (no divergence). Field names match what the 3 HTML mockups render (verified: `correlationId`, `kgCo2`, `factorVersionId`, `factorValidFrom`, `scope`, `factorRate`).

### `GET /api/products/{productId}/footprint`

**Path variable**: `productId` (string).

**Query parameters**:

| name | type | required | default | notes |
|---|---|---|---|---|
| `asOf` | ISO-8601 instant | no | `now()` | `t` for `versionAt(t)` |
| `materialWeightKg` | decimal | no | from `ProductAttributesPort` | override |
| `supplierDistanceKm` | decimal | no | from port | override |
| `destinationDistanceKm` | decimal | no | from port | override |
| `lastMileDistanceKm` | decimal | no | from port | override |
| `storageDays` | integer | no | `0` | override |
| `requiresRefrigeration` | boolean | no | from port | override |
| `unit` | enum | no | `TOTAL` | `TOTAL` \| `PER_100G` |
| `strictness` | enum | no | `STRICT` | `STRICT` \| `LENIENT` |
| `dryRun` | boolean | no | `false` | suppresses audit event |

**Headers**:

- `X-Correlation-Id` (optional UUID) — engine generates if absent.
- `X-Caller-Id` (optional string) — defaults to `"anonymous"`.
- `X-Comparison-Group` (optional UUID) — links calls into one comparison group.

**200 OK response** (`application/json`, fields match mockups):

```json
{
  "correlationId": "f3a5-4c9e-...",
  "comparisonGroupId": null,
  "computedAt": "2026-05-20T14:32:11Z",
  "total": 5.3724,
  "unit": "kgCO2",
  "parametersEcho": {
    "timestamp": "2026-07-15T00:00:00Z",
    "productId": "OFB-330",
    "materialWeightKg": 0.5,
    "supplierDistanceKm": 2800,
    "destinationDistanceKm": 570,
    "lastMileDistanceKm": 15,
    "storageDays": 14,
    "requiresRefrigeration": true
  },
  "options": { "strictness": "STRICT", "normalisation": "TOTAL", "dryRun": false },
  "breakdown": {
    "componentId": "product-footprint",
    "kgCo2": 5.3724,
    "children": [
      {
        "componentId": "materials",
        "kgCo2": 1.3500,
        "children": [
          {
            "componentId": "raw-material",
            "kgCo2": 0.4500,
            "scope": 3,
            "factorVersionId": "f3a5-...",
            "factorRate": 0.9000,
            "factorValidFrom": "2026-01-01T00:00:00Z",
            "quantity": 0.5,
            "warnings": []
          }
        ],
        "warnings": []
      }
    ],
    "warnings": []
  },
  "rootWarnings": [],
  "factorVersions": [
    { "componentId": "raw-material", "factorVersionId": "f3a5-...", "validFrom": "2026-01-01T00:00:00Z" }
  ]
}
```

**Error responses**: see Problem+JSON Implementation section. Content-Type `application/problem+json`. Statuses: 400, 409, 422.

---

## CSV Export Contract

### Endpoint

`GET /api/footprints/calculations/{correlationId}/export?format=csv`

| Status | Condition |
|---|---|
| 200 | audit row found; CSV body returned |
| 404 | no row with that `correlationId` |
| 400 | `format` query param present but ≠ `csv` (invalid request — malformed enum). Returns Problem+JSON with code `INVALID_PARAMETERS`. |

**Response headers**:
- `Content-Type: text/csv; charset=utf-8`
- `Content-Disposition: attachment; filename="footprint-{correlationId}.csv"`

### CSV Body

12 columns (header row + one row per active leaf):

```
correlation_id,computed_at,product_id,timestamp_param,component_path,component_id,kg_co2,scope,factor_value,factor_valid_from,factor_version_id,warnings
```

| column | source |
|---|---|
| `correlation_id` | audit row |
| `computed_at` | audit row `requested_at` |
| `product_id` | audit row |
| `timestamp_param` | from `breakdown` JSON `parametersEcho.timestamp` |
| `component_path` | dot-delimited path from root, **root excluded** (e.g. `materials.raw-material`) |
| `component_id` | leaf id |
| `kg_co2` | leaf `kgCo2`, locale-invariant decimal `.`, 4 decimals |
| `scope` | leaf `scope` (1/2/3) |
| `factor_value` | leaf `factorRate` |
| `factor_valid_from` | leaf `factorValidFrom` ISO-8601 |
| `factor_version_id` | leaf `factorVersionId` |
| `warnings` | empty string if none; otherwise JSON array literal of warning objects |

### Escaping & Encoding

- UTF-8, no BOM.
- Decimal separator `.` (locale-invariant — use `BigDecimal.toPlainString()`).
- Apache Commons CSV `CSVFormat.DEFAULT.withHeader(...)` handles quoting (RFC 4180): values containing `,`, `"`, `\r`, `\n` are double-quoted; embedded `"` is doubled to `""`.
- `warnings` column written as raw JSON string (Commons CSV quotes the entire JSON literal).

### Usage pattern (illustrative — not implementation code)

```
try (CSVPrinter printer = CSVFormat.DEFAULT
        .builder()
        .setHeader("correlation_id", "computed_at", ..., "warnings")
        .build()
        .print(response.getWriter())) {
  flattener.leaves(auditRow).forEach(leaf -> printer.printRecord(...));
}
```

---

## Security Config Diff

`src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java` — add **one** line inside the existing `.authorizeHttpRequests(auth -> auth …)` chain, in the READ block (next to the existing `/api/products/**` rule):

```java
.requestMatchers(HttpMethod.GET, "/api/footprints/calculations/*/export").hasAnyAuthority("PERMISSION_READ", "PERMISSION_mcp:read")
```

**Why only one line**: the engine endpoint `/api/products/{id}/footprint` is already covered by
the existing `/api/products/**` matcher at line 110 (`hasAnyAuthority("PERMISSION_READ",
"PERMISSION_mcp:read")`). Adding a redundant `/api/products/*/footprint` matcher would either be
dead code (if inserted after line 110) or would silently drop MCP read access (if inserted before).
The single new matcher is for the genuinely uncovered `/api/footprints/calculations/*/export`.

**PathPattern rules** (Spring Security 7):
- `*` is **single-segment** — correct for `{correlationId}` which never contains `/`.
- `**` would be multi-segment and is wrong here.
- `hasAnyAuthority(...)` (not `hasAuthority(...)`) keeps the export endpoint accessible by MCP
  read clients, matching the surrounding `/api/products/**` convention.

No other change to this file.

---

## Acceptance Tests

Per `standards/testing/backend-testing.md`: integration-first, TestContainers PG18 + MockMvc + `jsonPath()`/Hamcrest, `@Transactional` rollback, `*Tests` suffix, package-private, mirror production package, methods named `action_condition_expectedResult`, 2–8 tests per class, split `*IntegrationTests` vs `*ValidationTests`, `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})`, `@WithMockEditUser`.

### Test Classes (9 classes, ~25 test methods total)

#### Integration tests (`*IntegrationTests`)

1. **`pl.devstyle.aj.footprint.internal.FootprintEngineIntegrationTests`** — facade against stub ports, Spring context.
   - `calculateTotal_summerContext_returnsSummerFactorBreakdown` (Scenario A summer)
   - `calculateTotal_winterContext_returnsWinterFactorBreakdown` (Scenario A winter — historical reproducibility)
   - `calculateTotal_nonRefrigeratedProduct_excludesColdStorageSubtree` (Scenario C — applicability)

2. **`pl.devstyle.aj.footprint.web.FootprintControllerIntegrationTests`** — MockMvc happy paths.
   - `getFootprint_validRequest_returnsBreakdownAndCorrelationId` (Scenario A via REST)
   - `getFootprint_dryRunTrue_returnsBreakdownAndNoAuditRow` (Scenario G)
   - `getFootprint_sharedComparisonGroupHeader_persistsThreeLinkedAuditRows` (Scenario H)

3. **`pl.devstyle.aj.footprint.audit.FootprintAuditListenerIntegrationTests`** — async listener correctness.
   - `onFootprintCalculated_committedTransaction_persistsAuditRowWithinFiveSeconds` (Scenario B; poll-based with 5s timeout)
   - `onFootprintCalculated_lenientMissingFactor_persistsAuditRowWithWarnings` (Scenario E persistence side)
   - `calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit` (Scenario I audit-row identity — see Implementation Notes §8)

4. **`pl.devstyle.aj.footprint.export.FootprintCsvExportIntegrationTests`** — CSV happy paths.
   - `exportCsv_existingRefrigeratedAuditRow_returnsHeaderPlusNineLeafRows` (Scenario F refrigerated)
   - `exportCsv_existingNonRefrigeratedAuditRow_returnsHeaderPlusFourLeafRows` (Scenario F non-refrigerated)
   - `exportCsv_sumOfKgCo2_matchesAuditTotalWithinTolerance` (CSV invariant)

#### Validation tests (`*ValidationTests`)

5. **`pl.devstyle.aj.footprint.web.FootprintControllerValidationTests`** — Problem+JSON error paths.
   - `getFootprint_strictModeMissingFactor_returns422ProblemJson` (Scenario D)
   - `getFootprint_lenientModeMissingLastMileFactor_returns200WithLeafWarning` (Scenario E REST side)
   - `getFootprint_negativeMaterialWeight_returns400InvalidParameters`
   - `getFootprint_unknownProductId_returns422MissingAttribute`

6. **`pl.devstyle.aj.footprint.export.FootprintCsvExportValidationTests`** — export error paths.
   - `exportCsv_unknownCorrelationId_returns404`
   - `exportCsv_formatNotCsv_returns400InvalidParameters`

#### Pure unit tests (no Spring)

7. **`pl.devstyle.aj.footprint.internal.BreakdownScalerTests`** — composite-sum invariant.
   - `scale_per100gOnHalfKg_doublesEveryLeaf`
   - `scale_composite_equalsSumOfScaledChildren` (no rounding drift)
   - `scale_inactiveSubtreeAbsent_doesNotProduceNaN`

8. **`pl.devstyle.aj.archetype.pricing.SimpleFixedCalculatorTests`** — calculator contract.
   - `calculate_rateTimesQuantity_roundsHalfUpAt4Decimals`
   - `calculate_zeroQuantity_returnsZero`

9. **`pl.devstyle.aj.archetype.pricing.ValidityTests`** — half-open + REJECT_OVERLAPPING.
   - `covers_instantAtValidFrom_returnsTrue` (inclusive lower)
   - `covers_instantAtValidTo_returnsFalse` (exclusive upper)
   - `assertNonOverlapping_overlappingWindows_throwsIllegalArgumentException`

### Test Fixtures

- Reuse production `InMemoryEmissionFactorPort` and `InMemoryProductAttributesPort` as test fixtures — they are seeded for exactly the 9 acceptance scenarios. No separate test-only stub seeder needed.
- `createAndSaveAuditRow(...)` private helper in `FootprintCsvExportIntegrationTests` uses `saveAndFlush()` per testing standard.
- Determinism contract (Scenario I — `calculateUnit` invariant) is exercised inside `BreakdownScalerTests` (`scale_per100gOnHalfKg_doublesEveryLeaf` + composite sum invariant). A dedicated `FootprintFacadeContractTests` is **not** added — covering the invariant at the scaler level is sufficient and avoids duplicating the integration assertions.

---

## Standards Compliance

- `standards/global/minimal-implementation.md` — no speculative archetype primitives beyond what V1 uses; in-house build instead of unconfirmed external dependency; CSV slice ships only what the spec requires.
- `standards/global/error-handling.md`, `validation.md`, `coding-style.md`, `commenting.md`, `conventions.md` — applied uniformly.
- `standards/backend/api.md` — RESTful plural nouns (`/api/products`, `/api/footprints`); pure GET; no nested resources beyond one level.
- `standards/backend/models.md` — `FootprintAuditEntity` extends `BaseEntity`; `@SequenceGenerator(allocationSize=1)`; `EnumType.STRING` for `strictness`/`normalisation`; LAZY default (entity has no relationships in V1); business-key `equals`/`hashCode` on `correlation_id`; Lombok `@Getter`/`@Setter`/`@NoArgsConstructor` only; no `@Data`, no `CascadeType.ALL`.
- `standards/backend/queries.md` — Spring Data JPA `findByCorrelationId(UUID)` for CSV export; index `idx_footprint_audit_correlation_id` backs it.
- `standards/backend/jooq.md` — JPA used for CRUD on the audit table; jOOQ deferred (no complex audit-log read in V1).
- `standards/backend/migrations.md` — YAML changelog at next index 011; small focused changeset; reversible (Liquibase auto-rollback for `createTable`/`createIndex`/`createSequence`); explicit constraint names.
- `standards/backend/security.md` — centralized `SecurityFilterChain` `requestMatchers`; no `@PreAuthorize` on controllers; existing JSON 401/403 entry-point preserved.
- `standards/testing/backend-testing.md` — integration-first; `*Tests` suffix package-private; 2–8 tests per class; `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})`; `@WithMockEditUser` (covers PERMISSION_READ); `MockMvc` + `jsonPath()`; single-segment `*` in Security 7 PathPatterns.

---

## Implementation Notes

Additional binding clarifications surfaced by the spec audit:

1. **QuantityExtractor for `warehouse-refrigeration` leaf**: the extractor lambda receives the
   full `FootprintParameters` record and returns
   `BigDecimal.valueOf(params.storageDays()).multiply(params.materialWeightKg())`. The cross-field
   arithmetic stays inside the lambda — no archetype change required.
2. **`ProblemDetail.instance` URI**: set to the request URI
   (`ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()`) so clients can correlate
   problems with their originating call. Never `null`.
3. **JSONB serialization mode**: `warnings`/`breakdown`/`factor_versions` are stored as **compact**
   JSON via the existing project `ObjectMapper` (no pretty-printing). Default Jackson behavior.
4. **`@Retryable` exhaustion semantics**: when retries exhaust, `@Recover` logs an ERROR with the
   correlation id and increments a `footprint.audit.failed` Micrometer counter. The original
   facade caller is **unaffected** — audit is fire-and-forget by contract (Success Criterion 5
   is "eventually consistent within 5 s", not "guaranteed within request").
5. **`X-Comparison-Group` validation**: a non-UUID header value triggers
   `InvalidParametersException(code="INVALID_PARAMETERS")` → Problem+JSON 400.
6. **`X-Correlation-Id` validation**: a non-UUID header value triggers the same
   `InvalidParametersException` → 400. If the header is absent, the engine generates a fresh
   `UUID.randomUUID()`.
7. **Error code stability**: each sealed exception subclass exposes a `public static final String CODE`
   constant (e.g. `MissingFactorException.CODE = "MISSING_FACTOR"`); the `code()` instance method
   returns this constant. This pins the public API surface and prevents drift on refactor.
8. **Scenario I — `calculateUnit` audit-row identity**: in addition to `BreakdownScalerTests`,
   `FootprintEngineIntegrationTests` includes one method
   `calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit` asserting
   that the canonical TOTAL breakdown is persisted (not the per-100g view) and only **one** audit
   row is produced even though `calculateUnit` internally invokes `calculateTotal`.

---

## Out of Scope

Per `requirements.md` and `clarifications.md`:

- Real Emission Factor Management module with persistent factor storage.
- Real `ProductAttributesPort` adapter wired to `ProductEntity`.
- Frontend UI (3 HTML mockups handed to a future FE task).
- Result caching layer.
- Audit retention / archival policy.
- Kernel/plugin module split (engine sits in monolith core).
- POST variant of the engine endpoint (URL length cap deferred to V2).
- Outbox pattern for audit (deferred unless failure metric > 0.01%).
- Audit divergence detector (deferred).
- New `PERMISSION_FOOTPRINT_EXPORT` enum value (reuse `PERMISSION_READ`).

---

## Success Criteria

1. All 9 acceptance scenarios in feature-spec §8 pass as integration tests against TestContainers PG18.
2. `mvn verify` is green; jOOQ codegen picks up `footprint_audit_log` without manual edits.
3. `FootprintFacade` callable in-process via `@Autowired` from any `pl.devstyle.aj.*` consumer.
4. Same `params` → byte-identical `breakdown.root` and `breakdown.total` (envelope fields excluded). Enforced by `BreakdownScalerTests` invariants + facade integration tests.
5. Async audit write completes within 5 seconds of REST response for non-dryRun calls (validated by Scenario B polling).
6. `dryRun=true` produces zero rows in `footprint_audit_log` for the call (Scenario G).
7. Problem+JSON responses validate against RFC 7807 shape; `code` extension stable across the 5 subclasses.
8. CSV export produces 9 rows for refrigerated and 4 rows for non-refrigerated, with `Σ(kg_co2) == total` within HALF_UP @ 4 tolerance.
9. `SecurityConfiguration` changes are additive (2 lines); existing module behavior unchanged.

---

## Assumptions

1. **Permission claim format**: JWT `permissions` claim is a JSON array of plain strings (`["READ"]`), mapped by `JwtAuthenticationFilter` to authorities `PERMISSION_READ`. Confirmed by reading `JwtTokenProvider`/`JwtAuthenticationFilter` indirectly via `SecurityConfiguration` matchers (e.g. line 109: `hasAnyAuthority("PERMISSION_READ", "PERMISSION_mcp:read")`).
2. **Lombok usage**: `@Getter`/`@Setter`/`@NoArgsConstructor` on `FootprintAuditEntity` only. Records (archetype primitives, API DTOs, breakdown nodes, exceptions' `details`) use no Lombok.
3. **jOOQ vs JPA**: JPA for V1 audit CRUD. jOOQ generated class exists but is unused in V1 — it becomes useful in V2 when query-by-comparison-group dashboards are added.
4. **JSONB serialization**: `tools.jackson.databind.ObjectMapper` (the project's existing Jackson facade — see `SecurityConfiguration.java` import) used for `breakdown`/`warnings`/`factor_versions`/`params` JSONB column mapping via `@JdbcTypeCode(SqlTypes.JSON)`. No custom converter required.
5. **`Instant` columns**: PostgreSQL `timestamp with time zone` with Hibernate's default `Instant ↔ timestamptz` mapping. Spec wrote `timestamptz`; YAML uses `timestamp with time zone` (Liquibase canonical).
6. **`fallbackExecution=true`**: when the engine is called outside an HTTP transaction (e.g. in-process from a non-`@Transactional` caller), the listener still runs synchronously after the call. `@Retryable` covers transient DB failures in both modes.
7. **CSV export `format` param**: only `csv` is recognized in V1; missing or empty value defaults to `csv`. Any other value triggers `InvalidParametersException` → Problem+JSON **400** (REST convention for invalid enum value; matches feature-spec §3). `INVALID_PARAMETERS` maps to 400 in the engine taxonomy already.
8. **`ComponentTreeRegistry` immutability**: built once at bean init; thereafter read-only. Adding a 14th component is a Java-source change + recompile, no schema or API change.
