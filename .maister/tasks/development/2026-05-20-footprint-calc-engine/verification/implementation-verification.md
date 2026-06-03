# Implementation Verification — Footprint Calculation Engine

**Date**: 2026-05-20
**Overall Status**: ⚠️ **Passed with Issues** (V1 acceptance-ready; not yet customer-facing-production-ready)

## Executive Summary

The 10 task groups landed cleanly; the full project test suite is green (160/160 active, 2 `@Disabled`, 0 regressions) and all standards-compliance checks pass. Two convergent **critical** concerns surfaced across reviews: (1) the in-memory SPI stubs are unconditional `@Component`s and will ship to production unless gated; (2) Scenario I (`calculateUnit` audit-row identity) has no direct integration assertion because the test classes are `@Transactional` and the listener fires AFTER_COMMIT. Both are small mechanical fixes; the engine itself is mechanically correct and the spec-audit findings are resolved.

| Dimension | Status | Notes |
|---|---|---|
| Implementation plan completion | 10/10 (100%) | All TGs complete; checkboxes via prose, work-log "ALL CHECKBOXES GREEN" |
| Standards compliance | 11/11 applicable | Verified in `verification/implementation-completeness.md` |
| Test suite (skipped — verified in implementation) | 160/160 active, 2 @Disabled | 0 regressions in pre-existing modules |
| Documentation completeness | adequate | spec.md, work-log.md, requirements.md, all phase summaries present |
| Code review | passed_with_issues | 0 critical, 7 warning, 9 info |
| Pragmatic review | acceptable_with_caveats | 0 critical, 4 medium, 5 low |
| Production readiness | **NO-GO** | 2 critical blockers, 6 warnings |
| Reality assessment | **GO with caveats** | 8.5/9 scenarios fully tested |

## Findings (consolidated by severity)

### 🔴 Critical (2) — must address before production

| # | Source | Description | Location | Fixable |
|---|---|---|---|---|
| C1 | production-readiness + reality | **Stub SPI adapters ship to production unconditionally.** `InMemoryEmissionFactorPort` and `InMemoryProductAttributesPort` are `@Component` with no `@Profile`/`@ConditionalOnProperty` guard. Real SKUs will throw `MissingProductAttributeException`; successful calls return audit rows with fictional factor UUIDs. | `src/main/java/pl/devstyle/aj/footprint/spi/stub/*` | yes (15 min: add `@ConditionalOnProperty(name="app.footprint.adapters", havingValue="in-memory")`) |
| C2 | production-readiness | **Audit-failure metric registers on an in-memory `SimpleMeterRegistry` that no exporter reads.** Failed audits are invisible. | `FootprintModuleConfig.footprintMeterRegistry()` + missing `spring-boot-starter-actuator` | yes (infra: add actuator + Prometheus registry, or downgrade to org-wide concern) |

### 🟡 Warning (notable; consolidated and de-duplicated)

| # | Source(s) | Description | Location | Fixable |
|---|---|---|---|---|
| W1 | completeness, reality | **Scenario I (`calculateUnit` audit-row identity) has no direct integration assertion** because both candidate test classes are `@Transactional`. Composite-sum invariant is covered by `BreakdownScalerTests`; audit-row identity is not. | `FootprintEngineIntegrationTests.java:88`, `FootprintAuditListenerIntegrationTests.java:81` | yes (30 min: split into a new non-`@Transactional` test class with `@AfterEach auditRepository.deleteAll()`) |
| W2 | code-review | **ProblemDetail.instance exposes absolute internal request URI** (host:port via `ServletUriComponentsBuilder.fromCurrentRequestUri()`), risking topology disclosure on 4xx/5xx. RFC 7807 allows path-only. | `FootprintExceptionHandler.java:72` | yes |
| W3 | code-review | **Audit listener retry loop will deterministically exhaust on `DataIntegrityViolationException`** (unique constraint on `correlation_id`). | `FootprintAuditListener.java:23-31` | yes (catch DIVE and short-circuit as success) |
| W4 | code-review | **Dead ternary**: `params.storageDays() != 0 ? params.storageDays() : 0`. No fallback path because `ProductAttributes` has no `storageDays` either. | `DefaultFootprintFacade.java:122` | yes (simplify to `params.storageDays()` or add to ProductAttributes) |
| W5 | code-review | **`@SequenceGenerator(name="base_seq")` re-declared** on the subclass with a different `sequenceName`. Works (subclass wins) but is fragile if `BaseEntity` changes. | `FootprintAuditEntity.java:27` | yes (rename to `footprint_audit_seq` or document) |
| W6 | code-review | **`BigDecimal.ZERO.setScale(4)` bypasses centralized `RoundingPolicy.round(...)`.** Inconsistent with surrounding policy. | `BreakdownTreeBuilder.java:141` | yes (one-line) |
| W7 | pragmatic | **`ParameterValue` implemented as `Map<String,Object>` with typed accessors instead of the spec's 6-field record.** Adds string-key fragility, type coercion, HashMap allocation per call, and silent `orElse(ZERO)` fallbacks that can mask missing attributes. | `archetype/pricing/ParameterValue.java` + `BreakdownTreeBuilder.toParameterValue` + `ComponentTreeRegistry` lambdas | yes (highest-leverage simplification: −40 LOC) |
| W8 | production-readiness | **No latency timers** on calculation or audit-persist path; only the failure counter exists. | `DefaultFootprintFacade`, `FootprintAuditListener` | yes |
| W9 | production-readiness | **Audit listener runs synchronously on the commit thread** (despite "async" spec wording). Slow audit insert blocks HTTP response. | `FootprintAuditListener` | yes (add `@Async` with bounded executor) |
| W10 | production-readiness | **Retry knobs hardcoded in annotation** (`maxAttempts=3, delay=200, multiplier=2`). | `FootprintAuditListener` | yes (`@Retryable(maxAttemptsExpression="${...}")`) |
| W11 | production-readiness | **`app.footprint.problem-base-uri` default duplicated** in `application.properties` and `@Value` fallback. Drift risk. | `application.properties:14` + `FootprintExceptionHandler` | yes |
| W12 | production-readiness | **CSV export materializes full breakdown JSON in memory** with no row-count guard. Acceptable for 9 rows; OOM vector if tree depth grows. | `BreakdownCsvFlattener` | yes |
| W13 | pragmatic | **`EmissionCalculator` is a one-line wrapper** around `SimpleFixedCalculator` it instantiates with `new`. No DI benefit. | `EmissionCalculator.java` | yes (delete; inject `SimpleFixedCalculator` directly) |
| W14 | pragmatic | **`AuditMetrics` exists as a `@Component` only to wrap one Counter.** Combined with the conditional `MeterRegistry` bean, two extra classes for one metric. | `AuditMetrics.java` + `FootprintModuleConfig` | yes (inline into listener) |

