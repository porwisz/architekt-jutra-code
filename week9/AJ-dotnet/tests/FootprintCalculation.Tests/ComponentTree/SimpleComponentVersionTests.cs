using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.ComponentTree;

public class SimpleComponentVersionTests
{
    /// <summary>
    /// Rule: WersjaKomponentuJestNiezmienna (Structure).
    /// Demonstrates that <see cref="SimpleComponentVersion"/> is immutable: the record exposes
    /// only <c>init</c>-style positional properties (no setters — a compile-time guarantee), and
    /// using the record <c>with</c>-expression returns a NEW instance, leaving the original
    /// unchanged.
    /// </summary>
    [Fact]
    [Scenario("WersjaKomponentuJestNiezmienna")]
    public void WersjaKomponentuJestNiezmienna()
    {
        var originalRate = new EmissionFactorRate(0.9m, "kgCO2/kg");
        var validity = Validity.From(new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero)));
        var definedAt = new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var version = new SimpleComponentVersion("calc-emission", originalRate, validity, definedAt);

        var newRate = new EmissionFactorRate(1.2m, "kgCO2/kg");
        var mutated = version with { Rate = newRate };

        Assert.NotSame(version, mutated);
        Assert.Equal(0.9m, version.Rate.Value);
        Assert.Equal(1.2m, mutated.Rate.Value);
        Assert.Equal("calc-emission", version.CalculatorId);
        Assert.Equal(validity, version.Validity);
        Assert.Equal(definedAt, version.DefinedAt);
    }
}
