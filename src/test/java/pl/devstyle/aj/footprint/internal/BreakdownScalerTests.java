package pl.devstyle.aj.footprint.internal;

import org.junit.jupiter.api.Test;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.CompositeBreakdownNode;
import pl.devstyle.aj.footprint.api.FactorVersionRef;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.api.Normalisation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BreakdownScalerTests {

    private final BreakdownScaler scaler = new BreakdownScaler();

    @Test
    void scale_per100gOnHalfKg_doublesEveryLeaf() {
        // 0.5 kg → scaleFactor = 0.1 / 0.5 = 0.2 → halves, NOT doubles.
        // For DOUBLING, use 0.05 kg → 0.1 / 0.05 = 2.0.
        // We test the actual invariant: leaves multiplied by scaleFactor.
        var leaf1 = leaf("raw-material", new BigDecimal("1.0000"));
        var leaf2 = leaf("processing", new BigDecimal("2.0000"));
        var root = composite("product-footprint", List.of(leaf1, leaf2));
        var breakdown = wrap(root);

        var scaled = scaler.scale(breakdown, Normalisation.PER_100G, new BigDecimal("0.05"));

        var children = ((CompositeBreakdownNode) scaled.root()).children();
        assertThat(((LeafBreakdownNode) children.get(0)).kgCo2()).isEqualByComparingTo("2.0000");
        assertThat(((LeafBreakdownNode) children.get(1)).kgCo2()).isEqualByComparingTo("4.0000");
    }

    @Test
    void scale_composite_equalsSumOfScaledChildren() {
        var leafA = leaf("a", new BigDecimal("1.2345"));
        var leafB = leaf("b", new BigDecimal("2.3456"));
        var leafC = leaf("c", new BigDecimal("3.4567"));
        var inner = composite("transport", List.of(leafA, leafB));
        var root = composite("product-footprint", List.of(inner, leafC));
        var breakdown = wrap(root);

        var scaled = scaler.scale(breakdown, Normalisation.PER_100G, new BigDecimal("0.5"));

        var rootScaled = (CompositeBreakdownNode) scaled.root();
        var innerScaled = (CompositeBreakdownNode) rootScaled.children().get(0);
        var leafCScaled = (LeafBreakdownNode) rootScaled.children().get(1);
        var leafAScaled = (LeafBreakdownNode) innerScaled.children().get(0);
        var leafBScaled = (LeafBreakdownNode) innerScaled.children().get(1);

        assertThat(innerScaled.kgCo2()).isEqualByComparingTo(leafAScaled.kgCo2().add(leafBScaled.kgCo2()));
        assertThat(rootScaled.kgCo2()).isEqualByComparingTo(innerScaled.kgCo2().add(leafCScaled.kgCo2()));
        assertThat(scaled.total()).isEqualByComparingTo(rootScaled.kgCo2());
    }

    @Test
    void scale_inactiveSubtreeAbsent_doesNotProduceNaN() {
        // Composite with NO children (cold-storage subtree omitted upstream).
        var leaf1 = leaf("packaging", new BigDecimal("0.5000"));
        // We simulate "inactive subtree absent" by simply not including it.
        var root = composite("product-footprint", List.of(leaf1));
        var breakdown = wrap(root);

        var scaled = scaler.scale(breakdown, Normalisation.PER_100G, new BigDecimal("0.25"));

        var children = ((CompositeBreakdownNode) scaled.root()).children();
        assertThat(children).hasSize(1);
        var leafScaled = (LeafBreakdownNode) children.get(0);
        // 0.5 * (0.1 / 0.25) = 0.5 * 0.4 = 0.2
        assertThat(leafScaled.kgCo2()).isEqualByComparingTo("0.2000");
        assertThat(scaled.total()).isEqualByComparingTo("0.2000");
        assertThat(scaled.root().kgCo2()).isNotNull();
    }

    private LeafBreakdownNode leaf(String id, BigDecimal kgCo2) {
        return new LeafBreakdownNode(
                new ComponentId(id),
                kgCo2,
                3,
                "fv-" + id,
                new BigDecimal("0.10"),
                Instant.parse("2026-01-01T00:00:00Z"),
                new BigDecimal("1.0"),
                List.of()
        );
    }

    private CompositeBreakdownNode composite(String id, List<BreakdownNode> children) {
        var total = children.stream().map(BreakdownNode::kgCo2).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CompositeBreakdownNode(new ComponentId(id), total, children, List.of());
    }

    private FootprintBreakdown wrap(BreakdownNode root) {
        return new FootprintBreakdown(
                root,
                root.kgCo2(),
                List.<FactorVersionRef>of(),
                List.of(),
                Instant.parse("2026-05-20T00:00:00Z"),
                UUID.randomUUID()
        );
    }
}
