using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Shared abstraction for nodes of the <c>FootprintBreakdown</c> tree. Each node carries the
/// originating <see cref="ComponentId"/> and an accumulated <see cref="KgCO2"/> value.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public abstract record BreakdownNode(ComponentId ComponentId, KgCO2 KgCO2);
