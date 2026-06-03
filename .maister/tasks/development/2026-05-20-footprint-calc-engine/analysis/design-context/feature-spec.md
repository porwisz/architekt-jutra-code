# Feature Specification — Footprint Calculation Engine

Implementation-ready specification. Sections are appended as approved during Phase 6 of the product-design workflow.

---

## Section 1 — Module Structure & Integration

### Package Layout

```
pl.devstyle.aj.footprint
├── api                              (public surface — exported from module)
│   ├── FootprintFacade.java         (interface: calculateTotal / calculateUnit)
│   ├── FootprintParameters.java     (record)
│   ├── CalculationOptions.java      (record)
│   ├── FootprintBreakdown.java      (sealed)
│   ├── BreakdownNode.java           (sealed: CompositeNode | LeafNode)
│   ├── FootprintWarning.java        (record)
│   ├── Strictness.java              (enum: STRICT, LENIENT)
│   ├── Normalisation.java           (enum: TOTAL, PER_100G)
│   └── exceptions/
│       ├── FootprintCalculationException.java     (sealed)
│       ├── MissingFactorException.java
│       ├── MissingProductAttributeException.java
│       ├── InvalidParametersException.java
│       ├── ApplicabilityResolutionException.java
│       └── FactorVersionOverlapException.java
├── internal                         (package-private — not exported)
│   ├── DefaultFootprintFacade.java                (@Service)
│   ├── ComponentTreeRegistry.java                 (bean — registers 13 components)
│   ├── EmissionCalculator.java                    (Pricing Archetype SimpleFixedCalculator wrapper)
│   ├── BreakdownTreeBuilder.java
│   ├── BreakdownScaler.java                       (post-divide for calculateUnit)
│   └── ports/
│       ├── EmissionFactorPort.java                (interface — read-only)
│       └── ProductAttributesPort.java             (interface — read-only)
├── audit
│   ├── FootprintCalculatedEvent.java              (record published from facade)
│   ├── FootprintAuditListener.java                (@TransactionalEventListener)
│   ├── FootprintAuditEntity.java                  (JPA entity for footprint_audit_log)
│   ├── FootprintAuditRepository.java              (Spring Data JPA)
│   └── FootprintAuditEntityMapper.java
├── web
│   ├── FootprintController.java                   (@RestController — GET endpoints)
│   ├── FootprintExceptionHandler.java             (@RestControllerAdvice — Problem+JSON)
│   ├── FootprintQueryRequest.java                 (DTO bound from query params)
│   └── FootprintResponseDto.java                  (Jackson-friendly envelope)
└── config
    └── FootprintModuleConfig.java                 (@Configuration — wires Pricing Archetype)

pl.devstyle.aj.footprint.export                    (separate slice)
├── FootprintExportController.java                 (@RestController — CSV endpoint)
├── BreakdownCsvFlattener.java
└── config/FootprintExportConfig.java
```

### Spring Wiring

- `FootprintFacade` is a `@Service` bean exposed by `FootprintModuleConfig`. Auto-discovered by Spring Boot 4.0.5 component scan.
- Two SPI ports (`EmissionFactorPort`, `ProductAttributesPort`) are interfaces only — concrete implementations live outside the engine module (Emission Factor Management module + Product Catalog module). Engine fails-fast at startup if no implementation is wired (`@Autowired(required=true)`).
- Audit listener uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)`. `fallbackExecution=true` ensures audit writes happen even when the calculation is called outside an active transaction (REST endpoint case).
- All Pricing Archetype beans wired in `FootprintModuleConfig`.

### Module Boundary Contracts (SPI Ports)

| Port | Direction | Method | Semantics |
|---|---|---|---|
| `EmissionFactorPort` | engine → factor mgmt | `versionAt(componentId: String, t: Instant): Optional<EmissionFactorVersion>` | Returns `Optional.empty()` if no version valid at `t`; engine maps to `MissingFactorException` in strict mode or to a leaf-level warning in lenient mode. |
| `ProductAttributesPort` | engine → product catalog | `getAttributes(productId: ProductId): Optional<ProductAttributes>` | Read-only. `ProductAttributes` carries `materialWeightKg`, `requiresRefrigeration`, and default distances tied to the product's home warehouse. |

`EmissionFactorVersion` shape (immutable record returned by port):
```java
record EmissionFactorVersion(
    String componentId,
    String factorVersionId,           // stable UUID owned by RC module
    BigDecimal rate,
    Instant validFrom,
    Instant validTo                   // exclusive; null = open-ended
) {}
```

### Module Placement

Engine is a **core service** loaded by the kernel always (not a plugin). When the platform's plugin framework is finalized (PF4J/OSGi/JPMS pending), the engine remains in core — only the SPI ports are pluggable.

### Liquibase Ownership

Engine module owns the `footprint_audit_log` table migration (see Section 6). The `footprint-export` slice owns no schema. SPI port implementations own their own schemas.


---

## Section 2 — Domain Model & API Contracts

### Public Types

```java
// Math input — flat record, immutable
public record FootprintParameters(
    Instant timestamp,                // resolves emission factor versions
    ProductId productId,
    BigDecimal materialWeightKg,      // > 0
    BigDecimal supplierDistanceKm,    // ≥ 0
    BigDecimal destinationDistanceKm, // ≥ 0
    BigDecimal lastMileDistanceKm,    // ≥ 0
    int storageDays,                  // ≥ 0
    boolean requiresRefrigeration
) {
  public FootprintParameters {
    // Compact constructor enforces invariants:
    // - timestamp != null, productId != null
    // - materialWeightKg > 0
    // - all distance fields ≥ 0
    // - storageDays ≥ 0
    // Violations throw InvalidParametersException at construction time.
  }
  public FootprintParameters withTransport(BigDecimal supplier, BigDecimal dest, BigDecimal lastMile) { ... }
  public FootprintParameters withTimestamp(Instant t) { ... }
}