### 🟢 Info (selected; awareness items, see individual reports for full list)

- `FootprintCalculatedEvent` duplicates fields already inside `FootprintBreakdown` (totalKgCo2/warnings/factorVersions). De-dup opportunity in V2.
- `FootprintQueryRequest` has no Bean Validation annotations; only `materialWeightKg < 0` is checked ad-hoc.
- `BreakdownCsvFlattener.formatWarnings` catches `Exception` silently — mask programming errors.
- No deny-by-default for non-GET methods on `/api/footprints/**` (TG-10 added one GET matcher only).
- `EntityNotFoundException` reused from `pl.devstyle.aj.core.error` for CSV 404 (works, but mixes error conventions in one module).
- `requiresRefrigeration` uses OR-merge: caller cannot override a product flagged refrigerated.
- 2 `@Disabled` tests still in main branch; should either be re-enabled (W1) or deleted.

### Standards Evolution Candidates (3)

1. **`@RestControllerAdvice(basePackages=...)` + `@Order(HIGHEST_PRECEDENCE)`** for per-module Problem+JSON scoping when a project-wide catch-all exists. (`FootprintExceptionHandler` precedent.)
2. **Async/event-listener test guidance**: integration tests for `@TransactionalEventListener(AFTER_COMMIT)` must NOT inherit class-level `@Transactional` rollback — use a separate non-transactional class or drive via `TransactionTemplate`. (Add to `standards/testing/backend-testing.md`.)
3. **Sealed exception hierarchy with `public static final String CODE`** for API-facing taxonomies driving Problem+JSON. (Add to `standards/backend/api.md` or `standards/global/error-handling.md`.)

## Visual Fidelity

N/A — backend task. Mockups are inputs for a separate FE task. REST response field names verified against mockup data attributes in `implementation/visual-coverage.md`.

## Sub-reports

- `verification/implementation-completeness.md` — plan, standards, documentation
- `verification/code-review-report.md` — quality, security, performance (returned inline by subagent)
- `verification/pragmatic-review.md` — over-engineering / unnecessary complexity (returned inline by subagent)
- `verification/production-readiness-report.md` — deployment go/no-go (returned inline by subagent)
- `verification/reality-check.md` — scenario coverage, persona validation (returned inline by subagent)
- `verification/spec-audit.md` — Phase 6 audit (all findings resolved before Phase 7)

## Verdict

**Passed with issues.** The engine is V1-acceptance-ready: 8.5/9 scenarios fully tested end-to-end, the half-scenario (calculateUnit audit-row identity) is correct by construction and partially covered by composite-sum invariants. **Two critical items must be fixed before any production deployment**: gate the stub adapters (C1), and either expose the audit-failure metric or accept the silent failure mode and document it (C2). Scenario I (W1) should be promoted to a direct integration assertion (≈30 min).

## Recommended fix prioritization

1. **C1 stub gating** (15 min, mechanical)
2. **W1 Scenario I direct test** (30 min, mechanical)
3. **W2 Problem URI path-only** + **W3 DIVE catch** + **W4 storageDays dead ternary** + **W6 RoundingPolicy consistency** (small batch, ~30 min total)
4. **W7 ParameterValue → typed record** (~1 h, highest leverage simplification)
5. **C2 + W8/W9/W10/W11/W12** — production hardening (separate task / sprint)
