using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Leaf of the component tree representing a single "emission cost type" — raw-material,
/// processing, supplier-to-warehouse, packaging, warehouse-refrigeration, etc. Holds an ordered
/// collection of historical <see cref="SimpleComponentVersion"/>s; at calculation time the engine
/// picks one via <see cref="VersionAt"/>. <see cref="QuantitySource"/> is the name of the field on
/// <c>FootprintParameters</c> from which the quantity for the calculator is taken.
/// <see cref="Scope"/> is the GHG Protocol Scope 1/2/3 label.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record SimpleComponent(
    ComponentId Id,
    Applicability Applicability,
    EmissionScope Scope,
    string QuantitySource,
    IReadOnlyList<SimpleComponentVersion> Versions) : Component(Id, Applicability)
{
    /// <summary>
    /// Picks the <see cref="SimpleComponentVersion"/> in effect at the given moment. Returns the
    /// version whose <c>Validity.ValidFrom &lt;= timestamp &lt; Validity.ValidTo</c>. The
    /// non-overlap invariant on Versions guarantees at most one match — the absence of a covering
    /// version is a state error and throws.
    /// </summary>
    [DomainBehavior]
    public SimpleComponentVersion VersionAt(Timestamp timestamp) =>
        Versions.First(v => v.Validity.IsValidAt(timestamp));
}
