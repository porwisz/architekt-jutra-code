# Codebase Analysis Report

**Date**: 2026-05-20
**Task**: Implement frontend UI for carbon footprint calculation engine (detail, comparison, historical timeline screens)
**Description**: Three HTML mockups under `analysis/design-context/mockups/` are binding inputs. Backend exposes `GET /api/products/{productId}/footprint`, `GET /api/footprints/calculations/{correlationId}/export?format=csv`, and emits Problem+JSON 400/409/422 errors.
**Analyzer**: codebase-analyzer skill (3 Explore agents: File Discovery, Code Analysis, Pattern Mining)

---

## Summary

The host frontend (`src/main/frontend/`) is a React 19 + TypeScript + Vite + React Router v7 + Chakra UI v3 app using manual `useState`+`useEffect` data fetching (no react-query) over a thin `fetch` wrapper. Existing product pages provide clean templates for all three footprint screens, but four supporting capabilities are missing and must be scaffolded: a Problem+JSON parser, a CSV blob download utility, CO₂e/per-100g formatters, and a chart library decision for the timeline view. Risk is **low-to-medium** — patterns are well established and consistent, the only genuine unknown is which charting approach to adopt.

---

## Files Identified

### Primary Files (templates and integration points)

- **`src/main/frontend/src/pages/ProductDetailPage.tsx`** (211L) — single-resource detail with breadcrumb, heading, tabs, plugin integration. **Primary template for footprint detail.**
- **`src/main/frontend/src/pages/ProductListPage.tsx`** (375L) — debounced search, filter bar, table, inline delete with `ConfirmDialog`. **Template for footprint comparison (multi-select instead of search).**
- **`src/main/frontend/src/api/client.ts`** (84L) — `fetch` wrapper with `ApiError { status, statusText, body }`, Bearer token from `localStorage("auth_token")`, auto 401 redirect. Methods `get/post/put/patch/delete`. **Raw body kept in `ApiError.body` — no RFC 7807 normalisation yet.** Verify in spec phase whether callers pass `/api/...` prefixed paths.
- **`src/main/frontend/src/api/products.ts`** (70L) — typed domain endpoint module; `URLSearchParams` for query strings. **Pattern for new `src/api/footprints.ts`.**
- **`src/main/frontend/src/hooks/useProducts.ts`** (78L) — `{ data, loading, error, refetch, create, update, remove }`. **Template for `useProductFootprint`, `useFootprintComparison`, `useProductFootprintHistory`.**
- **`src/main/frontend/src/router.tsx`** (59L) — `createBrowserRouter`, nested protected `Layout` with `AuthGuard` + `PluginProvider`. Add new footprint routes here.
- **`src/main/frontend/src/components/layout/Sidebar.tsx`** (100L) — `NavItem` with active-route detection. Add Footprint nav entry (lucide `Leaf`).
- **`src/main/frontend/src/components/layout/MobileDrawer.tsx`** (90L) — keep in sync with Sidebar.
- **`src/main/frontend/src/components/layout/Header.tsx`** (90L) — breadcrumbs via `getBreadcrumbs(location.pathname)`. Extend for new routes.
- **`src/main/frontend/src/utils/format.ts`** (7L) — only `formatDate`; extend with `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`. Note: `formatPrice` is duplicated inline in `ProductDetailPage` + `ProductListPage` — opportunity to extract.

### Related Files

- `src/main/frontend/src/main.tsx` (18L) — `ChakraProvider → AuthProvider → RouterProvider`
- `src/main/frontend/src/theme/index.ts` (43L) — Chakra v3 `createSystem`; brand (blue) + accent (amber); body bg `#FAFAF9`. Use tokens, not inline hex.
- `src/main/frontend/src/components/layout/AppShell.tsx` (26L) — `Flex` with `Outlet`
- `src/main/frontend/src/components/shared/` — `ConfirmDialog.tsx`, `EmptyState.tsx`, `PrimaryButton.tsx`, `Icons.tsx` (add `FootprintIcon` to `ICON_MAP`)
- `src/main/frontend/src/auth/AuthContext.tsx` (93L) + `AuthGuard.tsx` (26L) — JWT in localStorage; redirects to `/login?returnTo`
- `src/main/frontend/src/plugins/PluginContext.tsx` (113L) — extension points; **not required for V1 footprint screens**
- `src/main/frontend/src/hooks/useCategories.ts` (60L) — identical pattern to `useProducts`
- `src/main/frontend/vite.config.ts` (18L) — outputs to `../resources/static`, proxies `/api/*` → `http://localhost:8080`
- `src/main/frontend/vitest.config.ts` + `src/test/setup.ts` — Vitest globals/jsdom + `@testing-library/jest-dom`
- `src/main/frontend/src/test/pages.test.tsx` (153L) + `foundation.test.tsx` (74L) — per-file `renderWithProviders` wrapping `ChakraProvider` + `PluginProvider` + `MemoryRouter`; `vi.mock` at module top, `vi.resetAllMocks()` in `beforeEach`

