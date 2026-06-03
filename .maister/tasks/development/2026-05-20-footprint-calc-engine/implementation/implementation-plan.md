# Implementation Plan — Footprint Calculation Engine

## Plan Overview

Stateless carbon-footprint calculation engine under `pl.devstyle.aj.footprint` in the Spring Boot 4.0.5 / Java 25 `aj` monolith. Build comprises ten task groups (TG-1..TG-10) covering: project setup, in-house Pricing-Archetype-lite primitives, footprint domain types, SPI ports + in-memory stubs, engine internals, Liquibase migration, async audit infrastructure, REST web layer with Problem+JSON, CSV export slice, and a single-line Security wiring.

- Task groups: **10**
- Total tests: **~26** (3 unit-test groups + 6 integration/validation test classes from spec §Acceptance Tests)
- Expected `mvn verify`: green; jOOQ codegen self-services on the new `footprint_audit_log` table.

## Build Order Rationale

The dependency chain follows the layering called out in the spec:

1. **Setup first (TG-1)** — adds new Maven deps and `@EnableRetry`/property knob. Everything else needs `commons-csv`, `spring-retry`, `spring-aspects` on the classpath, plus the externalized `app.footprint.problem-base-uri`.
2. **Pricing primitives (TG-2)** — `Calculator`, `Component`, `Validity`, `ComponentVersion`, `Applicability`, `QuantityExtractor`, `ParameterValue` are leaf-most types with zero internal deps. Footprint domain (TG-3) and SPI ports (TG-4) both consume them.
3. **Footprint domain types (TG-3)** — pure records / sealed interfaces consumed by every higher layer (ports, engine, web, audit, csv). No Spring; parallelizable with TG-2 because both are leaf modules under different packages.
4. **SPI ports + stubs (TG-4)** — depend on TG-2 (`ComponentVersion`, `Validity`, `ComponentId`) and TG-3 (`FootprintParameters`). Stub seeders must be ready before engine integration tests fire up.
5. **Engine internals (TG-5)** — `ComponentTreeRegistry`, `BreakdownTreeBuilder`, `BreakdownScaler`, `DefaultFootprintFacade`. Depends on TG-2/3/4.
6. **Liquibase migration (TG-6)** — must precede audit infra (TG-7) and CSV export (TG-9) because they hit the `footprint_audit_log` table during integration tests; jOOQ codegen runs on `mvn compile`. Parallelizable with TG-5 (independent file).
7. **Audit infra (TG-7)** — depends on TG-3 (event payload types), TG-5 (engine publishes event), TG-6 (table exists).
8. **Web layer (TG-8)** — depends on TG-5 (facade) and TG-3 (exceptions for handler). Publishes events handled by TG-7 listener.
9. **CSV export slice (TG-9)** — depends on TG-7 (reads `FootprintAuditEntity` rows). Can ship in parallel with TG-8 once TG-7 is green, but is wave-after-TG-8 to keep the Problem+JSON handler shared with web tests stable.
10. **Security wiring (TG-10)** — last; trivially small one-line edit, validated by TG-9 integration tests.

### Parallelizable waves

| Wave | Task groups | Notes |
|---|---|---|
| 1 | TG-1 | Setup must complete before anything compiles with new deps. |
| 2 | TG-2, TG-3, TG-6 | Three leaf modules: archetype primitives, footprint domain types, Liquibase changelog. Disjoint files. |
| 3 | TG-4 | Needs TG-2 + TG-3 types. |
| 4 | TG-5, TG-7 | Engine internals + audit infra. TG-7 needs TG-6 (wave 2) for table existence; TG-5 needs TG-4 (wave 3). They share no files. |
| 5 | TG-8, TG-9 | Web layer + CSV export. Both depend on the engine + audit table. Disjoint controllers + handlers. |
| 6 | TG-10 | Single-line `SecurityConfiguration` edit. |

`--sequential` mode falls back to TG-1 → TG-10 in order.

---

## Task Groups

### TG-1 Project setup

- **Goal**: Bring the three new Maven deps onto the classpath, enable Spring Retry, and externalize the Problem+JSON base URI.
- **Files to create / modify**:
  - `pom.xml` — add `org.springframework.retry:spring-retry`, `org.springframework:spring-aspects`, `org.apache.commons:commons-csv` under `<dependencies>`.
  - `src/main/java/pl/devstyle/aj/footprint/config/FootprintModuleConfig.java` — `@Configuration` class annotated with `@EnableRetry`.
  - `src/main/resources/application.properties` — append `app.footprint.problem-base-uri=/problems`.
