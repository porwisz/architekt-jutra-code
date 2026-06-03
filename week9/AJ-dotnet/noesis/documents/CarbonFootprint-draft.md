# High-Level Design: Footprint Calculation Engine

## Design Overview

System produktowy potrzebuje modułu obliczającego **carbon footprint produktu** — ile kg CO₂ generuje dany produkt w danym kontekście dostawy, z pełnym rozbiciem na składowe i reprodukowalnością historyczną.

Analiza wymagań (iteracje 1-3) wykazała, że problem obliczeniowy mapuje się na **Pricing Archetype Level 7**: drzewo komponentów, temporalnie wersjonowane współczynniki, kontekstowość, audytowalność. Jednostką wyjścia jest `kg CO₂` zamiast pieniędzy — struktura architektoniczna jest identyczna.

Moduł jest **pure function** — bezstanowy, nie zapisuje wyników, nie zarządza współczynnikami emisji. Współczynniki przychodzą z osobnego modułu (Emission Factor Management, klasyfikacja: Resource Contention — poza scope tego HLD).

**Key decisions:**

- Wzoruj implementację na Pricing Archetype z biblioteki `com.softwarearchetypes.pricing`
- Jeden typ kalkulatora (`SimpleFixedCalculator`) dla wszystkich komponentów — formuła zawsze `rate × quantity`
- Sezon modelowany jako temporal versioning (różne wersje współczynników z sezonowymi oknami validity), nie jako osobny parametr kontekstowy
- Scope 1/2/3 jako metadata na komponentach, nie jako koncept archetypowy

---

## Architecture (C4 Level 3 — Component)

Moduł obliczeniowy w kontekście systemu produktowego:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Footprint Calculation Module                         │
│                                                                          │
│  ┌──────────────────┐   ┌─────────────────────┐   ┌──────────────────┐  │
│  │ FootprintFacade  │──>│ Component Tree      │──>│ ComponentBreak-  │  │
│  │                  │   │ Resolution          │   │ down Builder     │  │
│  │ calculateTotal() │   │                     │   │                  │  │
│  │ calculateUnit()  │   │ - resolves versions │   │ - walks tree     │  │
│  │                  │   │   via versionAt(t)  │   │ - calls calcs    │  │
│  │ Input:           │   │ - checks applic.    │   │ - sums composites│  │
│  │  Parameters      │   │                     │   │ - returns tree   │  │
│  └────────┬─────────┘   └─────────┬───────────┘   └──────────────────┘  │
│           │                       │                                      │
│           │              ┌────────▼───────────┐                          │
│           │              │ Calculators        │                          │
│           │              │                    │                          │
│           │              │ calc-emission:     │                          │
│           │              │  rate × quantity   │                          │
│           │              │  = kg CO₂          │                          │
│           │              └────────────────────┘                          │
│           │                                                              │
└───────────┼──────────────────────────────────────────────────────────────┘
            │
     reads factor versions
     (valid at timestamp)
            │
            ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│ Emission Factor          │          │ Product Catalog          │
