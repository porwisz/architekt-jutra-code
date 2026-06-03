# Specification Audit — Footprint Calculation Engine

**Verdict**: pass-with-concerns
**Critical**: 2 · **Warning**: 6 · **Info**: 5
Date: 2026-05-20

The spec is thorough: absorbs all 9 acceptance scenarios, all 13 tree nodes, both modes, both
facade methods, all 5 sealed exception subtypes, both endpoints, and 4 of 5 Phase-1+2 decisions
faithfully. Package map, Liquibase index 011, jOOQ codegen wiring, TestContainers import,
`WithMockEditUser` reuse, and Permission claim assumption all verified against actual files and
match. **However** two correctness defects in the audit-entity contract will break
compile/runtime and must be resolved before planning. A redundant security matcher silently
shadows existing rules. A handful of edge-case behaviors are under-specified.

---

## Critical (2)

### C1. `BaseEntity` timestamp type mismatch with audit migration
`BaseEntity.createdAt`/`updatedAt` are `LocalDateTime`; Hibernate maps these to
`timestamp without time zone`. Spec migration declares the same columns as
`timestamp with time zone` (and `requested_at` likewise). DDL validation will fail, or silent
TZ stripping on writes vs `now()` defaults. Fix: align migration to
`timestamp without time zone` for `BaseEntity` columns (and decide `requested_at`/`computed_at`
explicitly — recommend matching with `LocalDateTime` to avoid mixing).

### C2. Missing `@SequenceGenerator(name="base_seq", sequenceName="footprint_audit_log_id_seq", allocationSize=1)` on entity
`BaseEntity` references generator `"base_seq"` but the generator is defined per-entity (see
`Product.java:24`). Spec creates the sequence in Liquibase but never declares the matching
`@SequenceGenerator` on `FootprintAuditEntity`. App will fail at startup with `Unknown Id.generator: base_seq`.
Fix: spec must explicitly state the annotation on the entity.

---

## Warning (6)

- **W1**: Redundant `/api/products/*/footprint` matcher shadows `/api/products/**` (line 110, hasAnyAuthority READ + mcp:read). Either dead code or silently drops MCP read. **Drop the matcher, or add `PERMISSION_mcp:read`. The `/api/footprints/calculations/*/export` matcher is genuinely needed.**
- **W2**: 422 for invalid `format` query enum deviates from feature-spec (400) and REST convention. `INVALID_PARAMETERS` already maps to 400 in the engine taxonomy. Recommend revert to 400.
- **W3**: `numeric(12,4)` cap (~99M kg) is safe; consider adding `CHECK (total_kg_co2 >= 0)`.
- **W4**: Six unspecified items a planner must invent — `QuantityExtractor` cross-component arithmetic for warehouse-refrigeration leaf; `ProblemDetail.instance` URI (request URI vs null); `warnings` JSON serialization mode (compact); fire-and-forget caller semantics when `@Retryable` exhausts; `X-Comparison-Group` non-UUID handling; `X-Correlation-Id` non-UUID handling. Add "Implementation Notes" subsection.
- **W5**: Error-code stability should be pinned via `public static final String CODE` constants on each sealed exception subclass.
- **W6**: `idx_footprint_audit_comparison_group` is speculative — no V1 production query uses it (only Scenario H integration assertion). Drop, or document V1 justification.

---

## Info (5)

- **I1**: 9 tests map 1:1 to acceptance scenarios A–I. **Caveat**: Scenario I's "single audit row shared between `calculateUnit` and its internal `calculateTotal`" assertion is folded into `BreakdownScalerTests` (pure unit) — recommend adding one engine-integration method to cover the audit-row identity.
- **I2**: Standards compliance broadly correct (models, api, security, migrations, testing, jooq deferral, minimal-implementation) — subject to C1/C2/W6.
- **I3**: Reusability: no significant rebuilding; net-new infra (commons-csv, spring-retry, transactional event listener) is approved scope.
- **I4**: Scope creep minimal; `app.footprint.problem-base-uri` and `CalculatorId` wrapper acceptable.
- **I5**: Verdict on 3 flagged adaptations: (a) **not acceptable** as written (see W1), (b) `(12,4)` **acceptable**, (c) 422 for format param **borderline** — recommend revert to 400.

---

## Recommended actions before planning

1. Fix **C1** (timestamp types in migration align with `BaseEntity`'s `LocalDateTime`).
2. Fix **C2** (declare `@SequenceGenerator(name="base_seq", sequenceName="footprint_audit_log_id_seq", allocationSize=1)` on `FootprintAuditEntity`).
3. Resolve **W1**: drop redundant matcher OR add `PERMISSION_mcp:read`.
4. Decide **W2**: revert to 400 recommended.
5. Add **W4** Implementation Notes subsection.
6. Decide **W5** (error-code constants) and **W6** (drop or justify index).
