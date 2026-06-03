using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Calculation-context object for a footprint. Carries every value needed to resolve component
/// versions (<see cref="Timestamp"/>), evaluate applicability predicates
/// (<see cref="RequiresRefrigeration"/>), and feed the emission calculators.
/// </summary>
[DddValueObject]
[EntitiesLayer]
public sealed record FootprintParameters(
    Timestamp Timestamp,
    ProductId ProductId,
    Quantity MaterialWeight,
    Quantity SupplierDistance,
    Quantity DestinationDistance,
    Quantity LastMileDistance,
    Quantity StorageDays,
    bool RequiresRefrigeration);