│ Management (RC)          │          │ (CRUD)                   │
│                          │          │                          │
│ - factor values          │          │ - product attributes     │
│ - validity periods       │          │ - material_weight_kg     │
│ - version history        │          │ - supplier_distance_km   │
│                          │          │ - requires_refrigeration │
│ (poza scope tego HLD)    │          │                          │
└──────────────────────────┘          └──────────────────────────┘
```

---

## Key Components

### Domain Components (Pricing Archetype mapping)

| Component               | Maps to                              | Responsibility                                               | Notes                                                        |
| ----------------------- | ------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **FootprintFacade**     | `PricingFacade`                      | Punkt wejścia — przyjmuje `FootprintParameters`, zwraca `FootprintBreakdown`. Dwa tryby: `calculateTotal()` (kg CO₂ całego produktu) i `calculateUnit()` (kg CO₂ per 100g via adapter). | Jedyny publiczny interfejs modułu                            |
| **FootprintParameters** | `Parameters`                         | Obiekt kontekstu obliczenia: `timestamp`, `productId`, `materialWeightKg`, `supplierDistanceKm`, `destinationDistanceKm`, `lastMileDistanceKm`, `storageDays`, `requiresRefrigeration` | Destination/season nie są tu jawnie — `timestamp` rozwiązuje sezon przez wersjonowanie, dystans przychodzi przeliczony z nazwy miasta |
| **FootprintBreakdown**  | `ComponentBreakdown`                 | Drzewo wyników — każdy węzeł (liść i kompozyt) ma: `componentId`, `kgCO2` (BigDecimal), `scope` (1/2/3), `emissionFactorUsed`, `factorValidFrom`. Korzenie kompozytowe mają sumę dzieci. | To jest artefakt audytowy — regulacje wymagają pełnego rozbicia |
| **EmissionCalculator**  | `Calculator` (SimpleFixedCalculator) | Pure function: `calculate(rate, quantity) → kgCO2`. Jeden typ dla wszystkich komponentów. `rate` to współczynnik emisji, `quantity` to jednostki aktywności (kg, km, dni, sztuki). | Calculator nie wie co oblicza — dostaje dwie liczby, mnoży   |

### Component Tree (9 liści, 4 kompozyty)

| Component ID                | Type      | Archetype mapping               | Calculator params                                            | Scope | Applicability                   |
| --------------------------- | --------- | ------------------------------- | ------------------------------------------------------------ | ----- | ------------------------------- |
| **product-footprint**       | Composite | Root CompositeComponent         | SumOf(materials, transport, packaging, cold-storage)         | —     | Zawsze aktywny                  |
| **materials**               | Composite | CompositeComponent              | SumOf(raw-material, processing)                              | —     | Zawsze aktywny                  |
| **raw-material**            | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKgMaterial`, qty: `materialWeightKg`          | 3     | Zawsze aktywny                  |
| **processing**              | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKgProcessed`, qty: `materialWeightKg`         | 1     | Zawsze aktywny                  |
| **transport**               | Composite | CompositeComponent              | SumOf(supplier-to-warehouse, warehouse-to-customer, last-mile) | —     | Zawsze aktywny                  |
| **supplier-to-warehouse**   | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKm` (truck type), qty: `supplierDistanceKm`   | 3     | Zawsze aktywny                  |
| **warehouse-to-customer**   | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKm` (truck type), qty: `destinationDistanceKm` | 3     | Zawsze aktywny                  |
| **last-mile**               | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKm` (van), qty: `lastMileDistanceKm`          | 3     | Zawsze aktywny                  |
| **packaging**               | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerUnit`, qty: `1`                               | 3     | Zawsze aktywny                  |
| **cold-storage**            | Composite | CompositeComponent              | SumOf(warehouse-refrig, transport-refrig, last-mile-cold)    | —     | `requiresRefrigeration == true` |
| **warehouse-refrigeration** | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKgPerDay`, qty: `storageDays`                 | 2     | Parent applicability            |
| **transport-refrigeration** | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKm` (refrig), qty: `supplierDistanceKm + destinationDistanceKm` | 2     | Parent applicability            |
| **last-mile-cold-chain**    | Simple    | SimpleComponent → calc-emission | rate: `kgCO2PerKm` (refrig van), qty: `lastMileDistanceKm`   | 3     | Parent applicability            |

### Versioning & Temporal Resolution

| Component             | What gets versioned    | Version structure                                            | Strategy           |
| --------------------- | ---------------------- | ------------------------------------------------------------ | ------------------ |
| Każdy SimpleComponent | Emission factor `rate` | `SimpleComponentVersion { calculatorId, rate, validity: [validFrom, validTo), definedAt }` | REJECT_OVERLAPPING |

**Sezon jako wersjonowanie** — przykład dla `warehouse-refrigeration`:

```
Versions:
  v1: rate = 0.045 kgCO₂/kg/day  validity = [2026-01-01, 2026-04-01)   -- zima
  v2: rate = 0.080 kgCO₂/kg/day  validity = [2026-04-01, 2026-10-01)   -- lato
  v3: rate = 0.045 kgCO₂/kg/day  validity = [2026-10-01, 2027-01-01)   -- zima
```

Zapytanie z `timestamp = 2026-07-15` automatycznie dostaje `rate = 0.080` (letni). Silnik nie zna pojęcia „sezon" — zna `versionAt(timestamp)`.

---

## Data Flow

### Obliczenie footprintu (główny scenariusz)

```
Frontend (product detail page)
  │
  │  GET /api/products/{id}/footprint?destination=Szczecin&asOf=2026-07-15
  │
  ▼
Application Layer (resolves context)
  │
  │  "Szczecin" → destinationDistanceKm = 570
  │  "July 2026" → timestamp = 2026-07-15T00:00:00Z
  │  Product attrs → materialWeightKg = 0.5, supplierDistanceKm = 2800, etc.
  │
  │  Builds FootprintParameters { timestamp, productId, materialWeightKg, ... }
  │
  ▼
