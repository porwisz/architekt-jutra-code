using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Predicate activating a component only when
/// <see cref="FootprintParameters.RequiresRefrigeration"/> is <c>true</c>. Used on the
/// 'cold-storage' composite.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record RequiresRefrigeration : Applicability
{
    private RequiresRefrigeration() { }

    public static RequiresRefrigeration Instance { get; } = new();

    internal override bool IsSatisfiedBy(FootprintParameters parameters) => parameters.RequiresRefrigeration;
}
