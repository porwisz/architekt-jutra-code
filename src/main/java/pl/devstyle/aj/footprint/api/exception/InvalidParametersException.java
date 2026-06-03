package pl.devstyle.aj.footprint.api.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class InvalidParametersException extends FootprintCalculationException {

    public static final String CODE = "INVALID_PARAMETERS";

    private final String parameter;
    private final Object value;

    public InvalidParametersException(String parameter, Object value) {
        super("Invalid parameter '" + parameter + "': " + value);
        this.parameter = parameter;
        this.value = value;
    }

    public String parameter() {
        return parameter;
    }

    public Object value() {
        return value;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> details() {
        Map<String, Object> details = new HashMap<>();
        details.put("parameter", parameter);
        details.put("value", value);
        return Collections.unmodifiableMap(details);
    }
}