- **Implementation steps**:
  1. Add the three `<dependency>` blocks (compile scope) to `pom.xml`; keep `<dependencyManagement>` precedence — let Spring BOM choose versions for `spring-retry`/`spring-aspects`; pin a current `commons-csv` (≥ 1.10).
  2. Create `FootprintModuleConfig` with `@Configuration` + `@EnableRetry`; package-private constructor.
  3. Add the property line to `application.properties`.
  4. Run `mvn -DskipTests compile` to prove the build resolves.
- **Tests**: none (setup-only group; verified by downstream groups compiling).
- **Acceptance criteria**:
  - `mvn -DskipTests compile` exits 0.
  - `pom.xml` `git diff` shows exactly three new `<dependency>` entries.
  - Grepping the codebase for `@EnableRetry` returns exactly one hit (`FootprintModuleConfig`).
- **Depends on**: none.
- **Wave**: 1.
- **Standards touched**: `standards/global/conventions.md`, `standards/global/minimal-implementation.md`.

---

### TG-2 Pricing-Archetype-lite primitives

- **Goal**: In-house archetype primitives under `pl.devstyle.aj.archetype.pricing`, immutable and thread-safe, sized exactly for the 13-node tree.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/archetype/pricing/Calculator.java`
  - `src/main/java/pl/devstyle/aj/archetype/pricing/SimpleFixedCalculator.java`
  - `src/main/java/pl/devstyle/aj/archetype/pricing/CalculatorId.java`
  - `src/main/java/pl/devstyle/aj/archetype/pricing/ComponentId.java`
  - `src/main/java/pl/devstyle/aj/archetype/pricing/Component.java` (sealed)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/SimpleComponent.java` (record)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/CompositeComponent.java` (record)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/Validity.java` (record + static `assertNonOverlapping`)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/ComponentVersion.java`
  - `src/main/java/pl/devstyle/aj/archetype/pricing/Applicability.java` (enum)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/QuantityExtractor.java` (functional interface)
  - `src/main/java/pl/devstyle/aj/archetype/pricing/ParameterValue.java`
  - `src/test/java/pl/devstyle/aj/archetype/pricing/SimpleFixedCalculatorTests.java`
  - `src/test/java/pl/devstyle/aj/archetype/pricing/ValidityTests.java`
- **Implementation steps**:
  1. Define identifier records (`ComponentId`, `CalculatorId`) with compact-constructor blank guards.
  2. Define `Calculator` interface + `SimpleFixedCalculator` (multiply then `setScale(4, HALF_UP)`).
  3. Define `Validity` with `covers(Instant)` (inclusive lower, exclusive upper), `static overlaps(a,b)`, and `static assertNonOverlapping(List<Validity>)` throwing `IllegalArgumentException` on violation.
  4. Define `ComponentVersion` record holding `(componentId, factorVersionId, rate, validity)`.
  5. Define sealed `Component` permitting `SimpleComponent` (id + calculatorId + extractor + applicability + scope int) and `CompositeComponent` (id + `List.copyOf(childIds)` + applicability).
  6. Define `Applicability` enum with `ALWAYS`, `REFRIGERATED_ONLY` and an `isActive(ParameterValue)` abstract method.
  7. Define `QuantityExtractor` functional interface and `ParameterValue` record per spec §In-House Pricing Archetype.
- **Tests**:
  - `SimpleFixedCalculatorTests.calculate_rateTimesQuantity_roundsHalfUpAt4Decimals` — proves HALF_UP rounding at 4 decimals.
  - `SimpleFixedCalculatorTests.calculate_zeroQuantity_returnsZero` — proves identity element behavior.
  - `ValidityTests.covers_instantAtValidFrom_returnsTrue` — inclusive lower bound.
  - `ValidityTests.covers_instantAtValidTo_returnsFalse` — exclusive upper bound.
  - `ValidityTests.assertNonOverlapping_overlappingWindows_throwsIllegalArgumentException` — REJECT_OVERLAPPING.
- **Acceptance criteria**:
  - All five tests pass under `mvn -Dtest='pl.devstyle.aj.archetype.pricing.*Tests' test`.
  - `Component` is `sealed` and permits only the two declared records.
  - `CompositeComponent` constructor stores defensive `List.copyOf` (verified by attempting mutation in a unit assertion — covered indirectly via record constructor).
