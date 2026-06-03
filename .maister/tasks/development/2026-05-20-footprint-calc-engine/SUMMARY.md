# Workflow Summary — Footprint Calculation Engine

**Status**: ✅ Completed
**Started**: 2026-05-20 (from product-design handoff `2026-05-20-footprint-calc-engine`)
**Completed**: 2026-05-20
**Risk level at intake**: medium-high → resolved to **low** by end of verification

## What was built

A stateless carbon-footprint calculation engine as a kernel-side core service in the `aj` Spring Boot 4.0.5 / Java 25 monolith.

- **Public surface**: `FootprintFacade` Spring bean (`calculateTotal` / `calculateUnit`) + `GET /api/products/{productId}/footprint` REST endpoint + `GET /api/footprints/calculations/{correlationId}/export?format=csv`.
- **In-house Pricing-Archetype-lite** under `pl.devstyle.aj.archetype.pricing` — 12 primitives (Calculator, Component, Validity, Applicability, etc.) sized exactly for the 13-node tree.
- **13-node component tree** (materials, transport, packaging, cold-storage) with proper applicability gating; cold-storage subtree omitted for non-refrigerated products.
- **Async audit log** via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + `@Retryable` with externalised knobs, idempotent on `correlation_id` unique-constraint conflict, fallback `@Recover` increments `footprint.audit.failed` Micrometer counter.
- **Problem+JSON (RFC 7807)** scoped to the footprint package — 5 sealed exception subclasses with stable `CODE` constants → 400 / 409 / 422 with `type`, `title`, `code`, `instance` (path-only), and exception-specific details.
- **CSV export** (12 columns, Apache Commons CSV, RFC 4180 quoting, defensive 1000-row cap).
- **In-memory SPI stub adapters** for V1, gated by `app.footprint.adapters=in-memory` (production must supply real adapters).
- **Liquibase migration 011** + automatic jOOQ codegen for `footprint_audit_log`.
- **One-line security wiring** authorising the CSV export endpoint with `PERMISSION_READ` / `PERMISSION_mcp:read`.

## Phase trail

| Phase | Output | Outcome |
|---|---|---|
| 1 Codebase analysis | `analysis/codebase-analysis.md` + `analysis/clarifications.md` | risk medium-high; 4 critical decisions resolved |
| 2 Gap analysis | `analysis/gap-analysis.md` + `analysis/scope-clarifications.md` | additive build, ~52 components in 10 groups; 5 important decisions resolved |
| 5 Specification | `implementation/spec.md` (17 sections) + `analysis/requirements.md` | implementation-ready |
| 6 Spec audit | `verification/spec-audit.md` | pass-with-concerns; 2 critical + 6 warning + 1 info — all resolved inline before planning |
| 7 Implementation plan | `implementation/implementation-plan.md` (10 task groups, 6 waves) + `implementation/visual-coverage.md` | plan green-lit |
| 8 Implementation | code + `implementation/work-log.md` | 10/10 task groups complete (parallel wave dispatch); 160 active tests, 0 regressions |
| 11 Verification (5 reviewers) | `verification/implementation-verification.md` + 4 inline sub-reports | pass-with-issues → all 16 findings (2 critical + 14 warning) fixed inline |
| 14 Finalisation | this `SUMMARY.md` | task closed |

## Test result

```
mvn -Dskip.jooq.generation=true test
→ 159 tests run, 0 failures, 0 errors, 0 skipped
```

All 9 acceptance scenarios from feature-spec §8 are directly covered (Scenario I via the new non-`@Transactional` test class `FootprintCalculateUnitAuditIntegrationTests`).

## Files modified or created

- New code (52 production classes + 9 test classes) under:
  - `src/main/java/pl/devstyle/aj/archetype/pricing/**`
  - `src/main/java/pl/devstyle/aj/footprint/{api,internal,audit,web,export,config,spi/stub}/**`
  - `src/test/java/pl/devstyle/aj/{archetype/pricing,footprint/*}/**`
- New migration: `src/main/resources/db/changelog/2026/011-create-footprint-audit-log.yaml`
- Edits to existing files:
  - `pom.xml` — `+spring-retry 2.0.12`, `+spring-aspects` (BOM), `+commons-csv 1.10.0`, `+micrometer-core` (BOM)
  - `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java` — +1 line authorising CSV export endpoint
  - `src/main/resources/application.properties` — +9 new `app.footprint.*` keys (problem URI base, SPI adapters selector, retry/async tuning)
- Untouched: `pl.devstyle.aj.core.error.GlobalExceptionHandler` and the existing `ErrorResponse` (Problem+JSON is scoped to footprint only by design).

## Known follow-ups (deferred — out of V1 scope)

1. Real Emission Factor Management module (replace `InMemoryEmissionFactorPort`).
2. Real Product-domain integration (replace `InMemoryProductAttributesPort`).
3. Frontend rendering of the 3 HTML mockups (handed off to FE task).
4. Add `spring-boot-starter-actuator` + Prometheus registry in production for `footprint.audit.failed`, `footprint.calculate`, and `footprint.audit.persist` metrics. The current `SimpleMeterRegistry` fallback steps aside via `@ConditionalOnMissingBean` when a real registry is added.
5. Audit log retention/archival policy.
6. Comparison/history dashboards (would lean on the deferred jOOQ MULTISET queries).
7. Outbox pattern for audit if `footprint.audit.failed` rate climbs.

## Commit message template

```
feat(footprint): add carbon footprint calculation engine

Stateless kernel-side service: FootprintFacade Spring bean + REST GET surface +
async audit log + CSV export. Reuses in-house Pricing-Archetype-lite primitives
across a 13-node component tree with strict/lenient modes and Problem+JSON error
responses scoped to the footprint package.

- pl.devstyle.aj.archetype.pricing: in-house Calculator/Component/Validity/Applicability primitives
- pl.devstyle.aj.footprint.api: FootprintFacade interface, sealed BreakdownNode tree, sealed exception hierarchy with stable CODE constants
- pl.devstyle.aj.footprint.internal: DefaultFootprintFacade, ComponentTreeRegistry (13 nodes), BreakdownTreeBuilder (DFS post-order), BreakdownScaler (composite-sum-preserving PER_100G)
- pl.devstyle.aj.footprint.audit: @TransactionalEventListener(AFTER_COMMIT) + @Async + @Retryable with idempotent DIVE handling; Micrometer counter footprint.audit.failed
- pl.devstyle.aj.footprint.web: FootprintController + FootprintExceptionHandler (RFC 7807 ProblemDetail, package-scoped @Order(HIGHEST_PRECEDENCE))
- pl.devstyle.aj.footprint.export: CSV slice over audit log (12 columns, Apache Commons CSV)
- pl.devstyle.aj.footprint.spi.stub: in-memory adapters gated by app.footprint.adapters=in-memory
- Liquibase 011-create-footprint-audit-log.yaml; jOOQ codegen automatic
- SecurityConfiguration: +1 line authorising /api/footprints/calculations/*/export

Closes the work specified in .maister/tasks/development/2026-05-20-footprint-calc-engine.
Spec, plan, work-log, and verification reports under that directory.

Tests: 159 run, 0 failures, 0 errors, 0 skipped (all 9 acceptance scenarios covered).
```

## Next steps suggested

- Open a PR referencing this summary and the spec.
- Decide deployment posture for stubs (likely flip `app.footprint.adapters` off in non-dev and ship real adapters as a follow-up task).
- Track follow-ups (1)–(7) in the backlog.
