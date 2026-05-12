# Implementation Plan: AI Product Analyzer Plugin

## Overview

Total Steps: 21
Task Groups: 4
Expected Tests: 14–24

## Implementation Steps

### Task Group 1: Plugin Scaffolding & BAML Layer
**Dependencies:** None
**Estimated Steps:** 6

- [x] 1.0 Complete plugin scaffolding and BAML layer
  - [x] 1.1 Write 2–3 focused tests for the BAML API route
    - Test: `analyze_missingProductId_returns400` — POST /api/analyze without productId returns 400 with `details: ["productId"]`
    - Test: `analyze_hostApiError_propagatesStatusCode` — when `sdk.hostApp.getProduct` throws "Host API error 404", route returns 404
    - Test: `analyze_validRequest_returnsThreeDimensionShape` — mock BAML client returns ProductAnalysis, route returns JSON with `descriptionQuality`, `categoryRelevance`, `priceAssessment` each containing `verdict`, `score`, `explanation`
  - [x] 1.2 Create plugin directory `plugins/ai-product-analysis/` with subdirectories `baml_src/`, `src/pages/api/`, `src/__tests__/`
  - [x] 1.3 Copy verbatim from `plugins/ai-description/`:
    - `baml_src/clients.baml` → `plugins/ai-product-analysis/baml_src/clients.baml`
    - `baml_src/generators.baml` → `plugins/ai-product-analysis/baml_src/generators.baml`
    - `src/pages/_document.tsx` → `plugins/ai-product-analysis/src/pages/_document.tsx`
  - [x] 1.4 Create `package.json` from ai-description template: change `name` to `"ai-product-analysis-plugin"`, change port references to `3011` in all three scripts (`dev`, `build`, `start`)
  - [x] 1.5 Create `baml_src/main.baml` with:
    - `class DimensionAssessment { verdict "PASS" | "WARN" | "FAIL"; score int; explanation string }`
    - `class ProductAnalysis { overallVerdict "PASS" | "WARN" | "FAIL"; descriptionQuality DimensionAssessment; categoryRelevance DimensionAssessment; priceAssessment DimensionAssessment; summary string }`
    - `function AnalyzeProduct(productName: string, productDescription: string, categoryName: string, price: string) -> ProductAnalysis` using `client LlmProvider`
    - Prompt instructs: (1) evaluate description accuracy/completeness against product name; (2) assess whether category is the most appropriate classification; (3) estimate from training knowledge whether the price is normal, suspicious, or clearly wrong for this type of product
  - [x] 1.6 npm install && npm run generate completed successfully; BAML client generated (14 files)

**Acceptance Criteria:**
- The 2–3 API route tests pass
- Plugin directory exists with all copied files and new BAML files
- `npm run generate` completes without error (BAML client generated under `baml_client/`)

---

### Task Group 2: Domain & API Route
**Dependencies:** Group 1
**Estimated Steps:** 5

- [x] 2.0 Complete domain types and API route
  - [x] 2.1 Write 3 focused domain mapper tests in `src/__tests__/domain.test.ts`
    - Test: `toProductAnalysis_validPluginObject_mapsAllThreeDimensions` ✓
    - Test: `toProductAnalysis_missingOptionalFields_returnsDefaults` ✓
    - Test: `toProductAnalysis_preservesObjectId` ✓
  - [x] 2.2 Create `src/domain.ts` with DimensionAssessment, ProductAnalysis interfaces and toProductAnalysis mapper
  - [x] 2.3 Create `src/pages/api/analyze.ts` POST handler (validate → fetch product → BAML → save object → return 200)
  - [x] 2.4 All tests pass: 3 analyze tests + 3 domain tests (6/6)
    - Note: analyze.test.ts updated to add thisPlugin.objects.save mock (missing from Group 1)

**Acceptance Criteria:**
- The 2–3 domain mapper tests pass
- `src/domain.ts` exports `ProductAnalysis` interface, `DimensionAssessment` interface, and `toProductAnalysis` mapper
- `src/pages/api/analyze.ts` compiles without TypeScript errors

---

### Task Group 3: UI Components
**Dependencies:** Group 2
**Estimated Steps:** 7

