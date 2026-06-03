package pl.devstyle.aj.footprint.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.footprint.api.CalculationOptions;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintFacade;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.FootprintWarning;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.api.exception.MissingProductAttributeException;
import pl.devstyle.aj.footprint.audit.FootprintCalculatedEvent;
import pl.devstyle.aj.footprint.internal.ports.ProductAttributesPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class DefaultFootprintFacade implements FootprintFacade {

    private static final Duration FUTURE_THRESHOLD = Duration.ofDays(30);
    private static final Duration ANCIENT_THRESHOLD = Duration.ofDays(365L * 10);

    private final BreakdownTreeBuilder builder;
    private final BreakdownScaler scaler;
    private final ProductAttributesPort productAttributesPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Timer calculateTimer;

    DefaultFootprintFacade(
            BreakdownTreeBuilder builder,
            BreakdownScaler scaler,
            ProductAttributesPort productAttributesPort,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        this.builder = builder;
        this.scaler = scaler;
        this.productAttributesPort = productAttributesPort;
        this.eventPublisher = eventPublisher;
        this.calculateTimer = meterRegistry.timer("footprint.calculate");
    }

    @Override
    public FootprintBreakdown calculateTotal(FootprintParameters params, CalculationOptions options) {
        return calculateTimer.record(() -> doCalculateTotal(params, options));
    }

    private FootprintBreakdown doCalculateTotal(FootprintParameters params, CalculationOptions options) {
        CalculationOptions effectiveOptions = withDefaults(options);
        FootprintParameters effectiveParams = mergeWithProductAttributes(params);
        Instant asOf = effectiveParams.asOf() != null ? effectiveParams.asOf() : Instant.now();
        Strictness strictness = effectiveOptions.strictness() != null ? effectiveOptions.strictness() : Strictness.STRICT;

        var result = builder.build(effectiveParams, asOf, strictness);
        List<FootprintWarning> rootWarnings = timestampWarnings(asOf);

        FootprintBreakdown breakdown = new FootprintBreakdown(
                result.root(),
                result.root().kgCo2(),
                result.factorVersions(),
                rootWarnings,
                Instant.now(),
                effectiveOptions.correlationId()
        );

        if (!effectiveOptions.dryRun()) {
            eventPublisher.publishEvent(toAuditEvent(effectiveParams, effectiveOptions, breakdown));
        }
        return breakdown;
    }

    private static FootprintCalculatedEvent toAuditEvent(
            FootprintParameters params,
            CalculationOptions options,
            FootprintBreakdown breakdown) {
        List<String> factorVersionIds = breakdown.factorVersions().stream()
                .map(fv -> fv.factorVersionId())
                .toList();
        return new FootprintCalculatedEvent(
                options.correlationId(),
                options.comparisonGroupId(),
                params.productId(),
                options.callerId(),
                breakdown.computedAt(),
                breakdown.total(),
                options.strictness(),
                options.normalisation(),
                breakdown,
                breakdown.rootWarnings(),
                factorVersionIds,
                options.dryRun()
        );
    }

    @Override
    public FootprintBreakdown calculateUnit(FootprintParameters params, CalculationOptions options) {
        FootprintBreakdown total = calculateTotal(params, options);
        FootprintParameters effectiveParams = mergeWithProductAttributes(params);
        return scaler.scale(total, Normalisation.PER_100G, effectiveParams.materialWeightKg());
    }

    private CalculationOptions withDefaults(CalculationOptions options) {
        CalculationOptions base = options != null ? options : CalculationOptions.defaults();
        return new CalculationOptions(
                base.strictness() != null ? base.strictness() : Strictness.STRICT,
                base.normalisation() != null ? base.normalisation() : Normalisation.TOTAL,
                base.dryRun(),
                base.correlationId() != null ? base.correlationId() : UUID.randomUUID(),
                base.comparisonGroupId(),
                base.callerId() != null ? base.callerId() : "anonymous"
        );
    }

    private FootprintParameters mergeWithProductAttributes(FootprintParameters params) {
        var attributes = productAttributesPort.findById(params.productId())
                .orElseThrow(() -> new MissingProductAttributeException(params.productId(), "product"));

        return new FootprintParameters(
                params.productId(),
                params.materialWeightKg() != null ? params.materialWeightKg() : attributes.materialWeightKg(),
                params.supplierDistanceKm() != null ? params.supplierDistanceKm() : attributes.supplierDistanceKm(),
                params.destinationDistanceKm() != null ? params.destinationDistanceKm() : attributes.destinationDistanceKm(),
                params.lastMileDistanceKm() != null ? params.lastMileDistanceKm() : attributes.lastMileDistanceKm(),
                params.storageDays(),
                params.requiresRefrigeration() || attributes.requiresRefrigeration(),
                params.asOf()
        );
    }

    private List<FootprintWarning> timestampWarnings(Instant asOf) {
        List<FootprintWarning> warnings = new ArrayList<>();
        Instant now = Instant.now();
        ComponentId rootId = ComponentTreeRegistry.ROOT_ID;
        if (asOf.isAfter(now.plus(FUTURE_THRESHOLD))) {
            warnings.add(new FootprintWarning("FUTURE_TIMESTAMP",
                    "asOf is more than 30 days in the future", rootId));
        }
        if (asOf.isBefore(now.minus(ANCIENT_THRESHOLD))) {
            warnings.add(new FootprintWarning("ANCIENT_TIMESTAMP",
                    "asOf is more than 10 years in the past", rootId));
        }
        return warnings;
    }
}
