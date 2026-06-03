package pl.devstyle.aj.footprint.internal;

import org.springframework.stereotype.Component;
import pl.devstyle.aj.archetype.pricing.Applicability;
import pl.devstyle.aj.archetype.pricing.CalculatorId;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.archetype.pricing.CompositeComponent;
import pl.devstyle.aj.archetype.pricing.QuantityExtractor;
import pl.devstyle.aj.archetype.pricing.SimpleComponent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ComponentTreeRegistry {

    static final ComponentId ROOT_ID = new ComponentId("product-footprint");

    private static final CalculatorId SIMPLE_FIXED = new CalculatorId("simple-fixed");

    private final Map<ComponentId, pl.devstyle.aj.archetype.pricing.Component> components;

    ComponentTreeRegistry() {
        Map<ComponentId, pl.devstyle.aj.archetype.pricing.Component> map = new LinkedHashMap<>();

        // Leaves — materials
        addLeaf(map, "raw-material", 3, Applicability.ALWAYS,
                pv -> pv.materialWeightKg());
        addLeaf(map, "processing", 2, Applicability.ALWAYS,
                pv -> pv.materialWeightKg());

        // Leaves — transport
        addLeaf(map, "supplier-to-warehouse", 3, Applicability.ALWAYS,
                pv -> pv.materialWeightKg().multiply(pv.supplierDistanceKm()));
        addLeaf(map, "warehouse-to-customer", 3, Applicability.ALWAYS,
                pv -> pv.materialWeightKg().multiply(pv.destinationDistanceKm()));
        addLeaf(map, "last-mile", 3, Applicability.ALWAYS,
                pv -> pv.materialWeightKg().multiply(pv.lastMileDistanceKm()));

        // Leaf — packaging
        addLeaf(map, "packaging", 3, Applicability.ALWAYS,
                pv -> pv.materialWeightKg());

        // Leaves — cold-storage
        addLeaf(map, "warehouse-refrigeration", 2, Applicability.REFRIGERATED_ONLY,
                pv -> pv.materialWeightKg().multiply(BigDecimal.valueOf(pv.storageDays())));
        addLeaf(map, "transport-refrigeration", 3, Applicability.REFRIGERATED_ONLY,
                pv -> pv.materialWeightKg().multiply(pv.supplierDistanceKm().add(pv.destinationDistanceKm())));
        addLeaf(map, "last-mile-cold-chain", 3, Applicability.REFRIGERATED_ONLY,
                pv -> pv.materialWeightKg().multiply(pv.lastMileDistanceKm()));

        // Composites — leaf children must exist before composite refers to them, but record
        // only carries IDs so ordering is for readability.
        addComposite(map, "materials", Applicability.ALWAYS,
                List.of("raw-material", "processing"));
        addComposite(map, "transport", Applicability.ALWAYS,
                List.of("supplier-to-warehouse", "warehouse-to-customer", "last-mile"));
        addComposite(map, "cold-storage", Applicability.REFRIGERATED_ONLY,
                List.of("warehouse-refrigeration", "transport-refrigeration", "last-mile-cold-chain"));
        addComposite(map, ROOT_ID.value(), Applicability.ALWAYS,
                List.of("materials", "transport", "packaging", "cold-storage"));

        this.components = Map.copyOf(map);
    }

    Map<ComponentId, pl.devstyle.aj.archetype.pricing.Component> all() {
        return components;
    }

    pl.devstyle.aj.archetype.pricing.Component byId(ComponentId id) {
        var c = components.get(id);
        if (c == null) {
            throw new IllegalStateException("Unknown component: " + id.value());
        }
        return c;
    }

    private static void addLeaf(
            Map<ComponentId, pl.devstyle.aj.archetype.pricing.Component> map,
            String id,
            int scope,
            Applicability applicability,
            QuantityExtractor extractor) {
        ComponentId cid = new ComponentId(id);
        map.put(cid, new SimpleComponent(cid, SIMPLE_FIXED, extractor, applicability, scope));
    }

    private static void addComposite(
            Map<ComponentId, pl.devstyle.aj.archetype.pricing.Component> map,
            String id,
            Applicability applicability,
            List<String> childIds) {
        ComponentId cid = new ComponentId(id);
        map.put(cid, new CompositeComponent(cid, childIds.stream().map(ComponentId::new).toList(), applicability));
    }
}
