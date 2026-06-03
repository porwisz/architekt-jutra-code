using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Result type of an emission calculation — analog of Money. Conceptually BigDecimal with scale 6.
/// All arithmetic operations return a new <see cref="KgCO2"/> (immutability).
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record KgCO2
{
    public decimal Value { get; init; }

    public KgCO2(decimal value)
    {
        if (value < 0m)
            throw new ArgumentException("KgCO2 value must be non-negative.", nameof(value));
        Value = value;
    }

    public static KgCO2 Zero { get; } = new(0m);

    internal KgCO2 Add(KgCO2 other) => new(Value + other.Value);

    public static KgCO2 operator +(KgCO2 left, KgCO2 right) => new(left.Value + right.Value);
}
