using FootprintCalculation.FootprintReporting;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class CalculateTotalFootprintQualityTests
{
    /// <summary>
    /// QA: Other:BreakdownAuditCompleteness.
    /// Every leaf has non-null EmissionFactorUsed and FactorValidFrom; sum of every composite's
    /// children's KgCO2 equals the composite's KgCO2 with exact decimal equality.
    /// </summary>
    [Fact]
    [QualityAttribute("Other:BreakdownAuditCompleteness")]
    public void BreakdownAuditCompleteness_AcrossEntireTree()
    {
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Szczecin_Summer();

        var breakdown = facade.CalculateTotalFootprint(parameters);

        AssertNodeIsAuditComplete(breakdown.Root);
    }

    private static void AssertNodeIsAuditComplete(BreakdownNode node)
    {
        switch (node)
        {
            case SimpleBreakdownNode leaf:
                Assert.NotNull(leaf.EmissionFactorUsed);
                Assert.NotNull(leaf.FactorValidFrom);
                Assert.NotNull(leaf.Scope);
                break;

            case CompositeBreakdownNode composite:
                {
                    decimal sum = 0m;
                    foreach (var child in composite.Children)
                    {
                        AssertNodeIsAuditComplete(child);
                        sum += child.KgCO2.Value;
                    }
                    // Exact decimal equality — no tolerance.
                    Assert.Equal(composite.KgCO2.Value, sum);
                    break;
                }

            default:
                throw new InvalidOperationException(
                    $"Unknown node type: {node.GetType().Name}");
        }
    }
}
