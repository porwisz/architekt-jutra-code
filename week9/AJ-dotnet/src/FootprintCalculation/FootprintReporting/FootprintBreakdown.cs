using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Wrapper around the root of the result tree — the top-level artefact returned by
/// <c>FootprintFacade</c>. Holds <see cref="Root"/> (always a <see cref="CompositeBreakdownNode"/> —
/// the root is the 'product-footprint') and <see cref="Total"/> (a copy of <c>Root.KgCO2</c> for
/// consumer/UI convenience, unchanging relative to the root).
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record FootprintBreakdown(CompositeBreakdownNode Root, KgCO2 Total)
{
    /// <summary>
    /// Convenience factory that enforces the "Total is a copy of root.KgCO2" invariant at
    /// construction. The primary constructor still allows direct construction; consumers using it
    /// MUST keep <see cref="Total"/> consistent with <see cref="Root"/>.<see cref="CompositeBreakdownNode.KgCO2"/>.
    /// </summary>
    internal static FootprintBreakdown FromRoot(CompositeBreakdownNode root) =>
        new(root, root.KgCO2);
}