- **Depends on**: TG-1.
- **Wave**: 2.
- **Standards touched**: `standards/global/minimal-implementation.md`, `standards/global/coding-style.md`, `standards/testing/backend-testing.md`.

---

### TG-3 Footprint domain types

- **Goal**: Records, enums, and sealed exception hierarchy that form the engine's public API contract.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/api/FootprintParameters.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/CalculationOptions.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/Strictness.java` (enum)
  - `src/main/java/pl/devstyle/aj/footprint/api/Normalisation.java` (enum: TOTAL, PER_100G)
  - `src/main/java/pl/devstyle/aj/footprint/api/Unit.java` (enum)
  - `src/main/java/pl/devstyle/aj/footprint/api/BreakdownNode.java` (sealed)
  - `src/main/java/pl/devstyle/aj/footprint/api/CompositeBreakdownNode.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/LeafBreakdownNode.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/FootprintBreakdown.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/FootprintWarning.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/FactorVersionRef.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/FootprintCalculationException.java` (sealed)
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/MissingFactorException.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/MissingProductAttributeException.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/InvalidParametersException.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/ApplicabilityResolutionException.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/exception/FactorVersionOverlapException.java`
- **Implementation steps**:
  1. Define `FootprintParameters` record per spec REST table (productId, materialWeightKg, supplierDistanceKm, destinationDistanceKm, lastMileDistanceKm, storageDays, requiresRefrigeration, asOf).
  2. Define `CalculationOptions` record (`strictness`, `normalisation`, `dryRun`, optional `correlationId`, optional `comparisonGroupId`, optional `callerId`).
  3. Define enums.
  4. Define sealed `BreakdownNode` with permits for `CompositeBreakdownNode(componentId, kgCo2, children, warnings)` and `LeafBreakdownNode(componentId, kgCo2, scope, factorVersionId, factorRate, factorValidFrom, quantity, warnings)`.
  5. Define `FootprintBreakdown(root, total, factorVersions, rootWarnings, computedAt, correlationId)`.
  6. Define `FootprintWarning(code, message, componentId)` and `FactorVersionRef(componentId, factorVersionId, validFrom)`.
  7. Define sealed `FootprintCalculationException extends RuntimeException` with abstract `String code()` and `Map<String,Object> details()`. Each concrete subclass exposes `public static final String CODE = "...";` per Implementation Notes §7 and the Problem+JSON mapping table.
- **Tests**: none in this group (types are exercised by TG-5/TG-8 integration tests; pure records have no behavior worth a dedicated test).
- **Acceptance criteria**:
  - `mvn -DskipTests compile` green.
  - `FootprintCalculationException` is `sealed` and permits exactly the five subclasses listed in the spec mapping table.
  - Each subclass exposes `public static final String CODE` with the value from the spec.
- **Depends on**: TG-1.
- **Wave**: 2.
- **Standards touched**: `standards/global/minimal-implementation.md`, `standards/global/coding-style.md`, `standards/backend/api.md`.

---

### TG-4 SPI ports + in-memory stub adapters

- **Goal**: Port interfaces under `internal/ports/` and in-memory stub `@Component` adapters under `spi/stub/` seeded for the 9 acceptance scenarios.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/internal/ports/EmissionFactorPort.java`
  - `src/main/java/pl/devstyle/aj/footprint/internal/ports/ProductAttributesPort.java`
  - `src/main/java/pl/devstyle/aj/footprint/spi/stub/InMemoryEmissionFactorPort.java`
  - `src/main/java/pl/devstyle/aj/footprint/spi/stub/InMemoryProductAttributesPort.java`
  - `src/main/java/pl/devstyle/aj/footprint/config/FootprintModuleConfig.java` (modify — wire the stubs / `ObjectMapper` bean if needed)
- **Implementation steps**:
  1. Define `EmissionFactorPort.versionAt(ComponentId, Instant) : Optional<ComponentVersion>` plus `factorVersionById(String) : Optional<ComponentVersion>` for CSV/audit lookups.
  2. Define `ProductAttributesPort.findById(String productId) : Optional<ProductAttributes>` where `ProductAttributes` is a nested record carrying `materialWeightKg, supplierDistanceKm, destinationDistanceKm, lastMileDistanceKm, requiresRefrigeration` defaults.
  3. Implement `InMemoryProductAttributesPort` as `@Component`; seed `OFB-330` and `CAW-042` per spec table; `UNKNOWN-1` deliberately absent.
  4. Implement `InMemoryEmissionFactorPort` as `@Component`; seed the 9 componentId entries with the exact `(rate, validity)` rows from spec §Stub SPI Adapters; assign stable `factorVersionId` UUIDs. Call `Validity.assertNonOverlapping(...)` per componentId in the seeder.
  5. Both stubs use `Map.copyOf` for immutability.
