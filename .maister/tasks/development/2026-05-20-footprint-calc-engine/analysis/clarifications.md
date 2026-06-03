# Phase 1 Clarifications

Date: 2026-05-20

## Decisions

1. **Pricing Archetype source** → **Build archetype-lite in-house**
   Create minimal in-house primitives under `pl.devstyle.aj.archetype.pricing` matching the spec's
   expected API (Calculator, Component (Simple/Composite), Validity, Applicability, ComponentVersion,
   QuantityExtractor, ParameterValue). No external dependency. Full control over API surface.

2. **Error response format** → **Problem+JSON (RFC 7807) scoped to footprint**
   New `FootprintExceptionHandler` (@RestControllerAdvice on `pl.devstyle.aj.footprint.web` package
   or `@ControllerAdvice(basePackages = "pl.devstyle.aj.footprint")`) returns Spring `ProblemDetail`
   for footprint + export endpoints only. The existing `GlobalExceptionHandler` and `ErrorResponse`
   record continue serving all other modules.

3. **EmissionFactorPort / ProductAttributesPort** → **Stub adapters (in-memory) in this task**
   Engine ships with port interfaces and in-memory stub adapters seeded for tests. Real Emission
   Factor Management module and Product-domain integration are tracked as separate follow-up tasks.
   The 9 acceptance integration tests run against the stubs.

4. **CSV export slice** → **Include in this task**
   Build `pl.devstyle.aj.footprint.export` with `GET /api/footprints/calculations/{correlationId}/export?format=csv`
   reading the `footprint_audit_log` table. Satisfies persona Anna and acceptance criterion 10.

## Scope Boundaries (confirmed)

In scope: archetype-lite primitives, footprint engine (kernel domain package), facade + REST,
async audit (event + listener + entity + Liquibase changelog), Problem+JSON (scoped), in-memory
stub adapters, CSV export slice, 9 integration tests per spec acceptance.

Out of scope: production Emission Factor Management module, Product-domain extension for real
attribute resolution, frontend UI, kernel/plugin module split, caching layer.
