using FootprintCalculation.ComponentTree;
using FootprintCalculation.FootprintReporting;
using Xunit;

namespace FootprintCalculation.Tests.ComponentTree;

public class ComponentRepositoryTests
{
    [Fact]
    public void FindRootByProductId_returns_root_for_known_product()
    {
        var productId = new ProductId("yogurt-500g");
        var root = new CompositeComponent(
            new ComponentId("root"),
            AlwaysApplicable.Instance,
            new List<Component>());

        var repository = new ComponentRepository(
            new Dictionary<ProductId, CompositeComponent>
            {
                [productId] = root
            });

        var actual = repository.FindRootByProductId(productId);

        Assert.Same(root, actual);
    }

    [Fact]
    public void FindRootByProductId_throws_for_unknown_product()
    {
        var repository = new ComponentRepository(
            new Dictionary<ProductId, CompositeComponent>());

        var unknown = new ProductId("nonexistent-sku");

        var ex = Assert.Throws<KeyNotFoundException>(
            () => repository.FindRootByProductId(unknown));

        Assert.Contains("nonexistent-sku", ex.Message);
    }
}
