using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// GHG Protocol Scope 1/2/3 metadata. A constant label on a SimpleComponent definition,
/// copied onto each SimpleBreakdownNode. No logic.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record EmissionScope
{
    public int Level { get; init; }

    public EmissionScope(int level)
    {
        if (level is < 1 or > 3)
            throw new ArgumentException("EmissionScope level must be 1, 2, or 3.", nameof(level));
        Level = level;
    }
}
