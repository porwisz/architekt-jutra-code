# Codebase Analysis Report

**Date**: 2026-04-07
**Task**: Implement reviews plugin for microkernel Spring Boot platform
**Risk Level**: Low
**Complexity**: Simple

---

## Summary

The codebase contains two complete reference plugin implementations (warehouse and box-size) plus a shared SDK that fully cover the patterns needed for the reviews plugin. All frontend infrastructure is established and well-documented; no custom backend Java code is required for v1 because the platform's generic plugin_objects and pluginData APIs already handle the data layer. Risk is low — this is a greenfield directory creation following a proven, duplicable pattern.

---

## Key Files

### Primary Templates
- `plugins/CLAUDE.md` — Authoritative plugin conventions guide (SDK loading, tc-* CSS classes, extension points, full SDK API reference)
- `plugins/sdk.ts` — Shared TypeScript type declarations; referenced by all plugins via `../../sdk`
- `plugins/warehouse/manifest.json` — Full-featured manifest with all 4 extension point types
- `plugins/warehouse/src/main.tsx` — Router-only entry point pattern (no SDK imports)
- `plugins/warehouse/src/domain.ts` — Domain type definitions + mapper functions pattern
- `plugins/warehouse/src/pages/WarehousePage.tsx` — **Template for ReviewsAdmin** (CRUD, tc-table, tc-primary-button, tc-error)
- `plugins/warehouse/src/pages/ProductStockTab.tsx` — **Template for ProductReviewsTab** (productId context, entity-filtered list)
- `plugins/warehouse/src/pages/ProductAvailability.tsx` — **Template for ProductRatingBadge** (compact ~60px, null-while-loading, tc-badge)
- `plugins/warehouse/vite.config.ts` — Direct copy with port=3003
- `plugins/warehouse/package.json` — Direct copy with name="reviews"
- `plugins/warehouse/tsconfig.json` — Identical copy
- `plugins/warehouse/index.html` — Direct copy (loads plugin-sdk.js + plugin-ui.css from localhost:8080)

### Related
- `plugins/box-size/manifest.json` — Minimal manifest for contrast
- `plugins/box-size/src/pages/ProductBoxTab.tsx` — Second product tab example
- `plugins/box-size/src/pages/ProductBoxBadge.tsx` — Second compact badge example
- `src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java` — Integration test template

---

## Plugin Directory Structure (New)

```
plugins/reviews/
├── index.html                    # Copy from warehouse, update title
├── manifest.json                 # 4 extension points (see below)
├── package.json                  # Copy from warehouse, name="reviews"
├── tsconfig.json                 # Identical copy from warehouse
├── vite.config.ts                # Copy from warehouse, port=3003
└── src/
    ├── main.tsx                  # Router: 3 routes matching manifest paths
    ├── domain.ts                 # ReviewStatus, Review, RatingSummary, toReview(), toRatingSummary()
    └── pages/
        ├── ReviewsAdmin.tsx      # menu.main — full CRUD, modeled on WarehousePage
        ├── ProductReviewsTab.tsx # product.detail.tabs — modeled on ProductStockTab
        └── ProductRatingBadge.tsx# product.detail.info — modeled on ProductAvailability
```

---

## Manifest Design

```json
{
  "name": "Product Reviews",
  "version": "1.0.0",
  "url": "http://localhost:3003",
  "description": "Product review and rating system",
  "extensionPoints": [
    { "type": "product.detail.tabs", "label": "Reviews", "path": "/product-reviews", "priority": 70 },
    { "type": "product.detail.info", "label": "Rating", "path": "/product-rating-badge", "priority": 5 },
    { "type": "product.list.filters", "label": "Min Rating", "filterKey": "rating", "filterType": "number", "priority": 20 },
    { "type": "menu.main", "label": "Reviews", "icon": "star", "path": "/", "priority": 90 }
  ]
}
```

---

## SDK Patterns

```typescript
// SDK init — inside page components only (never in main.tsx)
import { getSDK } from "../../sdk";
const sdk = getSDK();
const productId = sdk.thisPlugin.productId ?? "";

// Save review with PRODUCT entity binding
await sdk.thisPlugin.objects.save("review", crypto.randomUUID(),
  { rating: 5, title: "Great", body: "...", reviewer: "Alice", status: "PENDING" },
  { entityType: "PRODUCT", entityId: Number(sdk.thisPlugin.productId) }
)

// List approved reviews for a product
const reviews = await sdk.thisPlugin.objects.list("review", {
  entityType: "PRODUCT", entityId: Number(sdk.thisPlugin.productId),
  filter: "status:eq:APPROVED"
})

// Get/set rating summary (pluginData)
const data = await sdk.thisPlugin.getData(productId)
await sdk.thisPlugin.setData(productId, { rating: avg, count: reviews.length })
```

---

## tc-* Component Usage

```tsx
<div className="tc-plugin" style={{ padding: "1rem" }}>
  <table className="tc-table">
    <thead><tr><th>Reviewer</th><th align="right">Rating</th></tr></thead>
    <tbody>...</tbody>
  </table>
  <input className="tc-input" placeholder="Review title" />
  <select className="tc-select">...</select>
  <button className="tc-primary-button">Submit</button>
  <button className="tc-ghost-button tc-ghost-button--danger">Reject</button>
  <span className="tc-badge tc-badge--success">Approved</span>
  {error && <p className="tc-error">{error}</p>}
</div>
```

---

## Integration Test Pattern

```java
// Package: pl.devstyle.aj.reviews (not core.plugin)
// File: src/test/java/pl/devstyle/aj/reviews/ReviewsPluginObjectTests.java

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsPluginObjectTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper; // tools.jackson.databind — NOT com.fasterxml

    // Helper methods: createAndSavePlugin(), createAndSaveProduct(), createAndSaveCategory()
    // MockMvc endpoints:
    //   PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}?entityType=PRODUCT&entityId=42
    //   GET /api/plugins/{pluginId}/objects/{objectType}?entityType=PRODUCT&entityId=42&filter=status:eq:APPROVED
    //   PUT /api/plugins/{pluginId}/products/{productId}/data
    //   GET /api/plugins/{pluginId}/products/{productId}/data
}
```

---

## Anti-Patterns to Avoid

- Do NOT import SDK in main.tsx (only page components)
- Do NOT add sdk.ts inside the plugin directory (use shared `../../sdk`)
- Do NOT use inline styles for buttons/tables/inputs (use tc-* classes)
- Do NOT use `com.fasterxml.jackson` — use `tools.jackson.databind` (Spring Boot 4)
- Do NOT forget `Number()` cast: `entityId` must be numeric, `productId` comes as string from SDK

---

## Concerns

- `../../sdk` import path: reviews plugin must sit at exactly `plugins/reviews/src/` depth
- `Number(productId)` cast: always guard against undefined before numeric conversion
- `index.html` hardcodes `localhost:8080` — correct per convention, not portable
- `setData` is full-replace: fetch-compute-write pattern required for aggregate updates

---

## Test Plan

| Class | Scenarios |
|-------|-----------|
| ReviewsPluginObjectTests | save review, list by product, filter by status (eq:APPROVED), filter by rating (gt:3), list all without filter |
| ReviewsPluginDataTests | set + read summary, overwrite previous data, rating filter on product list |

---

```yaml
status: success
report_path: analysis/codebase-analysis.md
summary: "Two complete reference plugins and a shared SDK fully establish all patterns for the reviews plugin; no backend code required for v1."
files_found: 18
complexity: simple
risk_level: low
```
