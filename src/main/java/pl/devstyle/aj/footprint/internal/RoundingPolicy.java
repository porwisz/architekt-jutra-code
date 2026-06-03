package pl.devstyle.aj.footprint.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class RoundingPolicy {

    private RoundingPolicy() {
    }

    static BigDecimal round(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
