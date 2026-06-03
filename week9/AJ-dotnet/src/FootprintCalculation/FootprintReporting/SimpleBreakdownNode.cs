using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Leaf of the result tree — the counterpart of an active <c>SimpleComponent</c> in the product
/// tree. Carries the mandatory audit information: <see cref="Scope"/>,
/// <see cref="EmissionFactorUsed"/>, <see cref="FactorValidFrom"/>.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record SimpleBreakdownNode(
    ComponentId ComponentId,
    KgCO2 KgCO2,
    EmissionScope Scope,
    EmissionFactorRate EmissionFactorUsed,
    Timestamp FactorValidFrom) : BreakdownNode(ComponentId, KgCO2);
