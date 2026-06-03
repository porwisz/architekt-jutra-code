# Design Alternatives — Footprint Calculation Engine

Scope: explore HOW to design the undecided slices of the engine and its surfaces. Locked-in constraints (Pricing Archetype reuse, core-service placement, dual REST+bean surface, both `calculateTotal()` and `calculateUnit()` in V1, no caching, configurable strictness, engine-owned audit write, application-layer destination resolution, three UI views) are taken as given.

Evaluation perspectives applied to every alternative: technical feasibility, user impact (Marta/Tomek/Anna/Piotr), simplicity, risk, scalability.

---

## Decision Area 1 — `FootprintParameters` shape

**Question**: How is the input context object structured and constructed? How are strictness and audit metadata threaded through?

### Alt 1.A — Flat Java `record`, strictness as separate facade arg

```java
public record FootprintParameters(
    Instant timestamp,
    ProductId productId,
    BigDecimal materialWeightKg,
    BigDecimal supplierDistanceKm,
    BigDecimal destinationDistanceKm,
    BigDecimal lastMileDistanceKm,
    int storageDays,
    boolean requiresRefrigeration
) {}

facade.calculateTotal(parameters, CalculationOptions.strict());
```

- **Pros**: immutable by construction, exhaustive constructor enforces all fields at compile time, trivially serializable, no builder boilerplate, plays well with Jackson, matches Pricing Archetype's `Parameters` style.
- **Cons**: 8-arg constructor is painful at call sites; adding a field is a breaking change to every caller; conflates pure inputs with `requiresRefrigeration` which is really a product attribute.
- **Audit context**: passed in `CalculationOptions` (correlationId, callerId, dryRun), keeps `FootprintParameters` purely about the math.

### Alt 1.B — Grouped record with nested value objects

```java
public record FootprintParameters(
    Instant timestamp,
    ProductId productId,
    ProductAttributes product,         // materialWeightKg, requiresRefrigeration
    TransportContext transport,        // supplierKm, destinationKm, lastMileKm
    StorageContext storage             // storageDays
) {}
```

- **Pros**: domain-meaningful groupings; testing fixtures stay readable; adding a new `transport` field doesn't change top-level signature; matches the component-tree partitioning (materials / transport / cold-storage).
- **Cons**: more types to maintain; risk of nested groups drifting from how the tree actually consumes them; deserialization needs nested JSON.
- **Audit context**: same as 1.A — separate `CalculationOptions`.

### Alt 1.C — Builder + interface

```java
FootprintParameters p = FootprintParameters.builder()
    .timestamp(t).productId(id).materialWeightKg(...)
    .strictness(Strictness.LENIENT)
    .correlationId(corr)
    .build();
```

- **Pros**: ergonomic at the call site; trivially extensible; one parameter object carries everything (input + options + audit metadata).
- **Cons**: mutable builder state; conflates math input with cross-cutting options (strictness, correlationId) — leaks audit concerns into the value object; equality is harder (do two `FootprintParameters` differ if their correlationId differs? for caching: yes; for math: no).

### Recommendation — **Alt 1.B (grouped record) + separate `CalculationOptions`**

Use a grouped record for math inputs, and a sibling `CalculationOptions` record for `strictness`, `correlationId`, `callerId`, and `dryRun`. Two reasons: (1) determinism success criterion — equal math inputs must produce equal breakdown, and the cleanest way to express that invariant is "the math input type does not contain options"; (2) Marta's comparison view will construct ~5 parameter sets that differ only in `transport.destinationKm` — grouped records make that `.with(...)` ergonomic. Provide a `FootprintParameters.builder()` as a convenience but the canonical type is the record.

**Cross-area dependencies**: feeds Area 2 (`calculateUnit`) since the unit normaliser needs a clean way to copy + tweak parameters; feeds Area 5 (REST shape) since the JSON envelope mirrors the grouped structure.

---

## Decision Area 2 — `calculateUnit()` implementation

**Question**: How is "kg CO₂ per 100g" produced relative to `calculateTotal()`? V1 must include both as first-class.

