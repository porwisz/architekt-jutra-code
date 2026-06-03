package pl.devstyle.aj.footprint.api;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LeafBreakdownNode(
        ComponentId componentId,
        BigDecimal kgCo2,
        Integer scope,
        String factorVersionId,
        BigDecimal factorRate,
        Instant factorValidFrom,
        BigDecimal quantity,
        List<FootprintWarning> warnings
) implements BreakdownNode {
    public LeafBreakdownNode {
        warnings = List.copyOf(warnings);
    }
}