public record ProductId(String value) {
  public ProductId {
    if (value == null || value.isBlank()) throw new InvalidParametersException("productId blank");
  }
}

// Cross-cutting options — separate record
public record CalculationOptions(
    Strictness strictness,            // STRICT (default) | LENIENT
    Normalisation normalisation,      // TOTAL (default) | PER_100G
    UUID correlationId,               // null → engine generates
    String callerId,                  // service or user identity; null → "anonymous"
    boolean dryRun,                   // true → no audit event
    UUID comparisonGroupId            // nullable; from X-Comparison-Group header
) {
  public static CalculationOptions strictTotal() { ... }
  public static CalculationOptions lenientTotal() { ... }
  public static CalculationOptions strictPer100g() { ... }
}

public enum Strictness { STRICT, LENIENT }
public enum Normalisation { TOTAL, PER_100G }
```

### Facade Interface

```java
public interface FootprintFacade {
  /**
   * Total kg CO2 for the full product unit in the given context.
   * Strict mode throws on missing data; lenient returns partial breakdown with warnings.
   * Publishes FootprintCalculatedEvent unless options.dryRun() is true.
   */
  FootprintBreakdown calculateTotal(FootprintParameters params, CalculationOptions options);

  /**
   * Per-100g normalised breakdown. Internally calls calculateTotal then scales by (100/materialWeightKg).
   * Audit row records the canonical TOTAL breakdown with normalisation=PER_100G.
   */
  FootprintBreakdown calculateUnit(FootprintParameters params, CalculationOptions options);
}
```

### Result Types

```java
public sealed interface BreakdownNode permits CompositeNode, LeafNode {
  String componentId();
  BigDecimal kgCo2();
  List<FootprintWarning> warnings();
}

public record CompositeNode(
    String componentId,
    BigDecimal kgCo2,                 // sum of children, computed from scaled leaves
    List<BreakdownNode> children,
    List<FootprintWarning> warnings
) implements BreakdownNode {}

public record LeafNode(
    String componentId,
    BigDecimal kgCo2,
    int scope,                        // 1, 2, or 3
    String factorVersionId,           // stable id from EmissionFactorVersion
    BigDecimal factorRate,            // factor used
    Instant factorValidFrom,
    BigDecimal quantity,              // the activity quantity multiplied
    List<FootprintWarning> warnings
) implements BreakdownNode {}

public record FootprintBreakdown(
    UUID correlationId,
    Instant computedAt,
    BigDecimal total,                 // root.kgCo2 — denormalised
    String unit,                      // "kgCO2" | "kgCO2/100g"
    BreakdownNode root,               // always the "product-footprint" composite
    List<FootprintWarning> rootWarnings,
    List<FactorVersionRef> factorVersions  // flat list, for audit
) {}

public record FactorVersionRef(
    String componentId,
    String factorVersionId,
    Instant validFrom
) {}

