package pl.devstyle.aj.footprint.api;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.math.BigDecimal;
import java.util.List;

public sealed interface BreakdownNode permits CompositeBreakdownNode, LeafBreakdownNode {
    ComponentId componentId();

    BigDecimal kgCo2();

    List<FootprintWarning> warnings();
}
