package pl.devstyle.aj.footprint.web;

import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bound from query parameters by Spring MVC.
 * Headers are bound separately via @RequestHeader on the controller method.
 */
public record FootprintQueryRequest(
        Instant asOf,
        BigDecimal materialWeightKg,
        BigDecimal supplierDistanceKm,
        BigDecimal destinationDistanceKm,
        BigDecimal lastMileDistanceKm,
        Integer storageDays,
        Boolean requiresRefrigeration,
        Normalisation unit,
        Strictness strictness,
        Boolean dryRun
) {
}
