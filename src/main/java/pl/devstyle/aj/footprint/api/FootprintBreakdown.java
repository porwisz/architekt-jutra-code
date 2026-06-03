package pl.devstyle.aj.footprint.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FootprintBreakdown(
        BreakdownNode root,
        BigDecimal total,
        List<FactorVersionRef> factorVersions,
        List<FootprintWarning> rootWarnings,
        Instant computedAt,
        UUID correlationId
) {
    public FootprintBreakdown {
        factorVersions = List.copyOf(factorVersions);
        rootWarnings = List.copyOf(rootWarnings);
    }
}
