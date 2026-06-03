# Design Context — Footprint Calculation Engine

## Project Background

**aj** is a plugin-based microkernel platform (pre-alpha scaffolding phase), Java 25 + Spring Boot 4.0.5, PostgreSQL with JPA + JOOQ, Liquibase migrations. No business logic exists yet — this module is greenfield within a greenfield platform.

Target package: `pl.devstyle.aj.<module>` once plugin framework is selected (PF4J / OSGi / JPMS — pending).

## Imported Research (source of truth)

Imported from research task `2026-04-17-footprint-calc-engine`. Full document at `context/research-context/high-level-design.md`. Key findings:

### Problem Domain
- System produktowy potrzebuje kalkulacji **carbon footprint produktu** — kg CO₂ per produkt w danym kontekście dostawy
- Wymóg: pełne rozbicie na komponenty (audyt), reprodukowalność historyczna (regulacja), kontekstowość (destynacja/sezon)

### Architectural Classification
- Problem mapuje się na **Pricing Archetype Level 7** — drzewo komponentów, temporal versioning, applicability, audytowalność
- Jednostka wyjścia: `kg CO₂` zamiast pieniędzy. Struktura identyczna jak pricing.
- **Reuse decyzja**: biblioteka `com.softwarearchetypes.pricing` — nie buduje się od zera

### Module Boundary
- Moduł jest **pure function**, bezstanowy
- Nie zapisuje wyników, nie zarządza współczynnikami emisji
- Wejście: `FootprintParameters` (timestamp, productId, fizyczne wielkości, dystanse, dni)
- Wyjście: `FootprintBreakdown` (drzewo z kgCO₂ na każdym węźle, scope badges, factor info)

### Key Components (from research)
- `FootprintFacade` (entry point, `calculateTotal()` / `calculateUnit()`)
- `FootprintParameters` (context object)
- `FootprintBreakdown` (audit-ready tree result)
- `EmissionCalculator` (SimpleFixed: `rate × quantity → kgCO₂`)
- Component tree: 9 liści, 4 kompozyty (materials, transport, packaging, cold-storage)
- Versioning: temporal, REJECT_OVERLAPPING strategy
- Sezon = wersjonowanie (nie osobny parametr)
- Scope 1/2/3 = metadata na komponencie

### Out of Scope (research-confirmed)
- Emission Factor Management (Resource Contention archetype, osobny moduł)
- Emission Budget Ledger (Accounting archetype, osobny moduł)
- Historical Audit / Compliance Reporting (consumer wyniku)
- Product Catalog CRUD (czyta tylko)
- Eco Rating A/B/C (derived, prezentacyjne)
- Frontend wizualizacja breakdown (konsument JSON-a) — **NOTE**: produkt design task obejmuje wireframes UI breakdownu (user override w Phase 0)

## Implications for Design

1. **Engine surface jest minimalny** — `FootprintFacade.calculateTotal(parameters)`. Cała złożoność jest w wewnętrznym tree resolution + versioning.
2. **UI scope (user override)** — wireframes breakdownu (jak konsument widzi wynik) są częścią designu, choć rendering jest poza silnikiem. Personas i journeys obejmą zarówno konsumentów silnika (developer integrujący) jak i end-userów breakdownu (audytor, sustainability manager, konsument).
3. **Integration points** są wąskie: Emission Factor Management (read), Product Catalog (read), Frontend (REST). Każdy jest in-process query lub HTTP.
4. **Pricing Archetype reuse** dyktuje pewne decyzje — Calculator, Component, Validity, Applicability są już dane. Co zostaje do zaprojektowania: API kształt (REST shape, error model), unit conversion (calculateUnit), applicability rules wire-up, error handling przy brakujących factorach, observability/audit log.
5. **Greenfield platforma** — możemy projektować bez kompromisów backward compatibility, ale musimy zdecydować jak moduł integruje się z planowanym pluginem frameworkiem (czy jest plugin, czy core service).

## Cross-References

- `context/research-context/high-level-design.md` — pełny HLD (architecture, component tree, examples, decisions)
- `.maister/docs/project/tech-stack.md` — Java 25, Spring Boot 4.0.5, JPA+JOOQ, PostgreSQL
- `.maister/docs/project/architecture.md` — microkernel target, pre-alpha state

## Open Questions (for Phase 2)

- Czy moduł jest pluginem (loadowanym przez framework) czy core service?
- Jak frontend dostaje wynik — synchroniczny REST endpoint per request, czy materialised view?
- Czy `calculateUnit()` (kg CO₂ per 100g) jest pierwotnym kontraktem czy adapterem nad `calculateTotal()`?
- Error handling: co zwracamy gdy brakuje factor version dla timestamp? Brakuje atrybutu produktu?
- Observability: czy każde wywołanie loguje breakdown do audit-trail (poza scope tego silnika — ale interfejs musi to umożliwić)?
- Concurrency / caching: czy factor lookup jest cache'owany w obrębie jednego wywołania? Per-request? In-process?
