# Specification: Reviews Plugin

## Goal

Implement a self-contained `reviews` plugin (plugin ID: "reviews", port 3003) for the microkernel Spring Boot platform that lets users submit product reviews and lets admins moderate them, with per-product rating badges and a product-list rating filter — all via the platform SDK, no custom backend Java code.

## User Stories

- As a product visitor, I want to submit a review (name, rating 1–5, title, body) so that other users and admins can see my feedback.
- As a product visitor, I want to see all approved reviews for a product so that I can make an informed purchase decision.
- As an admin, I want to approve or reject pending reviews so that only quality feedback is displayed on the product.
- As a product browser, I want to see a compact rating badge (average + count) on product detail pages so that I can quickly gauge product quality.
- As a product browser, I want to filter the product list by exact rating so that I can find products with a specific rating.

## Core Requirements

1. **Review submission** — ProductReviewsTab provides a form with reviewer name, rating (1–5 select), title, and body textarea; saves as a `plugin_object` (objectType="review") with `status: "PENDING"` bound to `entityType: "PRODUCT"`, `entityId: productId` (string — no cast needed); after save, recalculates the per-product rating aggregate via fetch-compute-write and calls `setData`.
2. **APPROVED review list** — ProductReviewsTab lists only reviews with `status: "APPROVED"` for the current product; form is always visible (even when the list is empty); form collapses and shows a success message after successful submission.
3. **Admin moderation** — ReviewsAdmin loads all reviews once on mount; a client-side status dropdown (All / PENDING / APPROVED / REJECTED) filters the display; each row shows context-sensitive action buttons: PENDING rows show both Approve and Reject, APPROVED rows show only Reject, REJECTED rows show only Approve; after each approve/reject action the aggregate for that product is recalculated using `review.entityId`.
4. **Rating badge** — ProductRatingBadge reads `getData(productId)` on mount; returns `null` while loading and when no data exists; displays "★ {avg} ({count} reviews)" with `tc-badge--success` for avg ≥ 4.0, plain `<span>` for 2.0 ≤ avg < 4.0, `tc-badge--danger` for avg < 2.0; compact padding (`0.5rem 1rem`) for the ~60px iframe slot.
5. **Product list filter** — Manifest-declared filter: `filterKey: "rating"`, `filterType: "number"`, `label: "Rating"`, `priority: 20`; no React route required; platform renders the number input natively with `eq` semantics.
6. **Domain types** — `domain.ts` exports `ReviewStatus` union type, `Review` interface, `RatingSummary` interface, `toReview(PluginObject): Review` mapper, `toRatingSummary(Record<string, unknown>): RatingSummary` mapper.
7. **Backend integration tests** — `ReviewsPluginObjectTests` (5 scenarios) and `ReviewsPluginDataTests` (3 scenarios) in package `pl.devstyle.aj.reviews`, following the established annotation stack and `createAndSave*()` helper pattern.
8. **Plugin registration** — `manifest.json` with four extension points registered via `curl -X PUT http://localhost:8080/api/plugins/reviews/manifest`.

## Visual Design

Based on ASCII mockups in `analysis/ui-mockups.md`. Fidelity level: approximate — components and layout are specified, pixel-level styling is owned by `plugin-ui.css`.

### ReviewsAdmin (menu.main, path="/")

- Root: `<div className="tc-plugin" style={{ padding: "1rem", maxWidth: 900 }}`
- `<h1>Reviews</h1>` then `<p className="tc-error">` if error
- Single `<section className="tc-section">` containing:
  - `<div className="tc-flex">` toolbar with "Status:" label and `<select className="tc-select">` (All / PENDING / APPROVED / REJECTED)
  - `<table className="tc-table">` with 7 columns: Product ID, Reviewer, Rating (stars + raw number), Title, Status, Date, Actions (empty `<th>`)
  - Empty state: `<p>No reviews found.</p>` when filtered list is empty
- Button rules per row: PENDING → Approve (`tc-primary-button`) + Reject (`tc-ghost-button tc-ghost-button--danger`); APPROVED → Reject only; REJECTED → Approve only

### ProductReviewsTab (product.detail.tabs, path="/product-reviews")