- **Tests**: none directly (stubs are fixtures; correctness is asserted by TG-5/TG-7/TG-8 integration tests). Spec line 683 explicitly states "no separate test-only stub seeder needed".
- **Acceptance criteria**:
  - Spring context starts with both stubs as injectable beans (verified by TG-5 `FootprintEngineIntegrationTests`).
  - `InMemoryEmissionFactorPort` seeder calls `Validity.assertNonOverlapping` for every componentId at construction; a deliberately overlapping seed in a one-off local test throws.
  - `InMemoryProductAttributesPort.findById("UNKNOWN-1")` returns `Optional.empty()`.
- **Depends on**: TG-2, TG-3.
- **Wave**: 3.
- **Standards touched**: `standards/global/minimal-implementation.md`, `standards/global/conventions.md`.

---

### TG-5 Engine internals + facade

- **Goal**: 13-node `ComponentTreeRegistry`, DFS post-order calculation, composite-sum-preserving `BreakdownScaler`, rounding policy, and the public `FootprintFacade` bean.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/internal/ComponentTreeRegistry.java`
  - `src/main/java/pl/devstyle/aj/footprint/internal/EmissionCalculator.java` (wraps `SimpleFixedCalculator`)
  - `src/main/java/pl/devstyle/aj/footprint/internal/BreakdownTreeBuilder.java`
  - `src/main/java/pl/devstyle/aj/footprint/internal/BreakdownScaler.java`
  - `src/main/java/pl/devstyle/aj/footprint/internal/RoundingPolicy.java`
  - `src/main/java/pl/devstyle/aj/footprint/api/FootprintFacade.java` (interface)
  - `src/main/java/pl/devstyle/aj/footprint/internal/DefaultFootprintFacade.java`
  - `src/main/java/pl/devstyle/aj/footprint/config/FootprintModuleConfig.java` (modify — register registry + facade beans; declare `ApplicationEventPublisher` injection)
  - `src/test/java/pl/devstyle/aj/footprint/internal/BreakdownScalerTests.java`
  - `src/test/java/pl/devstyle/aj/footprint/internal/FootprintEngineIntegrationTests.java`
- **Implementation steps**:
  1. Build `ComponentTreeRegistry` as an immutable `Map<ComponentId, Component>` returning the 13 nodes wired by the feature-spec tree (`product-footprint → materials, transport, cold-storage, packaging` etc.). `cold-storage` and its 3 children carry `REFRIFRIGERATED_ONLY`.
  2. `BreakdownTreeBuilder.build(params, asOf, strictness)` performs DFS post-order:
     - For each leaf: resolve `Applicability.isActive(parameterValue)`; if inactive, skip subtree (no zero leaf).
     - Resolve `ComponentVersion = port.versionAt(...)`; STRICT → throw `MissingFactorException`, LENIENT → emit `FootprintWarning` and treat leaf kgCo2 = `BigDecimal.ZERO`, still emit a leaf node with null factor refs.
     - Compute leaf `kgCo2 = EmissionCalculator.apply(rate, extractor.extract(params))`.
     - For composite: sum active children; aggregate warnings.
  3. `BreakdownScaler.scale(breakdown, normalisation, materialWeightKg)` — if `PER_100G`, multiply every leaf kgCo2 by `0.1 / materialWeightKg`; composites recompute as sum of scaled children to preserve invariant.
  4. `RoundingPolicy.round(BigDecimal) = setScale(4, HALF_UP)` applied at leaf level only; composites stay exact.
  5. `DefaultFootprintFacade`:
     - `calculateTotal(params, options)`: build breakdown, apply Strict/Lenient policy, populate `FactorVersionRef` list, generate or honor incoming `correlationId`, emit timestamp sanity warnings (`FUTURE_TIMESTAMP` > 30d, `ANCIENT_TIMESTAMP` > 10y), publish `FootprintCalculatedEvent` unless `dryRun=true`.
     - `calculateUnit(params, options)`: invoke `calculateTotal` once (audit-row identity), apply scaler to the returned breakdown, return view; **does NOT publish a second event**.
- **Tests** (8 total in this group):
  - `BreakdownScalerTests.scale_per100gOnHalfKg_doublesEveryLeaf` — doubling invariant.
  - `BreakdownScalerTests.scale_composite_equalsSumOfScaledChildren` — no rounding drift at composite level.
  - `BreakdownScalerTests.scale_inactiveSubtreeAbsent_doesNotProduceNaN` — applicability propagation.
  - `FootprintEngineIntegrationTests.calculateTotal_summerContext_returnsSummerFactorBreakdown` — Scenario A summer.
  - `FootprintEngineIntegrationTests.calculateTotal_winterContext_returnsWinterFactorBreakdown` — Scenario A winter (historical reproducibility).
  - `FootprintEngineIntegrationTests.calculateTotal_nonRefrigeratedProduct_excludesColdStorageSubtree` — Scenario C (applicability).
  - `FootprintEngineIntegrationTests.calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit` — Scenario I + Implementation Notes §8 (audit-row identity).
- **Acceptance criteria**:
  - Registry contains exactly 13 components matching the feature-spec tree (asserted indirectly via summer test breakdown shape).
  - All seven tests pass.
  - Same `params` invoked twice yields byte-identical `breakdown` JSON (excluding envelope `correlationId`, `computedAt`) — covered by determinism test.
  - `calculateUnit` produces exactly one persisted audit row (asserted in Scenario I test via `findAll().size()`).
- **Depends on**: TG-2, TG-3, TG-4, (event publication path needs TG-7 in same wave).
- **Wave**: 4.
- **Standards touched**: `standards/global/minimal-implementation.md`, `standards/global/error-handling.md`, `standards/testing/backend-testing.md`.

---

### TG-6 Liquibase migration

- **Goal**: Drop `011-create-footprint-audit-log.yaml` into the 2026 changelog directory verbatim per spec.
- **Files to create / modify**:
  - `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml`
- **Implementation steps**:
  1. Copy the YAML from spec §Liquibase Migration into the file.
  2. Run `mvn -DskipTests compile` so jOOQ codegen picks up the new table and produces `pl.devstyle.aj.jooq.tables.FootprintAuditLog`.
  3. Verify no edit needed to `db.changelog-master.yaml` (`includeAll` of `2026/`).
- **Tests**: none in this group (verified by TG-7 integration tests booting Liquibase).
- **Acceptance criteria**:
  - File exists at exact path.
  - `mvn -DskipTests compile` regenerates jOOQ classes; `pl.devstyle.aj.jooq.tables.FootprintAuditLog` is present in `target/generated-sources/jooq`.
  - Indices `idx_footprint_audit_product_requested` and `idx_footprint_audit_correlation_id` and unique constraint `uk_footprint_audit_correlation_id` declared verbatim.
- **Depends on**: TG-1.
- **Wave**: 2.
- **Standards touched**: `standards/backend/migrations.md`.

---

### TG-7 Audit infra

- **Goal**: Async, transactional, retry-protected persistence of one audit row per `calculateTotal` call.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/audit/FootprintCalculatedEvent.java` (record payload)
  - `src/main/java/pl/devstyle/aj/footprint/audit/FootprintAuditEntity.java` (JPA entity per spec field bindings)
  - `src/main/java/pl/devstyle/aj/footprint/audit/FootprintAuditRepository.java` (Spring Data JPA)
  - `src/main/java/pl/devstyle/aj/footprint/audit/FootprintAuditMapper.java` (event → entity)
  - `src/main/java/pl/devstyle/aj/footprint/audit/FootprintAuditListener.java` (@TransactionalEventListener)
  - `src/test/java/pl/devstyle/aj/footprint/audit/FootprintAuditListenerIntegrationTests.java`
