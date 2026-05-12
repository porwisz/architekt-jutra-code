# Specification: AI Product Analyzer Plugin

## Goal

Create a new plugin `ai-product-analysis` running on port 3011 that uses BAML + LiteLLM to analyze products across three dimensions (description quality, category relevance, price assessment) and surfaces results in both a product detail tab and a compact sidebar info badge.

## User Stories

- As a product manager, I want to click "Analyze" on a product detail tab and receive an AI assessment of the product's description quality, category fit, and price reasonableness so that I can quickly identify products that need attention.
- As a product manager viewing a product, I want to see a compact PASS/WARN/FAIL badge in the product sidebar so that I can assess product health at a glance without opening the analysis tab.

## Core Requirements

1. Plugin registers at `http://localhost:3011` as `ai-product-analysis` with extension points `product.detail.tabs` and `product.detail.info`
2. Product detail tab displays: "Analyze" button (gated on `canEdit` EDIT permission — same as ai-description template), three-section results panel (description quality, category relevance, price assessment), loading state, and error state
3. Info badge in sidebar displays the overall verdict (PASS/WARN/FAIL) with a one-line summary, compact (~60px height)
4. BAML function `AnalyzeProduct(productName, productDescription, categoryName, price)` returns structured `ProductAnalysis` with three dimension assessments
5. Each dimension assessment contains: a verdict (PASS/WARN/FAIL), a score (1–10, displayed as a numeric value in the tab), and a short explanation string
6. All LLM calls route through LiteLLM proxy at `http://localhost:4000/v1` using model `gpt-4o-mini`; calls must appear in the LiteLLM panel logs
7. Analysis results are persisted as custom objects of type `"analysis"` bound to `entityType: "PRODUCT"` — loadable on subsequent tab visits without re-triggering the LLM
8. Re-analysis (clicking Analyze again) overwrites the previous result (same objectId = productId)
9. Price assessment section includes the disclaimer: "Based on LLM general knowledge, not real-time market data"
10. Plugin manifest is registered via `PUT /api/plugins/ai-product-analysis/manifest`

## Reusable Components

### Existing Code to Leverage

- `plugins/ai-description/baml_src/clients.baml` — LiteLLM client configuration; copy verbatim, no changes needed
- `plugins/ai-description/baml_src/generators.baml` — BAML TypeScript generator config; copy verbatim
- `plugins/ai-description/src/pages/_document.tsx` — Next.js custom document loading plugin-sdk.js and plugin-ui.css; copy verbatim
- `plugins/ai-description/package.json` — dependency versions for Next.js 15, BAML 0.220.0, React 19, TypeScript 5.9, Jest; use as template with name change to `ai-product-analysis-plugin` and port 3011
- `plugins/server-sdk.ts` — `createServerSDK(pluginId, undefined, req)` pattern for server-side host API calls with forwarded auth header; import unchanged
- `plugins/sdk.ts` — `getSDK()`, `PluginObject` type for client-side SDK; import unchanged
- `plugins/reviews/src/pages/ProductRatingBadge.tsx` — tc-badge conditional coloring pattern (success/danger/neutral based on score); adapt for PASS/WARN/FAIL verdict logic
- `plugins/ai-description/src/pages/product-tab.tsx` — full page lifecycle pattern: SDK init, useEffect load from objects.listByEntity, handleGenerate function with token forwarding, loading/error/canEdit states; adapt for three-section results display

### New Components Required

- `plugins/ai-product-analysis/baml_src/main.baml` — New `AnalyzeProduct` BAML function with `ProductAnalysis` output class containing three `DimensionAssessment` fields. Cannot reuse `main.baml` from ai-description because the function signature, output schema, and prompt are entirely different (three dimensions vs. one description).
- `plugins/ai-product-analysis/src/domain.ts` — New `ProductAnalysis` interface (three `DimensionAssessment` fields each with verdict, score, explanation) and `toProductAnalysis(obj: PluginObject)` mapper. Cannot reuse ai-description's `domain.ts` because the data shape is different.
- `plugins/ai-product-analysis/src/pages/api/analyze.ts` — New API route `POST /api/analyze`; fetches product (name, description, price, category.name), calls `b.AnalyzeProduct`, saves custom object, returns JSON. Cannot reuse `generate.ts` because the function call, product fields extracted, and object type differ.
- `plugins/ai-product-analysis/src/pages/product-tab.tsx` — New tab page adapted from ai-description template; renders three separate result sections instead of one; adds price disclaimer note.
- `plugins/ai-product-analysis/src/pages/product-info-badge.tsx` — New badge component for `product.detail.info` extension point; reads analysis custom object via `objects.listByEntity`, shows verdict badge. Pattern adapted from `ProductRatingBadge.tsx` but reads from custom objects not plugin data.
- `plugins/ai-product-analysis/manifest.json` — New manifest with both `product.detail.tabs` and `product.detail.info` extension points at port 3011.
- `plugins/ai-product-analysis/src/main.tsx` — New Next.js router with routes `/product-tab` and `/product-info-badge`.

## Technical Approach

### BAML Schema

`baml_src/main.baml` defines:

