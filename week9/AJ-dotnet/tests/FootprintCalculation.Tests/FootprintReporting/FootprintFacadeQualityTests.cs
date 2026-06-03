using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class FootprintFacadeQualityTests
{
    /// <summary>
    /// QA: Other:Determinism.
    /// Two consecutive calls of CalculateTotalFootprint with identical FootprintParameters
    /// return FootprintBreakdown values whose record-equality compares equal — same KgCO2 at
    /// every node and same audit fields on every leaf.
    /// </summary>
    [Fact]
    [QualityAttribute("Other:Determinism")]
    public void Determinism_TwoCallsWithIdenticalParametersReturnEqualBreakdowns()
    {
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Szczecin_Summer();

        var first = facade.CalculateTotalFootprint(parameters);
        var second = facade.CalculateTotalFootprint(parameters);

        // Record equality is value-equality and recursive over IEnumerable<> by default in C# records.
        // The Children list is IReadOnlyList<BreakdownNode>; default record equality uses
        // reference equality on the list. We therefore compare structurally instead.
        AssertBreakdownsAreEqual(first, second);
    }

    /// <summary>
    /// QA: Other:HistoricalReproducibility.
    /// Computing a footprint anchored at timestamp T uses the version active at T. Adding new
    /// versions later (simulating a future change) doesn't affect the result for the original
    /// timestamp — versionAt(T) is anchored by validity intervals, not by the catalogue's size.
    /// </summary>
    [Fact]
    [QualityAttribute("Other:HistoricalReproducibility")]
    public void HistoricalReproducibility_NewVersionDoesNotAffectPastTimestamp()
    {
        var v1Start = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var v1End = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 4, 1, 0, 0, 0, TimeSpan.Zero));
        var v2End = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 7, 1, 0, 0, 0, TimeSpan.Zero));
        var v3End = Timestamp.MaxValue;

        var v1 = new SimpleComponentVersion(
            "calc-emission",
            new EmissionFactorRate(0.0005m, "kgCO2/km"),
            Validity.Between(v1Start, v1End),
            FacadeTestFixtures.DefinedAt);
        var v2 = new SimpleComponentVersion(
            "calc-emission",
            new EmissionFactorRate(0.0007m, "kgCO2/km"),
            Validity.Between(v1End, v2End),
            FacadeTestFixtures.DefinedAt);
        var v3 = new SimpleComponentVersion(
            "calc-emission",
            new EmissionFactorRate(0.0009m, "kgCO2/km"),
            Validity.Between(v2End, v3End),
            FacadeTestFixtures.DefinedAt);

        // Tree A — two versions only (v1 + v2).
        var treeWithTwoVersions = BuildTreeWithVersions(new[] { v1, v2 });

        // Tree B — same plus a future v3 simulating a "later" catalogue change.
        var treeWithThreeVersions = BuildTreeWithVersions(new[] { v1, v2, v3 });

        var anchorParameters = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Ts(new DateTimeOffset(2026, 2, 1, 0, 0, 0, TimeSpan.Zero)),
            ProductId: new ProductId("PROD"),
            MaterialWeight: new Quantity(1m, "kg"),
            SupplierDistance: new Quantity(1000m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(0m, "day"),
            RequiresRefrigeration: false);

        var facadeBefore = FacadeTestFixtures.BuildFacade(
            new Dictionary<string, CompositeComponent> { ["PROD"] = treeWithTwoVersions });
        var resultBefore = facadeBefore.CalculateTotalFootprint(anchorParameters);

        var facadeAfter = FacadeTestFixtures.BuildFacade(
            new Dictionary<string, CompositeComponent> { ["PROD"] = treeWithThreeVersions });
        var resultAfter = facadeAfter.CalculateTotalFootprint(anchorParameters);

        AssertBreakdownsAreEqual(resultBefore, resultAfter);

        // Sanity: the result is anchored on v1 (rate=0.0005).
        var leaf = FacadeTestFixtures.SimpleLeaf(resultBefore.Root.Children[0]);
        Assert.Equal(0.0005m, leaf.EmissionFactorUsed.Value);
        Assert.Equal(v1Start.Value, leaf.FactorValidFrom.Value);
    }

    private static CompositeComponent BuildTreeWithVersions(
        IEnumerable<SimpleComponentVersion> versions)
    {
        var transportLeg = new SimpleComponent(
            new ComponentId("transport-leg"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "supplierDistance",
            versions.ToList());

        return new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { transportLeg });
    }

    private static void AssertBreakdownsAreEqual(FootprintBreakdown a, FootprintBreakdown b)
    {
        Assert.Equal(a.Total.Value, b.Total.Value);
        AssertNodesAreEqual(a.Root, b.Root);
    }

    private static void AssertNodesAreEqual(BreakdownNode a, BreakdownNode b)
    {
        Assert.Equal(a.ComponentId, b.ComponentId);
        Assert.Equal(a.KgCO2.Value, b.KgCO2.Value);
        switch (a)
        {
            case SimpleBreakdownNode la:
                {
                    var lb = Xunit.Assert.IsType<SimpleBreakdownNode>(b);
                    Assert.Equal(la.Scope, lb.Scope);
                    Assert.Equal(la.EmissionFactorUsed, lb.EmissionFactorUsed);
                    Assert.Equal(la.FactorValidFrom, lb.FactorValidFrom);
                    break;
                }

            case CompositeBreakdownNode ca:
                {
                    var cb = Xunit.Assert.IsType<CompositeBreakdownNode>(b);
                    Assert.Equal(ca.Children.Count, cb.Children.Count);
                    for (int i = 0; i < ca.Children.Count; i++)
                    {
                        AssertNodesAreEqual(ca.Children[i], cb.Children[i]);
                    }
                    break;
                }

            default:
                throw new InvalidOperationException(
                    $"Unknown node type: {a.GetType().Name}");
        }
    }
}
