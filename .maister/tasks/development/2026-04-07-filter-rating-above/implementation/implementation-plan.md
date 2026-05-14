# Implementation Plan: Change rating filter operator from `eq` to `gte` (filter by rating and above)

## Overview
Total Steps: 14
Task Groups: 3
Expected Tests: 4-6 total (TDD red gate test already exists; one new unit test added in Group 1; integration test verified in Group 2)

## Implementation Steps

### Task Group 1: Backend Layer
**Dependencies:** None
**Estimated Steps:** 6

- [x] 1.0 Complete backend `gte` operator support
  - [x] 1.1 Write unit test for `DbProductQueryService.parseFilter()` — `gte` operator case
    - Test file: `src/test/java/pl/devstyle/aj/product/DbProductQueryServiceParseFilterTests.java`
    - Test method name: `parseFilter_gteOperator_returnsGteCondition`
    - Verify `parseFilter("reviews:rating:gte:4")` returns a non-null Condition without throwing
    - Also verify `parseFilter("reviews:rating:gte:abc")` throws `IllegalArgumentException` (non-numeric value)
    - This test will fail (red gate) until the switch case is added
  - [x] 1.2 Add `gte` to the operator validation regex in `DbProductQueryService.parseFilter()`
    - File: `src/main/java/pl/devstyle/aj/product/DbProductQueryService.java`, line 138
    - Change regex from `"eq|gt|lt|exists|bool"` to `"eq|gte|gt|lt|exists|bool"`
  - [x] 1.3 Add `gte` case to the switch expression in `DbProductQueryService.parseFilter()`
    - After the existing `"gt"` case (lines 151-156), add a `"gte"` case
    - Pattern mirrors the `gt` case: cast to `Double`, call `.ge()` instead of `.gt()`, throw `IllegalArgumentException` for non-numeric values
    - Error message: `"Value must be numeric for 'gte' operator: " + val`
  - [x] 1.4 Also update the error message in the `default` branch to list `gte` among supported operators
    - Line 167: include `gte` in the supported operators list so error messages stay accurate
  - [x] 1.5 Run only the 2 unit tests written in step 1.1
    - Confirm `parseFilter_gteOperator_returnsGteCondition` passes (green gate)
    - Confirm non-numeric value test passes

**Acceptance Criteria:**
- Both unit tests from step 1.1 pass
- `parseFilter("reviews:rating:gte:4")` no longer throws
- `parseFilter("reviews:rating:gte:notANumber")` still throws `IllegalArgumentException`

---

### Task Group 2: Integration Test Verification
**Dependencies:** Group 1
**Estimated Steps:** 4

- [x] 2.0 Verify the existing TDD red gate integration test now passes
  - [x] 2.1 Review the pre-existing test in `ReviewsPluginDataTests`
    - File: `src/test/java/pl/devstyle/aj/reviews/ReviewsPluginDataTests.java`
    - Test method: `productListFilter_byRating_gte_returnsProductsAtOrAboveThreshold` (line 124)
    - Confirm the test sets up 3 products with ratings 4.5, 3.0, 4.0 and expects the `gte:4` filter to return products A and C but not B
  - [x] 2.2 Run the integration test against a real database via TestContainers
    - Run only `ReviewsPluginDataTests` — do NOT run the full test suite
    - Confirm the test passes end-to-end with the backend change from Group 1
  - [x] 2.3 Confirm existing passing tests in `ReviewsPluginDataTests` still pass
    - `setRatingSummary_getData_returnsPersistedValues`
    - `setRatingSummary_overwritesPreviousData`

**Acceptance Criteria:**
- All 3 tests in `ReviewsPluginDataTests` pass
- `productListFilter_byRating_gte_returnsProductsAtOrAboveThreshold` is the green gate confirming end-to-end correctness

---

### Task Group 3: Frontend and Manifest Layer
**Dependencies:** None (can run in parallel with Groups 1 and 2)
**Estimated Steps:** 6

