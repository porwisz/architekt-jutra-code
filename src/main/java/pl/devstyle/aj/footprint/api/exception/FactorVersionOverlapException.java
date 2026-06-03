package pl.devstyle.aj.footprint.api.exception;

import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.archetype.pricing.Validity;

import java.util.Map;

public final class FactorVersionOverlapException extends FootprintCalculationException {

    public static final String CODE = "FACTOR_VERSION_OVERLAP";

    private final ComponentId componentId;
    private final Validity a;
    private final Validity b;

    public FactorVersionOverlapException(ComponentId componentId, Validity a, Validity b) {
        super("Factor versions overlap for component " + componentId.value());
        this.componentId = componentId;
        this.a = a;
        this.b = b;
    }

    public ComponentId componentId() {
        return componentId;
    }

    public Validity a() {
        return a;
    }

    public Validity b() {
        return b;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "componentId", componentId.value(),
                "a", validityToIso(a),
                "b", validityToIso(b)
        );
    }

    private static String validityToIso(Validity v) {
        String to = v.validTo() == null ? "" : v.validTo().toString();
        return v.validFrom().toString() + "/" + to;
    }
}
