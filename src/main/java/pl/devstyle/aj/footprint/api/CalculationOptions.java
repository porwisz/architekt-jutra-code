package pl.devstyle.aj.footprint.api;

import java.util.UUID;

public record CalculationOptions(
        Strictness strictness,
        Normalisation normalisation,
        boolean dryRun,
        UUID correlationId,
        UUID comparisonGroupId,
        String callerId
) {
    public static CalculationOptions defaults() {
        return new CalculationOptions(Strictness.STRICT, Normalisation.TOTAL, false, null, null, "anonymous");
    }
}