- [x] 3.0 Complete frontend `filterOperator` support and manifest update
  - [x] 3.1 Write 2 focused frontend unit tests for `buildFilterString()` in `PluginFilterBar`
    - Test file: create `src/test/frontend/PluginFilterBar.test.tsx` (or place alongside existing frontend tests)
    - Test 1: `buildFilterString_numberType_withFilterOperatorGte_buildsGteFilterString`
      - Input: filter with `filterType: "number"`, `filterOperator: "gte"`, `pluginId: "reviews"`, `filterKey: "rating"`, value `4`
      - Expected output: `"reviews:rating:gte:4"`
    - Test 2: `buildFilterString_numberType_withoutFilterOperator_defaultsToEq`
      - Input: filter with `filterType: "number"`, no `filterOperator`, `pluginId: "reviews"`, `filterKey: "rating"`, value `4`
      - Expected output: `"reviews:rating:eq:4"`
    - Both tests fail (red gate) until the code change in step 3.2 is made
  - [x] 3.2 Add `filterOperator?: string` to the `ExtensionPoint` interface
    - File: `src/main/frontend/src/api/plugins.ts`, line 11 (after `filterType`)
    - Add optional field: `filterOperator?: string;`
    - No changes needed to `ResolvedExtensionPoint` in `PluginContext.tsx` — it extends `ExtensionPoint` so it inherits the new field automatically
  - [x] 3.3 Update `buildFilterString()` in `PluginFilterBar.tsx` to use `filterOperator`
    - File: `src/main/frontend/src/plugins/PluginFilterBar.tsx`, line 56
    - Change: `const operator = filter.filterType === "boolean" ? "bool" : "eq";`
    - To: `const operator = filter.filterType === "boolean" ? "bool" : (filter.filterOperator ?? "eq");`
    - No other changes needed in this file
  - [x] 3.4 Add `filterOperator` to the reviews plugin manifest
    - File: `plugins/reviews/manifest.json`
    - In the `product.list.filters` extension point (line 9), add `"filterOperator": "gte"` field
    - Final entry: `{ "type": "product.list.filters", "label": "Rating", "filterKey": "rating", "filterType": "number", "filterOperator": "gte", "priority": 20 }`
  - [x] 3.5 Run only the 2 frontend unit tests written in step 3.1
    - Confirm both pass (green gate)

**Acceptance Criteria:**
- Both frontend unit tests from step 3.1 pass
- `filterOperator` field propagates from manifest through `ExtensionPoint` type to `buildFilterString()`
- Boolean filters are unaffected (still hardcoded to `"bool"`)
- All other non-boolean filters without `filterOperator` still default to `"eq"` (no behavioral regression)

---

## Execution Order

1. Group 1: Backend Layer (4 steps) — no dependencies
2. Group 3: Frontend and Manifest Layer (5 steps) — no dependencies, can run in parallel with Group 1
3. Group 2: Integration Test Verification (3 steps) — depends on Group 1

Note: Group 3 (frontend) has no backend dependency and can be executed alongside Group 1.

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- `global/minimal-implementation.md` — Only the two touch points needed: regex + switch case in backend, one field + one `??` expression in frontend
- `global/coding-style.md` — Match existing naming conventions in `parseFilter()` switch (`"gt"` / `"lt"` pattern)
- `global/error-handling.md` — Typed exception with specific message for non-numeric `gte` values (mirrors existing `gt`/`lt` pattern)
- `backend/queries.md` — jOOQ bind parameters used; no raw string interpolation for values
- `backend/jooq.md` — Use `.ge()` jOOQ method (greater-than-or-equal) for the `gte` case; matches existing `.gt()` / `.lt()` pattern
- `testing/backend-testing.md` — Integration tests use TestContainers, MockMvc with jsonPath, `@Transactional` rollback, `createAndSave*()` helpers, `*Tests` suffix
- `testing/frontend-testing.md` — Vitest, @testing-library/react, `vi.mock()` for API, describe blocks named after feature

## Notes

- Test-Driven: Each group starts with tests written before implementation (red gate → green gate)
- Run Incrementally: Only run the specific 2 tests per group — not the full suite
- Mark Progress: Check off each sub-step as completed
- Reuse First: The `gte` backend case reuses the exact pattern of `gt`/`lt` — just change `.gt()` to `.ge()` and update the error message
- The TDD red gate test (`productListFilter_byRating_gte_returnsProductsAtOrAboveThreshold`) was pre-written and already exists in `ReviewsPluginDataTests.java` — Group 2 verifies it passes, not writes it
- Re-register manifest after updating `manifest.json`: `curl -X PUT http://localhost:8080/api/plugins/reviews/manifest -H "Content-Type: application/json" -d @plugins/reviews/manifest.json`
