# Problem Statement — Footprint Calculation Engine

## Problem

System produktowy **aj** musi dostarczać deterministyczne, audytowalne obliczenie **carbon footprint produktu** w zadanym kontekście (timestamp, destynacja, atrybuty produktu).

Wynik musi być:
- **Reprodukowalny historycznie** — wymóg regulacyjny: obliczenie z `timestamp=2026-01-15` używa styczniowych współczynników nawet gdy obecne są inne
- **Pełni rozbity na komponenty** — wymóg audytowy: każdy węzeł niesie kgCO₂, scope (1/2/3), użyty factor i jego validity

Silnik jest **core-service** w platformie aj (zawsze loadowany, nie plugin), służy zarówno frontendowi (synchroniczny REST) jak i innym backend services (in-process Spring bean) z identyczną semantyką.

## Constraints

1. **Pure-math API** — silnik przyjmuje pre-resolved numeric inputs (distances in km, weights in kg, days). Destination-name → distance resolution leży poza silnikiem (application layer).
2. **Pricing Archetype reuse** — `Calculator` / `Component` / `Validity` / `Applicability` biorą się z biblioteki `com.softwarearchetypes.pricing`. Nie projektujemy ich od zera.
3. **Dual surface** — REST endpoint + Spring bean. Identyczna semantyka, error model spójny (HTTP status mapping na typed exceptions).
4. **V1 includes both methods** — `calculateTotal()` i `calculateUnit()` first-class; `calculateUnit` reuses `calculateTotal` internally + normalizacja.
5. **No caching V1** — compute-every-call. Optymalizacja po pomiarach. Acceptable bo trees <20 nodes i factor lookups są indexed reads.
6. **Configurable strictness per call** — caller wybiera `strict` (fail-fast typed exception przy missing data) lub `lenient` (partial breakdown z warnings array).
7. **Engine writes audit log directly** — każde wywołanie persystowane do audit table. Trade-off: side effect w silniku, ale determinizm breakdownu zachowany.
8. **Core-service placement** — engine jest częścią kernela, nie pluginem ładowanym opcjonalnie.
9. **UI scope** — three views to design: detail, comparison (same product, different contexts), historical timeline.

## Success Criteria

1. **Determinizm** — `calculate(params)` zwraca identyczny breakdown dla identycznych params (audit log side-effect nie modyfikuje wyniku)
2. **Reprodukowalność historyczna** — `timestamp=past` używa factor versions valid wtedy
3. **Pełny breakdown** — drzewo z kgCO₂ na każdym węźle, scope, factor info na liściach
4. **Applicability** — produkty bez chłodzenia nie mają cold-storage poddrzewa (excluded, nie zero)
5. **Context sensitivity** — różna destynacja/sezon = różny wynik bez zmiany kodu
6. **Audit guarantee** — każde wywołanie ma odpowiadający rekord w audit log
7. **Strict mode fails loudly** — brakujący factor lub atrybut = typed exception → REST 422 z precyzyjnym powodem
8. **Lenient mode degrades gracefully** — partial breakdown + warnings array, frontend renderable
9. **UI breakdown surface** — detail / comparison / historical mockups acceptable do oddania frontend teamowi

## Key Assumptions

- Pricing Archetype library `com.softwarearchetypes.pricing` exists and exposes `Calculator`, `Component`, `SimpleComponentVersion`, `versionAt(t)`, applicability mechanics
- Emission Factor Management module exists (or będzie zaprojektowany osobno) — engine queries it read-only
- Product Catalog exposes attributes (materialWeightKg, supplierDistanceKm, requiresRefrigeration) via in-process query
- Audit table schema is engine's responsibility — Liquibase migration belongs to this module
- Frontend rendering of breakdown is a separate workstream — this design produces wireframes + JSON contract, not React code
