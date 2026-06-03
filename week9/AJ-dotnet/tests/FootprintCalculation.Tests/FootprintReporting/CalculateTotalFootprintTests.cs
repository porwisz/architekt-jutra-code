using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.FootprintReporting;

public class CalculateTotalFootprintTests
{
    /// <summary>
    /// Scenario: MrożoneOwoceSzczecinLato.
    /// Given product OFB-330 (materialWeight=0.5, supplierDistance=2800, requiresRefrigeration=true)
    /// and warehouse-refrigeration summer version rate=0.080 [2026-04-01, 2026-10-01).
    /// When CalculateTotalFootprint(timestamp=2026-07-15, productId=OFB-330) is invoked
    /// Then the engine resolves summer versions, activates cold-storage, and returns a breakdown
    /// where materials=1.35 + transport=1.880 + packaging=0.42 + cold-storage=1.72, total=5.37.
    /// </summary>
    [Fact]
    [Scenario("MrożoneOwoceSzczecinLato")]
    public void MrożoneOwoceSzczecinLato()
    {
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Szczecin_Summer();

        var breakdown = facade.CalculateTotalFootprint(parameters);

        Assert.Equal(5.37m, breakdown.Total.Value);
        Assert.Equal("product-footprint", breakdown.Root.ComponentId.Value);
        Assert.Equal(4, breakdown.Root.Children.Count);

        var materials = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(breakdown.Root, "materials"));
        Assert.Equal(1.35m, materials.KgCO2.Value);

        var transport = FacadeTestFixtures.Composite(
            FacadeTestFixtures.FindChild(breakdown.Root, "transport"));
        Assert.Equal(1.880m, transport.KgCO2.Value);

