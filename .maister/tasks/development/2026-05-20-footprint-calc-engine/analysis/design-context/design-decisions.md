# Design Decisions — Footprint Calculation Engine

Selected approach across the 8 decision areas explored in `analysis/alternatives.md`. For each area: the choice, brief rationale, and trade-offs accepted.

## Summary Table

| Area | Choice | Source alt |
|---|---|---|
| 1. FootprintParameters shape | Flat Java record + separate CalculationOptions | 1.A |
| 2. calculateUnit() | Adapter on calculateTotal (post-divide scaled tree) | 2.A |
| 3a. Audit schema | Single row per call, breakdown as JSONB | 3.A |
| 3b. Audit write | **Async** via `@TransactionalEventListener(phase=AFTER_COMMIT)` | 3.C async |
| 4. Errors / warnings | Per-node warnings on tree + Problem+JSON envelope for strict errors | 4.B + 4.C |
| 5. REST contract | **Pure GET** with query params | 5.A |
| 6. CSV export | Separate exporter resource reading audit log | 6.B |
| 7. Comparison support | N client-side GETs + `X-Comparison-Group` header for audit linkage | 7.A + group header |
| 8. Historical lookup | **Always recompute** via `versionAt(t)`; no verbatim by-correlationId retrieval | 8.A only |

## Per-Area Rationale

### 1. Flat record + CalculationOptions
Flat Java record for math inputs (timestamp, productId, materialWeightKg, supplierDistanceKm, destinationDistanceKm, lastMileDistanceKm, storageDays, requiresRefrigeration). Separate `CalculationOptions` record for `strictness`, `correlationId`, `callerId`, `dryRun`, `comparisonGroupId`. Determinism criterion preserved by keeping options out of the math-input record.

Trade-off: 8-arg constructor at call sites. Mitigation: static factory methods and a convenience builder for common scenarios.

### 2. Adapter (post-divide)
`calculateUnit(params) = calculateTotal(params).scaled(BigDecimal.valueOf(100).divide(materialWeightKg, MathContext.DECIMAL64))`. Audit log records the canonical total breakdown; the normalisation factor is recorded on `CalculationOptions`. Rounding: `HALF_UP` at 4 decimal places per node; composites recomputed from scaled leaves to preserve `sum(children) == parent`.

### 3a. Single-row JSONB audit
Table `footprint_audit_log`:
- id (bigserial PK), correlation_id (uuid unique), caller_id, product_id
- timestamp_param (the `t` used for versionAt), computed_at (wall clock)
- strictness, normalisation, total_kg_co2 (denormalised), warnings_count (denormalised)
- breakdown (jsonb), params (jsonb)
- comparison_group_id (uuid, nullable) — populated from `X-Comparison-Group` header
- factor_version_ids (text[], denormalised from breakdown for GIN index)

Indexes: `(product_id, timestamp_param)`, unique `(correlation_id)`, `(comparison_group_id)`, GIN on `factor_version_ids`.

### 3b. Async audit write
`FootprintCalculatedEvent` published from facade; `@TransactionalEventListener(phase=AFTER_COMMIT)` listener persists. Avoids latency penalty on calculation path. `dryRun=true` suppresses event publication entirely.

**Trade-off accepted**: success criterion #6 (audit guarantee) becomes "eventually consistent" rather than "synchronous". Mitigations: outbox-style retry on listener failure; alerting on listener queue depth.

### 4. Per-node warnings + Problem+JSON
Each `BreakdownNode` carries `List<FootprintWarning> warnings`. Root `FootprintBreakdown` also carries a root-level `warnings` list for engine-wide issues (e.g., timestamp far in future).

Strict mode → typed exception → REST emits `application/problem+json`:
- `MissingFactorException` → 422
- `MissingProductAttributeException` → 422
- `InvalidParametersException` → 400
- `ApplicabilityResolutionException` → 409
- `FactorVersionOverlapException` → 409

Sealed exception hierarchy:
```java
sealed class FootprintCalculationException permits
  MissingFactorException, MissingProductAttributeException,
  InvalidParametersException, ApplicabilityResolutionException,
  FactorVersionOverlapException;
```

### 5. Pure GET
- `GET /api/products/{id}/footprint?asOf=...&destinationKm=...&unit=TOTAL|PER_100G&strictness=STRICT|LENIENT`
- All numeric overrides (distances, storage days, etc.) appear as query parameters.
- Defaults pulled from Product Catalog attributes by the application layer before delegating to the facade.

**Trade-off accepted**: URL length cap (~2000 chars) limits how many overrides callers can pass. In practice, Marta's comparisons differ in 1-2 fields per scenario; well under the limit.

### 6. Separate exporter slice
- `GET /api/footprints/calculations/{correlationId}/export?format=csv` returns flattened CSV.
- One row per leaf component (composites omitted; Excel pivot friendly).
- Columns: `correlation_id, computed_at, product_id, component_path, component_id, kg_co2, scope, factor_value, factor_valid_from, factor_version_id, warnings`.
- Owned by a separate `footprint-export` slice; engine module never emits CSV.

### 7. N GETs + X-Comparison-Group header
Client (Marta's comparison UI) generates a UUID per comparison session, sends it as `X-Comparison-Group` header on each GET. Server reads header, populates `comparison_group_id` on the resulting audit row. Anna queries audit by `comparison_group_id` to retrieve the entire comparison as a unit.

### 8. Always recompute
Historical timeline view calls `GET /api/products/{id}/footprint?asOf=2026-01-15` and trusts `versionAt(t)` + `REJECT_OVERLAPPING` to return the same breakdown as January would have. No `GET /api/footprints/calculations/{correlationId}` endpoint.

**Trade-off accepted**: Anna's reconciliation depends on factor immutability holding. The audit log is still queryable (and exported via CSV) but not addressable by correlationId. If factor immutability ever weakens, revisit and add verbatim retrieval.

## Key Trade-offs Summary

1. **Audit guarantee weakened** from synchronous to eventually-consistent (async write). Acceptable because audit is read-mostly and listener can retry; latency win.
2. **Determinism boundary** stays at the math-input record (flat) — options separate. Comparison view uses `.with(...)` on the record.
3. **URL length cap** — pure GET means many overrides won't fit. Acceptable: comparison scenarios differ in 1-2 fields typically.
4. **No verbatim audit retrieval endpoint** — historical lookup is recompute-only. Acceptable while RC archetype guarantees factor immutability; CSV export still gives Anna addressability via correlationId.
5. **CSV is a separate slice** — keeps engine pure-JSON; minimal extra surface.

## References

Full alternatives and pros/cons in `analysis/alternatives.md`.
