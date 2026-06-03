using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Identifier of a product in the catalog. The value is supplied by the application layer;
/// the FootprintCalculation BC does not manage the product catalog.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record ProductId
{
    public string Value { get; init; }

    public ProductId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("ProductId value must not be null or whitespace.", nameof(value));
        Value = value;
    }
}