```
class DimensionAssessment {
  verdict "PASS" | "WARN" | "FAIL"
  score int          // 1–10
  explanation string
}

class ProductAnalysis {
  overallVerdict "PASS" | "WARN" | "FAIL"
  descriptionQuality DimensionAssessment
  categoryRelevance DimensionAssessment
  priceAssessment DimensionAssessment
  summary string     // one sentence for sidebar badge
}

function AnalyzeProduct(
  productName: string,
  productDescription: string,
  categoryName: string,
  price: string        // formatted as decimal string e.g. "29.99"
) -> ProductAnalysis
```

The prompt instructs the LLM to: (1) evaluate whether the product description is accurate, complete, and relevant to the product name; (2) assess whether the product's category is the most appropriate classification; (3) estimate from training knowledge whether the price is normal, suspicious, or clearly wrong for this type of product.

### Data Flow

1. Client-side tab calls `POST /api/analyze` with `{ productId }` plus Authorization header (Bearer token from `sdk.hostApp.getToken()`)
2. API route calls `createServerSDK("ai-product-analysis", undefined, req)` to forward auth
3. API fetches product via `sdk.hostApp.getProduct(productId)` — extracts `name`, `description`, `price`, `category.name`
4. Calls `b.AnalyzeProduct(...)` via BAML client → LiteLLM → gpt-4o-mini
5. Saves result as `sdk.thisPlugin.objects.save("analysis", productId, data, { entityType: "PRODUCT", entityId: productId })`
6. Returns JSON to client; client updates React state
7. Info badge on load: `sdk.thisPlugin.objects.listByEntity("PRODUCT", productId)` — reads same persisted object

### Verdict Badge Styling

Follow the reviews plugin pattern for conditional tc-badge classes:
- PASS → `tc-badge tc-badge--success` (green)
- WARN → plain span or neutral styling (no tc-badge color modifier)
- FAIL → `tc-badge tc-badge--danger` (red)

### Product Data Shape

Product object from `hostApp.getProduct()` provides: `name` (string), `description` (string), `price` (BigDecimal serialized as number or string), `category.name` (string). Price must be formatted as a decimal string before passing to BAML.

### Persistence Model

Object type: `"analysis"`, objectId: `productId`, entityType: `"PRODUCT"`, entityId: `productId`. Re-analysis uses the same objectId so `objects.save` performs an upsert (overwrites).

### Plugin Startup

Plugin runs as a local dev process (`npm run dev` on port 3011), not as a Docker service. No compose.yml entry needed. Dev startup follows the same manual workflow as other plugins.

## Implementation Guidance

### Testing Approach

Each step group should have 2–8 focused tests. Suggested coverage:

- **BAML function tests** (2–3 tests): mock BAML client, verify API route returns correct 3-dimension shape; verify 400 on missing productId; verify host API error propagates correct status code
- **Domain mapper tests** (2–3 tests): `toProductAnalysis` correctly maps all three dimensions from a PluginObject; handles missing/null fields gracefully
- **UI component tests** (2–4 tests): info badge renders PASS/WARN/FAIL with correct tc-badge class; tab shows "Analyze" button when no existing analysis; tab shows results sections when analysis loaded; price disclaimer text is present in rendered tab

Run only new tests per step group, not the full suite.

### Standards Compliance

- **Minimal implementation** (`.maister/docs/standards/global/minimal-implementation.md`): no speculative methods, no future-proofing stubs — only the three dimensions and two extension points specified
- **Error handling** (`.maister/docs/standards/global/error-handling.md`): typed error responses from API route, user-facing error messages in UI components, no silent swallowing of SDK errors
- **Coding style** (`.maister/docs/standards/global/coding-style.md`): consistent naming with existing plugin codebase, descriptive function names, TypeScript strict types
- **Frontend components** (`.maister/docs/standards/frontend/components.md`): single responsibility per page component, clear props interfaces
- **Backend testing** (`.maister/docs/standards/testing/backend-testing.md`): action_condition_expectedResult test naming; 2–8 tests per feature group

## Out of Scope

- Changes to the Spring Boot backend (no new controllers, entities, or migrations)
- External market price data APIs (price analysis uses LLM training knowledge only)
- Bulk analysis of all products
- `product.list.filters` extension point
- Analysis history or versioning (latest result always overwrites)
- Model selection UI or configurable model override
- compose.yml service entry (plugin runs as local dev process)

## Success Criteria

1. `POST /api/analyze` returns a valid `ProductAnalysis` JSON with three dimension assessments each containing verdict, score, and explanation
2. The LiteLLM admin panel at `http://localhost:4000` shows a log entry for each analysis call
3. Navigating to a product's analysis tab a second time loads persisted results without calling LiteLLM again
4. The info badge renders in the product sidebar showing the overall verdict with correct tc-badge color class (success/danger/neutral)
5. The price assessment section in the tab displays the disclaimer "Based on LLM general knowledge, not real-time market data"
6. Re-clicking "Analyze" overwrites the previous result and shows updated values
7. Plugin manifest accepted by host (`PUT /api/plugins/ai-product-analysis/manifest` returns 200)
