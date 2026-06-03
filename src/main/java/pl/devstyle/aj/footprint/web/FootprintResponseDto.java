package pl.devstyle.aj.footprint.web;

import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.CompositeBreakdownNode;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.FootprintWarning;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.api.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FootprintResponseDto(
        UUID correlationId,
        UUID comparisonGroupId,
        Instant computedAt,
        BigDecimal total,
        Unit unit,
        ParametersEcho parametersEcho,
        OptionsEcho options,
        BreakdownDto breakdown
) {

    public record ParametersEcho(
            String productId,
            BigDecimal materialWeightKg,
            BigDecimal supplierDistanceKm,
            BigDecimal destinationDistanceKm,
            BigDecimal lastMileDistanceKm,
            int storageDays,
            boolean requiresRefrigeration,
            Instant timestamp
    ) {
    }

    public record OptionsEcho(
            Strictness strictness,
            Normalisation normalisation,
            boolean dryRun
    ) {
    }

    public record WarningDto(String code, String message, String componentId) {
        static WarningDto from(FootprintWarning w) {
            return new WarningDto(w.code(), w.message(),
                    w.componentId() != null ? w.componentId().value() : null);
        }
    }

    public sealed interface BreakdownDto permits CompositeDto, LeafDto {
    }

    public record CompositeDto(
            String componentId,
            BigDecimal kgCo2,
            List<BreakdownDto> children,
            List<WarningDto> warnings
    ) implements BreakdownDto {
    }

    public record LeafDto(
            String componentId,
            BigDecimal kgCo2,
            Integer scope,
            String factorVersionId,
            BigDecimal factorRate,
            Instant factorValidFrom,
            BigDecimal quantity,
            List<WarningDto> warnings
    ) implements BreakdownDto {
    }

    public static FootprintResponseDto from(
            FootprintBreakdown breakdown,
            FootprintParameters echoParams,
            Strictness strictness,
            Normalisation normalisation,
            boolean dryRun,
            UUID comparisonGroupId,
            Instant asOf
    ) {
        Unit unit = normalisation == Normalisation.PER_100G ? Unit.KG_CO2_PER_100G : Unit.KG_CO2;
        ParametersEcho echo = new ParametersEcho(
                echoParams.productId(),
                echoParams.materialWeightKg(),
                echoParams.supplierDistanceKm(),
                echoParams.destinationDistanceKm(),
                echoParams.lastMileDistanceKm(),
                echoParams.storageDays(),
                echoParams.requiresRefrigeration(),
                asOf
        );
        OptionsEcho options = new OptionsEcho(strictness, normalisation, dryRun);
        BreakdownDto root = mapNode(breakdown.root());
        return new FootprintResponseDto(
                breakdown.correlationId(),
                comparisonGroupId,
                breakdown.computedAt(),
                breakdown.total(),
                unit,
                echo,
                options,
                root
        );
    }

    private static BreakdownDto mapNode(BreakdownNode node) {
        if (node instanceof CompositeBreakdownNode c) {
            return new CompositeDto(
                    c.componentId().value(),
                    c.kgCo2(),
                    c.children().stream().map(FootprintResponseDto::mapNode).toList(),
                    c.warnings().stream().map(WarningDto::from).toList()
            );
        }
        if (node instanceof LeafBreakdownNode l) {
            return new LeafDto(
                    l.componentId().value(),
                    l.kgCo2(),
                    l.scope(),
                    l.factorVersionId(),
                    l.factorRate(),
                    l.factorValidFrom(),
                    l.quantity(),
                    l.warnings().stream().map(WarningDto::from).toList()
            );
        }
        throw new IllegalStateException("Unknown breakdown node type: " + node.getClass());
    }
}
