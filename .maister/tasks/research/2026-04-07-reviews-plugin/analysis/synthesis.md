# Synthesis: Reviews Plugin Design

## Research Question

How should a new "reviews" plugin be designed and implemented for the existing microkernel-based Spring Boot platform?

---

## Executive Summary

The platform provides two storage primitives that together satisfy the full reviews domain without any architectural gaps. Individual review records map cleanly to `plugin_objects` (objectType="review", entity binding to PRODUCT), and per-product rating summaries map to `pluginData` (stored as `{ rating, count }` in `products.plugin_data` under the plugin's namespace). This hybrid is not a workaround — it is the pattern the platform was designed for, as demonstrated by the warehouse plugin (objects for warehouses/stock) and the box-size plugin (pluginData for per-product dimensions).

The `EntityType` enum being closed to `{PRODUCT, CATEGORY}` is not a constraint for reviews — products are the natural subject. The 1000-item hard limit on listing is a real boundary that means paginated review history pages are not possible via the current SDK; the design must either live within that limit or accept a custom backend endpoint for unbounded queries.

Two findings files from the research plan were not gathered (`domain-patterns-findings.md`, `frontend-plugin-sdk-findings.md`). This synthesis incorporates those knowledge areas directly from primary source reading of the codebase and SDK documentation.

---

## Cross-Source Analysis

### Validated Findings (confirmed by multiple sources)

| Finding | Sources | Confidence |
|---------|---------|------------|
| Individual records with entity binding → `plugin_objects` | PluginObjectService, warehouse plugin stock pattern, SDK CLAUDE.md | High |
| Per-product aggregates → `pluginData` | PluginDataService, box-size plugin getData/setData pattern, SDK CLAUDE.md | High |
| `product.list.filters` requires `filterKey` in `pluginData` (not `plugin_objects`) | PluginController manifest, DbProductQueryService pluginFilter, SDK CLAUDE.md | High |
| Next migration number is 008 | migration-and-testing-findings.md, directory listing of 001-007 files | High |
| `EntityType` enum is closed: PRODUCT and CATEGORY only | EntityType.java, plugin-architecture-findings.md | High |
| Test class annotations: `@Import`, `@SpringBootTest(MOCK)`, `@AutoConfigureMockMvc`, `@Transactional` | migration-and-testing-findings.md sourced from 3 test files | High |
| ObjectMapper import is `tools.jackson.databind` (Jackson 3) | migration-and-testing-findings.md | High |
| `setData` is a full replace, not merge | PluginDataService.java, SDK CLAUDE.md | High |
| No offset/cursor pagination on plugin objects (limit=1000 cap) | PluginObjectController.java, plugin-architecture-findings.md | High |
| Entity uses BaseEntity (`@MappedSuperclass`) with SEQUENCE pk, `@Version` updatedAt | BaseEntity.java, Category.java, Product.java | High |
| Response DTOs use static factory `from()` method on a record | CategoryResponse, ProductResponse pattern | High |
| Validation via `@NotBlank`/`@Size` on request records with `@Valid` at controller | CreateCategoryRequest, controller `@Valid @RequestBody` | High |
| JSONB filter: single-level keys only, 5 operators (eq, gt, lt, exists, bool) | DbPluginObjectQueryService, plugin-architecture-findings.md | High |
| Manifest `filterKey` maps to `pluginData->'pluginId'->>'filterKey'` in SQL | DbProductQueryService, SDK CLAUDE.md | High |

### Contradictions and Gaps

| Gap | Impact | Resolution |
|-----|--------|------------|
| `domain-patterns-findings.md` was not produced | Medium | Resolved: primary sources read directly (BaseEntity, Category, Product, CategoryService, CategoryController, CreateCategoryRequest, CategoryResponse, GlobalExceptionHandler) |
| `frontend-plugin-sdk-findings.md` was not produced | Medium | Resolved: `plugins/CLAUDE.md` contains the complete canonical SDK contract; both reference manifests read |
| Rating aggregation cannot be done in a single SDK call | High | Resolution: store pre-computed `{ rating, count }` in pluginData, update after each review save |
| No rating filter type exists in `product.list.filters` (only boolean/string/number) | Medium | `number` filterType + JSONB `gt`/`lt` operators covers numeric rating range; `filterKey: "rating"` with `filterType: "number"` works |

---

## Patterns and Themes

### Pattern 1: Hybrid Storage (plugin_objects + pluginData)

**Type**: Architectural  
**Description**: Plugins that need per-entity aggregates AND a collection of records use both storage mechanisms. Individual records go to `plugin_objects` with entity binding; the aggregate summary goes to `pluginData` so it can be filtered in product listings.  
**Evidence**: Warehouse plugin — stock entries as `plugin_objects`, no aggregate needed; box-size plugin — single JSONB blob per product via `setData`, no collection needed. Reviews is the first case needing both.  
**Prevalence**: Designed-for pattern, not yet exercised by existing plugins  
**Quality**: Platform primitives are both mature and fully tested

### Pattern 2: Flat Package Structure per Domain

**Type**: Organizational  
**Description**: Each domain lives in a single package with no sub-packages: entity, repository, service, controller, request records, response records, and exception classes all in `pl.devstyle.aj.{domain}/`.  
**Evidence**: `pl.devstyle.aj.category.*`, `pl.devstyle.aj.product.*` — 7-9 files each, flat structure  
**Prevalence**: 100% of existing feature packages  
**Quality**: Established, enforced by convention

### Pattern 3: Record-Based DTOs with Static Factory

**Type**: Design  
**Description**: All request and response DTOs are Java records. Response records carry a `static from(Entity)` factory method. Request records use JSR-303 annotations inline.  
**Evidence**: `CategoryResponse.from(category)`, `CreateCategoryRequest` record with `@NotBlank @Size`  
**Prevalence**: 100% of existing domain DTOs  
**Quality**: Established, consistent

### Pattern 4: Service Layer Transaction Ownership

**Type**: Implementation  
**Description**: Services own `@Transactional` annotations. Controllers are annotation-free. Read operations are `@Transactional(readOnly = true)`. Write operations are `@Transactional`. Services catch `DataIntegrityViolationException` when needed (e.g., FK constraint on delete) and rethrow typed exceptions.  
**Evidence**: `CategoryService`, `PluginDescriptorService`  
**Prevalence**: 100% of existing services  
**Quality**: Established

### Pattern 5: Custom Exception Types + Centralized Handler

**Type**: Implementation  
**Description**: Domain exceptions extend `EntityNotFoundException` or `BusinessConflictException`. `GlobalExceptionHandler` maps them to RFC-7807-style `ErrorResponse` with `status`, `error`, `message`, `fieldErrors`, `timestamp`. New plugins should use the same exception types — no new exception handler needed.  
**Evidence**: `GlobalExceptionHandler.java`, `EntityNotFoundException`, `CategoryHasProductsException`  
**Prevalence**: Platform-wide; applies to all plugins that use custom backend endpoints  
**Quality**: Established

### Pattern 6: Manifest-Driven Extension Points

**Type**: Architectural  
**Description**: All UI integration is declared in the manifest JSON. The host reads `extensionPoints` and renders native controls (`product.list.filters`) or iframes. Plugin code does not register handlers — it exposes routes matching manifest `path` fields.  
**Evidence**: Both existing manifests, SDK CLAUDE.md  
**Prevalence**: 100% of existing plugins  
**Quality**: Established

---

## Key Insights

### Insight 1: Rating Filter Requires pluginData, Not plugin_objects

**Evidence**: The `product.list.filters` extension point works by generating SQL against `products.plugin_data -> 'pluginId' ->> 'filterKey'`. This column is populated by `PluginDataService.setData()`. JSONB filters against `plugin_objects.data` cannot drive the product list filter — those are two different tables.  
**Implication**: The reviews plugin must call `thisPlugin.setData(productId, { rating: avg, count: n })` after every review save or update to keep the filter-ready aggregate fresh.  
**Confidence**: High

### Insight 2: No Dedicated Reviews Table Needed

**Evidence**: `plugin_objects` stores arbitrary JSONB per review, supports entity binding to PRODUCT, supports JSONB field filters (rating:gt:3), and supports listing all reviews for a product. The only limitation is the 1000-row cap. For a typical product catalogue this is not a constraint.  
**Implication**: Migration 008 is not required if the reviews plugin stores data exclusively via the platform SDK. A dedicated `reviews` table would only be justified if: (a) review counts per product routinely exceed 1000, or (b) complex SQL aggregation is needed in real time.  
**Confidence**: High (confirmed by platform capability analysis)

### Insight 3: Rating Aggregation Must Be Computed Client-Side or Post-Save

**Evidence**: There is no SQL aggregation endpoint in the platform SDK. `hostApp.fetch` can call arbitrary `/api/` endpoints, but no reviews aggregation endpoint exists. The `getData` call returns whatever was last written by `setData`.  
**Implication**: The frontend computes the new average after fetching all reviews, then calls `setData` to persist the aggregate. Alternatively, a custom backend endpoint at `/api/plugins/{pluginId}/reviews/summary` could perform a `CAST(data->>'rating' AS double)` average via jOOQ — but this requires custom backend code beyond the plugin object SDK.  
**Confidence**: High (client-side computation is sufficient for small review counts; custom endpoint needed only for high-volume scenarios)

### Insight 4: objectId Strategy for Reviews

**Evidence**: `plugin_objects` unique constraint is on `(pluginId, objectType, objectId)`. Reviews do not have a natural short key. Using a UUID as objectId (e.g., `rev-${uuid}`) avoids collisions and allows upsert semantics to function correctly.  
**Implication**: `thisPlugin.objects.save("review", reviewId, { rating, title, body, reviewer, status }, { entityType: "PRODUCT", entityId: productId })` is the canonical save call.  
**Confidence**: High

### Insight 5: Review Status Filter Works Natively

**Evidence**: `filter: "status:eq:APPROVED"` is a valid SDK filter expression for `plugin_objects.data`. The `eq` operator generates parameterized SQL.  
**Implication**: Listing only approved reviews is possible without a custom backend endpoint: `thisPlugin.objects.list("review", { entityType: "PRODUCT", entityId: productId, filter: "status:eq:APPROVED" })`.  
**Confidence**: High

### Insight 6: setData Full-Replace Requires Read-Before-Write for Aggregate Update

**Evidence**: `setData` replaces the entire namespace — it does not merge. If a concurrent save sets `{ rating: 4.0, count: 10 }` and another sets `{ rating: 4.2, count: 10 }` simultaneously, one will overwrite the other.  
**Implication**: The aggregate update (after saving a review) must fetch-then-compute-then-write. For a single-user admin plugin this is acceptable. For high-concurrency public review submission, optimistic locking concerns apply. Current platform has no atomic increment primitive.  
**Confidence**: High (limitation is a known platform constraint)

---

## Relationships and Dependencies

```
Reviews Plugin
├── Backend (plugin_objects)
│   ├── Reads/writes via PluginObjectController API
│   ├── Each review bound to: EntityType.PRODUCT + productId
│   └── JSONB data shape: { rating, title, body, reviewer, status }
│
├── Backend (pluginData)
│   ├── Reads/writes via PluginDataController API
│   └── Per-product JSONB shape: { rating: float, count: int }
│
├── Frontend Extension Points
│   ├── product.detail.tabs → path: /product-reviews → ReviewsTab component
│   │   └── Lists reviews for current productId, shows review form
│   ├── product.detail.info → path: /product-rating-badge → RatingBadge component
│   │   └── Calls getData(productId) → renders star rating
│   ├── product.list.filters → filterKey: "rating", filterType: "number"
│   │   └── Host renders native number filter; queries pluginData->'reviews'->>'rating'
│   └── menu.main (optional) → path: / → ReviewsAdmin component
│       └── Lists all pending reviews across all products
│
└── Data Flow
    Review submitted → objects.save("review", uuid, {...}, {PRODUCT, productId})
                    → objects.list("review", {PRODUCT, productId}) [fetch all for product]
                    → compute new avg + count
                    → setData(productId, { rating: newAvg, count: newCount })
```

---

## Gaps and Uncertainties

| Item | Type | Notes |
|------|------|-------|
| Rating aggregation under concurrent submissions | Unresolved | Platform has no atomic read-modify-write for pluginData; acceptable for low-traffic admin scenarios |
| Review count > 1000 per product | Future constraint | 1000-item limit is a hard cap; if exceeded, oldest reviews become unreachable without custom pagination |
| Reviewer identity | Unspecified | Platform has no user/auth concept; reviewer name must be passed by the frontend as part of the review payload |
| Review approval workflow | Optional | `status` field in JSONB covers PENDING/APPROVED/REJECTED; no workflow engine exists |
| Custom summary endpoint | Not needed initially | Client-side aggregation is sufficient; add custom endpoint only if performance evidence demands it |

---

## Synthesis by Framework (Mixed Research)

### Technical Analysis

**What exists**: Full plugin object storage (create/read/update/delete with JSONB filter + entity binding) and per-product embedded JSONB storage (getData/setData), all tested and battle-hardened in the platform core.

**Storage fit**: The reviews domain maps cleanly. Individual reviews → plugin_objects. Aggregate summary → pluginData. Rating filter on product list → pluginData via manifest filterKey. No schema migration needed.

**Data flows**: Review creation triggers two SDK calls (objects.save + setData). Review listing is a single objects.list call with entity binding and optional status filter.

### Requirements Analysis

**Stated**: Support product reviews with ratings and text. Integrate into product detail and product list views.

**Implicit**: Moderation (status field), aggregate display (star rating badge), filterable product list.

**Constraints**: Must use existing plugin infrastructure. No new first-class tables unless justified (and the analysis shows they are not justified).

### Best Practice Comparison

**Hybrid storage pattern**: Matches the CQRS principle of separating the write model (individual review records in plugin_objects) from the read/query model (pre-computed aggregate in pluginData). This is an appropriate trade-off given platform primitives.

**No dedicated table**: Follows the minimal implementation standard — build only what is needed. The plugin_objects table is sufficient. Adding a dedicated reviews table would add migration complexity for no additional capability within the current platform.

---

## Conclusions

### Primary Conclusions

1. **Storage: hybrid is correct.** Individual reviews in `plugin_objects` (objectType="review"), aggregate in `pluginData`. No migration 008 is needed unless a dedicated table is explicitly chosen.

2. **No custom backend endpoints required for initial implementation.** All CRUD, listing, filtering, and aggregate storage are achievable via the standard SDK. A custom summary endpoint is deferred.

3. **Four extension points are recommended**: `product.detail.tabs` (review list + submission form), `product.detail.info` (star rating badge), `product.list.filters` (minimum rating filter), and optionally `menu.main` (moderation admin page).

4. **pluginId must be `reviews`**. Conforms to `^[a-zA-Z0-9_-]+$`. The pluginData namespace key and all SDK calls will use this ID.

### Secondary Conclusions

5. The `status` field in review JSONB enables approval workflow with no additional infrastructure.

6. setData full-replace semantics require a fetch-compute-write sequence for aggregate updates — acceptable for expected load.

7. Test class `ReviewsIntegrationTests` in package `pl.devstyle.aj.reviews` follows established patterns directly.
