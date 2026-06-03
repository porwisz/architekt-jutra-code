using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class SimpleBreakdownNodeTests
{
    /// <summary>
    /// Rule: KażdyLiśćBreakdownNiesieFactorAuditu (Structure).
    /// C# nullable-reference-types enforce non-null at compile time; this test documents the
    /// audit invariant — a constructed <see cref="SimpleBreakdownNode"/> exposes non-null
    /// <see cref="SimpleBreakdownNode.EmissionFactorUsed"/> and
    /// <see cref="SimpleBreakdownNode.FactorValidFrom"/>.
    /// </summary>
    [Fact]
    [Scenario("KażdyLiśćBreakdownNiesieFactorAuditu")]
    public void KażdyLiśćBreakdownNiesieFactorAuditu()
    {
        var node = new SimpleBreakdownNode(
            new ComponentId("raw-material"),
            new KgCO2(0.9m),
            new EmissionScope(1),
            new EmissionFactorRate(0.9m, "kgCO2/kg"),
            new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero)));

        Assert.NotNull(node.EmissionFactorUsed);
        Assert.NotNull(node.FactorValidFrom);
    }

    /// <summary>
    /// Scenario: LiśćBreakdownZawieraFactorIValidFrom.
    /// Given: product OFB-330, for the raw-material the applicable version has
    /// rate=0.9 kgCO2/kg and validFrom=2026-01-01.
    /// When: a <see cref="SimpleBreakdownNode"/> is constructed with those values
    /// (the full <c>CalculateTotalFootprint</c> path through the facade is verified in a later
    /// batch).
    /// Then: the leaf exposes <c>EmissionFactorUsed.Value == 0.9m</c> and
    /// <c>FactorValidFrom.Value == 2026-01-01T00:00:00Z</c>.
    /// </summary>
    [Fact]
    [Scenario("LiśćBreakdownZawieraFactorIValidFrom")]
    public void LiśćBreakdownZawieraFactorIValidFrom()
    {
        var validFrom = new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var rate = new EmissionFactorRate(0.9m, "kgCO2/kg");

        var node = new SimpleBreakdownNode(
            new ComponentId("raw-material"),
            new KgCO2(0.9m),
            new EmissionScope(1),
            rate,
            validFrom);

        Assert.Equal(0.9m, node.EmissionFactorUsed.Value);
        Assert.Equal(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero), node.FactorValidFrom.Value);
    }
}