public record FootprintWarning(
    String code,                      // e.g., "MISSING_FACTOR"
    String componentId,               // empty string for engine-wide
    String message,
    Map<String, Object> details
) {}
```

### Exception Hierarchy

```java
public sealed class FootprintCalculationException extends RuntimeException permits
    MissingFactorException,
    MissingProductAttributeException,
    InvalidParametersException,
    ApplicabilityResolutionException,
    FactorVersionOverlapException {
  private final String code;
  private final Map<String, Object> details;
}
// Each subclass is final, carries a constant `code` and structured `details` for Problem+JSON mapping.
```

### Determinism Invariant

For any `params` and any two `options` differing only in `{correlationId, callerId, dryRun, comparisonGroupId}`,
`calculateTotal(params, options).total` and `.root` are byte-identical (excluding generated `correlationId` and `computedAt` envelope fields). Encoded as a contract test in Section 8.


---

## Section 3 — REST API & Exporter

### Engine endpoint

`GET /api/products/{productId}/footprint`

| Query param | Type | Required | Default | Notes |
|---|---|---|---|---|
| `asOf` | ISO-8601 instant | no | `now()` | The `t` used for `versionAt(t)` |
| `materialWeightKg` | decimal | no | from product catalog | Override |
| `supplierDistanceKm` | decimal | no | from product catalog | |
| `destinationDistanceKm` | decimal | no | from product catalog | |
| `lastMileDistanceKm` | decimal | no | from product catalog | |
| `storageDays` | integer | no | `0` | |
| `requiresRefrigeration` | boolean | no | from product catalog | |
| `unit` | enum | no | `TOTAL` | `TOTAL` \| `PER_100G` |
| `strictness` | enum | no | `STRICT` | `STRICT` \| `LENIENT` |
| `dryRun` | boolean | no | `false` | `true` → no audit event |

Headers:
- `X-Correlation-Id` (optional) — UUID; engine generates if absent
- `X-Caller-Id` (optional) — identifies caller service/user
- `X-Comparison-Group` (optional) — UUID linking multiple calls into a comparison group

### Success Response (200 OK, `application/json`)

```json
{
  "correlationId": "f3a5...",
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

### Error Response (`application/problem+json`)

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "https://aj.example.com/problems/missing-factor",
  "title": "Emission factor not available",
  "status": 422,
  "detail": "No emission factor version valid for component 'transport' at 2025-01-01T00:00:00Z",
  "code": "MISSING_FACTOR",
  "componentId": "transport",
  "correlationId": "f3a5..."
}
```

### Exporter endpoint

`GET /api/footprints/calculations/{correlationId}/export?format=csv`

- Sync, read-only against `footprint_audit_log`.
- 404 if `correlationId` not found.
- 400 if `format` other than `csv` (only `csv` in V1).

CSV columns (one row per leaf):

```csv
correlation_id,computed_at,product_id,timestamp_param,component_path,component_id,kg_co2,scope,factor_value,factor_valid_from,factor_version_id,warnings
```

- `component_path` is dot-delimited from root (root excluded).
- `warnings` is a JSON array literal if non-empty, empty string otherwise.
- Decimal separator `.` (locale-invariant); no BOM.

### Content Negotiation

Engine endpoint serves `application/json` only. Exporter serves `text/csv` only. No content negotiation in V1.


---

## Section 4 — Component Tree Definition & Registration

V1 ships a fixed component tree (13 nodes) registered at bean-init time.

### Tree Structure (verbatim from research HLD)

```
product-footprint        (composite, root)
├── materials            (composite)
│   ├── raw-material     (leaf, scope 3)
│   └── processing       (leaf, scope 1)
├── transport            (composite)
│   ├── supplier-to-warehouse    (leaf, scope 3)
│   ├── warehouse-to-customer    (leaf, scope 3)
│   └── last-mile                (leaf, scope 3)
├── packaging            (leaf, scope 3)
└── cold-storage         (composite, applicability: requiresRefrigeration)
    ├── warehouse-refrigeration  (leaf, scope 2)
    ├── transport-refrigeration  (leaf, scope 2)
    └── last-mile-cold-chain     (leaf, scope 3)
```

### Component Declaration Types

```java
record ComponentDefinition(
    String componentId,
    ComponentKind kind,                     // COMPOSITE | LEAF
    int scope,                              // -1 for composites
    QuantityExtractor quantityExtractor,    // for LEAF: params → BigDecimal
    Applicability applicability,            // ALWAYS | REFRIGERATED_ONLY
    List<String> childIds                   // for COMPOSITE
) {}

