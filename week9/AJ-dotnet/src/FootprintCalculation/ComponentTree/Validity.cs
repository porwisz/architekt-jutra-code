using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Half-open time interval <c>[validFrom, validTo)</c> representing the period during which a
/// <c>SimpleComponentVersion</c> is in effect.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record Validity
{
    public Timestamp ValidFrom { get; init; }
    public Timestamp ValidTo { get; init; }

    public Validity(Timestamp validFrom, Timestamp validTo)
    {
        ValidFrom = validFrom;
        ValidTo = validTo;
    }

    internal static Validity Always() => new(Timestamp.MinValue, Timestamp.MaxValue);

    internal static Validity From(Timestamp t) => new(t, Timestamp.MaxValue);

    internal static Validity Until(Timestamp t) => new(Timestamp.MinValue, t);

    internal static Validity Between(Timestamp t1, Timestamp t2) => new(t1, t2);

    internal bool IsValidAt(Timestamp t) => ValidFrom.Value <= t.Value && t.Value < ValidTo.Value;
}
