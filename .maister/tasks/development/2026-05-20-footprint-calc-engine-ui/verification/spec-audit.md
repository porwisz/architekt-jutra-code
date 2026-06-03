# Specification Audit — Footprint Calculator UI (V1)

**Spec**: `implementation/spec.md`
**Date**: 2026-05-20
**Verdict**: **pass-with-concerns**

## Summary

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 2 |
| Medium | 4 |
| Low | 3 |

Spec is largely implementable. Backend endpoints, DTO shapes, and reusable primitives independently verified. Two High findings stem from under-specified type-contract details. All other findings are clarifications, not blockers.

## Bindings honour-check
- Timeline page DEFERRED V1.1 — no FR for it ✓
- 2 V1 pages (detail + same-product compare) ✓
- No chart library / package.json marked unchanged ✓
- Shared Problem+JSON parser at `src/api/problem.ts` ✓
- Explicit Recalc button ✓
- Save-comparison = URL clipboard only ✓
- TypeScript types hand-written ✓
- Comparison-group UUID client-generated, in URL ✓

All 7 bindings respected.

## Findings

### High-1 — BigDecimal wire-type ambiguity → `tagBreakdown` parser is under-specified
**Spec ref**: §TypeScript Type Contracts L137-151 declares `kgCo2: number` with side-note "number/string — parse to number".
**Evidence**: `FootprintResponseDto.java:18-76` types fields as `java.math.BigDecimal`. No Jackson override in controller package → Spring default serialises BigDecimal as JSON **number**.
**Impact**: Spec hedge ("number/string") forces defensive code for a case that doesn't occur, or leaves `number | string` propagating through `BreakdownTree`. `tagBreakdown` runtime mapper is under-specified for nested traversal — must walk root + every child for the boundary normalisation.
**Recommendation**: Pin to `number`-only and enumerate which fields the boundary normaliser coerces; confirm `tagBreakdown` recurses.

### High-2 — Scenario auto-name format deviates from binding mockup
**Spec ref**: FR-3 L47 — `Scenario A — <date> / <destination km>`.
**Evidence**: `product-footprint-comparison.html` L200/218/236 uses `Scenario A — Summer / Szczecin` (season + city).
**Justification**: FR-2 L33 explicitly says destination-name resolution is backend-layer concern, out of V1 scope.
**Impact**: Functional but visually unlike mockup. QA may flag.
**Recommendation**: Make the deviation explicit in FR-3 alongside the auto-name decision.

### Medium-1 — `client.ts` extended signature unspecified
**Spec ref**: FR-5 L215 + Modified files L285 — "accept optional `{headers}` on `get/post/put/patch/delete`".
**Evidence**: `client.ts:54-83` — for `post/put/patch` current signature is `(path, body)`; adding `{headers}` requires choosing position (3rd arg) to avoid collision.
**Recommendation**: Pin signature explicitly. E.g. `get<T>(path, opts?: {headers?})` and `post<T>(path, body, opts?: {headers?})`.

### Medium-2 — `Unit` enum drift between TS and backend unverified
**Spec ref**: §TypeScript Type Contracts L124 — `Unit = "KG_CO2" | "KG_CO2_PER_100G"`.
**Evidence**: `FootprintResponseDto.java:11` imports `pl.devstyle.aj.footprint.api.Unit`. Controller maps `Normalisation.PER_100G → Unit.KG_CO2_PER_100G` (L87). Enum constant names not loaded.
**Recommendation**: Implementer should verify `pl.devstyle.aj.footprint.api.Unit` constants.

### Medium-3 — Expand/collapse a11y under-specified
**Spec ref**: §Standards Compliance L362 mentions `aria-expanded`, Space/Enter handling, `aria-live="polite"` on CSV status.
**Gaps**:
- No mention of `role="treegrid"` vs semantic `<table>`. Spec L36 says "Columns:" implying table, L362 says "semantic `<table>`". `<table>` cannot natively express expand/collapse.
- Comparison-form keyboard focus order not specified.
- `aria-live` for per-scenario "Exporting 2 of 3…" sequential status not specified.
**Recommendation**: Add 3-line a11y subsection per page; pick semantic `<table>` + visually-hidden expand control vs `role="treegrid"`.

### Medium-4 — Comparison page URL edge case unspecified
**Spec ref**: FR-3 L47 + Technical Approach L350-351 — baseline removal-protected; URL replace-navigates on scenario removal.
**Gap**: When URL contains a malformed `s=` param, `decodeScenario` returns `null` — spec doesn't say behaviour (drop silently / fall back / error banner).
**Recommendation**: Add "Invalid `s=` params dropped silently; if all invalid, fall back to single baseline derived from product."

### Low-1 — "Sent on every request" mildly ambiguous
**Spec ref**: FR-3 L46. Implicit meaning: every comparison-page request. Not blocking.

### Low-2 — 3 self-flagged warnings audited, all coherent
1. Negative `materialWeightKg` → backend throws `InvalidParametersException` (line 42-44 in controller) ✓
2. Cold storage row omitted when response tree lacks it (not based on input flag) ✓
3. `cg=<uuid>` minted client-side via `crypto.randomUUID()`, backend accepts arbitrary UUIDs ✓

### Low-3 — `ConfirmDialog` listed as reusable but FR-3 skips it
Cosmetic only; could remove row from reuse table to reduce noise. Non-blocking.

## Independent verifications

- `FootprintController.java` — path, query+header binding, UUID parsing match spec ✓
- `FootprintExportController.java` — path, Content-Disposition filename `footprint-<uuid>.csv` match spec ✓
- `FootprintResponseDto.java` — sealed `BreakdownDto` with `CompositeDto`/`LeafDto`, no on-wire discriminator → spec's `tagBreakdown` is correctly motivated ✓
- `FootprintExceptionHandler.java` — Problem+JSON shape matches: type, title, status, detail, instance, `code` property, application/problem+json; UNPROCESSABLE_ENTITY for missing factor, BAD_REQUEST for invalid parameters, CONFLICT for factor version overlap ✓
- `client.ts` — additive headers param is non-breaking for 26 existing call sites ✓
- `ConfirmDialog.tsx`, `EmptyState.tsx`, `PrimaryButton.tsx`, `Icons.tsx` exist ✓
- Mockup copy `+ Add scenario (up to 4)` matches spec FR-3 L48 ✓

## Top critical findings

No Critical. Two High:
1. **High-1** — BigDecimal wire-type ambiguity; pin to `number`-only and document `tagBreakdown` recursion.
2. **High-2** — Auto-name format deviates from mockup; justified but should be explicit in FR-3.

## Clarifications recommended (non-blocking)

1. Pin `client.ts` extended signature (positional vs options-bag).
2. Verify `pl.devstyle.aj.footprint.api.Unit` enum constants.
3. Confirm Jackson BigDecimal serialisation strategy (or document the chosen branch).
4. Decide breakdown a11y pattern.
5. Define URL fallback when `s=` params fail to decode.

## Verdict

**pass-with-concerns** — Implementable as written. Address H1+H2 (and ideally M1+M3) before planning to avoid downstream rework.
