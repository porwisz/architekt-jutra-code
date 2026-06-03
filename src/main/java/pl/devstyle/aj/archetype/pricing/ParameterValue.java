package pl.devstyle.aj.archetype.pricing;

import java.math.BigDecimal;

public record ParameterValue(
        String productId,
        BigDecimal materialWeightKg,
        BigDecimal supplierDistanceKm,
        BigDecimal destinationDistanceKm,
        BigDecimal lastMileDistanceKm,
        int storageDays,
        boolean requiresRefrigeration
) {
    public ParameterValue {
        materialWeightKg = nonNull(materialWeightKg);
        supplierDistanceKm = nonNull(supplierDistanceKm);
        destinationDistanceKm = nonNull(destinationDistanceKm);
        lastMileDistanceKm = nonNull(lastMileDistanceKm);
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