### Alt 2.A — Adapter on `calculateTotal()`: post-divide the whole tree

```java
FootprintBreakdown total = calculateTotal(params);
return total.scaled(BigDecimal.valueOf(100).divide(params.product().materialWeightKg(), MathContext.DECIMAL64));
```

- **Pros**: one calculation path, trivially consistent with `calculateTotal()`; `FootprintBreakdown.scaled(factor)` is a pure tree-map; per-node kgCO₂ is also normalised, which is what Tomek's bar chart wants.
- **Cons**: rounding happens on every node — `sum(children.scaled) != root.scaled` unless we recompute composites after scaling; needs explicit rounding policy at the tree level.

### Alt 2.B — Parameterised via `FootprintParameters.normalisation`

Add a `Normalisation` enum to options: `AS_IS | PER_100G | PER_KG | PER_UNIT`. `calculateUnit()` is sugar that sets `PER_100G`.

- **Pros**: one entry point; future normalisations (per kg, per serving) cost nothing; the audit log records exactly which normalisation was applied.
- **Cons**: normalisation is a presentation concern leaking into math params; if the API ever exposes raw `calculate(params)` with normalisation, callers will be confused about whether the audit log records pre- or post-normalisation values.

### Alt 2.C — Separate calculator path with its own Pricing Archetype tree

Construct a second component tree whose calculators divide by `materialWeightKg` at the leaf level.

- **Pros**: each leaf is self-consistent; no post-hoc scaling.
- **Cons**: doubles the tree definition; two sources of truth that must stay in lockstep; reviewer must verify the unit tree is the total tree divided by mass. High maintenance risk.

### Recommendation — **Alt 2.A (adapter, post-divide)**

`calculateUnit(params) = calculateTotal(params).scaled(100 / materialWeightKg)`. Audit log records the `total` breakdown plus the normalisation factor; the persisted row is always the canonical total (Anna can reconcile against factors directly). Rounding: scale composites by *recomputing* the composite sum from scaled leaves, with `HALF_UP` at 4 decimal places per node. This keeps `sum(children) == parent` invariant intact.

Reject 2.B because it tangles math params and presentation. Reject 2.C because it violates DRY for the component tree and doubles regression-test surface.

**Cross-area dependencies**: Area 3 (audit log) records the unscaled total; Area 5 (REST) exposes `calculateUnit` as a query flag (`?unit=PER_100G`) rather than a separate endpoint.

---

## Decision Area 3 — Audit log schema and write strategy

**Question**: One row per call or one row per leaf? Sync or async write? What fields are captured? Relation to the breakdown JSON?

### Alt 3.A — Single row per call, breakdown stored as JSONB

Table `footprint_audit_log`:

| column | type | notes |
|---|---|---|
| id | bigserial | PK |
| correlation_id | uuid | from `CalculationOptions` |
| caller_id | text | service / user identity |
| product_id | text | |
| timestamp_param | timestamptz | the `t` used for `versionAt(t)` |
| computed_at | timestamptz | wall clock |
| strictness | text | STRICT \| LENIENT |
| normalisation | text | AS_IS \| PER_100G |
| total_kg_co2 | numeric(18,6) | denormalised for fast filtering |
| warnings_count | int | denormalised |
| breakdown | jsonb | full tree |
| params | jsonb | full input parameters |

- **Pros**: trivially reproducible — replay `params` to get the same `breakdown` (modulo factor changes); Anna's CSV export reads one row and unfolds JSONB; small number of rows (one per call); GIN index on `breakdown` for ad-hoc factor lookups.
- **Cons**: querying "all calls that used factor version X" requires JSONB introspection; row size can grow if breakdown is large (acceptable here — <20 nodes).

### Alt 3.B — Row per leaf + parent header row

Two tables: `footprint_audit_call` (header) and `footprint_audit_node` (one row per tree node, FK to header).

- **Pros**: relational queries on factor usage are trivial (`SELECT * FROM footprint_audit_node WHERE factor_version_id = ...`); fits BI tools without JSONB tricks; per-node scope analytics are straightforward.
- **Cons**: ~13 rows per call (9 leaves + 4 composites); higher write amplification; schema must evolve when component tree changes; reproducibility requires reconstructing the tree from rows.

