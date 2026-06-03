package pl.devstyle.aj.footprint.api;

import java.math.BigDecimal;
import java.time.Instant;

public record FootprintParameters(
        String productId,
        BigDecimal materialWeightKg,
        BigDecimal supplierDistanceKm,
        BigDecimal destinationDistanceKm,
        BigDecimal lastMileDistanceKm,
        int storageDays,
        boolean requiresRefrigeration,
        Instant asOf
) {
    public FootprintParameters {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
    }
}
