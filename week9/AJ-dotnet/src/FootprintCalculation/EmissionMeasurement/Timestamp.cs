using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Point in time used to resolve component versions (<c>VersionAt(timestamp)</c>). UTC.
/// The boundary values <see cref="MinValue"/> and <see cref="MaxValue"/> are reserved for
/// unbounded Validity intervals.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record Timestamp
{
    public DateTimeOffset Value { get; init; }

    public Timestamp(DateTimeOffset value)
    {
        Value = value.ToUniversalTime();
    }

    /// <summary>Sentinel representing an unbounded lower edge of a Validity interval.</summary>
    public static Timestamp MinValue { get; } = new(DateTimeOffset.MinValue);

    /// <summary>Sentinel representing an unbounded upper edge of a Validity interval.</summary>
    public static Timestamp MaxValue { get; } = new(DateTimeOffset.MaxValue);
}
