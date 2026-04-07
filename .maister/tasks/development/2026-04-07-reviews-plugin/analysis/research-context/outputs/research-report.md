# Research Report: Reviews Plugin Design

**Research type**: Mixed (technical + requirements + literature)  
**Date**: 2026-04-07  
**Researcher**: Claude Sonnet 4.6  
**Status**: Complete — implementation-ready

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Research Objectives](#research-objectives)
3. [Methodology](#methodology)
4. [Findings](#findings)
5. [Architecture Decision: Storage Strategy](#architecture-decision-storage-strategy)
6. [Backend Design](#backend-design)
7. [Frontend Design](#frontend-design)
8. [Database Migration](#database-migration)
9. [Test Strategy](#test-strategy)
10. [Known Constraints and Limitations](#known-constraints-and-limitations)
11. [Conclusions](#conclusions)
12. [Next Steps](#next-steps)
13. [Appendices](#appendices)

---

## Executive Summary

The reviews plugin should be designed as follows:

- **Store individual reviews as `plugin_objects`** with `objectType="review"`, entity binding `entityType=PRODUCT`, and a JSONB payload containing `rating`, `title`, `body`, `reviewer`, and `status`. No dedicated database table is needed.
- **Store per-product rating summaries as `pluginData`** (`{ rating: float, count: int }`) to enable the product list filter. This is the only way to drive the `product.list.filters` extension point.
- **Use four extension points**: `product.detail.tabs` (review list + submission form), `product.detail.info` (star rating badge), `product.list.filters` (numeric rating filter), and optionally `menu.main` (moderation page).
- **No custom backend endpoints are required** for the initial implementation. All data operations are achievable via the standard plugin SDK.
- **No new database migration is needed** (unless a dedicated table is chosen as a future optimisation). Migration 008 is available if that decision changes.

---

## Research Objectives

### Primary Research Question

How should a new "reviews" plugin be designed and implemented for the existing microkernel-based Spring Boot platform?

### Sub-Questions

1. Which storage mechanism — `plugin_objects`, `pluginData`, or a dedicated table — best fits the reviews domain?
2. Which extension points are appropriate for a reviews plugin?
3. What manifest JSON is needed?
4. Is any custom backend code (endpoints, entities, migrations) required?
5. What are the specific SDK calls the frontend will make?
6. What integration test classes and scenarios are needed?

### Scope

**Included**: Plugin storage infrastructure, JPA domain patterns, REST conventions, frontend SDK contract, migration conventions, test infrastructure.  
**Excluded**: Third-party review platforms, non-plugin approaches, frontend implementation details beyond SDK usage patterns.

---

## Methodology

- **Research type**: Mixed — codebase analysis + domain modeling + SDK investigation + migration/test pattern analysis
- **Sources**: 2 findings files produced by research gatherers; 15+ source files read directly to fill gaps in missing findings files (domain-patterns, frontend-plugin-sdk)
- **Analysis framework**: Technical Analysis + Requirements Analysis + Best Practice Comparison
- **Primary sources read**: `BaseEntity.java`, `Category.java`, `Product.java`, `CategoryService.java`, `CategoryController.java`, `CreateCategoryRequest.java`, `CategoryResponse.java`, `GlobalExceptionHandler.java`, `PluginObject.java`, `PluginObjectService.java`, `PluginDataService.java`, `PluginDescriptorService.java`, `DbPluginObjectQueryService.java`, `plugins/CLAUDE.md`, `warehouse/manifest.json`, `box-size/manifest.json`

---

## Findings

### Finding F1: plugin_objects Fits the Review Record Model

**Category**: Architecture / Storage  
**Confidence**: High

The `plugin_objects` table provides: a natural key `(pluginId, objectType, objectId)`, entity binding to `PRODUCT`, arbitrary JSONB payload, and JSONB field filtering with 5 operators (`eq`, `gt`, `lt`, `exists`, `bool`). A review stored as `objectType="review"` with `entityType=PRODUCT` and `entityId=productId` is queryable as:

```
thisPlugin.objects.list("review", { entityType: "PRODUCT", entityId: productId })
thisPlugin.objects.list("review", { entityType: "PRODUCT", entityId: productId, filter: "status:eq:APPROVED" })
thisPlugin.objects.list("review", { entityType: "PRODUCT", entityId: productId, filter: "rating:gt:3" })
```

All three queries work with zero additional backend code.

### Finding F2: pluginData is the Only Path to product.list.filters

**Category**: Architecture / Storage  
**Confidence**: High

The `product.list.filters` extension point generates SQL against `products.plugin_data -> '{pluginId}' ->> '{filterKey}'`. This column is populated exclusively by `PluginDataService.setData()`. JSONB data in `plugin_objects.data` cannot drive this filter — the product query service only touches `products.plugin_data`. Therefore a per-product rating summary must be maintained in `pluginData` for the filter to work.

**Implication**: After every review save, the plugin must update `pluginData` with the new computed average:

```typescript
await thisPlugin.setData(productId, { rating: newAvg, count: newCount });
```

### Finding F3: setData is Full Replace

**Category**: Behaviour / Constraint  
**Confidence**: High

`PluginDataService.setData()` overwrites the entire `pluginData[pluginId]` namespace. There is no merge/patch operation. The fetch-compute-write pattern is required: read all reviews, compute new average, write aggregate. For low-traffic admin scenarios this is acceptable.

### Finding F4: Closed EntityType Enum

**Category**: Constraint  
**Confidence**: High

`EntityType` has exactly two values: `PRODUCT` and `CATEGORY`. It cannot be extended by plugins without modifying the platform source. For reviews this is not a constraint — products are the appropriate review subject.

### Finding F5: Hard Limit of 1000 Items

**Category**: Constraint  
**Confidence**: High

`PluginObjectController` caps the `limit` parameter at 1000 with no pagination support. A product with more than 1000 reviews cannot retrieve all of them in a single call. For typical product catalogues this is not a problem; it is a known ceiling for scale.

### Finding F6: Domain Entity Pattern

**Category**: Backend Conventions  
**Confidence**: High

All domain entities must:
- Extend `BaseEntity` (`@MappedSuperclass` providing `Long id` from sequence, `LocalDateTime createdAt` via `@CreatedDate`, `LocalDateTime updatedAt` via `@Version`)
- Use `@SequenceGenerator(name = "base_seq", sequenceName = "{entity}_seq", allocationSize = 1)` on the entity class
- Use `@Getter`, `@Setter`, `@NoArgsConstructor` from Lombok — never `@Data` or `@EqualsAndHashCode`
- Implement `equals`/`hashCode` based on a business key (not the database `id`)
- Use `EnumType.STRING` for all enumerations

If the reviews plugin adds a dedicated entity (e.g., for a custom endpoint), it follows these rules exactly.

### Finding F7: REST Controller Conventions

**Category**: Backend Conventions  
**Confidence**: High

- `@RestController` + `@RequestMapping("/api/...")`
- Constructor injection (no `@Autowired` on fields)
- `@Valid @RequestBody` for request DTOs
- `@ResponseStatus(HttpStatus.CREATED)` on create endpoints
- `@ResponseStatus(HttpStatus.NO_CONTENT)` on delete endpoints
- No `@Transactional` on controllers — transaction ownership is in the service layer

### Finding F8: Error Handling Requires No New Code

**Category**: Backend Conventions  
**Confidence**: High

`GlobalExceptionHandler` already handles: `EntityNotFoundException` (404), `BusinessConflictException` (409), `DataIntegrityViolationException` (409), `MethodArgumentNotValidException` (400 with field errors), `IllegalArgumentException` (400), and uncaught `Exception` (500). Any custom backend endpoint in the reviews plugin uses the same exception types — no new exception handler is needed.

### Finding F9: Manifest Extension Point Schema

**Category**: Frontend / Manifest  
**Confidence**: High (direct observation from reference plugins)

Extension point objects are free-form JSON blobs. The host does not validate their schema. Known working fields by extension point type:

| `type` | Required fields | Optional fields |
|--------|----------------|-----------------|
| `menu.main` | `label`, `path` | `icon` (Lucide name), `priority` |
| `product.detail.tabs` | `label`, `path` | `priority` |
| `product.detail.info` | `label`, `path` | `priority` |
| `product.list.filters` | `label`, `filterKey`, `filterType` | `priority` |

`filterType` values: `"boolean"`, `"string"`, `"number"`.

### Finding F10: Migration Conventions

**Category**: Infrastructure  
**Confidence**: High

- Next number: `008`
- File location: `src/main/resources/db/changelog/2026/`
- Filename: `008-{kebab-description}.yaml`
- changeSet `author`: always `aj`
- Sequence changeSet before table changeSet within the same file
- Every changeSet has an explicit `rollback:` block
- JSONB column type: `JSONB` (no `dbms` qualifier)
- Index naming: `idx_{table}_{purpose}`, FK: `fk_{table}_{column}`, UK: `uk_{table}_{columns_abbrev}`

---

## Architecture Decision: Storage Strategy

### Decision: Hybrid — plugin_objects + pluginData

Individual reviews are stored as `plugin_objects`. Per-product rating summaries are stored as `pluginData`. No dedicated `reviews` table is created.

### Why plugin_objects for Individual Reviews

The `plugin_objects` table provides everything the reviews domain needs:
- Natural key: `(pluginId, objectType="review", objectId=<uuid>)`
- Entity binding: `entityType=PRODUCT`, `entityId=productId` — enables entity-scoped listing
- JSONB data: `{ rating, title, body, reviewer, status }` — stores the full review
- JSONB filtering: `status:eq:APPROVED`, `rating:gt:3` work out of the box
- No schema migration required

A dedicated `reviews` table would only be justified if: (a) review counts per product exceed 1000 regularly, or (b) complex SQL aggregation (e.g., percentile distributions) is required in real time. Neither condition applies to the initial implementation.

### Why pluginData for Rating Summary

The `product.list.filters` extension point is hard-wired to query `products.plugin_data -> 'pluginId' ->> 'filterKey'`. There is no alternative. The summary `{ rating: 4.2, count: 15 }` stored via `setData` gives the platform all it needs to:
- Display the average rating on the product detail info badge (via `getData`)
- Filter product lists by minimum rating (via the manifest `filterKey`/`filterType`)

### Data Flow on Review Submit

```
1. User submits review (rating=5, title="Great", body="...", reviewer="Alice")
2. Frontend calls:
   await thisPlugin.objects.save(
     "review",
     crypto.randomUUID(),                  // objectId — UUID
     { rating: 5, title: "Great", body: "...", reviewer: "Alice", status: "PENDING" },
     { entityType: "PRODUCT", entityId: productId }
   )
3. Frontend fetches all APPROVED reviews to recompute aggregate:
   const reviews = await thisPlugin.objects.list("review", {
     entityType: "PRODUCT",
     entityId: productId,
     filter: "status:eq:APPROVED"
   })
4. Frontend computes:
   const avg = reviews.reduce((s, r) => s + r.data.rating, 0) / reviews.length
5. Frontend updates aggregate:
   await thisPlugin.setData(productId, { rating: avg, count: reviews.length })
```

### Trade-offs Acknowledged

| Trade-off | Accepted? | Rationale |
|-----------|-----------|-----------|
| setData full-replace creates race condition under concurrent saves | Yes for v1 | Low-traffic admin context; no atomic increment available in platform |
| 1000-review limit | Yes for v1 | Typical product catalogue; revisit when evidence shows it is insufficient |
| Client-side aggregation instead of SQL AVG() | Yes for v1 | Avoids custom backend endpoint; sufficient for expected volume |

---

## Backend Design

### Is Custom Backend Code Required?

**No, for the initial implementation.** All CRUD, listing, filtering, and aggregate storage are achievable via the standard plugin object SDK. A custom backend endpoint is deferred.

If a `/api/plugins/reviews/summary` endpoint is added in the future, it would:
- Use `DbPluginObjectQueryService` or a jOOQ query directly
- Compute `AVG(CAST(data->>'rating' AS double))` grouped by `entity_id`
- Return `{ productId, averageRating, reviewCount }`

### Package Structure

```
pl.devstyle.aj.reviews/          (flat — no sub-packages)
  (no Java files needed for v1 — plugin uses SDK exclusively)
```

If a custom endpoint is added later:

```
pl.devstyle.aj.reviews/
  ReviewSummary.java             (record: Long productId, double averageRating, int reviewCount)
  ReviewSummaryController.java   (GET /api/plugins/{pluginId}/reviews/summary)
  DbReviewQueryService.java      (jOOQ aggregation query)
```

### Custom Endpoint Design (deferred, for reference)

```
GET /api/plugins/{pluginId}/reviews/summary?entityId={productId}

Response: { "productId": 42, "averageRating": 4.2, "reviewCount": 15 }
```

This would call `pluginDescriptorService.findEnabledOrThrow(pluginId)` first, following the same guard as all plugin services.

---

## Frontend Design

### Plugin Identity

- **Plugin ID**: `reviews` (valid per `^[a-zA-Z0-9_-]+$`)
- **Base URL**: `http://localhost:3003` (or next available port after warehouse=3001, box-size=3002)
- **Plugin data namespace**: `pluginData["reviews"]`

### Manifest JSON

```json
{
  "name": "Product Reviews",
  "version": "1.0.0",
  "url": "http://localhost:3003",
  "description": "Product review and rating system",
  "extensionPoints": [
    {
      "type": "product.detail.tabs",
      "label": "Reviews",
      "path": "/product-reviews",
      "priority": 70
    },
    {
      "type": "product.detail.info",
      "label": "Rating",
      "path": "/product-rating-badge",
      "priority": 5
    },
    {
      "type": "product.list.filters",
      "label": "Min Rating",
      "filterKey": "rating",
      "filterType": "number",
      "priority": 20
    },
    {
      "type": "menu.main",
      "label": "Reviews",
      "icon": "star",
      "path": "/",
      "priority": 90
    }
  ]
}
```

### Router Setup (main.tsx)

```tsx
<Routes>
  <Route path="/"                    element={<ReviewsAdmin />} />
  <Route path="/product-reviews"     element={<ProductReviewsTab />} />
  <Route path="/product-rating-badge" element={<ProductRatingBadge />} />
</Routes>
```

`product.list.filters` requires no route — the host renders the control natively from manifest metadata.

### Extension Point: product.detail.tabs (/product-reviews)

Context: `PRODUCT_DETAIL`. Read `thisPlugin.productId` for the current product.

**SDK calls**:

```typescript
// Load reviews on mount
const reviews = await thisPlugin.objects.list("review", {
  entityType: "PRODUCT",
  entityId: Number(thisPlugin.productId)
})

// Submit a review
await thisPlugin.objects.save(
  "review",
  crypto.randomUUID(),
  { rating, title, body, reviewer: reviewerName, status: "PENDING" },
  { entityType: "PRODUCT", entityId: Number(thisPlugin.productId) }
)
// then recompute and setData for aggregate
```

**UI**: Table of reviews (tc-table), new review form (tc-input, tc-select for rating, tc-primary-button), error display (tc-error).

### Extension Point: product.detail.info (/product-rating-badge)

Context: `PRODUCT_DETAIL`. Compact badge (~60px height).

**SDK call**:

```typescript
const data = await thisPlugin.getData(thisPlugin.productId)
// data: { rating: 4.2, count: 15 } or empty
```

**UI**: Star rating display using `tc-badge tc-badge--success` or plain text. No input — read-only.

### Extension Point: product.list.filters

No SDK call needed. The host renders a number input control based on:
- `filterKey: "rating"` → queries `plugin_data -> 'reviews' ->> 'rating'`
- `filterType: "number"` → renders number input, uses `eq` operator

When the user enters a minimum value, the host filters products where `rating >= value`. Note: the `number` filterType uses `eq` by default. For a "minimum rating" semantic, the frontend page should communicate that this is an exact match filter, or a custom backend approach using `gt` would require the host to support a `filterOperator` manifest field (not currently implemented).

**Alternative**: Use `filterType: "string"` with user-typed exact rating value `"4"` — simpler and works with current platform.

### Extension Point: menu.main (/)

Context: `RENDER`. No productId.

**SDK calls**:

```typescript
// Fetch all PENDING reviews across all products
const pendingReviews = await thisPlugin.objects.list("review", {
  filter: "status:eq:PENDING"
})

// Approve a review
await thisPlugin.objects.save(
  "review",
  review.objectId,
  { ...review.data, status: "APPROVED" },
  { entityType: "PRODUCT", entityId: review.entityId }
)
// then recompute pluginData aggregate for the product
```

**UI**: Table of pending reviews with approve/reject actions. Uses `tc-plugin`, `tc-table`, `tc-primary-button`, `tc-ghost-button--danger`.

### Domain Types (src/domain.ts)

```typescript
import type { PluginObject } from "../../sdk";

export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface Review {
  objectId: string;
  productId: number;
  rating: number;        // 1–5 integer
  title: string;
  body: string;
  reviewer: string;
  status: ReviewStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RatingSummary {
  rating: number;
  count: number;
}

export function toReview(obj: PluginObject): Review {
  return {
    objectId: obj.objectId,
    productId: obj.entityId as number,
    rating: obj.data.rating as number,
    title: obj.data.title as string,
    body: obj.data.body as string,
    reviewer: obj.data.reviewer as string,
    status: obj.data.status as ReviewStatus,
    createdAt: obj.createdAt,
    updatedAt: obj.updatedAt,
  };
}

export function toRatingSummary(data: Record<string, unknown>): RatingSummary {
  return {
    rating: (data.rating as number) ?? 0,
    count: (data.count as number) ?? 0,
  };
}
```

---

## Database Migration

### Decision: No Migration Needed for Initial Implementation

The reviews plugin stores all data via the `plugin_objects` table (which already exists as migration 006/007) and the `products.plugin_data` column (migration 005). No new tables are needed.

### If a Dedicated Table Is Chosen (deferred)

If the team decides to add a dedicated reviews table for performance or query reasons, the migration would be:

**File**: `src/main/resources/db/changelog/2026/008-create-reviews-table.yaml`

The complete template is available in `analysis/findings/migration-and-testing-findings.md` section 1.8. Key points:

- changeSet IDs: `008-create-review-seq` (sequence first), `008-create-reviews-table` (table second)
- Author: `aj`
- Sequence: `review_seq`, startValue=1, incrementBy=1
- Columns: `id` (BIGINT PK), `plugin_id` (VARCHAR(255) NOT NULL, FK to plugins), `entity_type` (VARCHAR(50) NOT NULL), `entity_id` (BIGINT NOT NULL), `rating` (INTEGER NOT NULL), `title` (VARCHAR(255)), `body` (TEXT), `reviewer` (VARCHAR(255)), `status` (VARCHAR(50) NOT NULL), `data` (JSONB NOT NULL), `created_at` (TIMESTAMP NOT NULL), `updated_at` (TIMESTAMP NOT NULL)
- Indexes: `idx_reviews_entity (entity_type, entity_id)`, `idx_reviews_plugin_entity (plugin_id, entity_type, entity_id)`
- FK: `fk_reviews_plugin_id`
- Rollback: `dropTable` on table changeSet, `dropSequence` on sequence changeSet

---

## Test Strategy

### Test Class: ReviewsIntegrationTests

**Package**: `pl.devstyle.aj.reviews` (same package as production code — package-private class)  
**File**: `src/test/java/pl/devstyle/aj/reviews/ReviewsIntegrationTests.java`

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsIntegrationTests { ... }
```

Note: `tools.jackson.databind.ObjectMapper` (Jackson 3, Spring Boot 4) — not `com.fasterxml`.

### If No Custom Backend Endpoints Are Added

Since the initial implementation uses only the platform SDK (no custom Java code), there is no backend code to integration-test in the reviews plugin package. The platform's own test coverage (`PluginObjectApiAndFilterTests`, `PluginObjectEntityBindingTests`, `PluginDataAndObjectsIntegrationTests`) already covers the infrastructure.

The reviews plugin's backend test strategy therefore focuses on verifying platform behaviour under reviews-specific scenarios, using the plugin object API directly:

### Test Scenarios

| Test class | Scenario | Method name | Notes |
|-----------|----------|-------------|-------|
| `ReviewsPluginObjectTests` | Save review with PRODUCT entity binding | `saveReview_withProductBinding_returnsEntityFields` | Verify objectType, entityType=PRODUCT, entityId |
| `ReviewsPluginObjectTests` | List reviews for a product | `listReviews_byProduct_returnsOnlyProductReviews` | Verify entity-scoped listing |
| `ReviewsPluginObjectTests` | Filter reviews by status | `listReviews_statusFilter_returnsOnlyApproved` | Uses filter="status:eq:APPROVED" |
| `ReviewsPluginObjectTests` | Filter reviews by rating | `listReviews_ratingFilter_returnsAboveThreshold` | Uses filter="rating:gt:3" |
| `ReviewsPluginObjectTests` | List all reviews for product regardless of status | `listReviews_noFilter_returnsAllStatuses` | Baseline list |
| `ReviewsPluginDataTests` | Set and read rating summary | `setRatingSummary_getData_returnsPersistedValues` | Verify getData returns what setData wrote |
| `ReviewsPluginDataTests` | setData full-replace behaviour | `setRatingSummary_overwritesPreviousData` | Verify previous values are gone after setData |
| `ReviewsPluginDataTests` | Rating summary visible via product list filter | `productListFilter_byRating_returnsMatchingProducts` | Verify pluginFilter=reviews:rating:gt:3 works |

### Helper Methods (in test class)

```java
private PluginDescriptor createAndSaveReviewsPlugin() { ... }  // pluginId="reviews"
private PluginObject createAndSaveReview(String pluginId, Long productId, int rating, String status) { ... }
private Product createAndSaveProduct(String name, Category category) { ... }
private Category createAndSaveCategory(String name) { ... }
```

### Complete Test Class Skeleton

```java
package pl.devstyle.aj.reviews;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.core.plugin.PluginDescriptor;
import pl.devstyle.aj.core.plugin.PluginDescriptorRepository;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsPluginObjectTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PluginDescriptorRepository pluginDescriptorRepository;

    private PluginDescriptor createAndSavePlugin() {
        var plugin = new PluginDescriptor();
        plugin.setId("reviews");
        plugin.setName("Product Reviews");
        plugin.setVersion("1.0.0");
        plugin.setUrl("http://localhost:3003");
        plugin.setEnabled(true);
        plugin.setManifest(Map.of("name", "Product Reviews", "version", "1.0.0"));
        return pluginDescriptorRepository.saveAndFlush(plugin);
    }

    @Test
    void saveReview_withProductBinding_returnsEntityFields() throws Exception {
        createAndSavePlugin();
        var data = Map.of("rating", 5, "title", "Great", "body", "Excellent!", "reviewer", "Alice", "status", "PENDING");

        mockMvc.perform(put("/api/plugins/reviews/objects/review/rev-001?entityType=PRODUCT&entityId=42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("review"))
                .andExpect(jsonPath("$.entityType").value("PRODUCT"))
                .andExpect(jsonPath("$.entityId").value(42))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void listReviews_statusFilter_returnsOnlyApproved() throws Exception {
        createAndSavePlugin();
        // save PENDING review
        // save APPROVED review
        // list with filter=status:eq:APPROVED
        // expect hasSize(1) with status=APPROVED
    }
}
```

---

## Known Constraints and Limitations

| Constraint | Impact | Workaround |
|-----------|--------|-----------|
| `EntityType` enum is closed (PRODUCT, CATEGORY only) | Low — products are the natural subject | None needed |
| Hard limit of 1000 items per listing call | Medium — cap on reviews per product | Acceptable for v1; add custom pagination endpoint if exceeded |
| Single JSONB filter per plugin_objects request | Low — status and rating filters cannot be combined in one call | Filter by status first (smaller set), filter by rating on client side |
| `setData` is full replace — no atomic increment | Low for low-traffic | Fetch-compute-write pattern; revisit if concurrency is a concern |
| `product.list.filters` with `filterType: "number"` uses `eq` operator | Medium — cannot do "minimum rating" natively | Use `filterType: "string"` with exact match, or accept eq-only filtering for v1 |
| `PluginData` is product-only (no category equivalent) | None for reviews | Reviews are product-scoped; no gap |
| No cascade delete — orphaned plugin_objects when plugin is deleted | Low | Documented platform behaviour; acceptable for admin-managed plugins |
| Reviewer identity has no auth backing | Low — reviewer name is self-reported | Acceptable for internal admin-facing reviews plugin |

---

## Conclusions

### Direct Answer to the Research Question

**How should the reviews plugin be designed?**

1. **No dedicated database table.** Use `plugin_objects` (objectType="review", entity binding to PRODUCT) for individual reviews. The existing infrastructure is fully capable.

2. **Maintain a rating aggregate in pluginData.** After every review save/update/delete, recompute and write `{ rating: float, count: int }` via `setData(productId, aggregate)`. This is the only mechanism that enables the product list rating filter.

3. **Use four extension points**: `product.detail.tabs`, `product.detail.info`, `product.list.filters`, and `menu.main`.

4. **No custom backend Java code for v1.** All data operations go through the standard platform SDK. If aggregation performance becomes an issue, add a jOOQ-based `DbReviewQueryService` and a custom summary controller endpoint.

5. **Test via the existing plugin object infrastructure.** Test classes live in `pl.devstyle.aj.reviews`, follow established annotations, and test the reviews-specific usage of `plugin_objects` and `pluginData` APIs.

### Confidence Assessment

Overall confidence: **High**. All conclusions are based on direct code evidence from the platform source. The two missing findings files (domain-patterns, frontend-plugin-sdk) were compensated by reading primary sources directly. No speculation was required.

---

## Next Steps

To start implementation, invoke the development workflow:

```
/maister:work
```

The implementation plan should include, in order:

1. **Create plugin directory** `plugins/reviews/` from warehouse template
2. **Write manifest.json** (see Frontend Design section above)
3. **Register plugin** with `curl -X PUT http://localhost:8080/api/plugins/reviews/manifest -H "Content-Type: application/json" -d @manifest.json`
4. **Implement `src/domain.ts`** with `Review`, `RatingSummary`, `toReview`, `toRatingSummary`
5. **Implement `src/pages/ProductRatingBadge.tsx`** — reads pluginData, displays average rating
6. **Implement `src/pages/ProductReviewsTab.tsx`** — lists reviews, submit form, aggregate update on save
7. **Implement `src/pages/ReviewsAdmin.tsx`** — pending review moderation with approve/reject
8. **Write integration tests** in `src/test/java/pl/devstyle/aj/reviews/` covering the scenarios in the Test Strategy section

No backend Java code needs to be written before frontend work can begin — the SDK provides all required operations.

---

## Appendices

### Appendix A: Source Files Consulted

**Findings files**:
- `.maister/tasks/research/2026-04-07-reviews-plugin/analysis/findings/plugin-architecture-findings.md`
- `.maister/tasks/research/2026-04-07-reviews-plugin/analysis/findings/migration-and-testing-findings.md`

**Platform source files**:
- `src/main/java/pl/devstyle/aj/core/BaseEntity.java`
- `src/main/java/pl/devstyle/aj/category/Category.java`
- `src/main/java/pl/devstyle/aj/category/CategoryController.java`
- `src/main/java/pl/devstyle/aj/category/CategoryService.java`
- `src/main/java/pl/devstyle/aj/category/CreateCategoryRequest.java`
- `src/main/java/pl/devstyle/aj/category/CategoryResponse.java`
- `src/main/java/pl/devstyle/aj/product/Product.java`
- `src/main/java/pl/devstyle/aj/core/error/GlobalExceptionHandler.java`
- `src/main/java/pl/devstyle/aj/core/plugin/` (all files, via plugin-architecture-findings.md)
- `plugins/CLAUDE.md`
- `plugins/warehouse/manifest.json`
- `plugins/box-size/manifest.json`

**Planning documents**:
- `.maister/tasks/research/2026-04-07-reviews-plugin/planning/research-plan.md`
- `.maister/tasks/research/2026-04-07-reviews-plugin/planning/research-brief.md`

### Appendix B: Key Platform Constraints Summary

| Constraint | Value |
|-----------|-------|
| EntityType values | PRODUCT, CATEGORY |
| Max plugin_objects per list call | 1000 (hard cap) |
| JSONB filter count per request | 1 (single filter only on plugin objects API) |
| JSONB filter operators | eq, gt, lt, exists, bool |
| JSONB path depth | Top-level keys only (no nested path support) |
| pluginData scope | Products only (no category pluginData) |
| setData semantics | Full replace of plugin namespace |
| Cascade delete on plugin_objects | None — orphaned rows remain on plugin delete |
| Plugin ID pattern | `^[a-zA-Z0-9_-]+$` |
| Next migration number | 008 |

### Appendix C: SDK Quick Reference for Reviews Plugin

```typescript
const sdk = getSDK();
const { thisPlugin } = sdk;

// Save a new review
await thisPlugin.objects.save(
  "review",
  crypto.randomUUID(),
  { rating: 5, title: "...", body: "...", reviewer: "Alice", status: "PENDING" },
  { entityType: "PRODUCT", entityId: Number(thisPlugin.productId) }
)

// List all reviews for current product
const all = await thisPlugin.objects.list("review", {
  entityType: "PRODUCT", entityId: Number(thisPlugin.productId)
})

// List approved reviews only
const approved = await thisPlugin.objects.list("review", {
  entityType: "PRODUCT", entityId: Number(thisPlugin.productId),
  filter: "status:eq:APPROVED"
})

// Get rating summary for current product
const summary = await thisPlugin.getData(thisPlugin.productId)

// Update rating summary after new review
const avg = approved.reduce((s, r) => s + (r.data.rating as number), 0) / approved.length
await thisPlugin.setData(thisPlugin.productId, { rating: avg, count: approved.length })

// Approve a review (admin page)
await thisPlugin.objects.save(
  "review",
  review.objectId,
  { ...review.data, status: "APPROVED" },
  { entityType: "PRODUCT", entityId: review.entityId }
)
```
