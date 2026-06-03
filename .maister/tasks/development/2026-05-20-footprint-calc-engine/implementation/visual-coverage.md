# Visual Coverage Matrix — Footprint Calculation Engine

Source: `analysis/design-context/INDEX.md`

**Scope statement**: this is a **backend-only** task. The three HTML mockups are inputs handed to a future frontend task — no UI is rendered in this implementation. Coverage below records that the REST response field names emitted by this backend exactly match the data attributes each mockup renders. Frontend rendering itself is explicitly out of scope per `implementation/spec.md` §Out of Scope.

## Mockup-to-Backend-Contract Matrix

| Screen/Component ID | Mockup File | Covered By Task Group(s) | Coverage Form | Status |
|---|---|---|---|---|
| screen:product-footprint-detail | mockups/product-footprint-detail.html | TG-8 (FootprintController + FootprintResponseDto), TG-5 (engine producing breakdown tree) | Backend contract — REST response shape supplies `correlationId`, `total`, `kgCo2`, `factorVersionId`, `factorRate`, `factorValidFrom`, `scope`, `breakdown.children[].componentId`, `computedAt`. No backend rendering. | Backend-covered (FE deferred) |
| screen:product-footprint-comparison | mockups/product-footprint-comparison.html | TG-8 (FootprintController honours `X-Comparison-Group` header), TG-7 (audit row stores `comparisonGroupId` for backend re-query) | Backend contract — same REST response invoked N times; `comparisonGroupId` links calls. FE assembles the three-column view. | Backend-covered (FE deferred) |
| screen:product-footprint-historical-timeline | mockups/product-footprint-historical-timeline.html | TG-8 (FootprintController accepts `asOf` for historical reproducibility), TG-9 (CSV export by `correlationId` for the datapoints-table batch download) | Backend contract — historical re-calculation via `asOf` query param; CSV by `correlationId`. FE assembles chart + factor-version event log. | Backend-covered (FE deferred) |

## REST Response Field Verification

The TG-8 `FootprintControllerIntegrationTests.getFootprint_validRequest_returnsBreakdownAndCorrelationId` test asserts via `jsonPath()` the presence of every field the three mockups render:

- `$.correlationId`
- `$.computedAt`
- `$.total`
- `$.breakdown.kgCo2`
- `$.breakdown.children[*].componentId`
- `$.breakdown.children[*].children[*].kgCo2`
- `$.breakdown.children[*].children[*].factorVersionId`
- `$.breakdown.children[*].children[*].factorRate`
- `$.breakdown.children[*].children[*].factorValidFrom`
- `$.breakdown.children[*].children[*].scope`

## Uncovered Items

None at the **backend contract** level — every mockup data attribute is sourced from a field this implementation emits.

**UI rendering** is uniformly uncovered by design: this task ships zero frontend code. Pickup happens in a future FE task whose inputs are these mockups plus this backend's REST contract.
