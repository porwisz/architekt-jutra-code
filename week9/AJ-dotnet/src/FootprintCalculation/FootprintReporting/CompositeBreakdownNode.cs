using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Internal node of the result tree — aggregates child breakdown nodes. Children of inactive
/// sub-trees are COMPLETELY ABSENT from the <see cref="Children"/> collection.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record CompositeBreakdownNode(
    ComponentId ComponentId,
    KgCO2 KgCO2,
    IReadOnlyList<BreakdownNode> Children) : BreakdownNode(ComponentId, KgCO2);
