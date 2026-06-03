package pl.devstyle.aj.footprint.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.devstyle.aj.footprint.api.CalculationOptions;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintFacade;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.api.exception.InvalidParametersException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
class FootprintController {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_CALLER_ID = "X-Caller-Id";
    private static final String HEADER_COMPARISON_GROUP = "X-Comparison-Group";

    private final FootprintFacade footprintFacade;

    FootprintController(FootprintFacade footprintFacade) {
        this.footprintFacade = footprintFacade;
    }

    @GetMapping("/{productId}/footprint")
    ResponseEntity<FootprintResponseDto> getFootprint(
            @PathVariable String productId,
            FootprintQueryRequest query,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationIdHeader,
            @RequestHeader(value = HEADER_CALLER_ID, required = false) String callerIdHeader,
            @RequestHeader(value = HEADER_COMPARISON_GROUP, required = false) String comparisonGroupHeader
    ) {
        if (query.materialWeightKg() != null && query.materialWeightKg().signum() < 0) {
            throw new InvalidParametersException("materialWeightKg", query.materialWeightKg());
        }

        UUID correlationId = parseUuidHeader(HEADER_CORRELATION_ID, correlationIdHeader);
        UUID comparisonGroupId = parseUuidHeader(HEADER_COMPARISON_GROUP, comparisonGroupHeader);

        Instant asOf = query.asOf() != null ? query.asOf() : Instant.now();
        int storageDays = query.storageDays() != null ? query.storageDays() : 0;
        boolean requiresRefrigeration = query.requiresRefrigeration() != null && query.requiresRefrigeration();
        Strictness strictness = query.strictness() != null ? query.strictness() : Strictness.STRICT;
        Normalisation normalisation = query.unit() != null ? query.unit() : Normalisation.TOTAL;
        boolean dryRun = query.dryRun() != null && query.dryRun();
        String callerId = (callerIdHeader != null && !callerIdHeader.isBlank()) ? callerIdHeader : "anonymous";

        FootprintParameters params = new FootprintParameters(
                productId,
                query.materialWeightKg(),
                query.supplierDistanceKm(),
                query.destinationDistanceKm(),
                query.lastMileDistanceKm(),
                storageDays,
                requiresRefrigeration,
                asOf
        );

        CalculationOptions options = new CalculationOptions(
                strictness,
                normalisation,
                dryRun,
                correlationId,
                comparisonGroupId,
                callerId
        );

        FootprintBreakdown breakdown = normalisation == Normalisation.PER_100G
                ? footprintFacade.calculateUnit(params, options)
                : footprintFacade.calculateTotal(params, options);

        FootprintResponseDto body = FootprintResponseDto.from(
                breakdown, params, strictness, normalisation, dryRun, comparisonGroupId, asOf);
        return ResponseEntity.ok(body);
    }

    private static UUID parseUuidHeader(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidParametersException(name, value);
        }
    }
}
