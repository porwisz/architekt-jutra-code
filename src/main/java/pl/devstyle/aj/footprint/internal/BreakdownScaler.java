package pl.devstyle.aj.footprint.internal;

import org.springframework.stereotype.Component;
import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.CompositeBreakdownNode;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.api.Normalisation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
class BreakdownScaler {

    private static final BigDecimal POINT_ONE = new BigDecimal("0.1");

    FootprintBreakdown scale(FootprintBreakdown breakdown, Normalisation normalisation, BigDecimal materialWeightKg) {
        if (normalisation == Normalisation.TOTAL) {
            return breakdown;
        }
        if (materialWeightKg == null || materialWeightKg.signum() <= 0) {
            throw new IllegalArgumentException("materialWeightKg must be positive for PER_100G normalisation");
        }
        BigDecimal scaleFactor = POINT_ONE.divide(materialWeightKg, 10, RoundingMode.HALF_UP);
        BreakdownNode scaledRoot = scaleNode(breakdown.root(), scaleFactor);
        return new FootprintBreakdown(
                scaledRoot,
                scaledRoot.kgCo2(),
                breakdown.factorVersions(),
                breakdown.rootWarnings(),
                breakdown.computedAt(),
                breakdown.correlationId()
        );
    }

    private BreakdownNode scaleNode(BreakdownNode node, BigDecimal scaleFactor) {
        if (node instanceof LeafBreakdownNode leaf) {
            BigDecimal scaled = RoundingPolicy.round(leaf.kgCo2().multiply(scaleFactor));
            return new LeafBreakdownNode(
                    leaf.componentId(),
                    scaled,
                    leaf.scope(),
                    leaf.factorVersionId(),
                    leaf.factorRate(),
                    leaf.factorValidFrom(),
                    leaf.quantity(),
                    leaf.warnings()
            );
        }
        if (node instanceof CompositeBreakdownNode composite) {
            List<BreakdownNode> scaledChildren = composite.children().stream()
                    .map(c -> scaleNode(c, scaleFactor))
                    .toList();
            BigDecimal sum = scaledChildren.stream()
                    .map(BreakdownNode::kgCo2)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CompositeBreakdownNode(
                    composite.componentId(),
                    sum,
                    scaledChildren,
                    composite.warnings()
            );
        }
        throw new IllegalStateException("Unknown breakdown node kind: " + node.getClass());
    }
}