---

## Current Functionality

Container/presentational split. `pages/` are containers; `components/shared/` are presentational; `components/layout/` is the chrome.

### Data flow
1. Page calls `useParams()` → custom hook (`useProducts`)
2. Hook holds `{ data, loading, error }`; `useCallback` async loader; `useEffect` triggers load + exposes `refetch`
3. Hook calls typed API module → `api.get/post/...` → `fetch` wrapper
4. Errors thrown as `ApiError` (raw `body`); hook stores error string in state
5. Page renders conditionally: loading → error → not-found → content

### Auth
JWT in `localStorage("auth_token")`, `atob`-decoded client-side for `sub` + `permissions[]`, `exp` checked on load. `AuthGuard` wraps protected routes. Host app reads localStorage directly — `hostApp.getToken()` is the plugin-iframe pattern, not for host pages.

### Gaps relevant to this task
- **No RFC 7807 Problem+JSON parser** — body sits opaque on `ApiError.body`
- **No toast / notification system** — pages render `<Text color="red.500">{error}</Text>` inline
- **No error boundary**
- **No chart library installed**
- **No blob download helper**
- **No CO₂e or per-100g formatters**

---

## Dependencies

### New code depends on
- `src/api/client.ts` (`api`, `ApiError`) — use as-is
- `src/hooks/useProducts.ts` shape — copy
- `src/utils/format.ts` — extend
- `src/components/shared/` — `EmptyState`, `PrimaryButton`, `ConfirmDialog`
- `src/components/layout/*` — `AppShell`, `Header`, `Sidebar`, `MobileDrawer`
- Backend: `GET /api/products/{productId}/footprint`, `GET /api/footprints/calculations/{correlationId}/export?format=csv`

### Consumers of files being modified
- `router.tsx` ← `main.tsx`
- `Sidebar.tsx` / `MobileDrawer.tsx` ← `AppShell`/`Layout`
- `Header.tsx` (`getBreadcrumbs`) ← `AppShell`
- `utils/format.ts` ← `ProductDetailPage`, `ProductListPage` (additive)
- `Icons.tsx` (`ICON_MAP`, `resolveIcon`) ← plugin menu + shared components

**Impact scope: Low** — additive throughout.

---

## Test Coverage

- `src/test/pages.test.tsx` (153L) — page-level integration with mocked API
- `src/test/foundation.test.tsx` (74L) — provider/setup
- Pattern: per-file `renderWithProviders`, `vi.mock` top-level, `vi.resetAllMocks()` in `beforeEach`, `vi.mocked()` for type-safe config
- Norm: ~2–8 tests per feature
- Net new for footprint: happy path, loading, error (incl. Problem+JSON 422), CSV export trigger

---

## Coding Patterns

### Naming
- Pages: `*Page.tsx`
- Hooks: `use*.ts`
- API modules: `[domain].ts`
- Components: PascalCase

### Architecture
- Container/presentational split
- Hook returns `{ data, loading, error, refetch, ... }`
- Page: `useParams → useState → useCallback loader → useEffect → conditional render`
- Always `void` async calls in `useEffect`
- Chakra tokens (`colorPalette`, semantic tokens) — no inline hex
- API paths: confirm `/api/...` prefix convention in spec

### Anti-patterns
- `formatPrice` duplicated across two pages — extract
- Inline error rendering style varies
- Some hex values bypass theme tokens

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| New files | ~10–12 (3 pages, 3 hooks, 1 api module, 2–3 utils + nav additions) | Medium |
| Files modified | 5–6 (`router.tsx`, `Sidebar.tsx`, `MobileDrawer.tsx`, `Header.tsx`, `Icons.tsx`, `utils/format.ts`) | Medium |
| Dependencies to add | 0–1 (chart library, TBD) | Low |
| Consumers affected | Layout consumers only | Low |
| Test coverage | Net new, but pattern exists | Medium |

