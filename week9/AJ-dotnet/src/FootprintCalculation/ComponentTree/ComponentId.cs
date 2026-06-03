using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Stable, human-readable identifier of a component in the footprint tree (e.g. 'raw-material',
/// 'cold-storage'). Immutable.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record ComponentId
{
    public string Value { get; init; }

    public ComponentId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("ComponentId value must not be null or whitespace.", nameof(value));
        Value = value;
    }
}
