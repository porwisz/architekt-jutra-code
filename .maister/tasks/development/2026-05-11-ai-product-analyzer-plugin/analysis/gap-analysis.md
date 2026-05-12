# Gap Analysis Report

**Date**: 2026-05-11
**Task**: AI Product Analyzer Plugin — port 3011, BAML + LiteLLM

---

## Task Characteristics

- **has_reproducible_defect**: false
- **modifies_existing_code**: false
- **creates_new_entities**: true
- **involves_data_operations**: true
- **ui_heavy**: true

## Risk Level: LOW

The ai-description plugin is a near-exact structural template. Every infrastructure concern is proven and working. Port 3011 is unallocated. The only novelty is the BAML function logic (three analysis dimensions vs. one).

---

## Current State

- No `ai-product-analysis` plugin exists
- Port 3011 is free (used: 3001, 3002, 3003, 3010)
- LiteLLM proxy at port 4000 with gpt-4o-mini, claude-haiku, claude-sonnet, groq models
- Langfuse observability active — all LiteLLM calls logged automatically
- BAML 0.220.0 pattern documented and working in ai-description template
- Product entity fields available: name, description, price (BigDecimal), sku, category.name

---

## Desired State

A new plugin `plugins/ai-product-analysis/` running on port 3011 that:
1. BAML function `AnalyzeProduct` → structured 3-dimension result
2. `POST /api/analyze` — fetch product → call BAML → save custom object → return JSON
3. `product.detail.tabs` UI tab with trigger button and results display
4. `manifest.json` registered as `ai-product-analysis` at `http://localhost:3011`
5. All LLM calls through LiteLLM at `http://localhost:4000/v1`, visible in panel

---

## Gaps

**Gap 1** — Plugin directory does not exist  
Full scaffolding needed: package.json, tsconfig.json, next.config.js, manifest.json, .env.example

**Gap 2** — BAML function for product analysis  
`baml_src/clients.baml` (copy from template), `baml_src/generators.baml` (copy), `baml_src/main.baml` (new `AnalyzeProduct` with 3 output dimensions)

**Gap 3** — API route `src/pages/api/analyze.ts`  
Fetch product → call BAML → save custom object → return JSON

**Gap 4** — Domain types `src/domain.ts`  
`ProductAnalysis` interface + `toProductAnalysis(obj: PluginObject)` mapper

**Gap 5** — UI page `src/pages/product-tab.tsx`  
Load existing analysis, display results, trigger button, loading/error states

**Gap 6** — `src/pages/_document.tsx`  
Standard plugin document loading SDK + CSS from host

**Gap 7** — Price analysis constraint  
Product entity has `price` and `category.name` but NO external market price data. LLM must reason from training knowledge only.

---

## Decisions Needed

### Critical: NONE

### Important (to batch)

**1. price-analysis-scope**: No external market price data available.  
- Option A: LLM uses general knowledge ("a laptop at $20 is suspiciously cheap") — document limitation in UI  
- Option B: Fetch all category products and compute statistical mean/median baseline — more concrete but adds complexity  
- **Default**: A (minimal implementation, LLM-only)

**2. model-selection**: Which LLM model to use?  
- Option A: `gpt-4o-mini` (same as ai-description template — fast, cheap, structured)  
- Option B: `claude-haiku` (Anthropic, also fast and cheap)  
- Option C: Make model configurable via BAML fallback chain  
- **Default**: A (matches existing pattern)

---

## Integration Points

| Integration Point | Mechanism |
|---|---|
| Host product API | `sdk.hostApp.getProduct(productId)` via server-sdk.ts |
| Plugin object storage | `thisPlugin.objects.save("analysis", productId, data, { entityType: "PRODUCT" })` |
| LiteLLM proxy | BAML client at `http://localhost:4000/v1` with `LITELLM_API_KEY` |
| Langfuse observability | Automatic via LiteLLM callbacks — no plugin-side work |
| Plugin manifest | `PUT /api/plugins/ai-product-analysis/manifest` |
| Host UI CSS | `plugin-ui.css` classes (tc-plugin, tc-card, tc-primary-button) |
| Extension points | `product.detail.tabs` (primary) |

---

## Phase Summary

The new `ai-product-analysis` plugin is a structural clone of `ai-description` with a different BAML function (three dimensions: description quality, category relevance, price assessment) and corresponding UI. The only non-trivial decision is the price analysis scope and model selection.
