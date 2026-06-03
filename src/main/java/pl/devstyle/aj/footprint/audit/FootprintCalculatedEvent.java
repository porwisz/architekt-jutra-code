package pl.devstyle.aj.footprint.audit;

import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintWarning;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FootprintCalculatedEvent(
        UUID correlationId,
        UUID comparisonGroupId,
        String productId,
        String callerId,
        Instant requestedAt,
        BigDecimal totalKgCo2,
        Strictness strictness,
        Normalisation normalisation,
        FootprintBreakdown breakdown,
        List<FootprintWarning> warnings,
        List<String> factorVersions,
        boolean dryRun
) {
}
