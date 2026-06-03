package pl.devstyle.aj.footprint.api;

import pl.devstyle.aj.archetype.pricing.ComponentId;

public record FootprintWarning(
        String code,
        String message,
        ComponentId componentId
) {
}