- **Implementation steps**:
  1. Define `FootprintCalculatedEvent(UUID correlationId, UUID comparisonGroupId, String productId, String callerId, Instant requestedAt, BigDecimal totalKgCo2, Strictness strictness, Normalisation normalisation, FootprintBreakdown breakdown, List<FootprintWarning> warnings, List<String> factorVersions, boolean dryRun)`.
  2. Implement `FootprintAuditEntity` exactly per spec §Liquibase / "Audit entity field bindings": `@SequenceGenerator(name="base_seq",...)`, business-key `equals`/`hashCode` on `correlationId` (per `standards/backend/models.md`), Lombok `@Getter/@Setter/@NoArgsConstructor` only.
  3. `FootprintAuditRepository extends JpaRepository<FootprintAuditEntity, Long>` with `Optional<FootprintAuditEntity> findByCorrelationId(UUID correlationId)`.
  4. `FootprintAuditMapper.toEntity(event)`: serialise `breakdown`/`warnings`/`factorVersions` via the project's `ObjectMapper` to `Map<String,Object>`/`List<Map<String,Object>>`/`List<String>` (JSONB-friendly types).
  5. `FootprintAuditListener` with `@Component`:
     - `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`
     - `@Retryable(maxAttempts=3, backoff=@Backoff(delay=200, multiplier=2))`
     - `@Recover` method: log ERROR with correlationId, increment Micrometer counter `footprint.audit.failed`. Caller unaffected.
  6. Inject `MeterRegistry`; create the counter eagerly so the metric is observable even at zero failures.