- Root: `<div className="tc-plugin" style={{ padding: "1rem", maxWidth: 800 }}`
- `<h2>Reviews</h2>` then `<p className="tc-error">` if error
- Section 1 (`tc-section`): `<table className="tc-table">` with 5 columns (Reviewer, Rating, Title, Status, Date) — no Actions column; empty state `<p>No reviews yet for this product.</p>`
- Section 2 (`tc-section`): `<h3>Submit a Review</h3>` with label+input flex column (label span width: 80); fields: Reviewer (`tc-input`), Rating (`tc-select`, options 1–5), Title (`tc-input`), Body (`<textarea className="tc-input">`); submit row: `<div className="tc-flex">` with `<button className="tc-primary-button">Submit Review</button>`
- Post-submit: hide form, show `<p>Review submitted! Pending approval.</p>` success message

### ProductRatingBadge (product.detail.info, path="/product-rating-badge")

- Loading / no data: `return null`
- Root when data present: `<div className="tc-plugin" style={{ padding: "0.5rem 1rem" }}`
- `<span className="tc-badge tc-badge--success">★ {avg} ({count} reviews)</span>` when avg ≥ 4.0
- `<span>★ {avg} ({count} reviews)</span>` when 2.0 ≤ avg < 4.0
- `<span className="tc-badge tc-badge--danger">★ {avg} ({count} reviews)</span>` when avg < 2.0

## Reusable Components

### Existing Code to Leverage

All config files are direct copies (zero adaptation required):

- `plugins/warehouse/package.json` → `plugins/reviews/package.json` — change `name` to `"reviews-plugin"`, no other changes
- `plugins/warehouse/vite.config.ts` → `plugins/reviews/vite.config.ts` — change port to `3003`
- `plugins/warehouse/tsconfig.json` → `plugins/reviews/tsconfig.json` — identical copy; note `erasableSyntaxOnly: true` prohibits TypeScript enums (use union types)
- `plugins/warehouse/index.html` → `plugins/reviews/index.html` — change `<title>` to "Reviews Plugin"; SDK and `plugin-ui.css` load paths from `localhost:8080` are correct as-is

Page templates (require adaptation):

- `plugins/warehouse/src/pages/WarehousePage.tsx` → base for `ReviewsAdmin.tsx` — provides tc-table CRUD layout, tc-section, tc-flex toolbar, tc-select filter, tc-primary-button / tc-ghost-button--danger per-row actions, tc-error, loading/empty state patterns
- `plugins/warehouse/src/pages/ProductStockTab.tsx` → base for `ProductReviewsTab.tsx` — provides entity-scoped productId context, filtered object list, tc-table
- `plugins/box-size/src/pages/ProductBoxTab.tsx` → provides label+input flex column form pattern (label span with fixed width + tc-input/tc-select); note: ProductBoxTab uses a timed "Saved!" button state, not a form collapse — implement collapse fresh using a `submitted` boolean state that conditionally renders the form vs. the success message
- `plugins/warehouse/src/pages/ProductAvailability.tsx` → base for `ProductRatingBadge.tsx` — provides null-while-loading pattern, tc-badge conditional rendering, compact padding

Test infrastructure template:

- `src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java` → template for annotation stack, `createAndSavePlugin()`, `createAndSaveProduct()`, `createAndSaveCategory()` helper method signatures, MockMvc assertions with `jsonPath()`/Hamcrest

SDK and domain pattern:

- `plugins/warehouse/src/domain.ts` → pattern for `domain.ts` (interface definitions + `PluginObject` mapper functions)
- `plugins/warehouse/src/main.tsx` → pattern for `main.tsx` router-only entry point (no SDK imports in this file)
- `plugins/sdk.ts` → shared type declarations; import path from `plugins/reviews/src/` is `../../sdk`

### New Components Required

All twelve files listed below are new. No existing file is modified.

