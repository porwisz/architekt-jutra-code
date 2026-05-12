# Requirements

**Date**: 2026-05-11
**Task**: AI Product Analyzer Plugin — port 3011

---

## Initial Description

Create a plugin integrating with the platform backend that:
- Uses BAML for LLM invocation with structured data formats
- Routes all queries through LiteLLM
- Automatically checks product description correctness
- Verifies relevance of assigned category
- Evaluates whether product price deviates significantly from market norms
- Available on port 3011
- After implementation: verify LiteLLM panel shows communication in logs

---

## Q&A Clarifications

**Q: How will users access the analysis?**  
A: Manual trigger, product tab. User clicks "Analyze" button on the product detail tab. Same pattern as ai-description plugin. Result persisted in custom objects for subsequent views.

**Q: Display style / reuse patterns?**  
A: Borrow compact badge/score display style from reviews plugin (rating badge pattern). Each dimension shown with score indicator.

**Q: product.detail.info extension point (info badge)?**  
A: YES — add a compact badge showing overall verdict (PASS/WARN/FAIL) in the product detail sidebar. Both `product.detail.tabs` and `product.detail.info` extension points required.

**Q: Price analysis scope?**  
A: LLM general knowledge only — no external market data. Limitation disclosed in UI with note "Based on LLM general knowledge, not real-time market data."

**Q: Model?**  
A: `gpt-4o-mini` via LiteLLM proxy at `http://localhost:4000/v1`.

---

## Similar Features Identified

- `plugins/ai-description/` — primary template (Next.js + BAML + LiteLLM, product.detail.tabs)
- `plugins/reviews/` — badge/score display style, product.detail.info extension point pattern

---

## Visual Assets

None provided. Reference: plugins/ai-description/src/pages/product-tab.tsx (tab pattern) + plugins/reviews/ (badge style).

---

## Functional Requirements Summary

1. **FR-1**: Plugin registers at `http://localhost:3011` as `ai-product-analysis` with both `product.detail.tabs` and `product.detail.info` extension points
2. **FR-2**: Product detail tab shows: Analyze button, 3-section results display (description quality, category relevance, price assessment), loading state, error state
3. **FR-3**: Info badge shows: compact overall verdict (PASS/WARN/FAIL) + brief summary — displayed in product detail sidebar
4. **FR-4**: BAML function `AnalyzeProduct` takes `productName`, `productDescription`, `categoryName`, `price` → returns `ProductAnalysis` with 3 dimension assessments
5. **FR-5**: All LLM calls route through LiteLLM proxy (port 4000); visible in LiteLLM panel logs
6. **FR-6**: Analysis results persisted as custom objects (type `"analysis"`) with `entityType: "PRODUCT"` binding — loadable on subsequent tab visits
7. **FR-7**: Analysis is manually triggered (button click). Re-analysis overwrites previous result.
8. **FR-8**: Price disclaimer shown: "Based on LLM general knowledge, not real-time market data"

---

## Reusability Opportunities

- `plugins/server-sdk.ts` — import as-is for server-side host API calls
- `plugins/sdk.ts` — import as-is for client-side SDK
- `plugins/ai-description/baml_src/clients.baml` — copy LiteLLM client config (unchanged)
- `plugins/ai-description/baml_src/generators.baml` — copy BAML generator config (unchanged)
- `plugins/ai-description/src/pages/_document.tsx` — copy Next.js document template (unchanged)
- Reviews plugin badge CSS classes — tc-badge pattern for verdict display

---

## Scope Boundaries

**IN scope:**
- New plugin directory `plugins/ai-product-analysis/`
- BAML function with 3 analysis dimensions
- Next.js API route `/api/analyze`
- Two extension points: product.detail.tabs + product.detail.info
- Custom objects storage for analysis results
- compose.yml service entry for port 3011
- Dev startup integration

**OUT of scope:**
- Changes to Spring Boot backend
- External market price data APIs
- Bulk analysis of all products
- product.list.filters extension point
- Analysis history / versioning (latest overwrites)

---

## Technical Considerations

- BAML version must match `@boundaryml/baml ^0.220.0` in package.json
- next.config.js must allow external imports from plugins root (same as ai-description)
- Authorization header must be forwarded via createServerSDK(pluginId, undefined, req)
- info badge component must be ~60px height (host constraint for product.detail.info)
- Plugin manifest JSON must be registered via `curl PUT /api/plugins/ai-product-analysis/manifest`
