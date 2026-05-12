# Scope Clarifications

**Date**: 2026-05-11

## Decisions Made

### 1. Price Analysis Scope
**Decision**: LLM general knowledge only  
**Rationale**: No external market data available. LLM reasons from product name/description/category/price using training knowledge. Limitation will be disclosed in the UI. Consistent with minimal implementation standard.

### 2. Model Selection
**Decision**: `gpt-4o-mini`  
**Rationale**: Same model as ai-description plugin — fast, cost-effective, reliable for structured analysis tasks. Consistent with existing plugin pattern.

### 3. UI Mockups
**Decision**: Skip Phase 4 UI mockup generation  
**Rationale**: UI pattern is clear from the ai-description template; product-tab.tsx serves as a direct visual reference.

## Architecture Decisions
- BAML function: `AnalyzeProduct(productName, productDescription, categoryName, price)` → `ProductAnalysis`
- LiteLLM client model: `gpt-4o-mini` at `http://localhost:4000/v1`
- Storage: Custom objects type `"analysis"` with `entityType: "PRODUCT"` binding
- Extension points: `product.detail.tabs` (primary tab)
- Price disclaimer: Show "Based on LLM general knowledge, not real-time market data" in UI