| File | Justification |
|------|---------------|
| `plugins/reviews/manifest.json` | Plugin-specific identity and 4 extension points; cannot reuse warehouse manifest |
| `plugins/reviews/src/main.tsx` | Reviews-specific routes (`/`, `/product-reviews`, `/product-rating-badge`); router adaption is minimal but required |
| `plugins/reviews/src/domain.ts` | `ReviewStatus`, `Review`, `RatingSummary`, `toReview`, `toRatingSummary` are reviews-specific types with no warehouse equivalent |
| `plugins/reviews/src/pages/ReviewsAdmin.tsx` | Review moderation logic (approve/reject state machine, aggregate recalc via `review.entityId`) has no warehouse analogue |
| `plugins/reviews/src/pages/ProductReviewsTab.tsx` | Dual-section layout (APPROVED-only list + submit form with collapse-after-submit) differs from ProductStockTab (read-only) |
| `plugins/reviews/src/pages/ProductRatingBadge.tsx` | Rating-tier logic (success/plain/danger) and `getData` call differ from ProductAvailability (boolean availability) |
| `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginObjectTests.java` | Reviews-specific object scenarios (entity binding, status filter, rating filter) not covered by existing platform tests |
| `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginDataTests.java` | Reviews-specific pluginData scenarios (aggregate set/read/overwrite, product list filter by rating) |

Config copies (package.json, vite.config.ts, tsconfig.json, index.html) are new files in a new directory but are categorised as direct copies under "Existing Code to Leverage" above.

## Technical Approach

### Storage

- **Individual reviews**: `plugin_objects` table via SDK — `objectType="review"`, `entityType=PRODUCT`, `entityId=<productId as Long>`, JSONB payload: `{ rating, title, body, reviewer, status }`.
- **Rating aggregate**: `products.plugin_data["reviews"]` via `setData` — `{ rating: float (1 decimal), count: int }`. This is the only mechanism that enables `product.list.filters`.
- No database migration required; both storage mechanisms are operational.

### Data Flow

**On review submit (ProductReviewsTab)**:
1. `objects.save("review", crypto.randomUUID(), payload, { entityType: "PRODUCT", entityId: productId })`
2. `objects.list("review", { entityType: "PRODUCT", entityId: productId, filter: "status:eq:APPROVED" })`
3. `avg = sum(ratings) / count`, rounded to 1 decimal
4. `setData(productId, { rating: avg, count: reviews.length })`
5. Collapse form, show success message, re-fetch APPROVED reviews for the list

**On approve/reject (ReviewsAdmin)**:
1. `objects.save("review", review.objectId, { ...review.data, status: newStatus }, { entityType: "PRODUCT", entityId: review.entityId })`
2. `objects.list("review", { entityType: "PRODUCT", entityId: review.entityId, filter: "status:eq:APPROVED" })`
3. `setData(review.entityId, { rating: avg, count: reviews.length })`
4. Re-filter local `allReviews` state (no re-fetch)

### Key Technical Constraints

- `thisPlugin.productId` is a `string`; all SDK calls (`objects.save`, `objects.list`, `getData`, `setData`) accept string keys — no numeric cast needed anywhere.
- `review.entityId` in ReviewsAdmin is also a `string` (per SDK type declarations in `plugins/sdk.ts`) — pass directly to `objects.list` options and `setData` without casting.
- `setData` is full-replace — fetch-compute-write is the only pattern available.
- `erasableSyntaxOnly: true` in tsconfig — use `type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED"` (no TypeScript enum).
- Jackson 3 import: `tools.jackson.databind.ObjectMapper` (not `com.fasterxml`).
- SDK must not be imported in `main.tsx` — only in page component files.
- Race condition on concurrent aggregate updates is an accepted v1 trade-off.

### Manifest JSON

```
{
  "name": "Product Reviews",
  "version": "1.0.0",
  "url": "http://localhost:3003",
  "description": "Product review and rating system",
  "extensionPoints": [
    { "type": "product.detail.tabs", "label": "Reviews", "path": "/product-reviews", "priority": 70 },
    { "type": "product.detail.info", "label": "Rating", "path": "/product-rating-badge", "priority": 5 },
    { "type": "product.list.filters", "label": "Rating", "filterKey": "rating", "filterType": "number", "priority": 20 },
    { "type": "menu.main", "label": "Reviews", "icon": "star", "path": "/", "priority": 90 }
  ]
}
```

Note: `label: "Rating"` (not "Min Rating") — `filterType: "number"` uses `eq` semantics; label must accurately reflect platform capability.

### Plugin Registration Command

