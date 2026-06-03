using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using Xunit;

namespace FootprintCalculation.Tests.EmissionMeasurement;

public class EmissionFactorPortTests
{
    [Fact]
    public void GetActiveRate_returns_rate_active_at_timestamp()
    {
        var componentId = new ComponentId("raw-material");
        var rateJanFeb = new EmissionFactorRate(0.5m, "kgCO2/kg");
        var rateMarApr = new EmissionFactorRate(0.7m, "kgCO2/kg");

        var janFeb = Validity.Between(
            new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero)),
            new Timestamp(new DateTimeOffset(2026, 3, 1, 0, 0, 0, TimeSpan.Zero)));
        var marApr = Validity.Between(
            new Timestamp(new DateTimeOffset(2026, 3, 1, 0, 0, 0, TimeSpan.Zero)),
            new Timestamp(new DateTimeOffset(2026, 5, 1, 0, 0, 0, TimeSpan.Zero)));

        var entries = new Dictionary<ComponentId, IReadOnlyList<EmissionFactorEntry>>
        {
            [componentId] = new List<EmissionFactorEntry>
            {
                new(rateJanFeb, janFeb),
                new(rateMarApr, marApr),
            },
        };

        var port = new EmissionFactorPort(entries);

        var resultJan = port.GetActiveRate(
            componentId,
            new Timestamp(new DateTimeOffset(2026, 1, 15, 0, 0, 0, TimeSpan.Zero)));
        var resultApr = port.GetActiveRate(
            componentId,
            new Timestamp(new DateTimeOffset(2026, 4, 15, 0, 0, 0, TimeSpan.Zero)));

        Assert.Equal(rateJanFeb, resultJan);
        Assert.Equal(rateMarApr, resultApr);
    }

    [Fact]
    public void GetActiveRate_throws_when_component_unknown()
    {
        var port = new EmissionFactorPort(
            new Dictionary<ComponentId, IReadOnlyList<EmissionFactorEntry>>());

        Assert.Throws<KeyNotFoundException>(() =>
            port.GetActiveRate(
                new ComponentId("missing"),
                new Timestamp(new DateTimeOffset(2026, 1, 15, 0, 0, 0, TimeSpan.Zero))));
    }

    [Fact]
    public void GetActiveRate_throws_when_no_entry_covers_timestamp()
    {
        var componentId = new ComponentId("raw-material");
        var rate = new EmissionFactorRate(0.5m, "kgCO2/kg");
        var janFeb = Validity.Between(
            new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero)),
            new Timestamp(new DateTimeOffset(2026, 3, 1, 0, 0, 0, TimeSpan.Zero)));

        var entries = new Dictionary<ComponentId, IReadOnlyList<EmissionFactorEntry>>
        {
            [componentId] = new List<EmissionFactorEntry>
            {
                new(rate, janFeb),
            },
        };

        var port = new EmissionFactorPort(entries);

        Assert.Throws<InvalidOperationException>(() =>
            port.GetActiveRate(
                componentId,
                new Timestamp(new DateTimeOffset(2026, 4, 1, 0, 0, 0, TimeSpan.Zero))));
    }
}