### Alt 3.C — Sync write inline vs async via Spring `ApplicationEventPublisher`

Orthogonal to schema. Sync: facade calls `auditWriter.write(call, breakdown)` before returning. Async: facade publishes `FootprintCalculatedEvent`, a `@TransactionalEventListener(phase=AFTER_COMMIT)` listener persists it.

- **Sync pros**: audit guarantee is trivially provable — if the call returned, the row exists; aligned with constraint #6 ("each call has corresponding audit row").
- **Sync cons**: response latency includes a DB write; engine fails the calculation if the audit insert fails.
- **Async pros**: lower latency; calculation failure decoupled from audit failure.
- **Async cons**: weakens audit guarantee — success-criterion #6 becomes "eventually" rather than "always"; harder to test; events lost on JVM crash before flush.

### Recommendation — **Alt 3.A schema + synchronous write, with `dryRun` flag escape hatch**

Single row per call with JSONB breakdown. Synchronous write inside the same transaction as the calculation entry. Add `dryRun: boolean` on `CalculationOptions` (defaults to `false`); when `true`, no audit row is written — this is Piotr's "test without polluting audit" need. Engine must throw if `dryRun=false` and the audit insert fails (audit-guarantee criterion is non-negotiable).

Indexes: `(product_id, timestamp_param)` for historical lookup; `(correlation_id)` unique for idempotency; GIN on `breakdown->'factorVersionIds'` for Anna's factor-traceability queries (denormalise a `text[] factor_version_ids` column populated from the breakdown to avoid jsonb_path_query in hot paths).

Reject 3.B because the row-per-leaf model commits us to a stable schema while the component tree is greenfield; 13× write amplification for marginal query convenience is a bad trade. We can add a materialised view derived from JSONB later if Anna needs it.

Reject async write at V1: it breaks success-criterion #6 in observable ways for marginal latency gains (audit insert is ~1ms on indexed PG table). Revisit if benchmarks show audit write >10% of total latency.

**Cross-area dependencies**: Area 4 (warnings) — `warnings` array is part of the JSONB breakdown; Area 6 (CSV) — exporter reads `breakdown` JSONB and flattens; Area 8 (historical lookup) — drives the implementation choice.

---

## Decision Area 4 — Error / warning model

**Question**: Typed exception hierarchy; warnings payload shape for lenient mode; HTTP status mapping.

### Alt 4.A — Flat exception hierarchy + flat warnings list

```java
public sealed class FootprintCalculationException extends RuntimeException
    permits MissingFactorException, MissingProductAttributeException,
            InvalidParametersException, ApplicabilityResolutionException {}

record FootprintWarning(String code, String componentId, String message, Map<String,Object> details) {}
List<FootprintWarning> warnings;  // attached to FootprintBreakdown
```

- **Pros**: simple; `code` is the contract (`MISSING_FACTOR`, `MISSING_ATTRIBUTE`, `INACTIVE_COMPONENT`); easy to translate; flat list is easy to render.
- **Cons**: locating which node generated the warning requires scanning by `componentId`.

### Alt 4.B — Per-node warning annotations on the breakdown tree

Each `BreakdownNode` carries `List<FootprintWarning> warnings`. Composite nodes aggregate descendants' warnings via a `hasIssuesDeep()` helper.

- **Pros**: Tomek's UI hover ("why is this component missing?") gets exact node-level info for free; Anna's CSV export keeps warning context per row.
- **Cons**: warning is more verbose in JSON; an "engine-level" warning (e.g., timestamp far in the future) has no obvious node to attach to — needs an `engine` sentinel node or a root-level warnings list anyway.

### Alt 4.C — Problem+JSON envelope (RFC 7807) for errors, structured warnings for lenient

In strict mode the REST surface emits `application/problem+json` with `type`, `title`, `detail`, `status`, plus extension `errors: [{componentId, code, ...}]`. In lenient mode the regular 200 envelope carries `warnings`.

