package pl.devstyle.aj.footprint.api.exception;

import pl.devstyle.aj.archetype.pricing.ComponentId;

import java.time.Instant;
import java.util.Map;

public final class MissingFactorException extends FootprintCalculationException {

    public static final String CODE = "MISSING_FACTOR";

    private final ComponentId componentId;
    private final Instant timestamp;

    public MissingFactorException(ComponentId componentId, Instant timestamp) {
        super("No factor found for component " + componentId.value() + " at " + timestamp);
        this.componentId = componentId;
        this.timestamp = timestamp;
    }

    public ComponentId componentId() {
        return componentId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "componentId", componentId.value(),
                "timestamp", timestamp.toString()
        );
    }
}
