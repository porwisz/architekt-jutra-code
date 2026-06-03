package pl.devstyle.aj.archetype.pricing;

import java.math.BigDecimal;

public interface Calculator {

    BigDecimal calculate(BigDecimal rate, BigDecimal quantity);
}
