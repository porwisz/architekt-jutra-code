package pl.devstyle.aj.footprint.internal;

import org.springframework.stereotype.Component;
import pl.devstyle.aj.archetype.pricing.Calculator;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.archetype.pricing.ComponentVersion;
import pl.devstyle.aj.archetype.pricing.CompositeComponent;
import pl.devstyle.aj.archetype.pricing.ParameterValue;
import pl.devstyle.aj.archetype.pricing.SimpleComponent;
import pl.devstyle.aj.archetype.pricing.SimpleFixedCalculator;
import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.CompositeBreakdownNode;
import pl.devstyle.aj.footprint.api.FactorVersionRef;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.FootprintWarning;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.api.exception.MissingFactorException;
import pl.devstyle.aj.footprint.internal.ports.EmissionFactorPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
class BreakdownTreeBuilder {

    private static final Calculator CALCULATOR = new SimpleFixedCalculator();

    private final ComponentTreeRegistry registry;
    private final EmissionFactorPort emissionFactorPort;

    BreakdownTreeBuilder(
            ComponentTreeRegistry registry,
            EmissionFactorPort emissionFactorPort) {
        this.registry = registry;
        this.emissionFactorPort = emissionFactorPort;
    }

    Result build(FootprintParameters params, Instant asOf, Strictness strictness) {
        ParameterValue context = toParameterValue(params);
        List<FactorVersionRef> factorVersions = new ArrayList<>();
        BreakdownNode root = buildNode(registry.byId(ComponentTreeRegistry.ROOT_ID), context, asOf, strictness, factorVersions);
        if (root == null) {
            throw new IllegalStateException("Root component evaluated to absent — invariant violation");
        }
        return new Result(root, List.copyOf(factorVersions));
    }

    record Result(BreakdownNode root, List<FactorVersionRef> factorVersions) {
    }

    private BreakdownNode buildNode(
            pl.devstyle.aj.archetype.pricing.Component component,
            ParameterValue context,
            Instant asOf,
            Strictness strictness,
            List<FactorVersionRef> factorVersionsSink) {

        if (!component.applicability().isActive(context)) {
            return null;
        }

        if (component instanceof SimpleComponent leaf) {
            return buildLeaf(leaf, context, asOf, strictness, factorVersionsSink);
        }
        if (component instanceof CompositeComponent composite) {
            return buildComposite(composite, context, asOf, strictness, factorVersionsSink);
        }
        throw new IllegalStateException("Unknown component kind: " + component.getClass());
    }

    private BreakdownNode buildComposite(
            CompositeComponent composite,
            ParameterValue context,
            Instant asOf,
            Strictness strictness,
            List<FactorVersionRef> factorVersionsSink) {

        List<BreakdownNode> children = new ArrayList<>();
        List<FootprintWarning> warnings = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (ComponentId childId : composite.childIds()) {
            BreakdownNode child = buildNode(registry.byId(childId), context, asOf, strictness, factorVersionsSink);
            if (child == null) {
                continue;
            }
            children.add(child);
            warnings.addAll(child.warnings());
            sum = sum.add(child.kgCo2());
        }

        if (children.isEmpty()) {
            return null;
        }

        return new CompositeBreakdownNode(composite.id(), sum, children, warnings);
    }

    private BreakdownNode buildLeaf(
            SimpleComponent leaf,
            ParameterValue context,
            Instant asOf,
            Strictness strictness,
            List<FactorVersionRef> factorVersionsSink) {

        Optional<ComponentVersion> versionOpt = emissionFactorPort.versionAt(leaf.id(), asOf);
        BigDecimal quantity = leaf.extractor().extract(context);

        if (versionOpt.isPresent()) {
            ComponentVersion version = versionOpt.get();
            BigDecimal kgCo2 = RoundingPolicy.round(CALCULATOR.calculate(version.rate(), quantity));
            factorVersionsSink.add(new FactorVersionRef(leaf.id(), version.factorVersionId(), version.validity().validFrom()));
            return new LeafBreakdownNode(
                    leaf.id(),
                    kgCo2,
                    leaf.scope(),
                    version.factorVersionId(),
                    version.rate(),
                    version.validity().validFrom(),
                    quantity,
                    List.of()
            );
        }

        if (strictness == Strictness.STRICT) {
            throw new MissingFactorException(leaf.id(), asOf);
        }

        var warning = new FootprintWarning(
                "MISSING_FACTOR",
                "No emission factor for " + leaf.id().value() + " at " + asOf,
                leaf.id()
        );
        return new LeafBreakdownNode(
                leaf.id(),
                RoundingPolicy.round(BigDecimal.ZERO),
                leaf.scope(),
                null,
                null,
                null,
                quantity,
                List.of(warning)
        );
    }

    private ParameterValue toParameterValue(FootprintParameters params) {
        return new ParameterValue(
                params.productId(),
                params.materialWeightKg(),
                params.supplierDistanceKm(),
                params.destinationDistanceKm(),
                params.lastMileDistanceKm(),
                params.storageDays(),
                params.requiresRefrigeration()
        );
    }
}
