# Gap Analysis: Reviews Plugin

**Date**: 2026-04-07
**Risk Level**: Low
**Effort**: Medium (greenfield, multiple files + integration tests)

---

## Task Characteristics

- has_reproducible_defect: false
- modifies_existing_code: false
- creates_new_entities: true
- involves_data_operations: true
- ui_heavy: true

---

## Summary

`plugins/reviews/` directory does not exist. This is a pure additive greenfield implementation — no existing code is modified. The platform backend (plugin_objects, pluginData APIs) is fully operational. All frontend patterns are established by warehouse and box-size reference plugins.

---

## Gaps (All Files Missing)

| File | Template |
|------|----------|
| `plugins/reviews/package.json` | Copy warehouse, name="reviews-plugin" |
| `plugins/reviews/vite.config.ts` | Copy warehouse, port=3003 |
| `plugins/reviews/tsconfig.json` | Identical copy |
| `plugins/reviews/index.html` | Copy warehouse, title="Reviews Plugin" |
| `plugins/reviews/manifest.json` | 4 extension points (see below) |
| `plugins/reviews/src/main.tsx` | 3 routes: /, /product-reviews, /product-rating-badge |
| `plugins/reviews/src/domain.ts` | Review, RatingSummary, toReview(), toRatingSummary() |
| `plugins/reviews/src/pages/ReviewsAdmin.tsx` | WarehousePage pattern, all reviews + status filter |
| `plugins/reviews/src/pages/ProductReviewsTab.tsx` | ProductStockTab pattern, list + submit form |
| `plugins/reviews/src/pages/ProductRatingBadge.tsx` | ProductAvailability pattern, compact badge |
| `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginObjectTests.java` | PluginDataAndObjectsIntegrationTests pattern |
| `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginDataTests.java` | Same pattern |

---

## Integration Points

All via manifest + SDK — no platform code changes:
- menu.main → ReviewsAdmin
- product.detail.tabs → ProductReviewsTab
- product.detail.info → ProductRatingBadge
- product.list.filters → host-rendered natively (filterKey="rating", filterType="number", label="Rating")

---

## Important Decisions (Resolved via Clarifications)

### 1. Filter label semantics
`filterType: "number"` uses eq operator (exact match). Label must be **"Rating"** (not "Min Rating").
**Decision**: "Rating" with eq semantics. Honest about platform capability.

### 2. Admin page status filter
Load all reviews on mount, filter client-side via dropdown. Single SDK call.
**Decision**: Client-side filter dropdown (PENDING / APPROVED / REJECTED / All).

### 3. Admin page aggregate update
ReviewsAdmin runs in RENDER context (no productId). After approve/reject, must recalculate pluginData using `review.entityId` as the productId.
**Decision**: Implement aggregate update in ReviewsAdmin using `review.entityId`.

---

## Non-Obvious Requirements

- `Number()` cast required for entityId in objects.list options — `productId` from SDK is a string; cast needed in ProductReviewsTab (2 calls) and ProductRatingBadge (1 call). Admin page uses `review.entityId` which is already numeric.
- `setData` is full-replace — fetch-compute-write pattern required after every review status change.
- Race condition on concurrent saves is a known accepted trade-off for v1 (internal tool, low-traffic).
- Do NOT duplicate test scenarios from `PluginDataAndObjectsIntegrationTests.java` — cover reviews-specific composite flows only.