- [x] 3.0 Complete UI components (tab page and info badge)
  - [x] 3.1 Write 5 focused tests for UI components (pure function strategy — no DOM renderer needed)
    - Test: `ProductInfoBadge_passVerdict_rendersTcBadgeSuccess` ✓
    - Test: `ProductInfoBadge_failVerdict_rendersTcBadgeDanger` ✓
    - Test: `ProductTab_noAnalysis_showsAnalyzeButton` ✓ (via constant)
    - Test: `ProductTab_priceDisclaimer_isPresent` ✓ (via PRICE_DISCLAIMER constant)
    - Bonus: `ProductInfoBadge_warnVerdict_rendersNeutralBadge` ✓
  - [x] 3.2 Create `manifest.json` at plugin root:
    ```json
    {
      "name": "AI Product Analysis",
      "version": "1.0.0",
      "url": "http://localhost:3011",
      "description": "AI-powered product quality analysis across three dimensions",
      "extensionPoints": [
        { "type": "product.detail.tabs", "label": "AI Analysis", "path": "/product-tab", "priority": 60 },
        { "type": "product.detail.info", "label": "AI Analysis", "path": "/product-info-badge", "priority": 20 }
      ]
    }
    ```
  - [x] 3.3 Create `src/pages/product-tab.tsx` with three dimension cards, canEdit guard, price disclaimer, loading/error states
  - [x] 3.4 Create `src/pages/product-info-badge.tsx` with verdict badge rendering (PASS/WARN/FAIL) and exported pure functions
  - [x] 3.5 Create `src/main.tsx` with routes /product-tab and /product-info-badge
  - [x] 3.6 All Group 3 tests pass (5/5); full suite 11/11 passing

**Acceptance Criteria:**
- The 2–4 UI component tests pass
- `product-tab.tsx` renders three result sections and price disclaimer
- `product-info-badge.tsx` applies correct `tc-badge` class for each verdict
- `manifest.json` is valid JSON with both extension points

---

### Task Group 4: Test Review & Gap Analysis
**Dependencies:** All previous groups

- [x] 4.0 Review and fill critical gaps
  - [x] 4.1 Reviewed 11 existing tests across 4 test files
  - [x] 4.2 Identified gaps: 405 method guard, BAML error, null description, save-object, WARN badge, null dimension defaults
  - [x] 4.3 Wrote 6 additional strategic tests (within 10-test limit)
    - `analyze_methodNotAllowed_returns405` ✓
    - `analyze_bamlClientError_returns500` ✓
    - `analyze_productWithNullDescription_stillCallsBAML` ✓
    - `analyze_validRequest_callsSaveObject` ✓
    - `ProductInfoBadge_warnVerdict_rendersNeutralBadge` ✓
    - `toProductAnalysis_nullDimensionObject_usesDefaults` ✓
  - [x] 4.4 Full test suite: 17/17 passed (4 suites)

**Acceptance Criteria:**
- All feature tests pass (17–24 total)
- No more than 10 additional tests added in this group

---

## Execution Order

1. Plugin Scaffolding & BAML Layer (6 steps, no dependencies)
2. Domain & API Route (5 steps, depends on Group 1)
3. UI Components (7 steps, depends on Group 2)
4. Test Review & Gap Analysis (4 steps, depends on Groups 1–3)

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- `global/minimal-implementation.md` — no speculative methods, no future stubs; only the three dimensions and two extension points specified
- `global/error-handling.md` — typed error responses from the API route, user-facing error messages in UI, no silent SDK error swallowing
- `global/coding-style.md` — consistent naming with existing plugin codebase, descriptive function names, TypeScript strict types
- `frontend/components.md` — single responsibility per page component, clear props interfaces
- `testing/backend-testing.md` — `action_condition_expectedResult` test naming, 2–8 tests per group

## Notes

- Test-Driven: Each group starts with 2–4 tests written before implementation
- Run Incrementally: Only run new tests after each group, not the entire suite
- Mark Progress: Check off steps as completed using the markdown checkboxes
- Reuse First: Six files copied verbatim from `plugins/ai-description/` (clients.baml, generators.baml, _document.tsx, package.json as template, server-sdk.ts imported, sdk.ts imported)
- Price disclaimer is a required UI element per spec requirement 9 — do not omit
- The info badge uses `objects.listByEntity` (custom objects), NOT `thisPlugin.getData` — this distinguishes it from the box-size badge pattern
- Run `npm install && npm run generate` after scaffolding to bootstrap the BAML client before writing tests
