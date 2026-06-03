using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.EmissionMeasurement;

public class EmissionCalculatorTests
{
    [Fact]
    [Scenario("EmisjaJestIloczynemStawkiIKwantyfikacji")]
    public void Calculate_returns_rate_times_quantity()
    {
        var calculator = new EmissionCalculator();
        var rate = new EmissionFactorRate(0.5m, "kgCO2/kg");
        var quantity = new Quantity(10m, "kg");

        var result = calculator.Calculate(rate, quantity);

        Assert.Equal(5.0m, result.Value);
    }

    [Fact]
    [Scenario("EmisjaJestIloczynemStawkiIKwantyfikacji")]
    public void Calculate_realistic_warehouse_refrigeration_example()
    {
        var calculator = new EmissionCalculator();
        var rate = new EmissionFactorRate(0.080m, "kgCO2/kWh");
        var quantity = new Quantity(14m, "kWh");

        var result = calculator.Calculate(rate, quantity);

        Assert.Equal(1.12m, result.Value);
    }

    [Fact]
    [Scenario("EmisjaJestIloczynemStawkiIKwantyfikacji")]
    public void Calculate_with_zero_rate_returns_zero()
    {
        var calculator = new EmissionCalculator();
        var rate = new EmissionFactorRate(0m, "kgCO2/kg");
        var quantity = new Quantity(123.45m, "kg");

        var result = calculator.Calculate(rate, quantity);

        Assert.Equal(0m, result.Value);
    }

    [Fact]
    [Scenario("EmisjaJestIloczynemStawkiIKwantyfikacji")]
    public void Calculate_with_zero_quantity_returns_zero()
    {
        var calculator = new EmissionCalculator();
        var rate = new EmissionFactorRate(7.89m, "kgCO2/kg");
        var quantity = new Quantity(0m, "kg");

        var result = calculator.Calculate(rate, quantity);

        Assert.Equal(0m, result.Value);
    }
}
