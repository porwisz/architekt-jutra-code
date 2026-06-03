package pl.devstyle.aj.archetype.pricing;

public sealed interface Component permits SimpleComponent, CompositeComponent {

    ComponentId id();

    Applicability applicability();
}
