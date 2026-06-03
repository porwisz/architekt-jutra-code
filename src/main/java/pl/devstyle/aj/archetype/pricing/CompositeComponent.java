package pl.devstyle.aj.archetype.pricing;

import java.util.List;

public record CompositeComponent(
        ComponentId id,
        List<ComponentId> childIds,
        Applicability applicability
) implements Component {

    public CompositeComponent {
        childIds = List.copyOf(childIds);
    }
}
