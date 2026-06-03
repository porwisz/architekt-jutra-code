package pl.devstyle.aj.footprint.spi.stub;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.devstyle.aj.footprint.internal.ports.ProductAttributesPort;

/**
 * Dev/test stub. Enabled by {@code app.footprint.adapters=in-memory}. In production a real
 * Product-domain-backed adapter must be provided; this class must not be on the active classpath then.
 */
@Component
@ConditionalOnProperty(name = "app.footprint.adapters", havingValue = "in-memory", matchIfMissing = false)
class InMemoryProductAttributesPort implements ProductAttributesPort {

    private final Map<String, ProductAttributes> catalog;

    InMemoryProductAttributesPort() {
        Map<String, ProductAttributes> seed = new HashMap<>();
        // Original demo entries.
        seed.put("OFB-330", new ProductAttributes(
                "OFB-330", new BigDecimal("0.5"), new BigDecimal("2800"),
                new BigDecimal("570"), new BigDecimal("15"), true));
        seed.put("CAW-042", new ProductAttributes(
                "CAW-042", new BigDecimal("0.25"), new BigDecimal("800"),
                new BigDecimal("120"), new BigDecimal("5"), false));
        // Seeded products from 003-insert-sample-data.yaml — keyed by SKU so the
        // /products/{sku}/footprint endpoint resolves end-to-end in dev demos.
        // Weights/distances are illustrative defaults; refrigerated=false for all
        // consumer electronics. Real values arrive when a production adapter
        // replaces this stub (see app.footprint.adapters property).
        addElectronics(seed, "TV-LG-C4-65", "25", "9500");
        addElectronics(seed, "TV-SAM-S90D-55", "18", "9300");
        addElectronics(seed, "TV-SNY-BR7-75", "32", "9100");
        addElectronics(seed, "AUD-SNS-ARC", "3.5", "9400");
        addElectronics(seed, "AUD-BOSE-SB700", "4.8", "5400");
        addElectronics(seed, "AUD-KEF-LS50W2", "10", "1200");
        addElectronics(seed, "SH-AMZ-ECHOHUB", "0.6", "9200");
        addElectronics(seed, "SH-PHI-HUE-SK", "0.25", "7100");
        addElectronics(seed, "SH-GOOG-NEST4", "0.3", "8800");
        addElectronics(seed, "NET-ASUS-AXE7800", "1.5", "9300");
        addElectronics(seed, "NET-EERO-PRO6E-3", "1.2", "9400");
        addElectronics(seed, "HA-IRB-J9PLUS", "5.5", "8500");
        addElectronics(seed, "HA-DYS-HP07", "4.7", "1300");
        addElectronics(seed, "HA-BRV-BTI", "8.5", "1100");
        this.catalog = Map.copyOf(seed);
    }

    private static void addElectronics(
            Map<String, ProductAttributes> map, String sku, String weightKg, String supplierDistanceKm) {
        map.put(sku, new ProductAttributes(
                sku,
                new BigDecimal(weightKg),
                new BigDecimal(supplierDistanceKm),
                new BigDecimal("500"),
                new BigDecimal("0"),
                false));
    }

    @Override
    public Optional<ProductAttributes> findById(String productId) {
        return Optional.ofNullable(catalog.get(productId));
    }
}