```
curl -X PUT http://localhost:8080/api/plugins/reviews/manifest \
  -H "Content-Type: application/json" \
  -d @plugins/reviews/manifest.json
```

## Implementation Guidance

### Testing Approach

Write 2–8 focused tests per step group. Run only the new test classes during development (`ReviewsPluginObjectTests`, `ReviewsPluginDataTests`), not the full suite. Each test class has `@Transactional` for automatic rollback and its own `createAndSave*()` helpers.

**ReviewsPluginObjectTests** (5 tests — target count):
1. `saveReview_withProductBinding_returnsEntityFields` — verify objectType, entityType=PRODUCT, entityId, status=PENDING in response
2. `listReviews_byProduct_returnsOnlyProductReviews` — save two reviews on different products, verify entity-scoped list returns only the targeted product's reviews
3. `listReviews_statusFilter_returnsOnlyApproved` — save PENDING + APPROVED reviews, filter `status:eq:APPROVED`, verify hasSize(1)
4. `listReviews_ratingFilter_returnsAboveThreshold` — save rating=2 + rating=5 reviews, filter `rating:gt:3`, verify only rating=5 returned
5. `listReviews_noFilter_returnsAllStatuses` — save PENDING + APPROVED + REJECTED, list without filter, verify hasSize(3)

**ReviewsPluginDataTests** (3 tests — target count):
1. `setRatingSummary_getData_returnsPersistedValues` — `PUT .../data` with `{ rating: 4.2, count: 15 }`, `GET .../data` returns same values
2. `setRatingSummary_overwritesPreviousData` — set `{ rating: 3.0, count: 5 }`, then set `{ rating: 4.5, count: 10 }`, verify previous values gone
3. `productListFilter_byRating_returnsMatchingProducts` — set rating=4.5 on one product and rating=3.0 on another, `GET /api/products?pluginFilter=reviews:rating:eq:4` verifies correct product returned (or use `eq:4.5` for exact match)

Do not duplicate scenarios already covered in `PluginDataAndObjectsIntegrationTests` (generic save/list/upsert infrastructure is already tested there).

### Standards Compliance

- **Backend Testing** (`.maister/docs/standards/testing/backend-testing.md`): `@Import(TestcontainersConfiguration.class)` + `@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + `@Transactional`; `*Tests` suffix; package-private; `action_condition_expectedResult` method names; `createAndSave*()` helpers with `saveAndFlush()`; `jsonPath()` + Hamcrest; 2–8 tests per class.
- **Frontend Components** (`.maister/docs/standards/frontend/components.md`): Single responsibility per page component; no SDK imports in `main.tsx`.
- **CSS** (`.maister/docs/standards/frontend/css.md`): No custom CSS; use `tc-*` classes from `plugin-ui.css` exclusively; inline `style` only for layout concerns (padding, maxWidth, margins).
- **Minimal Implementation** (`.maister/docs/standards/global/minimal-implementation.md`): No v2 stubs; no speculative abstractions; every method has an immediate caller.
- **Error Handling** (`.maister/docs/standards/global/error-handling.md`): All SDK calls wrapped in `try/catch`; errors shown to user via `<p className="tc-error">`.

## Out of Scope

- Custom backend Java endpoint for rating aggregation (deferred to v2)
- Database migration (not needed — uses existing `plugin_objects` and `products.plugin_data` infrastructure)
- Frontend unit tests (consistent with warehouse/box-size — none exist)
- Auth-backed reviewer identity (reviewer name is self-reported)
- Review deletion UI
- Pagination beyond 1000 reviews per product
- Responsive table behavior

## Success Criteria

1. `npm run dev` starts the reviews plugin on port 3003 without errors.
2. Manifest registered via curl — plugin appears in host sidebar under "Reviews" with a star icon.
3. Submitting a review on a product detail tab saves it as PENDING and updates the pluginData aggregate.
4. ProductRatingBadge displays the correct average and count after reviews are approved.
5. ReviewsAdmin lists all reviews; Approve/Reject buttons update status and recalculate the aggregate.
6. Product list filter "Rating" narrows results when a numeric value is entered.
7. All 8 backend integration tests pass (`ReviewsPluginObjectTests` × 5, `ReviewsPluginDataTests` × 3).
8. No compiler or TypeScript errors (`npm run build` exits 0).
