# Personas — Footprint Calculation Engine

## Persona 1 — Marta, Sustainability Manager (ESG Officer)

- **Role**: In-house sustainability lead at a mid-size grocery e-commerce. Owns ESG reporting (CSRD), supplier scorecards, footprint reduction targets.
- **Goals**:
  - Trustworthy per-product footprints she can show to the board and regulators
  - Spot which SKUs / supply lanes drive emissions
  - Run "what-if" comparisons (alternative supplier, alternative season)
- **Pain points**:
  - Today: spreadsheets-and-PDFs world; cannot reproduce last quarter's numbers when factors change
  - No clear audit trail when a number is challenged
  - Cross-context comparisons (same product, different destinations/seasons) are manual
- **Key journey**: Internal admin → comparison view → picks 2 destinations for same SKU → side-by-side breakdown with factor metadata → exports for board deck.

## Persona 2 — Tomek, Online Grocery Shopper

- **Role**: Climate-conscious consumer; shops online weekly; compares alternatives by perceived environmental impact.
- **Goals**:
  - Quick "is this product greener than the alternative?" answer
  - Confidence the number isn't greenwashing
- **Pain points**:
  - Doesn't understand `kg CO₂` without context
  - Suspicious of vague "eco-friendly" badges
  - Wants to know *why*, not just *what*
- **Key journey**: Product detail page → footprint summary + bar-chart breakdown → hovers a component to see what it represents → "compare" → picks alternative shipping option to see if footprint drops.

## Persona 3 — Anna, Internal Auditor

- **Role**: In-house auditor / compliance officer at the same company. Reviews footprint records as part of internal controls and pre-audit for external assurance.
- **Goals**:
  - Sample historical calculations and verify reproducibility
  - Confirm factor sources are documented
  - Spot scope misclassification before an external auditor does
  - Pull data into her own tooling (Excel, BI tools) for cross-checks
- **Pain points**:
  - **Needs CSV export of the full breakdown tree** (kgCO₂, scope, factor, validFrom per leaf) for offline analysis
  - Historical calculations sometimes cannot be reproduced when factors are silently updated
  - Manual reconciliation between footprint values and source factors is tedious
- **Key journey**: Internal audit view → filter by date / product / scope → pick a past calculation → full breakdown with factor metadata → **export CSV** → open in Excel for reconciliation against factor change-log.

## Persona 4 — Piotr, Backend Developer (engine consumer)

- **Role**: Engineer on the eco-rating service team. Calls `FootprintFacade` to derive A/B/C rating from `kgCO₂`.
- **Goals**:
  - Stable contract, predictable errors, clear semantics for strict vs lenient mode
  - Integration in under one day
- **Pain points**:
  - Engines that change behavior between minor versions
  - Vague exceptions ("calculation failed")
  - No way to dry-run without writing audit records
- **Key journey**: Module README → wires Spring bean → integration test with a known SKU → handles `MissingFactorException` in strict mode → ships.

## Implications for Design

- **Marta** drives the **comparison view** and the need for context-flexibility on the API (same product, multiple parameter sets).
- **Tomek** drives the **consumer breakdown view** — visual, hover-explanations, "compare alternatives" affordance.
- **Anna** drives the **CSV export** feature and the **historical lookup view** — explicit non-functional requirement for V1.
- **Piotr** drives the **clean dual-surface API**, the typed exception taxonomy, and a possible **dry-run mode** that skips audit writes (open question — feed into Phase 4 alternatives).
