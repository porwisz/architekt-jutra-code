package pl.devstyle.aj.footprint.internal.ports;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductAttributesPort {

    Optional<ProductAttributes> findById(String productId);

    record ProductAttributes(
            String productId,
            BigDecimal materialWeightKg,
            BigDecimal supplierDistanceKm,
            BigDecimal destinationDistanceKm,
            BigDecimal lastMileDistanceKm,
            boolean requiresRefrigeration
    ) {
    }
}
