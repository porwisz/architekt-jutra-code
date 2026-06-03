using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class CalculateUnitFootprintTests
{
    /// <summary>
    /// Build a minimal product whose CalculateTotalFootprint result equals `desiredTotal` with
    /// the supplied materialWeight. Single composite root with a single materials leaf:
    /// rate * materialWeight = desiredTotal ⇒ rate = desiredTotal / materialWeight.
    /// </summary>
    private static (FootprintFacade facade, FootprintParameters parameters) Build(
        decimal materialWeightKg,
        decimal desiredTotal)
    {
        var rate = materialWeightKg == 0m ? 0m : desiredTotal / materialWeightKg;
        var materials = new SimpleComponent(
            new ComponentId("materials"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "materialWeight",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(rate, "kgCO2/kg"),
                    Validity.Always(),
                    FacadeTestFixtures.DefinedAt),
            });

        var root = new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { materials });

        var trees = new Dictionary<string, CompositeComponent> { ["PROD"] = root };
        var facade = FacadeTestFixtures.BuildFacade(trees);

        var parameters = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Summer_2026_07_15,
            ProductId: new ProductId("PROD"),
            MaterialWeight: new Quantity(materialWeightKg, "kg"),
            SupplierDistance: new Quantity(0m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(0m, "day"),
            RequiresRefrigeration: false);

        return (facade, parameters);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_PerHundredGrams_WithHalfKilogram")]
    public void CalculateUnitFootprint_PerHundredGrams_WithHalfKilogram()
    {
        var (facade, parameters) = Build(materialWeightKg: 0.5m, desiredTotal: 2.0m);

        // Sanity: total must equal 2.0 for the scenario premise.
        Assert.Equal(2.0m, facade.CalculateTotalFootprint(parameters).Total.Value);

        var unit = facade.CalculateUnitFootprint(parameters);

        Assert.Equal(0.4m, unit.Total.Value);
        // Each node scaled by 1/(0.5 * 10) = 0.2.
        var leaf = FacadeTestFixtures.SimpleLeaf(unit.Root.Children[0]);
        Assert.Equal(0.4m, leaf.KgCO2.Value); // 2.0 * 0.2
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_PerHundredGrams_WithOneKilogram")]
    public void CalculateUnitFootprint_PerHundredGrams_WithOneKilogram()
    {
        var (facade, parameters) = Build(materialWeightKg: 1.0m, desiredTotal: 5.0m);

        Assert.Equal(5.0m, facade.CalculateTotalFootprint(parameters).Total.Value);

        var unit = facade.CalculateUnitFootprint(parameters);

        Assert.Equal(0.5m, unit.Total.Value);
        // Scale = 1/(1.0 * 10) = 0.1.
        var leaf = FacadeTestFixtures.SimpleLeaf(unit.Root.Children[0]);
        Assert.Equal(0.5m, leaf.KgCO2.Value);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_PerHundredGrams_WithQuarterKilogramNonRound")]
    public void CalculateUnitFootprint_PerHundredGrams_WithQuarterKilogramNonRound()
    {
        var (facade, parameters) = Build(materialWeightKg: 0.25m, desiredTotal: 0.6m);

        Assert.Equal(0.6m, facade.CalculateTotalFootprint(parameters).Total.Value);

        var unit = facade.CalculateUnitFootprint(parameters);

        Assert.Equal(0.24m, unit.Total.Value);
        // Scale = 1/(0.25 * 10) = 0.4.
        var leaf = FacadeTestFixtures.SimpleLeaf(unit.Root.Children[0]);
        Assert.Equal(0.24m, leaf.KgCO2.Value);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_Throws_WhenMaterialWeightKgIsZero")]
    public void CalculateUnitFootprint_Throws_WhenMaterialWeightKgIsZero()
    {
        var (facade, _) = Build(materialWeightKg: 1.0m, desiredTotal: 1.0m);

        // Build a parameters value with MaterialWeight = 0 explicitly.
        var parameters = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Summer_2026_07_15,
            ProductId: new ProductId("PROD"),
            MaterialWeight: new Quantity(0m, "kg"),
            SupplierDistance: new Quantity(0m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(0m, "day"),
            RequiresRefrigeration: false);

        var ex = Assert.Throws<ArgumentException>(() => facade.CalculateUnitFootprint(parameters));
        Assert.Equal("parameters", ex.ParamName);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_Throws_WhenMaterialWeightKgIsNegative")]
    public void CalculateUnitFootprint_Throws_WhenMaterialWeightKgIsNegative()
    {
        var (facade, _) = Build(materialWeightKg: 1.0m, desiredTotal: 1.0m);

        var parameters = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Summer_2026_07_15,
            ProductId: new ProductId("PROD"),
            MaterialWeight: new Quantity(-0.5m, "kg"),
            SupplierDistance: new Quantity(0m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(0m, "day"),
            RequiresRefrigeration: false);

        var ex = Assert.Throws<ArgumentException>(() => facade.CalculateUnitFootprint(parameters));
        Assert.Equal("parameters", ex.ParamName);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_Throws_WhenParametersAreNull")]
    public void CalculateUnitFootprint_Throws_WhenParametersAreNull()
    {
        var (facade, _) = Build(materialWeightKg: 1.0m, desiredTotal: 1.0m);

        var ex = Assert.Throws<ArgumentNullException>(() => facade.CalculateUnitFootprint(null!));
        Assert.Equal("parameters", ex.ParamName);
    }

    /// <summary>
    /// Empty breakdown: when every component is inapplicable for the given parameters,
    /// CalculateTotalFootprint returns total=KgCO2(0) with empty children, and
    /// CalculateUnitFootprint divides 0 by a positive weight returning 0 without throwing.
    /// </summary>
    [Fact]
    [Scenario("CalculateUnitFootprint_ReturnsZero_WhenBreakdownIsEmpty")]
    public void CalculateUnitFootprint_ReturnsZero_WhenBreakdownIsEmpty()
    {
        // Build a tree whose only child is a cold-storage subtree, then call with
        // requiresRefrigeration=false ⇒ the subtree is excluded ⇒ root has 0 children.
        var coldStorage = new CompositeComponent(
            new ComponentId("cold-storage"),
            RequiresRefrigeration.Instance,
            new List<Component>
            {
                new SimpleComponent(
                    new ComponentId("warehouse-refrigeration"),
                    AlwaysApplicable.Instance,
                    new EmissionScope(2),
                    "storageDays",
                    new List<SimpleComponentVersion>
                    {
                        new(
                            "calc-emission",
                            new EmissionFactorRate(1m, "kgCO2/kg/day"),
                            Validity.Always(),
                            FacadeTestFixtures.DefinedAt),
                    }),
            });

        var root = new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { coldStorage });

        var trees = new Dictionary<string, CompositeComponent> { ["PROD"] = root };
        var facade = FacadeTestFixtures.BuildFacade(trees);

        var parameters = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Summer_2026_07_15,
            ProductId: new ProductId("PROD"),
            MaterialWeight: new Quantity(0.5m, "kg"),
            SupplierDistance: new Quantity(0m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(10m, "day"),
            RequiresRefrigeration: false);

        var total = facade.CalculateTotalFootprint(parameters);
        Assert.Equal(0m, total.Total.Value);
        Assert.Empty(total.Root.Children);

        var unit = facade.CalculateUnitFootprint(parameters);
        Assert.Equal(0m, unit.Total.Value);
        Assert.Empty(unit.Root.Children);
    }

    [Fact]
    [Scenario("CalculateUnitFootprint_PreservesPrecision_WithVerySmallMaterialWeight")]
    public void CalculateUnitFootprint_PreservesPrecision_WithVerySmallMaterialWeight()
    {
        var (facade, parameters) = Build(materialWeightKg: 0.0001m, desiredTotal: 0.001m);

        Assert.Equal(0.001m, facade.CalculateTotalFootprint(parameters).Total.Value);

        var unit = facade.CalculateUnitFootprint(parameters);

        // Scale = 1 / (0.0001 * 10) = 1000. unit total = 0.001 * 1000 = 1.0 exactly.
        Assert.Equal(1.0m, unit.Total.Value);
    }

    /// <summary>
    /// Invariant: unit.Total × MaterialWeightKg × 10 == total. Exact decimal equality.
    /// Verifies rule UnitFootprintIsTotalScaledToReferenceUnit.
    /// </summary>
    [Fact]
    [Scenario("CalculateUnitFootprint_IsConsistent_WithCalculateTotalFootprint")]
    public void CalculateUnitFootprint_IsConsistent_WithCalculateTotalFootprint()
    {
        // Use the OFB-330 / Szczecin fixture (a realistic multi-leaf tree).
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Szczecin_Summer();

        var total = facade.CalculateTotalFootprint(parameters);
        var unit = facade.CalculateUnitFootprint(parameters);

        Assert.Equal(
            total.Total.Value,
            unit.Total.Value * parameters.MaterialWeight.Value * 10m);
    }
}
