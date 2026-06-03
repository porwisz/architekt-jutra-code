package pl.devstyle.aj.footprint.spi.stub;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.archetype.pricing.ComponentVersion;
import pl.devstyle.aj.archetype.pricing.Validity;
import pl.devstyle.aj.footprint.internal.ports.EmissionFactorPort;

/**
 * Dev/test stub. Enabled by {@code app.footprint.adapters=in-memory}. In production a real
 * EFM-backed adapter must be provided; this class must not be on the active classpath then.
 */
@Component
@ConditionalOnProperty(name = "app.footprint.adapters", havingValue = "in-memory", matchIfMissing = false)
class InMemoryEmissionFactorPort implements EmissionFactorPort {

    // Stable factor version ids — tests assert these literals.
    static final String FV_RAW_MATERIAL_WINTER       = "11111111-1111-1111-1111-000000000001";
    static final String FV_RAW_MATERIAL_SUMMER       = "11111111-1111-1111-1111-000000000002";
    static final String FV_PROCESSING                = "11111111-1111-1111-1111-000000000003";
    static final String FV_SUPPLIER_TO_WAREHOUSE     = "11111111-1111-1111-1111-000000000004";
    static final String FV_WAREHOUSE_TO_CUSTOMER     = "11111111-1111-1111-1111-000000000005";
    static final String FV_LAST_MILE                 = "11111111-1111-1111-1111-000000000006";
    static final String FV_PACKAGING                 = "11111111-1111-1111-1111-000000000007";
    static final String FV_WAREHOUSE_REFRIGERATION   = "11111111-1111-1111-1111-000000000008";
    static final String FV_TRANSPORT_REFRIGERATION   = "11111111-1111-1111-1111-000000000009";
    static final String FV_LAST_MILE_COLD_CHAIN      = "11111111-1111-1111-1111-000000000010";

    private static final Instant JAN_2026 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant APR_2026 = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant JUN_2026 = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");

    private final Map<ComponentId, List<ComponentVersion>> versionsByComponent;
    private final Map<String, ComponentVersion> versionsById;

    InMemoryEmissionFactorPort() {
        Map<ComponentId, List<ComponentVersion>> byComponent = new HashMap<>();

        ComponentId rawMaterial = new ComponentId("raw-material");
        byComponent.put(rawMaterial, List.of(
                new ComponentVersion(rawMaterial, FV_RAW_MATERIAL_WINTER,
                        new BigDecimal("0.90"), new Validity(JAN_2026, JUN_2026)),
                new ComponentVersion(rawMaterial, FV_RAW_MATERIAL_SUMMER,
                        new BigDecimal("0.95"), new Validity(JUN_2026, JAN_2027))
        ));

        ComponentId processing = new ComponentId("processing");
        byComponent.put(processing, List.of(
                new ComponentVersion(processing, FV_PROCESSING,
                        new BigDecimal("0.30"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId supplierToWarehouse = new ComponentId("supplier-to-warehouse");
        byComponent.put(supplierToWarehouse, List.of(
                new ComponentVersion(supplierToWarehouse, FV_SUPPLIER_TO_WAREHOUSE,
                        new BigDecimal("0.00012"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId warehouseToCustomer = new ComponentId("warehouse-to-customer");
        byComponent.put(warehouseToCustomer, List.of(
                new ComponentVersion(warehouseToCustomer, FV_WAREHOUSE_TO_CUSTOMER,
                        new BigDecimal("0.00012"), new Validity(JAN_2026, JAN_2027))
        ));

        // last-mile covers the full 2026 year. The gap [2027-01-01, ∞) is intentional
        // so Scenario D (strict, missing factor) and Scenario E (lenient, missing factor)
        // can be exercised with asOf outside 2026 (TG-8 controller tests use asOf=2027-... ).
        ComponentId lastMile = new ComponentId("last-mile");
        byComponent.put(lastMile, List.of(
                new ComponentVersion(lastMile, FV_LAST_MILE,
                        new BigDecimal("0.00025"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId packaging = new ComponentId("packaging");
        byComponent.put(packaging, List.of(
                new ComponentVersion(packaging, FV_PACKAGING,
                        new BigDecimal("0.10"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId warehouseRefrigeration = new ComponentId("warehouse-refrigeration");
        byComponent.put(warehouseRefrigeration, List.of(
                new ComponentVersion(warehouseRefrigeration, FV_WAREHOUSE_REFRIGERATION,
                        new BigDecimal("0.0015"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId transportRefrigeration = new ComponentId("transport-refrigeration");
        byComponent.put(transportRefrigeration, List.of(
                new ComponentVersion(transportRefrigeration, FV_TRANSPORT_REFRIGERATION,
                        new BigDecimal("0.00003"), new Validity(JAN_2026, JAN_2027))
        ));

        ComponentId lastMileColdChain = new ComponentId("last-mile-cold-chain");
        byComponent.put(lastMileColdChain, List.of(
                new ComponentVersion(lastMileColdChain, FV_LAST_MILE_COLD_CHAIN,
                        new BigDecimal("0.00020"), new Validity(JAN_2026, JAN_2027))
        ));

        // Enforce REJECT_OVERLAPPING invariant at startup, per componentId.
        byComponent.forEach((id, versions) ->
                Validity.assertNonOverlapping(versions.stream().map(ComponentVersion::validity).toList()));

        Map<String, ComponentVersion> byId = new HashMap<>();
        byComponent.values().forEach(list ->
                list.forEach(v -> byId.put(v.factorVersionId(), v)));

        this.versionsByComponent = Map.copyOf(byComponent);
        this.versionsById = Map.copyOf(byId);
    }

    @Override
    public Optional<ComponentVersion> versionAt(ComponentId componentId, Instant t) {
        List<ComponentVersion> versions = versionsByComponent.get(componentId);
        if (versions == null) {
            return Optional.empty();
        }
        return versions.stream()
                .filter(v -> v.validity().covers(t))
                .findFirst();
    }

    @Override
    public Optional<ComponentVersion> factorVersionById(String factorVersionId) {
        return Optional.ofNullable(versionsById.get(factorVersionId));
    }
}