- **Pros**: RFC compliance; client libraries already understand it; consistent across the platform's REST endpoints.
- **Cons**: more upfront type design.

### Recommendation — **Alt 4.B (per-node warnings) + Alt 4.C (Problem+JSON for strict errors), with a root-level warnings array for engine-wide issues**

Combined model:

- Strict mode: throw typed exception → REST emits Problem+JSON with `status: 422 Unprocessable Entity` for missing factor / attribute, `400 Bad Request` for invalid parameters, `409 Conflict` for overlapping factor versions (engine-level inconsistency), `500` only for truly unexpected.
- Lenient mode: `FootprintBreakdown` returns 200 with `warnings: [...]` at the root *and* on each affected node. The two are linked by `correlationId` so the UI can highlight the offending node.

Status mapping table:

| Exception | HTTP | rationale |
|---|---|---|
| `MissingFactorException` | 422 | semantic input failure |
| `MissingProductAttributeException` | 422 | semantic input failure |
| `InvalidParametersException` | 400 | syntactic input failure |
| `ApplicabilityResolutionException` | 409 | engine-side consistency issue |
| `FactorVersionOverlapException` | 409 | engine-side consistency issue |

**Cross-area dependencies**: Area 3 (warnings persisted into audit JSONB so Anna can audit lenient calls); Area 5 (REST contract embeds Problem+JSON).

---

## Decision Area 5 — REST contract shape

**Question**: GET with query params or POST with body? URL design? Batch shape? Envelope vs flat?

### Alt 5.A — `GET /api/products/{productId}/footprint?asOf=...&destinationKm=...&unit=TOTAL|PER_100G&strictness=STRICT|LENIENT`

- **Pros**: cacheable in principle (we don't cache V1, but doesn't preclude later); idempotent semantics match a pure calculation; bookmarkable for Marta's saved scenarios.
- **Cons**: URL length explodes once we pass 6-8 numeric distance/weight overrides; query string is awkward for nested options.

### Alt 5.B — `POST /api/footprints/calculations` with full JSON body

```json
POST /api/footprints/calculations
{
  "productId": "OFB-330",
  "timestamp": "2026-07-15T00:00:00Z",
  "transport": { "supplierKm": 2800, "destinationKm": 570, "lastMileKm": 15 },
  "storage": { "storageDays": 14 },
  "options": { "strictness": "STRICT", "unit": "TOTAL", "dryRun": false }
}
```

- **Pros**: body matches the grouped `FootprintParameters` record 1:1; no URL length issues; arbitrary future fields fit naturally; comparison view sends one POST per scenario or uses batch (5.D).
- **Cons**: not bookmarkable; POST for a read feels wrong to REST purists; needs caller-supplied idempotency key (`Idempotency-Key` header) to map to the audit row's `correlation_id`.

### Alt 5.C — Hybrid: `GET` for the canonical "default scenario" + `POST` for overrides

