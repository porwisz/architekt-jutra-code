package pl.devstyle.aj.archetype.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SimpleFixedCalculator implements Calculator {

    @Override
    public BigDecimal calculate(BigDecimal rate, BigDecimal quantity) {
        return rate.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
    }
}
