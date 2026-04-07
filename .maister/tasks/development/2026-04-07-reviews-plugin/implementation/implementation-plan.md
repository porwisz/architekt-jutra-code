# Implementation Plan: Reviews Plugin

## Overview

Total Steps: 27
Task Groups: 4
Expected Tests: 8 backend (split across 2 test classes; no frontend unit tests per project convention)

## Implementation Steps

### Task Group 1: Plugin Scaffolding
**Dependencies:** None
**Estimated Steps:** 5

- [x] 1.0 Complete plugin scaffolding — create the `plugins/reviews/` directory with all config files
  - [x] 1.1 Create `plugins/reviews/package.json`
    - Copy from `plugins/warehouse/package.json`
    - Change `name` to `"reviews-plugin"`; no other fields change
  - [x] 1.2 Create `plugins/reviews/vite.config.ts`
    - Copy from `plugins/warehouse/vite.config.ts`
    - Change port to `3003`
  - [x] 1.3 Create `plugins/reviews/tsconfig.json`
    - Identical copy from `plugins/warehouse/tsconfig.json`
    - Preserve `erasableSyntaxOnly: true` — this prohibits TypeScript enums; use union types everywhere
  - [x] 1.4 Create `plugins/reviews/index.html`
    - Copy from `plugins/warehouse/index.html`
    - Change `<title>` to `"Reviews Plugin"`
    - SDK and `plugin-ui.css` load paths (`localhost:8080`) are correct as-is
  - [x] 1.5 Verify scaffolding: `npm install` — 28 packages, 0 vulnerabilities

**Acceptance Criteria:**
- `plugins/reviews/` contains all four config files
- `npm install` completes without errors
- No tests for this group (pure config copies)

---

### Task Group 2: Manifest, Domain Types, and Router
**Dependencies:** Group 1
**Estimated Steps:** 7

- [x] 2.0 Complete manifest, domain layer, and router — foundational files all other groups depend on
  - [x] 2.1 Create `plugins/reviews/manifest.json` — 4 extension points, label "Rating" confirmed
  - [x] 2.2 Create `plugins/reviews/src/domain.ts` — ReviewStatus union, Review/RatingSummary interfaces, toReview/toRatingSummary mappers; entityId typed as string; createdAt/updatedAt read from obj.data
  - [x] 2.3 Create `plugins/reviews/src/main.tsx` — router-only, no SDK imports, 3 routes
  - [x] 2.4 Created stub page components (ReviewsAdmin, ProductReviewsTab, ProductRatingBadge) — will be replaced in Group 3
  - [x] 2.5 `npm run build` — 0 TypeScript errors, build succeeded
  - [x] 2.6 entityId verified as string throughout domain.ts — no Number() cast
  - [x] 2.7 Type-level check complete via build pass

**Acceptance Criteria:**
- `manifest.json` has exactly 4 extension points with correct labels (filter label is "Rating" not "Min Rating")
- `domain.ts` exports all 5 items: `ReviewStatus`, `Review`, `RatingSummary`, `toReview`, `toRatingSummary`
- `Review.entityId` is typed as `string`
- `main.tsx` imports no SDK symbols
- `npm run build` exits 0

---

### Task Group 3: Frontend Page Components
**Dependencies:** Group 2
**Estimated Steps:** 10

- [x] 3.0 Complete the three frontend page components
  - [x] 3.1 Create `plugins/reviews/src/pages/ProductRatingBadge.tsx` — null-while-loading, getData, 3-tier badge
  - [x] 3.2 Create `plugins/reviews/src/pages/ReviewsAdmin.tsx` — all reviews, client-side filter, approve/reject with setData recalc
  - [x] 3.3 Create `plugins/reviews/src/pages/ProductReviewsTab.tsx` — APPROVED-only list, submit form collapses via submitted boolean
  - [x] 3.4 `npm run build` — 0 TypeScript errors (27 modules)
  - [x] 3.5 Smoke-test instructions documented