- **Tests** (3):
  - `FootprintAuditListenerIntegrationTests.onFootprintCalculated_committedTransaction_persistsAuditRowWithinFiveSeconds` — Scenario B; poll repository up to 5 s.
  - `FootprintAuditListenerIntegrationTests.onFootprintCalculated_lenientMissingFactor_persistsAuditRowWithWarnings` — Scenario E persistence side.
  - `FootprintAuditListenerIntegrationTests.calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit` — Scenario I; asserts the persisted `breakdown` is the TOTAL view, and `findAll().size() == 1`.
- **Acceptance criteria**:
  - All three tests pass.
  - `FootprintAuditEntity` extends `BaseEntity` and has business-key `equals/hashCode` on `correlationId`.
  - `@SequenceGenerator(name="base_seq", sequenceName="footprint_audit_log_id_seq", allocationSize=1)` present.
  - JSONB columns mapped via `@JdbcTypeCode(SqlTypes.JSON)`.
- **Depends on**: TG-3, TG-5, TG-6.
- **Wave**: 4.
- **Standards touched**: `standards/backend/models.md`, `standards/backend/queries.md`, `standards/backend/migrations.md`, `standards/testing/backend-testing.md`.

---

### TG-8 Web layer (engine endpoint + Problem+JSON)

- **Goal**: Expose `GET /api/products/{productId}/footprint` and the package-scoped Problem+JSON handler.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/web/FootprintController.java`
  - `src/main/java/pl/devstyle/aj/footprint/web/FootprintQueryRequest.java` (record carrying validated query params + headers)
  - `src/main/java/pl/devstyle/aj/footprint/web/FootprintResponseDto.java` (record mapping `FootprintBreakdown` to the wire shape from spec REST contract)
  - `src/main/java/pl/devstyle/aj/footprint/web/FootprintExceptionHandler.java` (`@RestControllerAdvice(basePackages="pl.devstyle.aj.footprint")`)
  - `src/test/java/pl/devstyle/aj/footprint/web/FootprintControllerIntegrationTests.java`
  - `src/test/java/pl/devstyle/aj/footprint/web/FootprintControllerValidationTests.java`
- **Implementation steps**:
  1. Build `FootprintController` exposing `GET /api/products/{productId}/footprint` with `@RequestParam(required=false)` for the 10 params from spec table and `@RequestHeader(required=false)` for `X-Correlation-Id`, `X-Caller-Id`, `X-Comparison-Group`.
  2. Validate UUID-typed headers; throw `InvalidParametersException` on bad UUID (Implementation Notes §5/§6); generate `UUID.randomUUID()` when `X-Correlation-Id` absent.
  3. Map to `FootprintParameters` + `CalculationOptions`, invoke facade, transform `FootprintBreakdown` to `FootprintResponseDto` matching the REST contract field names exactly (`correlationId`, `kgCo2`, `factorVersionId`, `factorValidFrom`, `scope`, `factorRate`, `computedAt`, `breakdown.children[].componentId`).
  4. Implement `FootprintExceptionHandler`:
     - `basePackages = "pl.devstyle.aj.footprint"` so it does not interfere with `GlobalExceptionHandler`.
     - Inject `@Value("${app.footprint.problem-base-uri:/problems}")`; normalise to end with `/`.
     - For each of the five sealed subclasses, produce `ProblemDetail.forStatusAndDetail(status, ex.getMessage())`, set `type = URI.create(base + kebabCode(code))`, set `title` per spec table, copy `details` keys via `setProperty(...)`, set `instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()` (Implementation Notes §2).
- **Tests** (7):
  - `FootprintControllerIntegrationTests.getFootprint_validRequest_returnsBreakdownAndCorrelationId` — Scenario A via REST.
  - `FootprintControllerIntegrationTests.getFootprint_dryRunTrue_returnsBreakdownAndNoAuditRow` — Scenario G.
  - `FootprintControllerIntegrationTests.getFootprint_sharedComparisonGroupHeader_persistsThreeLinkedAuditRows` — Scenario H.
  - `FootprintControllerValidationTests.getFootprint_strictModeMissingFactor_returns422ProblemJson` — Scenario D.
  - `FootprintControllerValidationTests.getFootprint_lenientModeMissingLastMileFactor_returns200WithLeafWarning` — Scenario E REST side.
  - `FootprintControllerValidationTests.getFootprint_negativeMaterialWeight_returns400InvalidParameters` — invalid param.
  - `FootprintControllerValidationTests.getFootprint_unknownProductId_returns422MissingAttribute` — unknown product.
- **Acceptance criteria**:
  - All seven tests pass with `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})` + `@WithMockEditUser`.
  - Problem+JSON responses include `type`, `title`, `status`, `detail`, `code`, `instance`, and exception-specific properties per spec mapping table.
  - `GlobalExceptionHandler` is untouched (`git diff` shows no edit to `core/error/`).
- **Depends on**: TG-5, TG-7.
- **Wave**: 5.
- **Standards touched**: `standards/backend/api.md`, `standards/backend/security.md`, `standards/global/error-handling.md`, `standards/global/validation.md`, `standards/testing/backend-testing.md`.

---

### TG-9 CSV export slice

- **Goal**: Flatten an audit row to the 12-column CSV per spec.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/footprint/export/FootprintExportController.java`
  - `src/main/java/pl/devstyle/aj/footprint/export/BreakdownCsvFlattener.java`
  - `src/main/java/pl/devstyle/aj/footprint/export/FootprintExportConfig.java` (optional — if not folded into `FootprintModuleConfig`)
  - `src/test/java/pl/devstyle/aj/footprint/export/FootprintCsvExportIntegrationTests.java`
  - `src/test/java/pl/devstyle/aj/footprint/export/FootprintCsvExportValidationTests.java`