### Overall: **Moderate**

Volume is moderate, conceptual complexity is **low** — existing pages give a 1:1 template for two of three screens. Timeline is the only screen without a direct precedent; complexity depends on the mockup's chart requirements.

---

## Key Findings

### Strengths
- Clean container/presentational split, uniform hook shape
- Layout chain (`AppShell` + `Header` + `Sidebar` + `MobileDrawer`) cleanly separated
- Theme tokens established
- Test infrastructure ready
- Typed API client with `ApiError`

### Concerns
- **No Problem+JSON normalisation** — must build before footprint UI can surface meaningful 422 messages
- **No chart library** — historical timeline needs decision (recharts vs victory vs plain SVG) before implementation
- **No toast/notification system** — CSV export success will need inline status or a new toast component
- **Sidebar / MobileDrawer / Header breadcrumbs must be kept in sync manually**
- **API path prefix inconsistency** — verify in spec

### Opportunities
- Extract `formatPrice` alongside new formatters
- Small shared error-display component to standardise inline error rendering
- Centralise Problem+JSON parser as a reusable helper (recommended over scoping per-hook)

---

## Impact Assessment

### Primary changes (new files)
- `src/main/frontend/src/api/footprints.ts` — typed `getProductFootprint`, `getFootprintCsvExport` (returns `Blob`), `ProblemDetail` type + `isProblemDetail` helper
- `src/main/frontend/src/hooks/useProductFootprint.ts`
- `src/main/frontend/src/hooks/useFootprintComparison.ts`
- `src/main/frontend/src/hooks/useProductFootprintHistory.ts`
- `src/main/frontend/src/pages/ProductFootprintPage.tsx` (detail)
- `src/main/frontend/src/pages/FootprintComparisonPage.tsx`
- `src/main/frontend/src/pages/ProductFootprintTimelinePage.tsx`
- `src/main/frontend/src/utils/download.ts` (blob → file download helper)
- Tests in `src/main/frontend/src/test/`

### Related changes (modifications)
- `router.tsx` — register 3 new routes under protected `Layout`
- `Sidebar.tsx` — add Footprint nav entry
- `MobileDrawer.tsx` — mirror Sidebar entry
- `Header.tsx` — extend `getBreadcrumbs` for new routes
- `Icons.tsx` — add `FootprintIcon` to `ICON_MAP`
- `utils/format.ts` — add `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`

### Risk level: **Low-Medium**
- Low: clear templates, low consumer impact, additive changes
- Medium: chart library decision, Problem+JSON parser is net-new shared abstraction, sync risk across nav files

---

## Recommendations

### Architecture
- Mirror `ProductDetailPage` for footprint detail (skip tabs unless mockup demands)
- Mirror `ProductListPage` for comparison (multi-select instead of debounced search)
- Treat timeline as highest-uncertainty — read the mockup first

### Cross-cutting scaffolding (build once)
1. `ProblemDetail` type + `isProblemDetail(body)` helper — centralise
2. `src/utils/download.ts` with `downloadBlob(blob, filename)` helper
3. Extend `src/utils/format.ts` with `formatCO2eKg`, `formatPer100g`, `formatRelativeDate`; move `formatPrice` here
4. Decide chart library before timeline implementation. Recommend **recharts** unless mockup is sparkline-simple

### Integration
- Footprint as **top-level sidebar entry** (comparison is cross-product)
- Update Sidebar + MobileDrawer in same commit
- All routes under existing protected `Layout`
- Chakra tokens throughout

### Open decisions for spec phase
1. Chart library: recharts vs victory vs plain SVG
2. Toast vs inline status: recommend inline for V1
3. Nav placement: top-level vs nested
4. react-query introduction: recommend **defer**
5. Problem+JSON helper scope: shared (recommend)
6. API path prefix convention: verify
7. Route naming: confirm against IA

### Plugin extensibility
V1 belongs in host app (footprint engine is kernel-side). Plugin extension points are out of scope.

---

## Next Steps

Invoke **gap-analyzer**. Validate against mockups:
1. Chart library choice for timeline
2. Problem+JSON parser fields surfaced to UI
3. CSV export UX (button placement, success feedback)
4. Multi-product comparison selection UX
5. API client `/api` prefix convention
6. Route naming
