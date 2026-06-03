using FootprintCalculation.FootprintReporting;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// In-memory, dictionary-backed adapter for <see cref="IComponentRepository"/>.
/// Suitable for the MVP and tests; production callers can substitute a DB- or
/// Product-Catalog-backed implementation behind the same port.
/// </summary>
[DddRepository]
[AdaptersLayer]
public sealed class ComponentRepository : IComponentRepository
{
    private readonly IReadOnlyDictionary<ProductId, CompositeComponent> _trees;

    public ComponentRepository(IReadOnlyDictionary<ProductId, CompositeComponent> trees)
    {
        ArgumentNullException.ThrowIfNull(trees);
        _trees = trees;
    }

    public CompositeComponent FindRootByProductId(ProductId productId)
    {
        ArgumentNullException.ThrowIfNull(productId);

        if (_trees.TryGetValue(productId, out var root))
        {
            return root;
        }

        throw new KeyNotFoundException(
            $"No component tree registered for productId '{productId.Value}'.");
    }
}