- **Implementation steps**:
  1. `FootprintExportController.exportCsv` mapped to `GET /api/footprints/calculations/{correlationId}/export`:
     - `@RequestParam(defaultValue="csv") String format`; throw `InvalidParametersException` if not `csv` (Assumption §7 → 400 Problem+JSON via the handler from TG-8).
     - Load audit row via `FootprintAuditRepository.findByCorrelationId`; 404 if absent (`ResponseStatusException` mapped to a clean response — spec line 548 simply requires 404, no Problem+JSON shape mandated for the not-found case).
     - Set `Content-Type: text/csv; charset=utf-8` and `Content-Disposition: attachment; filename="footprint-{correlationId}.csv"`.
  2. `BreakdownCsvFlattener.leaves(auditRow)`: deserialize `breakdown` JSON map, DFS, emit one row per active leaf with `component_path` (dot-delimited, root excluded), `kg_co2` via `BigDecimal.toPlainString()` with 4 decimals, ISO-8601 `factor_valid_from`, JSON literal for `warnings`.
  3. Use Apache Commons CSV `CSVFormat.DEFAULT.builder().setHeader(...)` for RFC 4180 quoting.
- **Tests** (5):
  - `FootprintCsvExportIntegrationTests.exportCsv_existingRefrigeratedAuditRow_returnsHeaderPlusNineLeafRows` — Scenario F refrigerated.
  - `FootprintCsvExportIntegrationTests.exportCsv_existingNonRefrigeratedAuditRow_returnsHeaderPlusFourLeafRows` — Scenario F non-refrigerated.
  - `FootprintCsvExportIntegrationTests.exportCsv_sumOfKgCo2_matchesAuditTotalWithinTolerance` — CSV invariant (Σ leaves == total at HALF_UP @ 4).
  - `FootprintCsvExportValidationTests.exportCsv_unknownCorrelationId_returns404`.
  - `FootprintCsvExportValidationTests.exportCsv_formatNotCsv_returns400InvalidParameters` — Problem+JSON via shared handler.
- **Acceptance criteria**:
  - All five tests pass.
  - CSV first line is exactly the 12-column header from spec.
  - Refrigerated row count = 9; non-refrigerated row count = 4 (plus header).
  - Uses `createAndSaveAuditRow(...)` helper with `saveAndFlush()` per testing standard.