enum ComponentKind { COMPOSITE, LEAF }
enum Applicability {
    ALWAYS,
    REFRIGERATED_ONLY;       // active iff parameters.requiresRefrigeration()
}

interface QuantityExtractor {
    BigDecimal extract(FootprintParameters params);
}
```

### Static Registry Contents

| componentId | kind | scope | quantityExtractor | applicability |
|---|---|---|---|---|
| `product-footprint` | COMPOSITE | -1 | — | ALWAYS |
| `materials` | COMPOSITE | -1 | — | ALWAYS |
| `raw-material` | LEAF | 3 | `params.materialWeightKg()` | ALWAYS |
| `processing` | LEAF | 1 | `params.materialWeightKg()` | ALWAYS |
| `transport` | COMPOSITE | -1 | — | ALWAYS |
| `supplier-to-warehouse` | LEAF | 3 | `params.supplierDistanceKm()` | ALWAYS |
| `warehouse-to-customer` | LEAF | 3 | `params.destinationDistanceKm()` | ALWAYS |
| `last-mile` | LEAF | 3 | `params.lastMileDistanceKm()` | ALWAYS |
| `packaging` | LEAF | 3 | `BigDecimal.ONE` | ALWAYS |
| `cold-storage` | COMPOSITE | -1 | — | REFRIGERATED_ONLY |
| `warehouse-refrigeration` | LEAF | 2 | `valueOf(storageDays) * materialWeightKg` | inherits parent |
| `transport-refrigeration` | LEAF | 2 | `supplierDistanceKm + destinationDistanceKm` | inherits parent |
| `last-mile-cold-chain` | LEAF | 3 | `params.lastMileDistanceKm()` | inherits parent |

### Mapping to Pricing Archetype

- Each LEAF → `SimpleComponent` wrapping `EmissionCalculator` (a `SimpleFixedCalculator`, formula `rate × quantity`).
- Each COMPOSITE → `CompositeComponent`, value = sum of resolved children (children skipped when inactive).
- Temporal `versionAt(componentId, t)` delegates to `EmissionFactorPort.versionAt(componentId, t)`. Engine does not duplicate version storage.

### Applicability Resolution

- Composites with applicability != `ALWAYS` are evaluated **before** tree walk.
- `REFRIGERATED_ONLY` is active iff `params.requiresRefrigeration() == true`.
- Inactive composite: entire subtree **excluded** from the breakdown (not zero-valued — the response simply omits that subtree).
- Children inherit parent applicability.

If a future component needs a more complex rule, `Applicability` becomes a sealed interface with predicate strategies. Out of scope V1.

### Tree Extensibility

Adding a component in V2:
1. Update `ComponentTreeRegistry.STATIC_DEFINITIONS`.
2. No audit-schema change (JSONB absorbs new component IDs).
3. Coordinate with Emission Factor Management to populate factors for new IDs.

No public API change required to add components.


---

## Section 5 — Calculation Flow & Determinism

### Algorithm — `calculateTotal(params, options)`

```
1. Validate params (compact constructor at construction; defensive recheck for nulls).
2. Generate or accept correlationId from options.
3. Resolve applicability for each composite (eager pre-pass).
4. Tree walk DFS post-order:
   a. For each LEAF in active subtree:
      i.   quantity = leaf.quantityExtractor.extract(params)
      ii.  factorVersion = emissionFactorPort.versionAt(leaf.componentId, params.timestamp)
      iii. if factorVersion.isEmpty():
             STRICT  → throw MissingFactorException(componentId, timestamp)
             LENIENT → emit LeafNode with kgCo2=ZERO, warning code=MISSING_FACTOR
      iv.  kgCo2 = factorVersion.rate × quantity, HALF_UP @ 4 decimals
      v.   emit LeafNode with all fields populated.
   b. For each COMPOSITE (active):
      i.   kgCo2 = sum(active children.kgCo2), HALF_UP @ 4 decimals
      ii.  warnings = engine-level only (not bubbled per-leaf)
5. Construct root FootprintBreakdown:
   - total = root.kgCo2
   - unit = "kgCO2"
   - factorVersions = flat dedup list (by factorVersionId)
   - rootWarnings = engine-wide warnings
