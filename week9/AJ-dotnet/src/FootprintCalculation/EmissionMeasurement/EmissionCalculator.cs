using NoesisVision.Annotations.Domain;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Pure, stateless, deterministic calculator that computes the emission for a single leaf
/// as <c>rate.Value * quantity.Value</c>. No business conditions, no clock, no I/O.
/// Unit-of-measure consistency between <see cref="EmissionFactorRate"/> and <see cref="Quantity"/>
/// is the configuration layer's responsibility, not the calculator's.
/// </summary>
[DddDomainService]
[EntitiesLayer]
public sealed class EmissionCalculator
{
    [DomainBehavior]
    public KgCO2 Calculate(EmissionFactorRate rate, Quantity quantity)
    {
        return new KgCO2(rate.Value * quantity.Value);
    }
}
