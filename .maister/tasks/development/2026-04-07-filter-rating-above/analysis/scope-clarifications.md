# Scope Clarifications

**Date**: 2026-04-08

## Decisions Made

### scope-filter-operator-approach
**Decision**: Option B — Add optional `filterOperator` field to manifest + `PluginFilterBar` reads it
**Rationale**: User wants extensibility; allows future number filters with `eq` semantics

### test-gte-coverage
**Decision**: Option B — Update existing integration test + add static unit test for `parseFilter` gte case
**Rationale**: User wants broader coverage of the new backend operator
