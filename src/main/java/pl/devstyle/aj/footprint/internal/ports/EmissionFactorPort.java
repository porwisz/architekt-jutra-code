package pl.devstyle.aj.footprint.internal.ports;

import java.time.Instant;
import java.util.Optional;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.archetype.pricing.ComponentVersion;

public interface EmissionFactorPort {

    Optional<ComponentVersion> versionAt(ComponentId componentId, Instant t);

    Optional<ComponentVersion> factorVersionById(String factorVersionId);
}
