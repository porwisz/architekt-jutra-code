package pl.devstyle.aj.archetype.pricing;

import java.math.BigDecimal;

public record ComponentVersion(
        ComponentId componentId,
        String factorVersionId,
        BigDecimal rate,
        Validity validity
) {
}
