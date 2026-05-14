# Requirements: Reviews Plugin

**Date**: 2026-04-07

---

## Initial Description

Implement a new 'reviews' plugin for the existing microkernel (plugin-based) Spring Boot platform. The plugin adds product review and rating functionality as an extra capability to the core system.

---

## Q&A — All Rounds

### From Research Phase (high-confidence decisions)
- Store individual reviews as `plugin_objects` (objectType="review", entityType=PRODUCT)
- Store per-product rating summaries as `pluginData` `{ rating: float, count: int }`
- Four extension points: product.detail.tabs, product.detail.info, product.list.filters, menu.main
- No custom backend Java code for v1 — all via SDK
- Plugin ID: "reviews", port 3003

### From Clarifications (Phase 1)
- Admin page shows ALL reviews with status dropdown filter (PENDING/APPROVED/REJECTED/All)
- Product list filter uses `filterType: "number"` with eq semantics, label "Rating"

### From Phase 5 Requirements
- Reviews are anonymous — reviewer enters their own name (no auth backing)
- ProductReviewsTab shows only APPROVED reviews (PENDING/REJECTED hidden from product tab)
- Submit form collapses after successful submission, shows success message
- No visual assets — use ASCII mockups from Phase 4

---

## Similar Features (Reuse Opportunities)

- **WarehousePage.tsx** — template for ReviewsAdmin (full CRUD, status filter, tc-table, approve/reject actions)
- **ProductStockTab.tsx** — template for ProductReviewsTab (product-scoped, entity-filtered list + form)
- **ProductAvailability.tsx** — template for ProductRatingBadge (compact badge, null-while-loading, tc-badge)
- **ProductBoxTab.tsx** — submit form pattern (label+input column, validation, collapse-after-save pattern)
- **plugins/warehouse/package.json, vite.config.ts, tsconfig.json, index.html** — direct copies with port/name changes

---

## Functional Requirements Summary

### FR1: Review Submission (ProductReviewsTab)
- User enters: reviewer name, rating (1-5), title, body
- Review saved with status=PENDING via `objects.save("review", uuid, payload, { entityType: "PRODUCT", entityId: Number(productId) })`
- After save: fetch all APPROVED reviews for product, recompute average, call `setData(productId, { rating: avg, count: n })`
- Form collapses after successful submission, success message shown
- ProductReviewsTab shows only APPROVED reviews in the list

### FR2: Review Moderation (ReviewsAdmin)
- Shows all reviews across all products
- Status filter dropdown: All / PENDING / APPROVED / REJECTED (client-side, single SDK load)
- PENDING reviews: Approve + Reject buttons
- APPROVED reviews: Reject button only
- REJECTED reviews: Approve button only
- After approve/reject: fetch all APPROVED reviews for that product (using `review.entityId`), recompute average, call `setData`

### FR3: Rating Badge (ProductRatingBadge)
- Reads `getData(productId)` → `{ rating: float, count: int }`
- Returns null/empty while loading and when no data exists
- Display: "★ {avg}" + "({count} reviews)"
- Visual: tc-badge--success if avg >= 4.0, plain span if 2.0–3.9, tc-badge--danger if < 2.0
- Read-only, ~60px height, compact layout

### FR4: Product List Filter
- Manifest-driven, no React route needed
- filterKey="rating", filterType="number", label="Rating"
- Platform renders number input natively; queries products where pluginData.reviews.rating == value (eq semantics)
- Aggregate written by FR1 and FR2 enables this filter

### FR5: Domain Types
- `ReviewStatus`: "PENDING" | "APPROVED" | "REJECTED"
- `Review`: objectId, productId (number), rating (number 1-5), title, body, reviewer, status, createdAt, updatedAt
- `RatingSummary`: rating (number), count (number)
- `toReview(obj: PluginObject): Review` mapper
- `toRatingSummary(data: Record<string, unknown>): RatingSummary` mapper

### FR6: Backend Integration Tests
- Package: `pl.devstyle.aj.reviews`
- Class `ReviewsPluginObjectTests` (5 scenarios): save review with PRODUCT binding, list by product, filter by status (eq:APPROVED), filter by rating (gt:3), list all without filter
- Class `ReviewsPluginDataTests` (3 scenarios): set + read summary, overwrite previous data, product list filter by rating

---

## Scope Boundaries

**In scope:**
- All 12 files in plugins/reviews/ directory
- 2 backend integration test classes (8 tests total)
- Plugin manifest registration (curl command documented)

**Out of scope:**
- Custom backend Java endpoint for rating summaries (deferred to v2)
- Database migration (not needed — uses existing plugin_objects + plugin_data infrastructure)
- Frontend unit tests (consistent with warehouse/box-size — none exist)
- Auth-backed reviewer identity
- Review deletion (no delete UI in v1)
- Pagination beyond 1000 reviews

---

## Technical Considerations

- `../../sdk` import path requires exact `plugins/reviews/src/` directory depth
- `Number(productId)` cast required in ProductReviewsTab (2 calls) and ProductRatingBadge (1 call); ReviewsAdmin uses `review.entityId` which is already numeric
- `setData` is full-replace — fetch-compute-write pattern in both ProductReviewsTab and ReviewsAdmin
- Fetch-compute-write race condition accepted for v1 (internal tool, low-traffic)
- Jackson 3 import: `tools.jackson.databind.ObjectMapper` (not `com.fasterxml`)
- `erasableSyntaxOnly: true` in tsconfig — no enums, use union types (`type ReviewStatus = "PENDING" | ...`)
