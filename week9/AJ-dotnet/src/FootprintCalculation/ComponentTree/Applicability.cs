using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Base abstraction for the predicate "is this component active in the given calculation context (FootprintParameters)?".
/// Concrete implementations (AlwaysApplicable, RequiresRefrigeration) express the semantics via
/// <see cref="IsSatisfiedBy(FootprintParameters)"/>.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public abstract record Applicability
{
    /// <summary>
    /// Returns <c>true</c> when this applicability predicate accepts the given calculation
    /// context (i.e. the component associated with this predicate participates in the footprint).
    /// Internal: not modelled as a formal domain Behaviour because it returns a primitive
    /// (see design-doc note on Applicability).
    /// </summary>
    internal abstract bool IsSatisfiedBy(FootprintParameters parameters);
}
