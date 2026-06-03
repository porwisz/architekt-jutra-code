package pl.devstyle.aj.footprint.api;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.math.BigDecimal;
import java.util.List;

public record CompositeBreakdownNode(
        ComponentId componentId,
        BigDecimal kgCo2,
        List<BreakdownNode> children,
        List<FootprintWarning> warnings
) implements BreakdownNode {
    public CompositeBreakdownNode {
        children = List.copyOf(children);
        warnings = List.copyOf(warnings);
    }
}
