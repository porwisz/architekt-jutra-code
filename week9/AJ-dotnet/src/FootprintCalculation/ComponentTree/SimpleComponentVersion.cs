using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Immutable snapshot of a <c>SimpleComponent</c> configuration: <c>CalculatorId</c>, <c>Rate</c>,
/// <c>Validity</c>, <c>DefinedAt</c>. <c>DefinedAt</c> is a system timestamp captured at the moment
/// the version is created and is never modifiable. In MVP, <c>CalculatorId</c> is always
/// <c>'calc-emission'</c>.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record SimpleComponentVersion(
    string CalculatorId,
    EmissionFactorRate Rate,
    Validity Validity,
    Timestamp DefinedAt);
