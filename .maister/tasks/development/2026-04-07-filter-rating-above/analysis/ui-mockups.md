# UI Mockups: filterOperator Field for product.list.filters

**Generated**: 2026-04-08
**Task Path**: `.maister/tasks/development/2026-04-07-filter-rating-above`
**Feature Type**: Enhancement

---

## Overview

### UI Requirements

This change has no visible UI changes for the end user. The filter control rendered in the product list toolbar looks and behaves identically before and after. The only behavioral change is invisible: the query operator embedded in the filter string sent to the backend changes from `eq` to the value declared in the manifest.

The change touches three layers:

- `plugins/reviews/manifest.json` — adds `filterOperator: "gte"` to the rating filter extension point
- `src/main/frontend/src/api/plugins.ts` — adds optional `filterOperator` field to the `ExtensionPoint` interface
- `src/main/frontend/src/plugins/PluginFilterBar.tsx` — reads `filterOperator` instead of hardcoding `"eq"`

`PluginContext.tsx` does NOT need modification. `ResolvedExtensionPoint` extends `ExtensionPoint` via spread, so the new field is inherited automatically once `ExtensionPoint` is updated.

### Integration Strategy

**Decision**: No new UI components or layout changes. The `filterOperator` value is a configuration detail read at filter-string-build time inside `buildFilterString()`.

**Rationale**: The product list filter bar is already rendered natively by the host from manifest metadata. Adding an optional field to the manifest and reading it in the existing builder function is the minimal change that achieves the desired behavior without altering any visible UI structure.

---

## Existing Layout Analysis

### Application Structure

The product list page renders a horizontal filter bar above the product table. Plugin-contributed filters are rendered by `PluginFilterBar`, which iterates over `ResolvedExtensionPoint[]` objects returned by `getProductListFilters()`. Each filter control calls `buildFilterString()` to produce a filter token of the form:

```
{pluginId}:{filterKey}:{operator}:{value}
```

These tokens are passed to the product list API query.

### Key Components

- Filter bar: `src/main/frontend/src/plugins/PluginFilterBar.tsx`
- Extension point types: `src/main/frontend/src/api/plugins.ts` — `ExtensionPoint` interface
- Context + resolution: `src/main/frontend/src/plugins/PluginContext.tsx` — `ResolvedExtensionPoint` (extends `ExtensionPoint`)
- Plugin manifest: `plugins/reviews/manifest.json`

### Identified Patterns

- **Optional manifest fields**: `filterKey`, `filterType`, `icon`, `path`, `label` are all optional on `ExtensionPoint`. `filterOperator` follows the same pattern.
- **Fallback defaults in builder**: `buildFilterString()` already branches on `filterType` to choose `"bool"` vs `"eq"`. The same pattern (read field, fall back to default) applies for `filterOperator`.
- **No runtime UI for operator**: The operator is never shown to the user. It is embedded silently in the filter string. No label, tooltip, or control change is needed.

---

## Mockups

### Mockup 1: Manifest JSON — Before vs After

**Context**: `plugins/reviews/manifest.json`, the `product.list.filters` extension point entry.

```
BEFORE
──────────────────────────────────────────────────────────
  {
    "type": "product.list.filters",
    "label": "Rating",
    "filterKey": "rating",
    "filterType": "number",
    "priority": 20
  }

AFTER
──────────────────────────────────────────────────────────
  {
    "type": "product.list.filters",
    "label": "Rating",
    "filterKey": "rating",
    "filterType": "number",
    "filterOperator": "gte",        ← NEW FIELD
    "priority": 20
  }
──────────────────────────────────────────────────────────
  No other extension points in manifest.json are affected.
  No structural change — one key added to one object.
```

**Integration Points**:
- The host backend stores manifest JSON as-is and returns it via `GET /api/plugins`. The new field passes through without backend changes because the storage is schema-flexible (JSONB).
- The frontend `ExtensionPoint` interface must declare `filterOperator` as optional to avoid TypeScript errors when deserializing the API response.


### Mockup 2: ExtensionPoint Interface — Before vs After

**Context**: `src/main/frontend/src/api/plugins.ts`, the `ExtensionPoint` interface. This is the single source of type truth. `ResolvedExtensionPoint` in `PluginContext.tsx` inherits all fields via `extends ExtensionPoint`, so no change is needed there.

```
BEFORE
──────────────────────────────────────────────────────────
  export interface ExtensionPoint {
    type: ExtensionPointType;
    label?: string;
    icon?: string;
    path?: string;
    priority: number;
    filterKey?: string;
    filterType?: "boolean" | "string" | "number";
  }

AFTER
──────────────────────────────────────────────────────────
  export interface ExtensionPoint {
    type: ExtensionPointType;
    label?: string;
    icon?: string;
    path?: string;
    priority: number;
    filterKey?: string;
    filterType?: "boolean" | "string" | "number";
    filterOperator?: string;           ← NEW OPTIONAL FIELD
  }
──────────────────────────────────────────────────────────
  ResolvedExtensionPoint (PluginContext.tsx line 9) extends
  ExtensionPoint — no modification needed there.
```

**Integration Points**:
- `filterOperator` is typed as `string` (not a union) because the set of valid operators is defined by the backend query layer, not the frontend. Keeping it open avoids coupling.
- Optional (`?`) — absence means the caller falls back to `"eq"`.


### Mockup 3: buildFilterString() Logic — Before vs After

**Context**: `src/main/frontend/src/plugins/PluginFilterBar.tsx`, inside `PluginFilterControl`, lines 53-58.

