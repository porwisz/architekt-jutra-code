package pl.devstyle.aj.footprint.api.exception;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.util.Map;

public final class ApplicabilityResolutionException extends FootprintCalculationException {

    public static final String CODE = "APPLICABILITY_RESOLUTION_FAILED";

    private final ComponentId componentId;
    private final String reason;

    public ApplicabilityResolutionException(ComponentId componentId, String reason) {
        super("Applicability resolution failed for " + componentId.value() + ": " + reason);
        this.componentId = componentId;
        this.reason = reason;
    }

    public ComponentId componentId() {
        return componentId;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "componentId", componentId.value(),
                "reason", reason
        );
    }
}