`GET /api/products/{id}/footprint` runs default scenario (today's date, default destination from product); `POST /api/footprints/calculations` for explicit parameters.

- **Pros**: serves Tomek's product detail page with a single GET; serves Marta's scenario tool with POST.
- **Cons**: two paths to maintain; risk of subtle drift between them.

### Alt 5.D — Batch endpoint: `POST /api/footprints/calculations:batch`

Body: `{ scenarios: [ {...params1}, {...params2}, ... ] }`; response: `{ results: [...breakdowns...] }`. Limit ≤20 scenarios per request.

- **Pros**: directly serves Marta's comparison view in one round-trip; each scenario gets its own audit row but they share a `batchId` so Anna can audit the comparison as a unit.
- **Cons**: partial failures need a per-scenario status field; response size can grow.

### Recommendation — **Alt 5.B as the canonical endpoint + Alt 5.D for batch; Alt 5.C's GET is a thin convenience over POST**

Primary: `POST /api/footprints/calculations` with the grouped JSON body and `Idempotency-Key` header → `correlation_id`. The response envelope:

```json
{
  "correlationId": "...",
  "calculatedAt": "...",
  "parametersEcho": { ... },
  "result": { "total": 5.37, "unit": "kgCO2", "breakdown": { ...tree... } },
  "warnings": [ ... ],
  "factorVersions": [ { "componentId": "...", "factorVersionId": "...", "validFrom": "..." } ]
}
```

Batch: `POST /api/footprints/calculations:batch` with up to 20 scenarios; each result carries its own `correlationId` plus a shared `batchId`. Partial failures: each scenario element has `status: "ok" | "error"` and either `result` or `error` (Problem+JSON shape).

Convenience GET: `GET /api/products/{id}/footprint?asOf=...&destination=...` exists for Tomek's product detail page and Anna's quick browsing; internally it constructs the same `FootprintParameters` and calls the same facade. The application layer (not the engine) resolves `destination=Szczecin` → distances before delegating.

Reject pure-GET (5.A) as the only surface: distance overrides for Marta's "what-if" comparisons inflate URLs beyond practical limits.

**Cross-area dependencies**: Area 4 (Problem+JSON for errors); Area 6 (CSV is a content negotiation on the same resource); Area 7 (batch endpoint serves comparison view directly).

---

## Decision Area 6 — CSV export delivery

**Question**: Engine-emitted or separate exporter? Format? Sync download or async job?

### Alt 6.A — Engine emits CSV directly via content negotiation

`GET /api/products/{id}/footprint` with `Accept: text/csv` returns the flattened breakdown. Same for `POST /api/footprints/calculations`.

- **Pros**: zero extra surface; Anna sets `Accept: text/csv` and pipes to file; one source of truth (the breakdown).
- **Cons**: CSV formatting concerns leak into the engine module; column conventions and locale (decimal separator) need owning.

### Alt 6.B — Separate exporter service that consumes the breakdown

`POST /api/footprints/exports` body: `{ correlationId: "..." }` — looks up audit row, flattens to CSV, streams response.

- **Pros**: clean module boundary; engine emits JSON only; exporter can evolve formats (CSV, XLSX) independently.
- **Cons**: two modules; Anna needs two calls; correlationId may not exist for ad-hoc one-off exports unless we always persist (which we do, via Decision 3).

### Alt 6.C — Async export job for bulk

`POST /api/footprints/exports:bulk` with filters (date range, product list); returns `jobId`; client polls or receives webhook; final result is a downloadable file.

- **Pros**: handles Anna's quarterly "all products, Q1" reconciliation.
- **Cons**: jobs/scheduling are heavy machinery; V1 overkill given <100k calls/quarter.

### Recommendation — **Alt 6.B (separate exporter resource, sync) for V1; defer 6.C until volume demands it**

Engine never emits CSV. Exporter is a thin resource on the audit-log read path: `GET /api/footprints/calculations/{correlationId}/export?format=csv` returns flattened CSV synchronously. Columns: `correlation_id, computed_at, product_id, component_path, component_id, kg_co2, scope, factor_value, factor_valid_from, factor_version_id, warnings`. One row per leaf; composites omitted (Excel-friendly — Anna can pivot).

This keeps the engine's responsibility "produce breakdown JSON + write audit row" and lets a small `footprint-export` slice own format concerns. Reject 6.A — CSV concerns are presentation, not calculation. Reject 6.C for V1 — defer until measured volume justifies it; Anna's V1 workflow (sample-based audit) is sync per call.

**Cross-area dependencies**: Area 3 (exporter reads `breakdown` JSONB) — schema must include factor version ids per leaf.

---

## Decision Area 7 — Comparison view backend support

**Question**: One endpoint with multiple param sets, N client-side calls, or a server-side batch endpoint?

### Alt 7.A — N independent client-side calls

Marta's UI fires 2-5 parallel `POST /api/footprints/calculations`.

- **Pros**: no new endpoint; reuses existing surface; failures isolated per scenario.
- **Cons**: N round trips, N audit rows with no link between them — Anna can't audit "the comparison Marta showed the board" as a unit.

### Alt 7.B — Dedicated comparison endpoint with shared baseline

`POST /api/footprints/comparisons` with `{ baseline: {...params}, variants: [{ override: {transport.destinationKm: 15} }] }`.

- **Pros**: semantically rich; encodes Marta's intent ("same product, different destinations"); audit row carries baseline + variant deltas.
- **Cons**: extra endpoint with overlapping responsibility vs batch; bespoke shape.

### Alt 7.C — Generic batch endpoint (from Decision 5)

Reuse `POST /api/footprints/calculations:batch` with `batchId` linking the audit rows.

- **Pros**: one new surface serves both Marta's comparison and any other multi-scenario workload (e.g., Piotr's regression suite calling 50 SKUs); each scenario is fully specified, no baseline-merge logic.
- **Cons**: no notion of "common baseline" so 5 scenarios repeat the unchanged fields 5 times — verbose payload.