FootprintFacade.calculateTotal(parameters)
  │
  │  1. Resolve component tree for productId
  │  2. For each SimpleComponent:
  │     a. versionAt(timestamp) → get emission factor rate valid at that moment
  │     b. Check applicability (e.g., requiresRefrigeration for cold-storage)
  │     c. calc-emission.calculate(rate, quantity) → kgCO₂
  │  3. For each CompositeComponent:
  │     a. Sum children kgCO₂
  │  4. Build FootprintBreakdown tree with values at every node
  │
  ▼
FootprintBreakdown
  │
  │  {
  │    total: 5.37 kgCO₂,
  │    components: [
  │      { id: "materials",  kgCO2: 1.35, scope: null, children: [
  │          { id: "raw-material", kgCO2: 0.45, scope: 3, factor: 0.9, factorValidFrom: "2026-01-01" },
  │          { id: "processing",   kgCO2: 0.90, scope: 1, factor: 1.8, factorValidFrom: "2026-01-01" }
  │      ]},
  │      { id: "transport",  kgCO2: 1.88, scope: null, children: [...] },
  │      { id: "packaging",  kgCO2: 0.42, scope: 3,    factor: 0.42, factorValidFrom: "2026-01-01" },
  │      { id: "cold-storage", kgCO2: 1.72, scope: null, children: [...] }
  │    ]
  │  }
  │
  ▼
Frontend renders breakdown table + bar chart + scope badges
```

### Historyczne porównanie

```
"What was the footprint in January 2026 for Warsaw?"

→ FootprintFacade.calculateTotal(
    parameters with timestamp = 2026-01-15, destinationDistanceKm = 15
  )

