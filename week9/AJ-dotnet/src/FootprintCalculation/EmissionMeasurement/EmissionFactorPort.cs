using FootprintCalculation.ComponentTree;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Internal record bundling an <see cref="EmissionFactorRate"/> with its
/// <see cref="Validity"/> window. Used only by <see cref="EmissionFactorPort"/>
/// to keep the adapter independent of <c>SimpleComponent.versions</c>.
/// </summary>
internal sealed record EmissionFactorEntry(EmissionFactorRate Rate, Validity Validity);

/// <summary>
/// In-memory adapter for <see cref="IEmissionFactorPort"/>. For MVP it does not call the
/// real Emission Factor Management module — entries are supplied at construction time as a
/// dictionary keyed by <see cref="ComponentId"/>. The active entry at a given
/// <see cref="Timestamp"/> is selected via the half-open <c>[validFrom, validTo)</c>
/// semantics on <see cref="Validity"/>.
/// </summary>
[DddRepository]
[AdaptersLayer]
public sealed class EmissionFactorPort : IEmissionFactorPort
{
    private readonly IReadOnlyDictionary<ComponentId, IReadOnlyList<EmissionFactorEntry>> _entries;

    internal EmissionFactorPort(IReadOnlyDictionary<ComponentId, IReadOnlyList<EmissionFactorEntry>> entries)
    {
        _entries = entries ?? throw new ArgumentNullException(nameof(entries));
    }

    public EmissionFactorRate GetActiveRate(ComponentId componentId, Timestamp timestamp)
    {
        if (!_entries.TryGetValue(componentId, out var entries))
        {
            throw new KeyNotFoundException(
                $"No EmissionFactorRate entries registered for ComponentId '{componentId.Value}'.");
        }

        foreach (var entry in entries)
        {
            if (entry.Validity.IsValidAt(timestamp))
            {
                return entry.Rate;
            }
        }

        throw new InvalidOperationException(
            $"No active EmissionFactorRate for '{componentId.Value}' at {timestamp.Value:O}.");
    }
}
