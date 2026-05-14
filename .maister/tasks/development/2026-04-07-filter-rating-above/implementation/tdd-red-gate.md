# TDD Red Gate

**Date**: 2026-04-08
**Status**: PASSED (test confirmed failing)

## Test Added

`ReviewsPluginDataTests#productListFilter_byRating_gte_returnsProductsAtOrAboveThreshold`

**Location**: `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginDataTests.java`

## What the Test Proves

Sends `pluginFilter=reviews:rating:gte:4` to `GET /api/products`.
Current backend rejects `gte` with 400 Bad Request:
> "Unsupported operator: gte. Supported: eq, gt, lt, exists, bool"

## Expected After Implementation

Returns 200 OK with products having rating >= 4 (Alpha=4.5 ✓, Gamma=4.0 ✓) and excludes products below threshold (Beta=3.0 ✗).

## Note on Replaced Test

The old test `productListFilter_byRating_returnsMatchingProducts` used `eq:4.5` exact-match semantics — removed and replaced with the gte-semantics test above.
