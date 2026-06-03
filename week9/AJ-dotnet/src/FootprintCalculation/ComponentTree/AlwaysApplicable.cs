using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Identity predicate — <see cref="IsSatisfiedBy(FootprintParameters)"/> always returns <c>true</c>.
/// Used for components that are active for all products (materials, transport, packaging).
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record AlwaysApplicable : Applicability
{
    private AlwaysApplicable() { }

    public static AlwaysApplicable Instance { get; } = new();

    internal override bool IsSatisfiedBy(FootprintParameters parameters) => true;
}