6. If not dryRun: publish FootprintCalculatedEvent.
7. Return FootprintBreakdown.
```

### Algorithm — `calculateUnit(params, options)`

```
1. total = calculateTotal(params, options)        // publishes audit event once
2. scaleFactor = BigDecimal.valueOf(100).divide(materialWeightKg, MathContext.DECIMAL64)
3. scaledRoot = BreakdownScaler.scale(total.root, scaleFactor):
   - LEAF: kgCo2 = (kgCo2 × scaleFactor), HALF_UP @ 4
   - COMPOSITE: kgCo2 = sum(scaled children), HALF_UP @ 4  (recomputed, not scaled directly — preserves sum invariant)
4. Return new FootprintBreakdown(
     correlationId = total.correlationId,         // reuse — same audit row
     computedAt   = total.computedAt,
     total        = scaledRoot.kgCo2,
     unit         = "kgCO2/100g",
     root         = scaledRoot,
     rootWarnings = total.rootWarnings,
     factorVersions = total.factorVersions
   )
// No second event published — calculateUnit shares the calculateTotal audit row.
```

### Determinism Guarantees

1. **Pure math on resolved inputs** — `calculateTotal(params, opts)` and `calculateTotal(params, opts')` produce byte-identical `breakdown.root` and `breakdown.total` whenever `opts` and `opts'` differ only in `{correlationId, callerId, dryRun, comparisonGroupId}`.
2. **`versionAt(t)` is the only time-dependent input** — for the same `params.timestamp`, the same factor versions are resolved (assumes RC archetype immutability — see Section 8).
3. **Composite sum invariant**: `composite.kgCo2 == sum(child.kgCo2 for active children)` exactly. No rounding drift from scaling.
4. **Rounding policy**: `HALF_UP` at 4 decimals applied per node. Single source of truth: a `RoundingPolicy` static utility in `internal`.

### Failure Semantics

| Condition | Strict mode | Lenient mode |
|---|---|---|
| Missing emission factor at `t` for componentId X | throw `MissingFactorException` | leaf X emits `kgCo2=0` with warning `MISSING_FACTOR`; parent composite sum excludes it |
| Missing product attribute | application layer throws `MissingProductAttributeException` before engine call | application layer returns warning; engine never sees the call |
| Port returns >1 valid version (REJECT_OVERLAPPING violation) | throw `FactorVersionOverlapException` (HTTP 409) | same — never silently pick one |
| `materialWeightKg == 0` for `calculateUnit` | `InvalidParametersException` (compact constructor catches `<= 0`) | same — record-level invariant |
| Applicability resolution fails | throw `ApplicabilityResolutionException` (HTTP 409) | same — engine-level inconsistency |

### Timestamp Sanity Warnings (both modes)

- `timestamp` > 30 days in future → root warning `FUTURE_TIMESTAMP`.
- `timestamp` > 10 years in past → root warning `ANCIENT_TIMESTAMP`.

Advisory only; do not affect calculation. Useful for audit log inspection.

### Concurrency

Engine is stateless on the calculation path. `ComponentTreeRegistry` is immutable, built at startup. SPI ports must be thread-safe (contract requirement). No engine-internal synchronisation.


---

## Section 6 — Audit Logging & Event Flow

### Liquibase Migration

File: `src/main/resources/db/changelog/footprint/001-create-footprint-audit-log.xml`

```xml
<changeSet id="footprint-001" author="footprint-engine">
  <createTable tableName="footprint_audit_log">
    <column name="id" type="bigserial"><constraints primaryKey="true"/></column>
    <column name="correlation_id" type="uuid"><constraints nullable="false" unique="true"/></column>
    <column name="comparison_group_id" type="uuid"/>
    <column name="caller_id" type="varchar(255)"/>
    <column name="product_id" type="varchar(64)"><constraints nullable="false"/></column>
    <column name="timestamp_param" type="timestamptz"><constraints nullable="false"/></column>
    <column name="computed_at" type="timestamptz" defaultValueComputed="now()"><constraints nullable="false"/></column>
    <column name="strictness" type="varchar(16)"><constraints nullable="false"/></column>
    <column name="normalisation" type="varchar(16)"><constraints nullable="false"/></column>
    <column name="total_kg_co2" type="numeric(18,6)"><constraints nullable="false"/></column>
    <column name="warnings_count" type="integer" defaultValueNumeric="0"><constraints nullable="false"/></column>
    <column name="factor_version_ids" type="text[]"/>
    <column name="breakdown" type="jsonb"><constraints nullable="false"/></column>
    <column name="params" type="jsonb"><constraints nullable="false"/></column>
  </createTable>
  <createIndex indexName="idx_audit_product_timestamp" tableName="footprint_audit_log">
    <column name="product_id"/><column name="timestamp_param"/>
  </createIndex>
  <createIndex indexName="idx_audit_comparison_group" tableName="footprint_audit_log">
    <column name="comparison_group_id"/>
  </createIndex>
  <sql>CREATE INDEX idx_audit_factor_versions_gin ON footprint_audit_log USING GIN (factor_version_ids);</sql>
</changeSet>
```

### Event Publication

```java
@Service
class DefaultFootprintFacade implements FootprintFacade {
  private final ApplicationEventPublisher events;
  public FootprintBreakdown calculateTotal(FootprintParameters p, CalculationOptions o) {
    var breakdown = computeInternally(p, o);
    if (!o.dryRun()) {
      events.publishEvent(new FootprintCalculatedEvent(breakdown, p, o, Instant.now()));
    }
    return breakdown;
  }
}

public record FootprintCalculatedEvent(
    FootprintBreakdown breakdown,
    FootprintParameters params,
    CalculationOptions options,
    Instant publishedAt
) {}
```

### Listener

```java
@Component
class FootprintAuditListener {
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2.0))
  public void onCalculated(FootprintCalculatedEvent e) {
    var entity = FootprintAuditEntity.builder()
        .correlationId(e.breakdown().correlationId())
        .comparisonGroupId(e.options().comparisonGroupId())
        .callerId(Optional.ofNullable(e.options().callerId()).orElse("anonymous"))
        .productId(e.params().productId().value())
        .timestampParam(e.params().timestamp())
        .computedAt(e.breakdown().computedAt())
        .strictness(e.options().strictness().name())
        .normalisation(e.options().normalisation().name())
        .totalKgCo2(e.breakdown().total())
        .warningsCount(countWarnings(e.breakdown()))
        .factorVersionIds(e.breakdown().factorVersions().stream().map(FactorVersionRef::factorVersionId).toArray(String[]::new))
        .breakdownJson(mapper.writeValueAsString(e.breakdown()))
        .paramsJson(mapper.writeValueAsString(e.params()))
        .build();
    repo.save(entity);
  }

  @Recover
  public void recover(Exception ex, FootprintCalculatedEvent e) {
    log.error("Audit persistence failed after retries for correlationId={}", e.breakdown().correlationId(), ex);
    auditFailureMetric.increment();
  }
}
```

### Async Write Semantics — Trade-off Acknowledgement

- Listener fires `AFTER_COMMIT`. The REST response may return before the audit row is persisted (~10ms window).
- Anna's reconciliation tooling must tolerate eventual consistency: 1-2 second polling delay acceptable on lookup-after-write.
- Retry: 3 attempts, exponential backoff for transient DB issues.
- `@Recover` is terminal: logs ERROR + increments `footprint_audit_failure_total` metric. Operations team alerts on non-zero rate.

### Outbox Alternative (Deferred)

If audit-failure metric exceeds 0.01% of calls, switch to an outbox pattern (insert in same tx as caller's tx + poller). Out of scope V1.

### dryRun Semantics

`options.dryRun() == true`:
- No `FootprintCalculatedEvent` is published.
- No audit row exists.
- Response otherwise identical (correlationId still generated, breakdown still returned).
- Intended for Piotr's integration tests and "preview before locking" workflows.

### Query Patterns Supported

| Query | Index used |
|---|---|
| All calls for productId in date range | `idx_audit_product_timestamp` |
| All calls in a comparison group | `idx_audit_comparison_group` |
| All calls that used a specific factor version | `idx_audit_factor_versions_gin` |
| Lookup by correlationId (exporter) | unique constraint on `correlation_id` |


---

## Section 7 — Error & Warning Taxonomy

### Exception → HTTP → Code Mapping

| Exception | HTTP | `code` | When raised |
|---|---|---|---|
| `MissingFactorException` | 422 | `MISSING_FACTOR` | `EmissionFactorPort.versionAt()` empty for active leaf, STRICT mode |
| `MissingProductAttributeException` | 422 | `MISSING_ATTRIBUTE` | Application layer (not engine) — product lacks `materialWeightKg` etc. |
| `InvalidParametersException` | 400 | `INVALID_PARAMETERS` | Record invariants violated (null timestamp, negative distance, zero weight) |
| `ApplicabilityResolutionException` | 409 | `INCONSISTENT_APPLICABILITY` | Composite's applicability rule cannot be evaluated |
| `FactorVersionOverlapException` | 409 | `OVERLAPPING_FACTOR_VERSIONS` | `EmissionFactorPort` returns >1 valid version for same `(componentId, t)` |

### Problem+JSON Shape (RFC 7807)

```json
{
  "type": "https://aj.example.com/problems/{kebab-case-code}",
  "title": "<human-readable summary>",
  "status": 422,
  "detail": "<full explanation>",
  "code": "MISSING_FACTOR",
  "correlationId": "<uuid>",
  "componentId": "transport",
  "params": { ... echo ... }
}
```

The `code` extension is the contract — clients SHOULD switch on it (not on `title`/`detail`).

### Warning Catalog (Lenient Mode)

| code | Emitted by | Attached to | Details |
|---|---|---|---|
| `MISSING_FACTOR` | Engine | Leaf node | `{ componentId, timestamp }` |
| `MISSING_ATTRIBUTE` | Application layer | Root | `{ attributeName }` |
| `FUTURE_TIMESTAMP` | Engine | Root | `{ timestamp, daysAhead }` |
| `ANCIENT_TIMESTAMP` | Engine | Root | `{ timestamp, yearsAgo }` |
| `INACTIVE_SUBTREE` | Engine | Root (informational) | `{ componentId }` — emitted when an applicability rule excludes a subtree |

### Exception Handler

```java
@RestControllerAdvice
class FootprintExceptionHandler {
  @ExceptionHandler(MissingFactorException.class)
  ProblemDetail handleMissingFactor(MissingFactorException ex, HttpServletRequest req) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    pd.setType(URI.create("https://aj.example.com/problems/missing-factor"));
    pd.setTitle("Emission factor not available");
    pd.setProperty("code", "MISSING_FACTOR");
    pd.setProperty("componentId", ex.details().get("componentId"));
    pd.setProperty("correlationId", ex.details().get("correlationId"));
    return pd;
  }
  // ... one handler per exception subclass
}
```

### Example Error Responses

```http
GET /api/products/OFB-330/footprint?asOf=2099-01-01

HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "https://aj.example.com/problems/missing-factor",
  "title": "Emission factor not available",
  "status": 422,
  "detail": "No emission factor version valid for component 'raw-material' at 2099-01-01T00:00:00Z",
  "code": "MISSING_FACTOR",
  "componentId": "raw-material",
  "correlationId": "8a3f-..."
}
```

```http
GET /api/products/OFB-330/footprint?materialWeightKg=-1

HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://aj.example.com/problems/invalid-parameters",
  "title": "Invalid calculation parameters",
  "status": 400,
  "detail": "materialWeightKg must be > 0",
  "code": "INVALID_PARAMETERS",
  "field": "materialWeightKg"
}
```

### Logging Policy

- STRICT-mode exceptions: WARN with `correlationId`, `productId`, `componentId`, code. Stack trace at DEBUG.
- LENIENT-mode warnings: DEBUG only.
- 409 errors: ERROR — engine/factor module misconfiguration, not user error.


---

## Section 8 — Acceptance Criteria & Test Scenarios

### Success Criteria Mapping (revised — async audit reflected)

| # | Criterion | Verifiable by |
|---|---|---|
| 1 | Determinism — same params → byte-identical breakdown | Contract test in `DefaultFootprintFacadeContractTests` |
| 2 | Historical reproducibility — past `t` uses past factors | Scenario A |
| 3 | Full breakdown — kgCo2 + scope + factor info on every leaf | Scenario A + JSON schema validation |
| 4 | Applicability — non-refrigerated excludes cold-storage subtree | Scenario C |
| 5 | Context sensitivity — different destination = different result | Scenario A |
| 6 | Audit guarantee (eventually consistent) — every non-dryRun call → audit row within 5s | Scenario B + listener integration test |
| 7 | Strict mode fails loudly — missing factor → 422 Problem+JSON | Scenario D |
| 8 | Lenient mode degrades gracefully — partial breakdown + warnings | Scenario E |
| 9 | UI surfaces — Phase 7 mockups acceptable to frontend team | Phase 7 deliverable |
| 10 | CSV export — Anna downloads flattened breakdown by correlationId | Scenario F |

### Test Scenarios (TestContainers PostgreSQL)

#### Scenario A — Frozen berries, multiple contexts
```
GIVEN  product OFB-330 (materialWeightKg=0.5, supplierDistanceKm=2800, requiresRefrigeration=true)
       summer + winter factor versions
