using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Emission factor — a numeric value (conceptually BigDecimal with scale 6) plus a textual
/// description of the source unit (e.g. "kgCO2/kg"). The unit description is informative and
/// does not participate in arithmetic.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record EmissionFactorRate
{
    public decimal Value { get; init; }
    public string UnitDescription { get; init; }

    public EmissionFactorRate(decimal value, string unitDescription)
    {
        if (string.IsNullOrWhiteSpace(unitDescription))
            throw new ArgumentException("UnitDescription must not be null or whitespace.", nameof(unitDescription));
        Value = value;
        UnitDescription = unitDescription;
    }
}