### Recommendation — **Alt 7.C (generic batch with `batchId`)**

Use the batch endpoint from Decision 5. Marta's UI sends one POST with 2-5 fully-specified scenarios; response returns 2-5 breakdowns each with its own `correlationId` plus a shared `batchId`. Anna queries audit by `batchId` to retrieve the entire comparison as a unit. Verbosity of repeated fields is acceptable (<10KB request body in practice).

Reject 7.A — losing the comparison-as-a-unit relationship in audit data hurts Anna's reconciliation workflow.

Reject 7.B — the "baseline + overrides" shape is appealing but adds merging semantics (what wins on conflict? deep merge of grouped record?) that aren't worth the complexity for 3-5 scenarios. If repeated payload size becomes a real problem (>50KB), introduce 7.B as a syntactic sugar on top of 7.C later.

**Cross-area dependencies**: Area 3 schema needs a nullable `batch_id` column.

---

## Decision Area 8 — Historical lookup: replay audit vs recompute

**Question**: "What was the footprint of OFB-330 on 2026-01-15?" — re-read audit row or re-compute with `timestamp=2026-01-15`?

### Alt 8.A — Re-compute deterministically from past timestamp

`POST /api/footprints/calculations` with `timestamp=2026-01-15`. `versionAt(t)` resolves January factor versions; the engine produces the same answer it produced in January (assuming no factor versions have been retro-edited — which `REJECT_OVERLAPPING` prevents).