WHEN   GET /api/products/OFB-330/footprint?asOf=2026-07-15&destinationKm=570&lastMileKm=15&storageDays=14
THEN   200; total ≈ 5.37; breakdown has materials, transport, packaging, cold-storage; factorVersions includes summer IDs
WHEN   GET /api/products/OFB-330/footprint?asOf=2026-01-15&destinationKm=15&lastMileKm=5&storageDays=14
THEN   200; total ≈ 2.90; factorVersions includes winter IDs
```

#### Scenario B — Audit row (eventually consistent)
```
WHEN   GET /api/products/OFB-330/footprint?asOf=2026-07-15
THEN   200 with correlationId X
AND    poll SELECT FROM footprint_audit_log WHERE correlation_id=X (5s timeout) returns one row
       row.breakdown JSONB matches response body
       row.warnings_count == 0
```

#### Scenario C — Non-refrigerated excludes cold-storage
```
GIVEN  product CAW-042 (requiresRefrigeration=false)
WHEN   GET /api/products/CAW-042/footprint?asOf=2026-07-15
THEN   200; breakdown.children IDs == [materials, transport, packaging]   // no cold-storage
       rootWarnings contains { code: INACTIVE_SUBTREE, componentId: cold-storage }
```

#### Scenario D — Strict missing factor
```
GIVEN  no factor versions defined before 2020
WHEN   GET /api/products/OFB-330/footprint?asOf=2019-01-01&strictness=STRICT
THEN   422; application/problem+json with code=MISSING_FACTOR; no audit row
```

#### Scenario E — Lenient partial breakdown
```
GIVEN  factor missing for last-mile only
WHEN   GET /api/products/OFB-330/footprint?asOf=2026-07-15&strictness=LENIENT
THEN   200; breakdown.children[transport].children[last-mile].kgCo2 == 0
       that leaf.warnings includes { code: MISSING_FACTOR }
       audit row written with warnings_count == 1