```
BEFORE
──────────────────────────────────────────────────────────
  function buildFilterString(
    val: string | boolean | number | undefined
  ): string | undefined {
    if (val === undefined || val === "" || val === false)
      return undefined;

    const operator =
      filter.filterType === "boolean" ? "bool" : "eq";
                                                   ^^^
                                             always "eq" for
                                             number and string

    return `${filter.pluginId}:${filter.filterKey}:${operator}:${val}`;
  }

AFTER
──────────────────────────────────────────────────────────
  function buildFilterString(
    val: string | boolean | number | undefined
  ): string | undefined {
    if (val === undefined || val === "" || val === false)
      return undefined;

    const operator =
      filter.filterType === "boolean"
        ? "bool"
        : (filter.filterOperator ?? "eq");
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           reads manifest field; falls back
           to "eq" when absent

    return `${filter.pluginId}:${filter.filterKey}:${operator}:${val}`;
  }
──────────────────────────────────────────────────────────
  All other code in PluginFilterBar.tsx is unchanged.
  The boolean branch is unaffected — boolean filters
  always use "bool" regardless of filterOperator.
```

**Integration Points**:
- The change is confined to a single expression inside `buildFilterString()`. No other functions, components, or files in this module change.
- Existing plugins that do not declare `filterOperator` continue to receive `"eq"` — full backward compatibility.
- The reviews plugin, which declares `"filterOperator": "gte"`, will now emit `reviews:rating:gte:3` instead of `reviews:rating:eq:3` when the user types `3`.


### Mockup 4: Product List Filter Bar — Runtime Behavior

**Context**: The product list page filter toolbar as seen by the user. The visual appearance does not change. This diagram shows what is visible and what changes beneath the surface.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Products                                      [+ Add Product]      │
│                                                                     │
│  Filter bar (host-rendered from plugin manifests)                   │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ [==] In Stock    │  │  Search...   │  │  Rating    [ 3     ] │  │
│  └──────────────────┘  └──────────────┘  └──────────────────────┘  │
│      warehouse plugin      (host filter)       reviews plugin       │
│      boolean toggle        string input        number input         │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ Name              │ Category  │ Price   │ Stock            │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │ Product A         │ Tools     │ $12.00  │ [In Stock]       │   │
│  │ Product B         │ Parts     │ $45.00  │ [In Stock]       │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘

What changes when user types "3" in the Rating input:

  BEFORE: filter token = "reviews:rating:eq:3"
          → shows only products where rating == 3

  AFTER:  filter token = "reviews:rating:gte:3"
          → shows products where rating >= 3
          (controlled by filterOperator: "gte" in manifest)

  The Rating input control is VISUALLY IDENTICAL in both cases.
  No label change. No placeholder change. No new UI element.
  The operator change is invisible to the user.
```

**Component Reuse**:
- `Input` (Chakra UI) — unchanged, already used for number filter
- `PluginFilterControl` (`PluginFilterBar.tsx`) — same component, only `buildFilterString` changes internally
- Filter bar layout — unchanged

---

## Reusable Components

### Modified Files (3 total)

- **`ExtensionPoint` interface**: `src/main/frontend/src/api/plugins.ts`
  - Add `filterOperator?: string` — follows the existing optional-field pattern of `filterKey` and `filterType`

- **`buildFilterString()`**: `src/main/frontend/src/plugins/PluginFilterBar.tsx`
  - Replace `"eq"` literal with `filter.filterOperator ?? "eq"` — one expression change

- **`plugins/reviews/manifest.json`**
  - Add `"filterOperator": "gte"` to the `product.list.filters` entry

### Unchanged Files

- `src/main/frontend/src/plugins/PluginContext.tsx` — `ResolvedExtensionPoint` inherits the new field automatically
- All other plugin manifests — absence of `filterOperator` falls back to `"eq"`, no breakage
- All other `PluginFilterBar.tsx` control branches (boolean, string) — unaffected

---

## Implementation Notes

### Consistency Checklist

- The new field follows the established optional-field pattern in `ExtensionPoint` (`filterKey`, `filterType`, `icon`, `path` are all optional)
- Fallback to `"eq"` preserves existing behavior for all plugins that omit `filterOperator`
- The boolean branch (`"bool"`) is intentionally excluded from the `filterOperator` override — boolean filters always use `"bool"` regardless of what a manifest might declare

### Accessibility Considerations

- No new interactive elements are introduced
- The existing number `Input` component's keyboard, label, and focus behavior is unchanged

### Responsive Behavior

- No layout change. The filter bar already handles overflow on smaller screens. The new field adds no width or visual element.

---

## Alternatives Considered

### Option 1: New dedicated "gte" filterType (Rejected)

Add `"filterType": "number-gte"` as a distinct type variant instead of a separate `filterOperator` field.

**Why rejected**: Mixes two orthogonal concerns (rendered control type vs query operator). A `number-gte` type would still render as a number input — the same control. The `filterType` field governs UI rendering; operator selection is a separate concern and should be a separate field.

### Option 2: filterOperator as a typed union (e.g., "eq" | "gt" | "gte" | "lt" | "lte") (Considered, not chosen)

Would provide type safety in TypeScript for the field value.

**Why not chosen for now**: The valid operator set is defined by the backend query layer, not the frontend. Coupling the frontend type to a hardcoded union risks divergence if backend adds operators. A plain `string` with a documented fallback default is sufficient and more flexible. Can be tightened later once the operator set is stable.

### Option 3: Chosen Approach — optional `filterOperator?: string` with fallback (Selected)

**Why**: Minimal surface area. Single optional field. One expression changed in `buildFilterString()`. Zero visual change. Full backward compatibility. Follows the existing optional-field pattern in `ExtensionPoint`.

---

*Generated by ui-mockup-generator subagent*