**Acceptance Criteria:**
- `npm run build` exits 0, no TypeScript errors
- `ProductRatingBadge` returns null when no data, renders correct badge tier when data present
- `ReviewsAdmin` shows all reviews with correct per-row action buttons; re-filters without re-fetch after action
- `ProductReviewsTab` shows APPROVED-only list; form collapses to success message after submit
- No `Number()` cast on `productId` or `entityId` anywhere in any component
- No SDK imports in `main.tsx`

---

### Task Group 4: Backend Integration Tests
**Dependencies:** None (backend tests are independent of frontend; can run in parallel with Group 3)
**Estimated Steps:** 5

- [x] 4.0 Complete backend integration test classes covering reviews-specific platform API scenarios
  - [x] 4.1 Write `ReviewsPluginObjectTests` (5 tests) — entity binding, product-scoped listing, status filter, rating filter, no-filter baseline
  - [x] 4.2 Write `ReviewsPluginDataTests` (3 tests) — set/read summary, overwrite, product list filter by rating
  - [x] 4.3 Test naming verified — all follow `action_condition_expectedResult` pattern
  - [x] 4.4 Tests run: 8 passed, 0 failed (ReviewsPluginObjectTests: 5, ReviewsPluginDataTests: 3)
  - [x] 4.5 No duplication confirmed with PluginDataAndObjectsIntegrationTests

**Acceptance Criteria:**
- `ReviewsPluginObjectTests` contains exactly 5 tests; all pass
- `ReviewsPluginDataTests` contains exactly 3 tests; all pass
- Both classes are package-private, in package `pl.devstyle.aj.reviews`
- All imports use `tools.jackson.databind.ObjectMapper`
- All helper methods use `saveAndFlush()`
- 8/8 tests pass when run in isolation

---

## Execution Order

1. Group 1: Plugin Scaffolding (5 steps) — no dependencies
2. Group 2: Manifest, Domain Types, and Router (7 steps) — depends on Group 1
3. Group 3: Frontend Page Components (10 steps) — depends on Group 2
4. Group 4: Backend Integration Tests (5 steps) — no frontend dependency; can start after Group 1 or run in parallel with Group 3

Groups 3 and 4 can proceed in parallel once Group 2 is complete.

## Standards Compliance

Follow standards from `.maister/docs/standards/`:

- `global/minimal-implementation.md` — no v2 stubs, no speculative abstractions, every method has an immediate caller
- `global/error-handling.md` — all SDK calls wrapped in `try/catch`; errors shown via `<p className="tc-error">`
- `global/coding-style.md` — descriptive names, focused functions
- `frontend/components.md` — single responsibility per page component; no SDK imports in `main.tsx`
- `frontend/css.md` — no custom CSS; use `tc-*` classes exclusively; inline `style` only for layout (padding, maxWidth)
- `frontend/accessibility.md` — semantic HTML elements (label, th, button)
- `testing/backend-testing.md` — `@Import(TestcontainersConfiguration.class)` + `@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + `@Transactional`; `*Tests` suffix; package-private; `action_condition_expectedResult`; `createAndSave*()` with `saveAndFlush()`; `jsonPath()` + Hamcrest

## Notes

- Test-Driven: Backend groups write tests first; frontend has no unit tests (consistent with warehouse/box-size reference plugins)
- Run Incrementally: Run only `ReviewsPluginObjectTests` and `ReviewsPluginDataTests` during development — not the full suite
- Mark Progress: Check off steps as completed
- Reuse First: Config files are direct copies; page components are adapted from warehouse/box-size templates per spec
- Critical Constraint: `entityId` is always `string` throughout — no `Number()` or numeric cast anywhere in frontend code
- Critical Constraint: Filter label must be "Rating" (not "Min Rating") per gap analysis finding
- Critical Constraint: `erasableSyntaxOnly: true` in tsconfig prohibits TypeScript enums — use union type `"PENDING" | "APPROVED" | "REJECTED"` for `ReviewStatus`
- Critical Constraint: Jackson 3 import path is `tools.jackson.databind.ObjectMapper`, not `com.fasterxml.jackson`
- Aggregate recalc in `ReviewsAdmin` uses `review.entityId` (string) passed directly to `objects.list` and `setData` — no re-fetch of `allReviews` after action, only local state update
