package pl.devstyle.aj.footprint.api.exception;

import java.util.Map;

public sealed abstract class FootprintCalculationException extends RuntimeException
        permits MissingFactorException,
                MissingProductAttributeException,
                InvalidParametersException,
                ApplicabilityResolutionException,
                FactorVersionOverlapException {

    protected FootprintCalculationException(String message) {
        super(message);
    }

    protected FootprintCalculationException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String code();

    public abstract Map<String, Object> details();
}