        var packaging = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(breakdown.Root, "packaging"));
        Assert.Equal(0.42m, packaging.KgCO2.Value);

        var coldStorage = FacadeTestFixtures.Composite(
            FacadeTestFixtures.FindChild(breakdown.Root, "cold-storage"));
        Assert.Equal(1.72m, coldStorage.KgCO2.Value);

        // Summer warehouse-refrigeration version (rate=0.080, validFrom=2026-04-01) selected.
        var warehouse = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(coldStorage, "warehouse-refrigeration"));
        Assert.Equal(0.080m, warehouse.EmissionFactorUsed.Value);
        Assert.Equal(FacadeTestFixtures.SeasonBoundary.Value, warehouse.FactorValidFrom.Value);
    }

    /// <summary>
    /// Scenario: MrożoneOwoceWarszawaZima. Same product, different context — different destination
    /// distances and a winter timestamp ⇒ different active cold-storage version.
    /// Expected exact total = 2.9025 (within ~0.1% of the doc's nominal 2.90; the small delta is
    /// the cost of constraining all internal rates to exact decimal arithmetic).
    /// </summary>
    [Fact]
    [Scenario("MrożoneOwoceWarszawaZima")]
    public void MrożoneOwoceWarszawaZima()
    {
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Warszawa_Winter();

        var breakdown = facade.CalculateTotalFootprint(parameters);

        Assert.Equal(2.9025m, breakdown.Total.Value);
        Assert.Equal(4, breakdown.Root.Children.Count);

        var materials = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(breakdown.Root, "materials"));
        Assert.Equal(1.35m, materials.KgCO2.Value);

        var transport = FacadeTestFixtures.Composite(
            FacadeTestFixtures.FindChild(breakdown.Root, "transport"));
        Assert.Equal(0.480m, transport.KgCO2.Value);

        var packaging = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(breakdown.Root, "packaging"));
        Assert.Equal(0.42m, packaging.KgCO2.Value);

        var coldStorage = FacadeTestFixtures.Composite(
            FacadeTestFixtures.FindChild(breakdown.Root, "cold-storage"));
        Assert.Equal(0.6525m, coldStorage.KgCO2.Value);

        var warehouse = FacadeTestFixtures.SimpleLeaf(
            FacadeTestFixtures.FindChild(coldStorage, "warehouse-refrigeration"));
        // Winter version (rate=0.045, validFrom=2026-01-01) selected.
        Assert.Equal(0.045m, warehouse.EmissionFactorUsed.Value);
        Assert.Equal(FacadeTestFixtures.WinterStart.Value, warehouse.FactorValidFrom.Value);
    }

    /// <summary>
    /// Scenario: ProduktNiechłodzonyNieZawieraColdStorage.
    /// Given product CAW-042 with requiresRefrigeration=false.
    /// Then the cold-storage subtree is COMPLETELY ABSENT (not present as KgCO2=0).
    /// Verifies rule NieaktywnyKompozytJestWykluczonyZBreakdown.
    /// </summary>
    [Fact]
    [Scenario("ProduktNiechłodzonyNieZawieraColdStorage")]
    public void ProduktNiechłodzonyNieZawieraColdStorage()
    {
        var facade = FacadeTestFixtures.BuildFacade();
        var parameters = FacadeTestFixtures.Caw042_Default();

        var breakdown = facade.CalculateTotalFootprint(parameters);

        Assert.Equal(0.8m, breakdown.Total.Value);

        // Only materials, transport, packaging — NO cold-storage entry at all.
        Assert.Equal(3, breakdown.Root.Children.Count);
        Assert.True(FacadeTestFixtures.HasChild(breakdown.Root, "materials"));
        Assert.True(FacadeTestFixtures.HasChild(breakdown.Root, "transport"));
        Assert.True(FacadeTestFixtures.HasChild(breakdown.Root, "packaging"));
        Assert.False(FacadeTestFixtures.HasChild(breakdown.Root, "cold-storage"));
    }

    /// <summary>
    /// Scenario: ZmianaWspółczynnikaZachowujeStareWyniki.
    /// Given transport-leg has v1 (rate=0.00058, validity=[2026-01-01, 2026-03-15))
    /// and v2 (rate=0.00062, validity=[2026-03-15, MAX)).
    /// When CalculateTotalFootprint is invoked with timestamp=2026-02-01 and again with 2026-04-01
    /// Then the first uses v1 (rate=0.00058) and the second uses v2 (rate=0.00062);
    /// results are reproducible for their respective timestamps.
    /// Verifies rule FootprintZAOkreślonegoMomentuUżywaWersjiZTamtejChwili.
    /// </summary>
    [Fact]
    [Scenario("ZmianaWspółczynnikaZachowujeStareWyniki")]
    public void ZmianaWspółczynnikaZachowujeStareWyniki()
    {
        var v1Start = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var v1End = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 3, 15, 0, 0, 0, TimeSpan.Zero));
        var v2End = Timestamp.MaxValue;

        var v1 = new SimpleComponentVersion(
            "calc-emission",
            new EmissionFactorRate(0.00058m, "kgCO2/kg/km"),
            Validity.Between(v1Start, v1End),
            FacadeTestFixtures.DefinedAt);
        var v2 = new SimpleComponentVersion(
            "calc-emission",
            new EmissionFactorRate(0.00062m, "kgCO2/kg/km"),
            Validity.Between(v1End, v2End),
            FacadeTestFixtures.DefinedAt);

        var transportLeg = new SimpleComponent(
            new ComponentId("transport-leg"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "supplierDistance",
            new List<SimpleComponentVersion> { v1, v2 });

        var root = new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { transportLeg });

        var trees = new Dictionary<string, CompositeComponent> { ["PROD-XYZ"] = root };
        var facade = FacadeTestFixtures.BuildFacade(trees);

        var baseParams = new FootprintParameters(
            Timestamp: FacadeTestFixtures.Ts(new DateTimeOffset(2026, 2, 1, 0, 0, 0, TimeSpan.Zero)),
            ProductId: new ProductId("PROD-XYZ"),
            MaterialWeight: new Quantity(1m, "kg"),
            SupplierDistance: new Quantity(1000m, "km"),
            DestinationDistance: new Quantity(0m, "km"),
            LastMileDistance: new Quantity(0m, "km"),
            StorageDays: new Quantity(0m, "day"),
            RequiresRefrigeration: false);

        // First call with timestamp inside v1 validity.
        var earlyResult = facade.CalculateTotalFootprint(baseParams);
        var earlyLeaf = FacadeTestFixtures.SimpleLeaf(earlyResult.Root.Children[0]);
        Assert.Equal(0.00058m, earlyLeaf.EmissionFactorUsed.Value);
        Assert.Equal(v1Start.Value, earlyLeaf.FactorValidFrom.Value);
        Assert.Equal(0.58m, earlyLeaf.KgCO2.Value); // 0.00058 * 1000

        // Second call with timestamp inside v2 validity.
        var lateParams = baseParams with
        {
            Timestamp = FacadeTestFixtures.Ts(new DateTimeOffset(2026, 4, 1, 0, 0, 0, TimeSpan.Zero)),
        };
        var lateResult = facade.CalculateTotalFootprint(lateParams);
        var lateLeaf = FacadeTestFixtures.SimpleLeaf(lateResult.Root.Children[0]);
        Assert.Equal(0.00062m, lateLeaf.EmissionFactorUsed.Value);
        Assert.Equal(v1End.Value, lateLeaf.FactorValidFrom.Value);
        Assert.Equal(0.62m, lateLeaf.KgCO2.Value); // 0.00062 * 1000

        // The two results are different (rule is exercised).
        Assert.NotEqual(earlyLeaf.KgCO2.Value, lateLeaf.KgCO2.Value);
    }
}
