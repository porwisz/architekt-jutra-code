using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Activity value injected into the EmissionCalculator — mass, distance, days, units, etc.
/// The unit description is informative and does not participate in arithmetic.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record Quantity
{
    public decimal Value { get; init; }
    public string UnitDescription { get; init; }

    public Quantity(decimal value, string unitDescription)
    {
        if (string.IsNullOrWhiteSpace(unitDescription))
            throw new ArgumentException("UnitDescription must not be null or whitespace.", nameof(unitDescription));
        Value = value;
        UnitDescription = unitDescription;
    }
}