```

#### Scenario F — CSV export
```
GIVEN  scenario A executed, correlationId X
WHEN   GET /api/footprints/calculations/{X}/export?format=csv
THEN   200; Content-Type text/csv; header row + 9 leaf rows (refrigerated) or 4 (non-refrigerated)
       Σ(kg_co2) == body.total within rounding tolerance
```

#### Scenario G — dryRun suppresses audit
```
WHEN   GET /api/products/OFB-330/footprint?asOf=2026-07-15&dryRun=true
THEN   200 with correlationId Y
AND    SELECT FROM footprint_audit_log WHERE correlation_id=Y → 0 rows (after 5s)
```

#### Scenario H — Comparison group linkage
```
GIVEN  X-Comparison-Group: <uuid G>
WHEN   3 sequential GETs with same header, different asOf
THEN   each returns its own correlationId
AND    SELECT FROM footprint_audit_log WHERE comparison_group_id=G → 3 rows; same product_id; different timestamp_param
```

#### Scenario I — calculateUnit invariant
```
GIVEN  scenario A executed, total=T
WHEN   calculateUnit called with same params
THEN   returned total == round(T × 100 / materialWeightKg, HALF_UP @ 4)
       composite sum invariant holds at every node
       audit row records canonical TOTAL (not unit) breakdown
       only ONE audit row exists (shared between calculateUnit & internal calculateTotal call)
```

### Test Scope

- Integration: TestContainers PostgreSQL + MockMvc + real `FootprintFacade`.
- Unit: 2-3 tests for `BreakdownScaler` (composite-sum invariant under scaling).
- Mocks: only the SPI ports — fake implementations in test fixtures.
- ~9 integration + ~3 unit tests — aligned with the standard "2-8 tests per feature, CRUD plus edge cases."

### Risk Register

| Risk | Mitigation |
|---|---|
| Emission Factor Management retroactively edits factor versions | Contract assumption documented; deferred audit divergence detection (Alt 8.C) ready in design alternatives. Add `CHECK` constraint or trigger if RC module's immutability proves weak. |
| Async audit write loses events on JVM crash before flush | Outbox pattern (deferred V2) ready to swap in if metric crosses 0.01% threshold. |
| URL length cap on GET endpoint | Practical payload <500 chars; acceptable V1. If exceeded, add POST in V2. |
| Pricing Archetype library version drift | Pin version in pom.xml; CI smoke test on upgrade. |

