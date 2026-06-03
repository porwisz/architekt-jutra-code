package pl.devstyle.aj.archetype.pricing;

import java.math.BigDecimal;

@FunctionalInterface
public interface QuantityExtractor {

    BigDecimal extract(ParameterValue context);
}
