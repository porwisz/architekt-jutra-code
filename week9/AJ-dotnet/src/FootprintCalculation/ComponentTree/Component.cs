using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Shared abstraction for nodes of the carbon-footprint composition tree — composites
/// (<c>CompositeComponent</c>) and leaves (<c>SimpleComponent</c>).
/// </summary>
[DddValueObject]
[EntitiesLayer]
public abstract record Component(ComponentId Id, Applicability Applicability);
