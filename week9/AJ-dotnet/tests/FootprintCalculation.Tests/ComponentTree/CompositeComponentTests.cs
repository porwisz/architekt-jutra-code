using FootprintCalculation.ComponentTree;
using NoesisVision.Annotations.Domain;
using Xunit;

namespace FootprintCalculation.Tests.ComponentTree;

public class CompositeComponentTests
{
    /// <summary>
    /// Rule: KompozytSumujeEmisjeAktywnychDzieci (Computation).
    /// At this Building Block level the rule expresses a STRUCTURAL contract: a
    /// <see cref="CompositeComponent"/> must be able to address its children, preserving order
    /// and identity. The actual SumOf computation is performed by <c>FootprintFacade</c> and is
    /// exercised at the Facade level in a later batch.
    /// In this test we nest two <see cref="CompositeComponent"/> instances inside a parent
    /// composite (since <c>SimpleComponent</c> is not yet available in this batch) and verify
    /// that the <c>Children</c> list preserves both order and identity.
    /// </summary>
    [Fact]
    [Scenario("KompozytSumujeEmisjeAktywnychDzieci")]
    public void KompozytSumujeEmisjeAktywnychDzieci_StructuralContract()
    {
        var childA = new CompositeComponent(
            new ComponentId("transport"),
            AlwaysApplicable.Instance,
            new List<Component>());
        var childB = new CompositeComponent(
            new ComponentId("cold-storage"),
            RequiresRefrigeration.Instance,
            new List<Component>());

        var parent = new CompositeComponent(
            new ComponentId("root"),
            AlwaysApplicable.Instance,
            new List<Component> { childA, childB });

        Assert.Equal(2, parent.Children.Count);
        Assert.Same(childA, parent.Children[0]);
        Assert.Same(childB, parent.Children[1]);
        Assert.Equal(new ComponentId("transport"), parent.Children[0].Id);
        Assert.Equal(new ComponentId("cold-storage"), parent.Children[1].Id);
    }
}
