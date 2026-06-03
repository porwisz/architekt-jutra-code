using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class BreakdownNodeTests
{
    private sealed record TestBreakdownNode(ComponentId ComponentId, KgCO2 KgCO2)
        : BreakdownNode(ComponentId, KgCO2);

    [Fact]
    [Scenario("KażdyWęzełBreakdownMaWartośćKgCO2")]
    public void KażdyWęzełBreakdownMaWartośćKgCO2()
    {
        var node = new TestBreakdownNode(new ComponentId("raw-material"), new KgCO2(1.5m));

        Assert.NotNull(node.KgCO2);
        Assert.Equal(1.5m, node.KgCO2.Value);
        Assert.NotNull(node.ComponentId);
    }
}