→ versionAt(2026-01-15) resolves to winter emission factors
→ Result: 2.9 kgCO₂ (no summer surcharges, shorter transport)
```

Nie ma osobnej logiki „historii" — ten sam silnik, inne parametry, wynik automatycznie inny.

---

## Integration Points

| Integration             | From              | To                         | Protocol                                                     | Notes                                                        |
| ----------------------- | ----------------- | -------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| Factor values read      | FootprintFacade   | Emission Factor Management | In-process query: `getFactorsValidAt(componentId, timestamp)` | Read-only. Silnik nigdy nie zapisuje do modułu Factor.       |
| Product attributes read | Application Layer | Product Catalog            | In-process query: `getProduct(productId)` → materialWeightKg, supplierDistanceKm, requiresRefrigeration | Resolves at call time, not cached                            |
| Destination resolution  | Application Layer | (lookup table / geocoding) | `"Szczecin"` → `{distanceKm: 570, lastMileKm: 15}`           | Konwersja nazwy miasta na dystansy — poza silnikiem obliczeniowym |
| Breakdown display       | Frontend          | FootprintFacade            | REST: `GET /api/products/{id}/footprint`                     | Returns FootprintBreakdown as JSON                           |

---

## Design Decisions

| #    | Decision                                   | Rationale                                                    |
| ---- | ------------------------------------------ | ------------------------------------------------------------ |
| 1    | Reużycie Pricing Archetype                 | Carbon footprint = pricing z jednostką kgCO₂. Identyczna struktura: Calculator (pure math), Component (tree), Validity (temporal versioning), Applicability (conditional activation). Budowanie od zera to reinventing the wheel. |
| 2    | Jeden typ kalkulatora (SimpleFixed)        | Wszystkie 9 liści używa `rate × quantity`. Złożoność nie jest w formule — jest w drzewie komponentów i wersjonowaniu. Jeśli w przyszłości pojawi się formuła nieliniowa (np. logarytmiczna emisja transportu), dodamy CompositeFunctionCalculator bez zmiany architektury. |
| 3    | Sezon = temporal versioning, nie parametr  | Eliminuje wymiar złożoności. Silnik nie musi „wiedzieć" o sezonach — `timestamp` automatycznie wybiera sezonowy współczynnik. Trade-off: moduł RC musi tworzyć więcej wersji (2/rok per sezonowy współczynnik). |
| 4    | REJECT_OVERLAPPING                         | Gwarantuje jednoznaczną rozdzielczość wersji — w każdym momencie dokładnie jeden współczynnik jest ważny. Upraszcza logikę i eliminuje edge-case'y. |
| 5    | Scope jako metadata, nie archetype concept | Scope 1/2/3 to stała etykieta na komponencie, nie dynamiczna logika. Nie wymaga osobnego modelowania — wystarczy pole na definicji komponentu. |
| 6    | FootprintBreakdown zawiera factor info     | Każdy liść breakdown niesie `factor` i `factorValidFrom` — wymóg regulacyjny. Audytor musi widzieć jakim współczynnikiem policzono i od kiedy obowiązywał. |

---

## Concrete Examples

### Example 1: Frozen berries, Szczecin, lato

**Given** produkt `OFB-330` (Organic Frozen Berries 500g, `requiresRefrigeration=true`, `materialWeightKg=0.5`, `supplierDistanceKm=2800`)
**When** `calculateTotal(timestamp=2026-07-15, destinationDistanceKm=570, lastMileDistanceKm=15, storageDays=14)`
**Then** silnik:

- Resolves summer factor versions (validity zawiera lipiec)
- Activates cold-storage subtree (requiresRefrigeration=true)
- Returns breakdown: materials 1.35 + transport 1.88 + packaging 0.42 + cold-storage 1.72 = **5.37 kgCO₂**

### Example 2: Ten sam produkt, Warszawa, zima

**Given** ten sam `OFB-330`
**When** `calculateTotal(timestamp=2026-01-15, destinationDistanceKm=15, lastMileDistanceKm=5, storageDays=14)`
**Then** silnik:

- Resolves winter factor versions (niższe stawki chłodzenia)
- Shorter transport distances
- Returns: materials 1.35 + transport 0.48 + packaging 0.42 + cold-storage 0.65 = **2.90 kgCO₂**

### Example 3: Produkt niechłodzony

**Given** produkt `CAW-042` (Classic Analog Watch, `requiresRefrigeration=false`)
**When** `calculateTotal(timestamp=2026-07-15, destinationDistanceKm=15, ...)`
**Then** cold-storage subtree jest **excluded** (applicability false) → breakdown ma tylko materials + transport + packaging = **0.8 kgCO₂**

### Example 4: Zmiana współczynnika — reprodukowalność

**Given** współczynnik transportu zmieniony z `0.00058` na `0.00062` kgCO₂/kg/km z `validFrom=2026-03-15`
**When** `calculateTotal(timestamp=2026-02-01, ...)` (luty — przed zmianą)
**Then** `versionAt(2026-02-01)` zwraca starą wersję `0.00058` → wynik obliczony starym współczynnikiem
**When** `calculateTotal(timestamp=2026-04-01, ...)` (kwiecień — po zmianie)
**Then** `versionAt(2026-04-01)` zwraca nową wersję `0.00062` → wynik obliczony nowym współczynnikiem

---

## Out of Scope

- **Emission Factor Management** — zarządzanie współczynnikami (CRUD + wersjonowanie + locking + limit 4x/rok). Osobny moduł, klasyfikacja: Resource Contention. Osobny HLD.
- **Emission Budget Ledger** — budżet kwartalny z debits/credits. Osobny moduł, klasyfikacja: Accounting Archetype. Osobny HLD.
- **Historical Audit / Compliance Reporting** — zamrażanie footprintu w momencie sprzedaży. Konsumuje wynik tego silnika, ale jest osobnym kontekstem.
- **Product Catalog** — CRUD produktów. Ten moduł czyta atrybuty produktu, ale ich nie zarządza.
- **Eco Rating (A/B/C)** — derived z wyniku footprintu. Logika prezentacyjna, nie silnik obliczeniowy.
- **Frontend** — wizualizacja breakdown (tabela, bar chart, selektory kontekstu). Konsumuje JSON z FootprintBreakdown.

---

## Success Criteria

1. **Pure function** — `calculateTotal(params)` zwraca identyczny wynik dla identycznych parametrów, niezależnie od momentu wywołania (determinizm)
2. **Historyczna reprodukowalność** — footprint z `timestamp=January` używa styczniowych współczynników, nawet gdy obecne są inne
3. **Pełny breakdown** — wynik zawiera drzewo z kgCO₂ na każdym węźle, scope badge, i factor info na każdym liściu
4. **Sezonowość przez versioning** — zapytanie z lipcowym timestamp automatycznie dostaje letnie współczynniki bez jawnego parametru „sezon"
5. **Applicability** — produkty bez chłodzenia nie mają cold-storage w breakdown (excluded, nie zero)
6. **Context sensitivity** — ten sam produkt, różna destynacja = różny wynik (dystans wpływa na transport i cold chain)