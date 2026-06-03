using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;

namespace FootprintCalculation.Tests.FootprintReporting;

/// <summary>
/// Shared deterministic builders for the two product trees used by FootprintFacade scenarios.
/// Builders intentionally produce trees + parameters whose internal numbers are chosen for
/// exact decimal arithmetic. The aggregate totals match the design-doc Examples
/// (Szczecin summer = 5.37, Warszawa winter ≈ 2.9025, CAW-042 = 0.8 etc.).
/// </summary>
internal static class FacadeTestFixtures
{
    public static readonly Timestamp WinterStart =
        Ts(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero));

    public static readonly Timestamp SeasonBoundary =
        Ts(new DateTimeOffset(2026, 4, 1, 0, 0, 0, TimeSpan.Zero));

    public static readonly Timestamp SummerEnd =
        Ts(new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero));

    public static readonly Timestamp DefinedAt =
        Ts(new DateTimeOffset(2025, 12, 1, 0, 0, 0, TimeSpan.Zero));

    public static readonly Timestamp Summer_2026_07_15 =
        Ts(new DateTimeOffset(2026, 7, 15, 0, 0, 0, TimeSpan.Zero));

    public static readonly Timestamp Winter_2026_01_15 =
        Ts(new DateTimeOffset(2026, 1, 15, 0, 0, 0, TimeSpan.Zero));

    public static Timestamp Ts(DateTimeOffset d) => new(d);

    /// <summary>Build the OFB-330 tree (Organic Frozen Berries 500g).</summary>
    public static CompositeComponent BuildOfb330Tree()
    {
        var materials = new SimpleComponent(
            new ComponentId("materials"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "materialWeight",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(2.7m, "kgCO2/kg"),
                    Validity.Always(),
                    DefinedAt),
            });

        var transport = new CompositeComponent(
            new ComponentId("transport"),
            AlwaysApplicable.Instance,
            new List<Component>
            {
                new SimpleComponent(
                    new ComponentId("supplier-leg"),
                    AlwaysApplicable.Instance,
                    new EmissionScope(3),
                    "supplierDistance",
                    new List<SimpleComponentVersion>
                    {
                        new(
                            "calc-emission",
                            new EmissionFactorRate(0.0005m, "kgCO2/km"),
                            Validity.Always(),
                            DefinedAt),
                    }),
                new SimpleComponent(
                    new ComponentId("destination-leg"),
                    AlwaysApplicable.Instance,
                    new EmissionScope(3),
                    "destinationDistance",
                    new List<SimpleComponentVersion>
                    {
                        new(
                            "calc-emission",
                            new EmissionFactorRate(0.0008m, "kgCO2/km"),
                            Validity.Always(),
                            DefinedAt),
                    }),
                new SimpleComponent(
                    new ComponentId("last-mile-leg"),
                    AlwaysApplicable.Instance,
                    new EmissionScope(3),
                    "lastMileDistance",
                    new List<SimpleComponentVersion>
                    {
                        new(
                            "calc-emission",
                            new EmissionFactorRate(0.0016m, "kgCO2/km"),
                            Validity.Always(),
                            DefinedAt),
                    }),
            });

        var packaging = new SimpleComponent(
            new ComponentId("packaging"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "materialWeight",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(0.84m, "kgCO2/kg"),
                    Validity.Always(),
                    DefinedAt),
            });

        var warehouseRefrigeration = new SimpleComponent(
            new ComponentId("warehouse-refrigeration"),
            AlwaysApplicable.Instance,
            new EmissionScope(2),
            "storageDays",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(0.045m, "kgCO2/kg/day"),
                    Validity.Between(WinterStart, SeasonBoundary),
                    DefinedAt),
                new(
                    "calc-emission",
                    new EmissionFactorRate(0.080m, "kgCO2/kg/day"),
                    Validity.Between(SeasonBoundary, SummerEnd),
                    DefinedAt),
            });

        var coldStorage = new CompositeComponent(
            new ComponentId("cold-storage"),
            RequiresRefrigeration.Instance,
            new List<Component> { warehouseRefrigeration });

        return new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { materials, transport, packaging, coldStorage });
    }

    /// <summary>Build the CAW-042 tree (Classic Analog Watch — no cold-storage).</summary>
    public static CompositeComponent BuildCaw042Tree()
    {
        var materials = new SimpleComponent(
            new ComponentId("materials"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "materialWeight",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(0.4m, "kgCO2/kg"),
                    Validity.Always(),
                    DefinedAt),
            });

        var transport = new CompositeComponent(
            new ComponentId("transport"),
            AlwaysApplicable.Instance,
            new List<Component>
            {
                new SimpleComponent(
                    new ComponentId("supplier-leg"),
                    AlwaysApplicable.Instance,
                    new EmissionScope(3),
                    "supplierDistance",
                    new List<SimpleComponentVersion>
                    {
                        new(
                            "calc-emission",
                            new EmissionFactorRate(0.001m, "kgCO2/km"),
                            Validity.Always(),
                            DefinedAt),
                    }),
            });

        var packaging = new SimpleComponent(
            new ComponentId("packaging"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "materialWeight",
            new List<SimpleComponentVersion>
            {
                new(
                    "calc-emission",
                    new EmissionFactorRate(0.4m, "kgCO2/kg"),
                    Validity.Always(),
                    DefinedAt),
            });

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
                            new EmissionFactorRate(0.045m, "kgCO2/kg/day"),
                            Validity.Always(),
                            DefinedAt),
                    }),
            });

        return new CompositeComponent(
            new ComponentId("product-footprint"),
            AlwaysApplicable.Instance,
            new List<Component> { materials, transport, packaging, coldStorage });
    }

    public static FootprintParameters Szczecin_Summer() => new(
        Timestamp: Summer_2026_07_15,
        ProductId: new ProductId("OFB-330"),
        MaterialWeight: new Quantity(0.5m, "kg"),
        SupplierDistance: new Quantity(2800m, "km"),
        DestinationDistance: new Quantity(570m, "km"),
        LastMileDistance: new Quantity(15m, "km"),
        StorageDays: new Quantity(21.5m, "day"),
        RequiresRefrigeration: true);

    public static FootprintParameters Warszawa_Winter() => new(
        Timestamp: Winter_2026_01_15,
        ProductId: new ProductId("OFB-330"),
        MaterialWeight: new Quantity(0.5m, "kg"),
        SupplierDistance: new Quantity(920m, "km"),
        DestinationDistance: new Quantity(15m, "km"),
        LastMileDistance: new Quantity(5m, "km"),
        StorageDays: new Quantity(14.5m, "day"),
        RequiresRefrigeration: true);

    public static FootprintParameters Caw042_Default() => new(
        Timestamp: Summer_2026_07_15,
        ProductId: new ProductId("CAW-042"),
        MaterialWeight: new Quantity(0.5m, "kg"),
        SupplierDistance: new Quantity(400m, "km"),
        DestinationDistance: new Quantity(15m, "km"),
        LastMileDistance: new Quantity(5m, "km"),
        StorageDays: new Quantity(0m, "day"),
        RequiresRefrigeration: false);

    /// <summary>
    /// Tiny in-memory <see cref="IComponentRepository"/> serving a fixed product → root mapping.
    /// </summary>
    public sealed class InMemoryComponentRepository : IComponentRepository
    {
        private readonly Dictionary<string, CompositeComponent> _trees;

        public InMemoryComponentRepository(IDictionary<string, CompositeComponent> trees)
        {
            _trees = new Dictionary<string, CompositeComponent>(trees);
        }

        public CompositeComponent FindRootByProductId(ProductId productId)
        {
            if (_trees.TryGetValue(productId.Value, out var root))
            {
                return root;
            }

            throw new InvalidOperationException(
                $"No tree registered for productId '{productId.Value}'.");
        }
    }

    /// <summary>Stub port; FootprintFacade uses VersionAt for the MVP and never calls this.</summary>
    public sealed class StubEmissionFactorPort : IEmissionFactorPort
    {
        public EmissionFactorRate GetActiveRate(ComponentId componentId, Timestamp timestamp) =>
            throw new InvalidOperationException(
                "StubEmissionFactorPort.GetActiveRate must not be invoked — the facade resolves rates via SimpleComponent.VersionAt for the MVP.");
    }

    public static FootprintFacade BuildFacade(
        Dictionary<string, CompositeComponent>? trees = null)
    {
        var repo = new InMemoryComponentRepository(
            trees ?? new Dictionary<string, CompositeComponent>
            {
                ["OFB-330"] = BuildOfb330Tree(),
                ["CAW-042"] = BuildCaw042Tree(),
            });
        return new FootprintFacade(repo, new StubEmissionFactorPort(), new EmissionCalculator());
    }

    public static SimpleBreakdownNode SimpleLeaf(BreakdownNode node)
    {
        return Xunit.Assert.IsType<SimpleBreakdownNode>(node);
    }

    public static CompositeBreakdownNode Composite(BreakdownNode node)
    {
        return Xunit.Assert.IsType<CompositeBreakdownNode>(node);
    }

    public static BreakdownNode FindChild(CompositeBreakdownNode parent, string id)
    {
        return parent.Children.First(c => c.ComponentId.Value == id);
    }

    public static bool HasChild(CompositeBreakdownNode parent, string id)
    {
        return parent.Children.Any(c => c.ComponentId.Value == id);
    }
}
