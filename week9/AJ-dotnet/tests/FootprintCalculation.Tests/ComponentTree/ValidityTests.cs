using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.ComponentTree;

public class ValidityTests
{
    [Fact]
    [Scenario("WażnośćJestPółotwartymPrzedziałem")]
    public void WażnośćJestPółotwartymPrzedziałem()
    {
        var from = new Timestamp(new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var to = new Timestamp(new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero));
        var validity = Validity.Between(from, to);

        var beforeFrom = new Timestamp(new DateTimeOffset(2023, 12, 31, 23, 59, 59, TimeSpan.Zero));
        var atFrom = from;
        var betweenFromAndTo = new Timestamp(new DateTimeOffset(2024, 6, 1, 0, 0, 0, TimeSpan.Zero));
        var atTo = to;
        var afterTo = new Timestamp(new DateTimeOffset(2025, 1, 1, 0, 0, 1, TimeSpan.Zero));

        Assert.False(validity.IsValidAt(beforeFrom));
        Assert.True(validity.IsValidAt(atFrom));
        Assert.True(validity.IsValidAt(betweenFromAndTo));
        Assert.False(validity.IsValidAt(atTo));
        Assert.False(validity.IsValidAt(afterTo));
    }
}
