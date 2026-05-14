# Specification Audit: Reviews Plugin

**Date**: 2026-04-07
**Verdict**: MOSTLY COMPLIANT — 2 Medium issues (will cause build failures), 2 Low issues

---

## Findings

### Finding 1 — `Number()` cast wrong for SDK `entityId` options (MEDIUM — build failure)

`plugins/sdk.ts` defines `entityId` as `string` in `objects.save` and `objects.list` options. Passing `Number(productId)` produces a TypeScript error under `strict: true`. The spec's instruction to use `Number()` cast for these SDK calls is incorrect.

**Fix**: Use `entityId: productId` (already a string) — no cast. Same for `review.entityId` in ReviewsAdmin (it's `string`, not numeric). Remove `Number()` cast instructions from Data Flow and Key Technical Constraints.

### Finding 2 — `Number()` cast wrong for `getData(productId)` (MEDIUM — build failure)

`plugins/sdk.ts` defines `getData(productId: string)`. The spec says `Number()` cast required for the `getData` call in ProductRatingBadge. Incorrect — use `getData(productId)` where productId is already a string. Template `ProductAvailability.tsx` confirms no cast needed.

**Fix**: Remove `Number()` cast mention for `getData` from Key Technical Constraints.

### Finding 3 — ProductBoxTab collapse template citation misleading (LOW)

`ProductBoxTab.tsx` uses a timed "Saved!" button state (2s timeout), not a form collapse. The spec cites it as a collapse template, which is inaccurate.

**Fix**: Clarify that the collapse pattern must be implemented fresh — use a `submitted` boolean state that switches between form view and success message view.

### Finding 4 — Research report has stale "Min Rating" label (LOW — informational)

Research report manifest still shows `"label": "Min Rating"`. Spec correctly uses `"Rating"`. Treat spec as authoritative. No spec change needed — note already present in spec.

---

## Verified Correct

All 8 key constraints confirmed:
- `erasableSyntaxOnly: true` — union types only (no enums) ✓
- `setData` full-replace — fetch-compute-write pattern documented ✓
- ReviewsAdmin RENDER context — uses `review.entityId` for aggregate recalc ✓
- filterType="number" eq semantics — label "Rating" (not "Min Rating") ✓
- ProductReviewsTab shows only APPROVED reviews ✓
- Form collapses after success (requirement unambiguous despite template citation issue) ✓
- `tools.jackson.databind.ObjectMapper` (not com.fasterxml) ✓
- Manifest label "Rating" correct ✓

---

## Action Required Before Implementation

The spec needs two corrections before implementation proceeds (Findings 1 and 2):

1. Replace all `entityId: Number(productId)` in SDK option objects with `entityId: productId`
2. Replace `Number()` cast instruction for `getData` with plain `getData(productId)`
3. Clarify ProductBoxTab collapse template citation (Finding 3)
