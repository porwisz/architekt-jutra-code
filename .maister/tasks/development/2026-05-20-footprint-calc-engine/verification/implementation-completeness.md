# Implementation Completeness — Footprint Calculation Engine

**Date**: 2026-05-20
**Verifier**: implementation-completeness-checker
**Status**: `passed_with_issues`

---

## Summary

| Dimension | Status | Score |
|---|---|---|
| Plan completion | complete | 10/10 task groups (100%) |
| Standards compliance | compliant | All applicable standards met |
| Documentation | adequate | Work-log complete; minor stale @Disabled message |

| Severity | Count |
|---|---|
| Critical | 0 |
| Warning | 3 |
| Info | 2 |

---

## 1. Plan Completion

**Status**: complete — 10/10 task groups (100%)

The `implementation-plan.md` uses prose rather than per-step `[x]` checkboxes; completion is tracked through:
- `work-log.md` final entry ("ALL CHECKBOXES GREEN", line 121) explicitly marks all 10 task groups as SUCCESS.
- Maister task list (#10–#19) shows TG-1 through TG-10 as `completed`.
- Spot-checked code evidence confirms each group landed real artifacts:

| TG | Evidence (file existence on disk) |
|---|---|
| TG-1 | `pom.xml` deps; `src/main/java/pl/devstyle/aj/footprint/config/FootprintModuleConfig.java`; `application.properties` `app.footprint.problem-base-uri` |
| TG-2 | 12 source files under `src/main/java/pl/devstyle/aj/archetype/pricing/` (Calculator, SimpleFixedCalculator, Validity, Component sealed, etc.) |
| TG-3 | `src/main/java/pl/devstyle/aj/footprint/api/` (records + sealed `FootprintCalculationException` with 5 permits) |
| TG-4 | `internal/ports/EmissionFactorPort.java`, `ProductAttributesPort.java`; `spi/stub/InMemory*Port.java` |
| TG-5 | `internal/{ComponentTreeRegistry,EmissionCalculator,BreakdownTreeBuilder,BreakdownScaler,RoundingPolicy,DefaultFootprintFacade}.java`, `api/FootprintFacade.java` |
| TG-6 | `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml` (3.4 KB) |
| TG-7 | `audit/{FootprintCalculatedEvent,FootprintAuditEntity,FootprintAuditRepository,FootprintAuditMapper,FootprintAuditListener,AuditMetrics}.java` |
| TG-8 | `web/{FootprintController,FootprintQueryRequest,FootprintResponseDto,FootprintExceptionHandler}.java` |
| TG-9 | `export/{FootprintExportController,BreakdownCsvFlattener}.java` |
| TG-10 | `core/security/SecurityConfiguration.java` +1 line for `/api/footprints/calculations/*/export` (confirmed in work-log §Wave 6) |

**Test counts confirmed**: 27 feature tests run (25 active + 2 `@Disabled`) per work-log line 83 — matches the ~26 estimate in the plan.

No missing artifacts. No unchecked steps with absent code.

---

## 2. Standards Compliance

**Status**: compliant — all applicable standards from `.maister/docs/INDEX.md` are followed.

### Applicability reasoning

| Standard | Applies? | Reasoning & evidence |
|---|---|---|
| `global/minimal-implementation.md` | Yes | Backend feature; no speculative abstractions found. Stubs are scoped to declared 9 scenarios; no unused public types. |
| `global/coding-style.md` | Yes | Naming consistent (records, sealed interfaces); no style outliers in spot-checked files. |
| `global/conventions.md` | Yes | Package layout matches plan; new property externalized in `application.properties`. |
| `global/error-handling.md` | Yes | Sealed `FootprintCalculationException` hierarchy with typed subclasses + `static final String CODE`; Problem+JSON mapping (TG-8). |
| `global/validation.md` | Yes | `FootprintControllerValidationTests` covers negative weight, unknown product, bad UUID headers — fail-fast at the controller boundary. |
| `global/commenting.md` | Yes | Spot check: code self-documents via record/method names; `@Disabled` rationale present on skipped tests. |
| `backend/models.md` | Yes | `FootprintAuditEntity` extends `BaseEntity`, declares `@SequenceGenerator(name="base_seq", sequenceName="footprint_audit_log_id_seq", allocationSize=1)`, business-key `equals`/`hashCode` on `correlationId` (lines 75–84), JSONB via `@JdbcTypeCode(SqlTypes.JSON)` on 3 columns (lines 59/63/67), Lombok @Getter/@Setter/@NoArgsConstructor (no @Data). All `models.md` invariants satisfied. |
| `backend/queries.md` | Yes | Audit repo limited to `findByCorrelationId` (indexed unique key). No N+1 risk in current scope. |
| `backend/jooq.md` | No (deferred) | Spec line 696 / work-log explicitly defers jOOQ for the audit table to a future task; engine uses JPA only — acceptable. |
| `backend/migrations.md` | Yes | YAML changelog, descriptive name (`011-create-footprint-audit-log.yaml`), single focused change (table + indices + sequence). Reversibility: Liquibase auto-generates rollback for `createTable`/`createIndex`/`addUniqueConstraint`/`createSequence` — explicit `<rollback>` block not required for those change types. |
| `backend/api.md` | Yes | Pure `GET` endpoints; plural nouns (`/api/products/{id}/footprint`, `/api/footprints/calculations/{id}/export`); versioned via `/api/` prefix consistent with project. |
| `backend/security.md` | Yes | Zero `@PreAuthorize` annotations in `pl.devstyle.aj.footprint` package; authorization centralized in `SecurityConfiguration.SecurityFilterChain` (TG-10 single-line matcher). Engine endpoint inherits `/api/products/**` rule. |
| `backend/plugin-auth.md` | No | No plugin SDK / plugin-side auth involved in V1. |
| `frontend/*` | No | Backend-only task; visual coverage matrix maps mockup fields to REST response (`implementation/visual-coverage.md`). |
| `testing/backend-testing.md` | Yes | All 5 integration test classes import `TestcontainersConfiguration`; the 4 MockMvc-bearing classes additionally import `SecurityMockMvcConfiguration` and use `@WithMockEditUser` (verified via grep). Class names end in `*Tests`; method names follow `action_condition_expectedResult` pattern (spot-checked: `calculate_rateTimesQuantity_roundsHalfUpAt4Decimals`, `getFootprint_strictModeMissingFactor_returns422ProblemJson`, etc.). MockMvc `jsonPath()` used. |

### Standards gaps

None at the critical or warning level. Code adheres to every applicable standard.

---

## 3. Documentation Completeness

**Status**: adequate

### work-log.md
- Multiple dated entries with per-wave/per-TG breakdowns (Wave 1 through Wave 6).
- Standards discovery noted per group.
- File modifications recorded.
- Final completion entry present ("Implementation Complete", line 102).
- Cross-wave reconciliation notes documented (lines 117–119).
- Standards-evolution candidates raised (`@Order(HIGHEST_PRECEDENCE)` for package-scoped handlers).

### spec.md alignment with 9 acceptance criteria
The spec covers all 9 scenarios A–I. Each scenario has a corresponding test, summarized in work-log lines 85–94:
- A (summer/winter): 2 tests in `FootprintEngineIntegrationTests` + 1 REST test.
- B (audit persistence): 1 test in listener integration suite.
- C (applicability — non-refrigerated): 1 test in `FootprintEngineIntegrationTests`.
- D (STRICT missing factor → 422): 1 test in `FootprintControllerValidationTests`.
- E (LENIENT missing factor): 1 listener test + 1 REST test.
- F (CSV refrigerated/non-refrigerated): 2 tests in CSV integration suite.
- G (dry-run no audit): 1 test in controller integration suite.
- H (comparison group): 1 test in controller integration suite.
- I (calculateUnit single audit row TOTAL): **partially covered** — see §4 below.

### Spec audit findings (from `verification/spec-audit.md`)
The pre-implementation audit raised 2 Critical + 6 Warning + 5 Info findings. Resolution status:

| Finding | Status |
|---|---|
| C1 timestamp type mismatch | Resolved — work-log notes timestamps without time zone in migration; matches `BaseEntity.LocalDateTime`. |
| C2 missing `@SequenceGenerator` | Resolved — present on `FootprintAuditEntity` line 27. |
| W1 redundant security matcher | Acknowledged — TG-10 added only the necessary `/api/footprints/calculations/*/export` matcher; engine endpoint inherits `/api/products/**`. |
| W2 422 vs 400 for bad `format` | Resolved — work-log line 80 confirms 400 via `InvalidParametersException`. |
| W3 CHECK constraint | Not implemented (not blocking — informational guard only). |
| W4 implementation notes | Resolved — spec line refs in plan §TG-5/TG-8 cite Implementation Notes §2/§5/§6/§7/§8. |
| W5 `CODE` constants | Resolved — per work-log TG-3, all 5 subclasses expose `public static final String CODE`. |
| W6 speculative comparison-group index | Resolved — work-log §TG-6 explicitly states "W6 verified absent (no comparison_group index)". |
| I1 audit-row identity test | Partially addressed — Scenario I tests authored but `@Disabled`; covered indirectly. See §4. |

### Documentation issues
- **WARN-D1**: The `@Disabled` reason on `FootprintAuditListenerIntegrationTests#calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit` (line 81) reads `"waits for TG-5 facade — re-enable after Wave 4"`. This is stale — TG-5 is complete. The current rationale (class-level `@Transactional` swallows `AFTER_COMMIT`) is documented only in `work-log.md` and on the sibling test in `FootprintEngineIntegrationTests` (line 105). Update the message for consistency.
- **INFO-D2**: No user-facing documentation produced; not required for a backend engine task.

---

## 4. @Disabled Tests — Cross-Check

Two `@Disabled` tests:

1. `FootprintEngineIntegrationTests#calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit`
   (`src/test/java/pl/devstyle/aj/footprint/internal/FootprintEngineIntegrationTests.java:105`)
   Reason: *"Class is @Transactional so AFTER_COMMIT listener never fires; move to a separate non-@Transactional test class to re-enable (tracked as a follow-up)."*

2. `FootprintAuditListenerIntegrationTests#calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalNotUnit`
   (`src/test/java/pl/devstyle/aj/footprint/audit/FootprintAuditListenerIntegrationTests.java:81`)
   Reason on annotation: *"waits for TG-5 facade — re-enable after Wave 4"* (stale message).

### Technical validity of the justification
**Valid.** Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` only fires after the surrounding transaction commits. When a test class is annotated `@Transactional` (default rollback), the test transaction rolls back, so the AFTER_COMMIT phase never runs — the listener never executes. The remediation (a non-`@Transactional` test class with explicit `repository.deleteAll()` cleanup) is exactly what plan §Risk #1 documents.

Note: the test in `FootprintEngineIntegrationTests` does call `transactionTemplate.execute(...)` to wrap `facade.calculateUnit(...)`, which would commit the inner programmatic transaction — but the outer class-level `@Transactional` still wraps the test method, so the inner transaction nests (or joins, depending on propagation) and the AFTER_COMMIT still ties to the outer rollback. The disable rationale is therefore correct.

### Coverage of the Scenario I invariant by other tests
The Scenario I invariant has two assertions:
- **(a) Composite-sum invariant under PER_100G scaling** — fully covered by `BreakdownScalerTests.scale_composite_equalsSumOfScaledChildren` and `scale_per100gOnHalfKg_doublesEveryLeaf`.
- **(b) `calculateUnit` writes exactly one audit row, with the TOTAL (not unit) breakdown** — **not directly covered** by any active integration test. The listener happy-path tests (`onFootprintCalculated_committedTransaction_persistsAuditRowWithinFiveSeconds`, `onFootprintCalculated_lenientMissingFactor_persistsAuditRowWithWarnings`) prove the listener works in general, and the engine determinism tests prove `calculateTotal` produces stable breakdowns — but the specific contract "`calculateUnit` does not publish a second event and the persisted row holds the TOTAL view" is asserted only by the two disabled tests.

This is a **warning-level gap**: the invariant is plausibly enforced by code inspection (the plan §TG-5 step 5 explicitly notes `calculateUnit` "does NOT publish a second event"), and the indirect coverage above is suggestive, but production confidence requires re-enabling at least one of the two tests in a non-`@Transactional` class. Tracked as a follow-up per the work-log.

---

## Issues

| ID | Source | Severity | Description | Location | Fixable | Suggestion |
|---|---|---|---|---|---|---|
| WARN-1 | standards/docs | warning | Stale `@Disabled` message references "waits for TG-5 / Wave 4" but TG-5 is complete; the real reason is `@Transactional` + AFTER_COMMIT. | `src/test/java/pl/devstyle/aj/footprint/audit/FootprintAuditListenerIntegrationTests.java:81` | true | Replace annotation reason with the same wording used in `FootprintEngineIntegrationTests.java:105`. |
| WARN-2 | plan_completion | warning | Scenario I assertion (b) — `calculateUnit` writes exactly one audit row holding TOTAL view — has no active integration test. Both candidate tests are `@Disabled`. | `FootprintEngineIntegrationTests:106`, `FootprintAuditListenerIntegrationTests:82` | true | Extract one of the two tests into a new `*Tests` class without class-level `@Transactional`; clean up via `auditRepository.deleteAll()` in `@AfterEach`. |
| WARN-3 | standards | warning | `BaseEntity` audit listener / spec-audit W3 suggested `CHECK (total_kg_co2 >= 0)`; not added. Non-blocking but the engine never produces negative totals — adding it would be defensive. | `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml` | true | Optional: add a `<sql>ALTER TABLE ... ADD CONSTRAINT ...</sql>` change or document the decision. |
| INFO-1 | docs | info | `implementation-plan.md` has no `[x]` per-step checkboxes; completion is tracked in `work-log.md` + maister task list. Acceptable for prose-style plans but reduces machine-checkability. | `implementation/implementation-plan.md` | false | Future convention: include explicit checkboxes per step. |
| INFO-2 | standards/evolution | info | Work-log raises `@Order(HIGHEST_PRECEDENCE)` for package-scoped `@RestControllerAdvice` as a candidate standard. | work-log.md:75, 119 | true | Invoke `/maister:standards-update` to formalize. |

---

## Structured Result

```yaml
status: passed_with_issues

plan_completion:
  status: complete
  total_steps: 10           # task groups
  completed_steps: 10
  completion_percentage: 100
  missing_steps: []
  spot_check_issues: []

standards_compliance:
  status: compliant
  standards_checked: 14
  standards_applicable: 11
  standards_followed: 11
  gaps: []

documentation:
  status: adequate
  issues:
    - artifact: FootprintAuditListenerIntegrationTests.java
      issue: "Stale @Disabled message references obsolete 'waits for TG-5' reason"
      severity: warning

issues:
  - source: documentation
    severity: warning
    description: "Stale @Disabled rationale on listener Scenario I test"
    location: "src/test/java/pl/devstyle/aj/footprint/audit/FootprintAuditListenerIntegrationTests.java:81"
    fixable: true
    suggestion: "Replace with the @Transactional/AFTER_COMMIT wording used in the engine test"
  - source: plan_completion
    severity: warning
    description: "Scenario I 'single audit row with TOTAL view' invariant has no active integration test"
    location: "Both Scenario I tests are @Disabled"
    fixable: true
    suggestion: "Extract one test into a non-@Transactional class with explicit cleanup"
  - source: standards
    severity: warning
    description: "Spec-audit W3 (CHECK constraint on total_kg_co2) not added; non-blocking"
    location: "db/changelog/2026/011-create-footprint-audit-log.yaml"
    fixable: true
    suggestion: "Add CHECK or document the decision"
  - source: documentation
    severity: info
    description: "implementation-plan.md lacks per-step [x] checkboxes"
    location: "implementation/implementation-plan.md"
    fixable: false
    suggestion: "Future convention only"
  - source: standards
    severity: info
    description: "@Order(HIGHEST_PRECEDENCE) for package-scoped advice raised as standards candidate"
    location: "work-log.md"
    fixable: true
    suggestion: "Invoke /maister:standards-update"

issue_counts:
  critical: 0
  warning: 3
  info: 2
```
