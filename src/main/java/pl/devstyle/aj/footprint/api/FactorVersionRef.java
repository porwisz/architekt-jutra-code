package pl.devstyle.aj.footprint.api;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.time.Instant;

public record FactorVersionRef(
        ComponentId componentId,
        String factorVersionId,
        Instant validFrom
) {
}
