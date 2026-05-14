# Gap Analysis: Change Rating Filter to "Rating and Above"

**Date**: 2026-04-07
**Risk Level**: Low

## Current State vs Desired State

**Current**: `PluginFilterBar.tsx` line 56 hardcodes `"eq"` for all non-boolean filter types. Rating filter sends `reviews:rating:eq:4`, matching only products with rating exactly 4.0. Backend `DbProductQueryService.parseFilter()` supports `eq`, `gt`, `lt`, `exists`, `bool` — no `gte` case exists.

**Desired**: Rating filter sends `reviews:rating:gte:4`, returning all products with rating >= 4.

## Gaps

- **Backend**: No `gte` operator in `parseFilter()` — regex and switch both lack it
- **Frontend**: `buildFilterString()` emits `"eq"` for number-type filters; must emit `"gte"`
- **Test**: `ReviewsPluginDataTests.productListFilter_byRating_returnsMatchingProducts` uses `eq:4.5` and asserts exact-match — must be updated

## Task Characteristics

```yaml
has_reproducible_defect: true
modifies_existing_code: true
creates_new_entities: false
involves_data_operations: true
ui_heavy: true
```

## Decisions Needed

### Important

**scope-filter-operator-approach**: Should number filters always use `gte` (minimal, 1-line frontend change) or should a new `filterOperator` manifest field control the operator per-filter (extensible but wider scope)?
- **Option A**: Change number filterType to emit `gte` globally
- **Option B**: Add optional `filterOperator` to manifest + PluginFilterBar reads it
- **Default**: Option A — no speculative abstraction; no current need for number filters with eq semantics

**test-gte-coverage**: Existing rating filter test uses `eq:4.5` and will become wrong after the change.
- **Option A**: Update existing integration test only
- **Option B**: Update existing integration test + add static unit test for parseFilter gte
- **Default**: Update existing integration test only

## Scope

**In scope**:
- `DbProductQueryService.parseFilter()` — add `gte` case (~6 lines)
- `PluginFilterBar.tsx` `buildFilterString()` — change operator for number filters
- `ReviewsPluginDataTests` — update test assertions

**Out of scope**: Manifest schema changes, documentation updates, other plugins' behavior