- **Pros**: pure-function guarantee — given identical params, identical result; works even if no audit row exists; Marta's "what would Q1 have looked like with January factors?" scenarios work without a historical row needing to exist.
- **Cons**: assumes factor history is immutable. If factors *could* be silently rewritten (they shouldn't — RC archetype is supposed to prevent this), recompute would diverge from the original answer.

### Alt 8.B — Query the audit log and return the historical row verbatim

`GET /api/footprints/calculations/{correlationId}` returns the persisted `breakdown` JSONB.

- **Pros**: bit-for-bit reproducible — Anna sees exactly what was returned to whoever called the engine then; no dependency on factor history immutability.
- **Cons**: only works for calls that were actually made; "what would it have been?" hypotheticals don't work; if audit row is corrupted, no recovery.

### Alt 8.C — Hybrid: try replay-from-audit first, fall back to recompute, flag divergence

Endpoint accepts `correlationId` or `productId+timestamp`; if `correlationId` matches, return audit row; if not, recompute via `versionAt(t)` and recompute. Optionally cross-validate: recompute the historical row and warn if it diverges from the audit-stored breakdown.

- **Pros**: best of both worlds; cross-validation surfaces tampering or factor-history bugs.
- **Cons**: two code paths in one endpoint; "divergence" semantics need careful design.

### Recommendation — **Alt 8.A for live calculation; Alt 8.B for audit retrieval; both surfaces explicit, no hybrid**

Two distinct semantics, two distinct endpoints — do not conflate them:

- **Recompute (8.A)**: `POST /api/footprints/calculations` with a past `timestamp` is the canonical way to ask "what does the engine compute for that moment?" — produces a fresh breakdown, writes a new audit row, deterministic via `versionAt(t)` + `REJECT_OVERLAPPING`. Marta's historical timeline UI uses this.
- **Audit retrieval (8.B)**: `GET /api/footprints/calculations/{correlationId}` returns the verbatim persisted row — no calculation occurs, no new audit row written. Anna's historical-lookup UI uses this to inspect what was actually returned to a past caller.

Frontend's "historical timeline" view (third UI scope) primarily uses 8.A — it visualises how the *same* product's footprint moved over time as factors evolved, which only `versionAt(t)` can do. It uses 8.B when the user clicks "view the specific calculation behind this datapoint".

Reject 8.C (hybrid with cross-validation) for V1 — it implies factor-history could change behind the engine's back, which the RC archetype is contracted to prevent. If that contract is later weakened, add divergence detection as a separate `POST /api/footprints/audit-verifications` endpoint owned by Anna's audit module — don't embed it in the calculation surface.

**Cross-area dependencies**: requires Area 3 schema columns (`correlation_id` unique, `product_id`, `timestamp_param` indexed); requires Area 5's POST shape to accept arbitrary historical timestamps; UI's timeline-view depends on 8.A.

---

## Cross-Area Dependency Summary

| Decision | Depends on | Provides to |
|---|---|---|
| 1 — Parameters shape | — | 2 (calculateUnit needs `.with(...)`), 3 (params persisted to JSONB), 5 (REST body mirrors grouped record) |
| 2 — calculateUnit | 1 | 3 (audit always records canonical total), 5 (`unit` is an option, not an endpoint) |
| 3 — Audit schema | 1, 2 | 6 (exporter reads JSONB), 7 (`batch_id`), 8 (audit retrieval) |
| 4 — Error/warning model | — | 3 (warnings in JSONB), 5 (Problem+JSON envelope) |
| 5 — REST contract | 1, 4 | 6 (content negotiation N/A — separate exporter), 7 (batch), 8 (POST for recompute, GET for retrieval) |
| 6 — CSV export | 3 | — |
| 7 — Comparison | 5 (batch) | 3 (`batch_id`) |
| 8 — Historical lookup | 3, 5 | UI timeline view |

---

## Recommended Stack — One-Line Summary

Grouped `FootprintParameters` record + sibling `CalculationOptions`; `calculateUnit` is `calculateTotal().scaled(100/mass)`; single-row JSONB audit log written synchronously with `dryRun` escape hatch; per-node warnings plus Problem+JSON for strict errors; `POST /api/footprints/calculations` as canonical surface with `:batch` for comparisons and a convenience GET for product detail pages; CSV via separate `footprint-export` slice reading the audit JSONB; historical lookup split into recompute (POST) and audit retrieval (GET) — no hybrid.

---

## Deferred Ideas (out of V1 scope)

- **Async batch exports** (Decision 6.C) — quarterly bulk reconciliation. Defer until measured volume justifies job infrastructure.
- **Baseline+overrides comparison shape** (Decision 7.B) — payload-size optimisation over generic batch. Defer until payloads exceed ~50KB.
- **Audit divergence detection** (Decision 8.C) — cross-validate recomputed vs persisted breakdown. Defer; belongs in a future audit module if RC archetype's immutability guarantee weakens.
- **Caching layer** — explicitly out of V1 per locked constraint.
- **Materialised view of audit nodes** — relational projection over JSONB for BI tools. Defer until Anna's BI tooling needs it.
- **GraphQL surface** — could simplify the comparison view's "I want these 5 fields from these 3 scenarios" use case. Defer; not aligned with the platform's REST conventions.

No out-of-scope problems were uncovered that warrant expanding the engine's responsibility — destination-name resolution, factor management, eco-rating derivation, and frontend rendering remain firmly outside.

---

## Confidence

**Overall confidence: high** on Decisions 1, 2, 3 (schema choice), 4, 5, 8 — evidence-backed by the locked constraints and personas. **Medium confidence** on Decision 3's sync-vs-async write choice (depends on measured audit-write latency, which we don't have yet) and Decision 6's exporter-as-separate-slice (could fold into engine if the platform's module conventions favour fewer slices). Key assumption underpinning all recommendations: the Emission Factor Management module honours `REJECT_OVERLAPPING` and never retro-edits historical factor versions. If that assumption breaks, Decision 8.A's recommendation must be revisited.
