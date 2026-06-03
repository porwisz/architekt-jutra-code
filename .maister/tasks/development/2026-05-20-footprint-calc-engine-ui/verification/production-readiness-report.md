# Production Readiness Report — Footprint Calculator UI

**Date**: 2026-05-21
**Target**: production
**Recommendation**: **GO** (with one pre-existing concern to track post-deploy)
**Overall Readiness**: ~92%
**Deployment Risk**: Low
**Blockers**: 0 | **Concerns**: 2 | **Recommendations**: 4

## Executive Summary

Frontend-only additive UI feature: 3 new pages, 4 shared components, 2 hooks, cross-cutting helpers, wired into the existing protected `Layout`. No backend, no schema, no API contract, no build/deploy pipeline change. Bundle delta is small (no chart library — recharts deferred with the timeline page). All concerns are pre-existing platform gaps explicitly flagged as V1.1 follow-ups.

## Category Breakdown

| Category | Score | Status |
|----------|-------|--------|
| Configuration | 100% | Pass — no new env vars, Vite config unchanged |
| Monitoring | 60% | Pre-existing gap — no frontend error tracking app-wide |
| Resilience | 95% | Pass — Problem+JSON surfacing, retry buttons, cancellation flags |
| Performance | 95% | Pass — no chart lib added; memoised queries; debounced search |
| Security | 95% | Pass — XSS clean, JWT pattern reused, blob URLs revoked |
| Deployment | 100% | Pass — same `../resources/static` build target |

## Detailed Findings

### Configuration — PASS
- No new env vars; `vite.config.ts` untouched.
- No hardcoded hosts/ports/URLs.
- `auth_token` read from `localStorage` exactly as existing `client.ts`.

### Monitoring — CONCERN (pre-existing)
- Frontend has no Sentry/Rollbar/Datadog RUM anywhere — verified via grep.
- Not a blocker: platform-wide gap; this feature cannot be held to a higher bar.
- Mitigation: user-facing errors flow through `extractProblemMessage` with explicit error UI + retry affordances + `role="status"` live regions.
- Action: file separate V1.1 task for platform Sentry init.

### Resilience — PASS
- All `fetch` paths → `ApiError` → `extractProblemMessage` (Problem+JSON or status fallback).
- Page-level Retry button on 422 errors.
- `useEffect` cleanup uses `cancelled` flags; `setTimeout` cleared on unmount.
- Sequential CSV export fails-fast with `Stopped at i/n: <message>` live region.
- 401 redirect inherited from `client.ts` for JSON paths; CSV export currently throws on 401 (see W6 in code review).

### Performance — PASS
- Bundle impact minimal. No new runtime deps in `package.json`.
- Query memoisation prevents typing-induced refetch storms (pending/applied split).
- Debounced landing-page search (300 ms).
- URL sync compares string serialisation before `setSearchParams`.

### Security — PASS
- No `dangerouslySetInnerHTML` introduced.
- `mergeHeaders` strips caller-supplied `Authorization` (bearer-wins) — security improvement introduced by this task.
- Clipboard payload contains only `cg` (UUID) + base64 scenario inputs — no tokens, no PII.
- `downloadBlob` revokes the object URL synchronously after `click()`.
- Filenames derived from server-issued `correlationId` — no path-injection vector.

### Deployment — PASS
- Vite emits to `../resources/static` (unchanged).
- Routes added under existing protected `Layout` (`router.tsx:52-54`).
- Plugin catch-all `:pluginId/*` still last in route table — no resolution conflict.
- No DB migrations; rollback = revert frontend bundle.

## V1.1 Follow-ups (Already Documented)

- Timeline page (3rd mockup) — deferred (no backend history endpoint).
- Cross-product comparison — out of scope.
- Toast system — inline UI + live regions chosen for V1.
- Frontend observability — pre-existing platform gap.
- Named-destination dropdown replaced by numeric km input (FR-2).
- "View history" button from mockup omitted.

## Blockers
None.

## Concerns
1. **No frontend error tracking** (pre-existing, app-wide). Track as separate V1.1.
2. **Untyped scenario URL payload** — backend Problem+JSON UI is the safety net.

## Recommendations
1. Memoise `leafTotalsFor(scenario)` via `useMemo` keyed on `scenario.data`.
2. Add 401-handler to `getFootprintCsvExport` (or consolidate into shared client per code-review W6).
3. Extract duplicated inline `<input style={{...}}>` to a shared form-input component.
4. Sentry / RUM platform integration as separate V1.1 task.

## Final Verdict: **GO**

Ship it. Bundle impact minimal, no infrastructure changes, security posture clean, error handling surfaced, routes correctly mounted behind existing AuthGuard.
