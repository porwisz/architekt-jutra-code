using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.ComponentTree;

public class SimpleComponentTests
{
    private static SimpleComponentVersion VersionFor(Validity validity, decimal rate = 0.5m) =>
        new(
            "calc-emission",
            new EmissionFactorRate(rate, "kgCO2/kg"),
            validity,
            new Timestamp(new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero)));

    /// <summary>
    /// Rule: WersjeKomponentuNieMogąNakładaćSięWCzasie (Consistency).
    /// The engine assumes the <see cref="SimpleComponent.Versions"/> collection contains
    /// time-disjoint <see cref="Validity"/> intervals — that way <see cref="SimpleComponent.VersionAt"/>
    /// has at most one match. Overlap-rejection itself is a writer-side concern (handled when the
    /// catalogue is mutated) and is out of scope for this BB. Here we document the read-side
    /// expectation: given two NON-overlapping versions, VersionAt picks the correct one for each
    /// timestamp.
    /// </summary>
    [Fact]
    [Scenario("WersjeKomponentuNieMogąNakładaćSięWCzasie")]
    public void WersjeKomponentuNieMogąNakładaćSięWCzasie()
    {
        var boundary = new Timestamp(new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var v1 = VersionFor(Validity.Until(boundary), rate: 0.4m);
        var v2 = VersionFor(Validity.From(boundary), rate: 0.6m);

        var component = new SimpleComponent(
            new ComponentId("raw-material"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "rawMaterialKg",
            new List<SimpleComponentVersion> { v1, v2 });

        var inFirst = new Timestamp(new DateTimeOffset(2024, 6, 1, 0, 0, 0, TimeSpan.Zero));
        var inSecond = new Timestamp(new DateTimeOffset(2025, 6, 1, 0, 0, 0, TimeSpan.Zero));

        Assert.Same(v1, component.VersionAt(inFirst));
        Assert.Same(v2, component.VersionAt(inSecond));
        Assert.Same(v2, component.VersionAt(boundary));
    }

    /// <summary>
    /// Rule: VersionAtWybieraWersjęObowiązującąWMomencie (Computation). Happy path —
    /// <see cref="SimpleComponent.VersionAt"/> returns the version whose validity contains the
    /// timestamp (validFrom &lt;= t &lt; validTo).
    /// </summary>
    [Fact]
    [Scenario("VersionAtWybieraWersjęObowiązującąWMomencie")]
    public void VersionAt_returns_version_whose_validity_contains_timestamp()
    {
        var from = new Timestamp(new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var to = new Timestamp(new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var version = VersionFor(Validity.Between(from, to), rate: 0.42m);

        var component = new SimpleComponent(
            new ComponentId("processing"),
            AlwaysApplicable.Instance,
            new EmissionScope(2),
            "processedKg",
            new List<SimpleComponentVersion> { version });

        var atFrom = from;
        var inside = new Timestamp(new DateTimeOffset(2024, 6, 1, 0, 0, 0, TimeSpan.Zero));

        Assert.Same(version, component.VersionAt(atFrom));
        Assert.Same(version, component.VersionAt(inside));
    }

    /// <summary>
    /// Rule: VersionAtWybieraWersjęObowiązującąWMomencie (Computation). Error path — when no
    /// version covers the given timestamp (empty list, or timestamp outside every interval),
    /// <see cref="SimpleComponent.VersionAt"/> throws rather than returning a default.
    /// </summary>
    [Fact]
    [Scenario("VersionAtWybieraWersjęObowiązującąWMomencie")]
    public void VersionAt_throws_when_no_version_covers_timestamp()
    {
        var empty = new SimpleComponent(
            new ComponentId("packaging"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "packagingKg",
            new List<SimpleComponentVersion>());

        var anyTimestamp = new Timestamp(new DateTimeOffset(2025, 6, 1, 0, 0, 0, TimeSpan.Zero));

        Assert.Throws<InvalidOperationException>(() => empty.VersionAt(anyTimestamp));

        var from = new Timestamp(new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var to = new Timestamp(new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var version = VersionFor(Validity.Between(from, to));

        var withGap = new SimpleComponent(
            new ComponentId("packaging"),
            AlwaysApplicable.Instance,
            new EmissionScope(3),
            "packagingKg",
            new List<SimpleComponentVersion> { version });

        var outside = new Timestamp(new DateTimeOffset(2026, 6, 1, 0, 0, 0, TimeSpan.Zero));
        Assert.Throws<InvalidOperationException>(() => withGap.VersionAt(outside));
    }
}