- **Depends on**: TG-7, TG-8 (shared Problem+JSON handler).
- **Wave**: 5.
- **Standards touched**: `standards/backend/api.md`, `standards/global/error-handling.md`, `standards/testing/backend-testing.md`.

---

### TG-10 Security wiring

- **Goal**: One-line addition authorizing the CSV export endpoint.
- **Files to create / modify**:
  - `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java` (one new `requestMatchers` line)
- **Implementation steps**:
  1. Add inside the READ block of `.authorizeHttpRequests`, adjacent to the existing `/api/products/**` rule:
     ```java
     .requestMatchers(HttpMethod.GET, "/api/footprints/calculations/*/export").hasAnyAuthority("PERMISSION_READ", "PERMISSION_mcp:read")
     ```
  2. Confirm the engine endpoint `/api/products/{id}/footprint` remains covered by the existing `/api/products/**` matcher — no additional line.
  3. Re-run `FootprintCsvExportIntegrationTests` from TG-9 to prove the new matcher is wired correctly.
- **Tests**: none new; covered indirectly by TG-9's integration tests running under `@WithMockEditUser`. An unauthenticated negative test is out of scope per spec.
- **Acceptance criteria**:
  - `git diff` against `SecurityConfiguration.java` shows exactly one line added.
  - All TG-8 + TG-9 integration tests still pass.
- **Depends on**: TG-9.
- **Wave**: 6.
- **Standards touched**: `standards/backend/security.md`.

---

## Wave Schedule

| Wave | Parallelizable Task Groups |
|---|---|
| 1 | TG-1 |
| 2 | TG-2, TG-3, TG-6 |
| 3 | TG-4 |
| 4 | TG-5, TG-7 |
| 5 | TG-8, TG-9 |
| 6 | TG-10 |

`--sequential` flag collapses this into TG-1 → TG-10 linearly.

---

## Visual Coverage

Backend-only task. The three HTML mockups (`screen:product-footprint-detail`, `screen:product-footprint-comparison`, `screen:product-footprint-historical-timeline`) are inputs for a future frontend task — this implementation ships zero rendered UI. Coverage is recorded at `implementation/visual-coverage.md` as required because `analysis/design-context/INDEX.md` exists: the matrix documents which REST response JSON field names back each mockup data attribute (`correlationId`, `kgCo2`, `factorVersionId`, `factorValidFrom`, `scope`, `factorRate`, `computedAt`, `breakdown.children[].componentId`). All TG-8 contract tests assert these field names.

---

## Risk & Mitigation

1. **`@TransactionalEventListener(AFTER_COMMIT)` flakiness in tests** — the listener fires *after* the test transaction commits, but `@Transactional` rollback in tests can suppress event delivery.
   - *Mitigation*: tests that exercise audit persistence (TG-7) use `TestTransaction.flagForCommit()` + explicit `TestTransaction.end()`, or run without `@Transactional` and clean up via `repository.deleteAll()` in `@AfterEach`. Listener already uses `fallbackExecution=true` to cover non-transactional callers.
2. **jOOQ codegen ordering vs. fresh checkout** — TG-7 imports `FootprintAuditEntity` (JPA) only, so it does not require jOOQ output. But CI must run `mvn compile` (which triggers the codegen plugin) before `mvn test`.
   - *Mitigation*: stick to JPA for V1 (spec line 696 explicitly defers jOOQ for the audit table). No engine code imports the generated class.
3. **TestContainers PostgreSQL 18 startup latency inflates `mvn verify` time** — multiple `@SpringBootTest` classes each spin a fresh context.
   - *Mitigation*: rely on the existing `@Import(TestcontainersConfiguration.class)` Spring Test context-cache key — identical imports across test classes reuse a single container. Avoid `@DirtiesContext` unless absolutely required.

---

## Dependencies on Out-of-Scope Work

V1 acceptance is fully unblocked by the in-memory stub adapters (`InMemoryEmissionFactorPort`, `InMemoryProductAttributesPort`). The following are explicit follow-up tasks (spec §Out of Scope):

- Real Emission Factor Management module (persistent factor storage).
- Real `ProductAttributesPort` adapter wired to `ProductEntity`.
- Frontend rendering of the 3 HTML mockups.
- Comparison/history dashboards (would lean on the deferred jOOQ MULTISET queries).
- Outbox pattern for audit if `footprint.audit.failed` > 0.01%.

None of these block the 9 acceptance scenarios; each can replace its stub via Spring `@Primary` or bean-name override without touching the engine.
