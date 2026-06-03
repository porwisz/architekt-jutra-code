using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Internal node of the component tree. Aggregates children of type <see cref="Component"/>
/// (mixed <c>SimpleComponent</c> and <c>CompositeComponent</c>). The composite's emission is
/// the sum of the emissions of its active children (computed by <c>FootprintFacade</c>).
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record CompositeComponent(
    ComponentId Id,
    Applicability Applicability,
    IReadOnlyList<Component> Children) : Component(Id, Applicability);
